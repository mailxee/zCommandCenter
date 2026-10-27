# Manage Everything — Architecture

Manage Everything (formerly xCommandCenter) is a personal command center for Android. Seven tabs (Tasks, Goals, Habits, Plans, Notes,
Books, Quotes) share one local Room database. Any item can be linked to any other item and
tagged, and one tag filter applies to every tab. Plans form a tree with completion, tasks can
repeat, and habits track streaks. Settings can export everything to an Excel workbook, import
one back, or delete everything.

- Language: Kotlin · UI: Jetpack Compose + Material 3 · Storage: Room (SQLite), local only
- minSdk 26, compile/target SDK 37 (the template's values)
- Reminders: `AlarmManager` → `BroadcastReceiver` → notification
- Launcher icon: the xCommandCenter logo as an adaptive icon (§2.14)

---

## 1. Layers

```
ui/        Compose screens + ViewModels      (state holders, user intents)
  │ calls
data/repository  Repositories               (the only API the UI uses for data)
  │ calls
data/dao + data/entity + data/db  Room      (SQL, schema, seed)
domain/    Pure Kotlin rules                 (streaks, recurrence, tab order, plan tree)
reminder/  AlarmManager + receivers          (works with the app closed)
```

- **ui** never touches DAOs. Each screen has one ViewModel; ViewModels expose `StateFlow`s (from
  Room `Flow`s) and Compose state for form fields.
- **data** owns persistence. Repositories and `@Transaction` DAO methods add behavior that is
  more than SQL: `TaskRepository` keeps alarms in step with stored reminders and `TaskDao.complete`
  creates a repeating task's next occurrence, `PlanRepository` rejects cycles and `PlanDao`
  enforces the completion rule, `LinkRepository` normalizes link pairs, and `TagDao` reuses tag
  names and deletes unused tags. Small UI settings (tab order, collapsed plans) live in
  DataStore under `data/prefs`.
- **domain** has no Android or Room imports, so its rules are unit-tested on the JVM.
- **reminder** is called by `TaskRepository` and by system broadcasts.

Dependencies are wired by hand in `AppContainer`, created once in `XCommandCenterApp`.

---

## 2. Design decisions

Each decision lists what was chosen, the alternatives considered, and why each alternative was rejected.

### 2.1 Single link table with normalized, undirected pairs

**Chosen:** one `links` table: `(id, aType, aId, bType, bId, createdAt)`. Each link is stored
**once**. Before insert, the pair is ordered so that `(aType, aId)` sorts before
`(bType, bId)` (`LinkEntity.between`). A unique index on `(aType, aId, bType, bId)` makes
duplicates impossible. A second index on `(bType, bId)` keeps lookups from the B side fast.
Queries match the item on either side.

| Alternative | Why rejected |
|---|---|
| Store each link twice (A→B and B→A) | Two writes per link that must stay in sync. A half-written or half-deleted pair would show the link on only one page. |
| One join table per pair of types (`task_book`, `note_quote`, …) | 8 linkable types give 36 tables, plus code for each. The plan requires a single many-to-many table. |
| Real foreign keys on the link table | SQLite foreign keys can't point at "one of eight tables". You would need eight nullable FK columns per side, which is clumsy and still needs per-type code. |
| Unordered storage with `(A,B) OR (B,A)` duplicate checks in code | A race or bug could still insert both orderings. Normalizing lets the database enforce uniqueness. |

**Trade-off:** there is no foreign key, so the database will not remove links when an item is
deleted. Every item DAO therefore has a `@Transaction delete(id)` that deletes the item's links
(and tags, §2.13) and the item together (§2.4).

### 2.2 `item_index` database view for titles across tables

**Chosen:** a Room `@DatabaseView` (`ItemIndexEntry`) that `UNION ALL`s every table into
`(type, id, title, subtitle)`. Linked-item lists, the per-row "Linked:" line on every tab and the
link picker's search are each a single SQL query over this view.

| Alternative | Why rejected |
|---|---|
| Resolve titles in Kotlin (read the links, then load each item by type) | Needs per-type code and N+1 queries. It also wouldn't be reactive: renaming a book wouldn't refresh lists that show it. |
| Store a copy of the title in the link row | Copies go stale when an item is renamed. |
| One generic `items` table for all types | Loses typed columns (due dates, read counts…) and makes every feature query awkward. |

Room tracks the tables behind a view, so a `Flow` over the view re-emits whenever any of those
tables changes.

### 2.3 Chapters as a linkable type that is not a tab

**Chosen:** `ItemType.CHAPTER` is a full link endpoint (`isTab = false`, `tab = BOOK`). In the
picker, choosing the Books tab lists books **and** chapters. A chapter's subtitle in the view is
its book's title, so a linked chapter shows as "Chapter in *Atomic Habits*". Linked items are
grouped by tab, so chapters appear under Books.

| Alternative | Why rejected |
|---|---|
| Link to the book only, store the chapter number in the link | The link table would need an extra column only books use. |
| An eighth "Chapters" tab | The plan defines seven tabs and says chapters belong to their book. |

### 2.4 Deleting items: transactional DAO methods

**Chosen:** each DAO has `@Transaction suspend fun delete(id)` that deletes the item's links and
tags, then the item. Special cases:
- **Book:** also deletes the links and tags of all its chapters. The chapters themselves go through the foreign key's `ON DELETE CASCADE`.
- **Habit:** marks cascade through their foreign key.
- **Plan:** child plans are moved up to the deleted plan's parent, so a subtree is never silently deleted.
- **Chapter:** links and tags removed, then the remaining chapters are renumbered.

| Alternative | Why rejected |
|---|---|
| SQLite triggers | Hidden from the Kotlin code, and their behaviour on cascaded deletes is easy to get wrong. |
| Two repository calls without a transaction | A crash between them would leave orphan links or tags. |
| Cascade-delete child plans | Deleting a parent would silently destroy work. Moving children up loses nothing. |
| Leave child plans at top level (`SET NULL`) | Throws away where they sat in the tree. The FK still uses `SET NULL` as a safety net. |

### 2.5 Habits: streak rules and storage

**Chosen semantics (confirmed with the user):**
- **Positive:** a mark means "done". Unmarked occurrences are *unknown*, never success. The streak is the number of consecutive marked occurrences, walking back from today. If today is unmarked it is skipped, because the day isn't over; any earlier unmarked occurrence ends the streak.
- **Negative:** this is a way of *counting*, not a bad habit. The user marks only **misses**, which suits habits that are already mostly formed. Unmarked occurrences count as done. The streak is the number of scheduled occurrences from today back to the most recent miss, or back to the habit's start date if there has never been one.
- Only **scheduled** occurrences count. Unscheduled days are skipped; they neither count nor break a streak.
- Totals are the number of marks ("done N times" / "missed N times").
- Monthly on day 29–31 falls on the last day of shorter months. "Every N days" counts from the habit's start date.

**Storage:** recurrence is stored as flat columns on `habits` (`recurrenceType`, `weekdays`
bitmask, `dayOfMonth`, `intervalDays`) and exposed as a sealed `Recurrence` type through
`Recurrence.of`, which repeating tasks use too (§2.12); the editor (`RecurrenceFields`) is
shared as well. Marks go in
`habit_marks(habitId, date)` with a composite primary key, so a date can be marked only once.
Streaks are computed on read by the pure function `HabitStats.streak`.

| Alternative | Why rejected |
|---|---|
| Store the streak as a column | It goes stale at midnight and whenever a past mark is edited. Computing it is cheap. |
| Recurrence as a JSON string | Needs a JSON library or hand parsing, and the columns can't be queried. |
| Separate table per recurrence kind | Four tables for one small value. |
| Store every day's status, including "unknown" | That is millions of rows over time, and "unknown" is just the absence of a row. |

### 2.6 Reminders: `AlarmManager` exact alarms with a fallback

**Chosen:** `ReminderScheduler` sets one alarm per task (request code = task id) with
`setExactAndAllowWhileIdle`. If the user hasn't granted exact alarms (Android 12+), it falls back
to `setAndAllowWhileIdle`, which is still delivered in Doze but may be a few minutes late. The
alarm fires `ReminderReceiver`, which posts the notification without starting any UI, so it works
with the app closed.

`RescheduleReceiver` re-registers all pending reminders after a reboot, an app update, or the
exact-alarm permission being granted. The app also does this at every start. Saving, completing
or deleting a task updates or cancels its alarm (`TaskRepository.syncReminder`).
`POST_NOTIFICATIONS` is requested at launch on Android 13+. Settings shows both permissions'
status, with buttons to fix them.

| Alternative | Why rejected |
|---|---|
| WorkManager `OneTimeWorkRequest` with an initial delay | WorkManager is built for deferrable work. Its timing can drift by many minutes under Doze and app-standby buckets, which is wrong for a reminder set to 9:00. |
| `setAlarmClock` | Shows an alarm-clock icon in the status bar and needs the same exact-alarm permission. Meant for wake-up alarms. |
| `USE_EXACT_ALARM` (granted automatically) | Google Play restricts it to alarm-clock and calendar apps. `SCHEDULE_EXACT_ALARM` plus the fallback is the policy-safe choice. |
| A foreground service | Heavy, needs a permanent notification, and isn't needed to fire a single event. |

### 2.7 Seed data inside Room's `onCreate`, as raw inserts

**Chosen:** `SeedData.insert` runs from `RoomDatabase.Callback.onCreate`. That runs once, inside
the transaction that creates the schema, so seeding is all-or-nothing and never repeats. IDs are
explicit so the seed links can refer to them. Dates are relative to the install day, so due dates
and streaks look current.

| Alternative | Why rejected |
|---|---|
| Insert through the DAOs from a coroutine launched in `onCreate` | It runs after the transaction, so a crash in between leaves a half-seeded database that is never re-seeded. DAOs also can't be used safely during `onCreate`. |
| A "seeded" flag in preferences | Two sources of truth: clearing one but not the other re-seeds or skips seeding. |
| A prepackaged `.db` asset (`createFromAsset`) | A binary file to maintain, with dates fixed at build time. |

### 2.8 Tab order (and other UI settings) in DataStore Preferences

**Chosen:** a comma-separated list of tab names under one key. `TabOrder.parse` ignores unknown
names and appends missing tabs, so adding a tab in a later version can't hide it. The default
is the enum order, with Quotes last (pinned by a test). Settings reorders by drag and drop (§2.20). The same preferences file (`SettingsStore.kt`, one DataStore
instance per process) also holds the set of collapsed plans (§2.11).

| Alternative | Why rejected |
|---|---|
| A Room table | Settings aren't domain data and would need a schema migration for every change. |
| `SharedPreferences` | Synchronous API on the main thread, with no `Flow`. DataStore is the current replacement. |
| Storing ordinals | Breaks silently if the enum order changes. |
| Drag-and-drop reordering | Needs extra gesture code or a library, and is less accessible than buttons for seven rows. |

### 2.9 UI structure

- **One `NavHost`, one route for all items:** `item/{type}/{id}?parent={parent}`. `id = 0` means
  "new". A single route lets any linked item open, whatever its type, from any screen.
  - *Rejected:* type-safe `@Serializable` routes need the kotlinx-serialization plugin for little gain over one route.
  - *Rejected:* a separate route per type duplicates the same three arguments eight times.
- **`ItemDetailViewModel` base class:** holds the id, the linked items and tags `Flow`s,
  link/unlink, tag/untag, picker search, and the message/closed state that every detail screen
  shares. `DetailScaffold`, `TagsSection` and `LinkedItemsSection` are the matching UI.
  - *Rejected:* copying this logic into each of the eight screens.
- **Explicit save, two buttons:** the save button saves and stays on the page (a snackbar says
  "Saved"); the ✓ saves and pops the screen, back to wherever it was opened from (a tab keeps its
  selection, a linked item's page shows again). A new item gets an id when first saved. Linking
  and tagging are enabled after that, because both need a real id. Completing a plan is the one exception:
  it applies at once, outside the form (§2.11).
  - *Rejected:* auto-saving every keystroke creates empty items whenever the user backs out of a "new" screen.
  - *Rejected:* creating the item when the screen opens has the same problem.
- **Surviving process death:** after the first save, the new id is written to the screen's
  `SavedStateHandle`, so a restored screen reloads the saved item. A reminder notification's intent
  carries a one-time token, and `MainActivity` remembers the last token it handled. A tap is then
  honoured even when Android recreates the activity from saved state, and isn't replayed on
  rotation.
  - *Rejected:* acting only on a launch without saved state; this missed taps that restore an old task.
  - *Rejected:* clearing the extra from the intent; the system's copy of the intent still has it after process death.
- **Lists show tags and links inline:** every row shows its tags (`#name`) and ends with
  "Linked: …", each from one query over all rows, so each tab shows how its items are organised.
- **Chapter reorder** is by drag and drop (§2.20) and renumbers positions `0..n-1` in a transaction.
- **Icons** are ten small vector drawables. *Rejected:* the Compose material-icons artifacts, a
  large dependency for ten glyphs.

### 2.10 Dependency injection by hand

**Chosen:** `AppContainer` builds the database, repositories and scheduler. A
`viewModelFactory` (`AppViewModelProvider`) creates ViewModels from it.

| Alternative | Why rejected |
|---|---|
| Hilt / Dagger | Adds annotation processing and build complexity for about a dozen objects. |
| Koin | A runtime service locator with no compile-time checks. Not needed at this size. |
| Singletons in `object`s | Hidden global state, harder to replace in tests. |

### 2.11 Plan completion and the Plans outline

**Chosen (confirmed with the user):** a `completed` flag on `plans`, with one rule: a plan
is completed only if every plan below it is completed.
- Ticking a plan with open subplans is refused, and a dialog offers to complete it
  together with its whole subtree (`PlanDao.complete` / `completeWithSubplans`).
- Anything that leaves an open plan under a completed one reopens the completed ancestors:
  unticking a subplan, adding a child, or moving an open plan under a completed parent
  (`PlanDao.reopen`, `saveDetails`). The rule therefore always holds.
- Completion is applied at once, from the list checkbox or the detail screen, not on ✓. Saving
  the form keeps the stored flag, so a form opened before a toggle can't undo it.
- The Plans tab hides completed plans unless "Completed plans" is on (not persisted, like
  the task sort). Hiding never orphans an open plan, because of the rule.

**Plans tab as an outline (confirmed with the user):** each plan's linked tasks are listed
right below it, ahead of its subplans, as outlined cards with their own checkbox. A task linked
to several plans appears under each. Open tasks come first, and completed tasks are hidden unless
the "Completed tasks" chip is on. The plan's "Linked:" line leaves tasks out, since they are
rows of their own. A chevron on any plan with visible tasks or subplans collapses all of
them; a collapsed plan says what it hides ("2 subplans · 1 task hidden"). Collapsed ids are
kept in DataStore (`CollapsedPlansRepository`) so the outline survives restarts; the two
"Completed" chips reset each launch, like the task sort. The rows are built in
`HomeViewModel.planTree` from the existing plan, task and link flows, with
`PlanTree.flatten(…, collapsed)` skipping what is under collapsed plans; no new query.

| Alternative | Why rejected |
|---|---|
| Independent flags per plan | The user wants a parent to count as done only when its subplans are. |
| Completing a parent silently completes its subtree | Too easy to close work by accident; the user asked for a block plus an explicit "complete all". |
| Refusing to reopen a subplan under a completed parent | Forces the user to reopen from the top down; reopening the ancestors is what the rule implies. |
| `fallbackToDestructiveMigration` | Would wipe the user's data on upgrade. |

### 2.12 Repeating tasks: completing creates the next occurrence

**Chosen (confirmed with the user):** a task can carry the same schedule types as a habit (daily,
weekly on chosen days, monthly, every N days), stored as nullable flat columns on `tasks` and
read through the shared `Recurrence.of`. Ticking a repeating task completes it and inserts a copy
due on `Recurrence.nextAfter(dueDate)`, the first scheduled day after the old due date, counting
the schedule from that date. The reminder moves by the same number of days, and the copy gets the
same links and tags, so it stays under its plan and in its filters (`TaskDao.complete`, one
transaction). The completed task stays as history and gets `nextCreated = true`, so unticking and
ticking it again doesn't make a second copy. A repeating task must have a due date. Completing from
the form goes through the same path (`TaskRepository.save` routes a change of `completed` to
`setCompleted`). The Tasks tab filters by All / Repeating / One-off; like the sort, the filter
resets each launch.

| Alternative | Why rejected |
|---|---|
| Roll the same task's due date forward | The user wanted finished occurrences kept as history. |
| Generate future occurrences ahead of time | Clutters every list and needs a horizon and cleanup. |
| A separate "series" table | One nullable schedule on the task is enough while copies only need to inherit it. |

### 2.13 Tags

**Chosen (confirmed with the user):** `tags(id, name)` with a case-insensitive unique name (NOCASE
column), and `item_tags(type, itemId, tagId)` with a composite primary key. Like links, the item
side can't have a foreign key, so every item DAO's delete also deletes the item's tag rows. The tag
side cascades. Removing a tag from its last item deletes the tag. Tags left behind by deleted items
are never offered, because the filter bar and suggestions list only tags in use. All eight item
types can be tagged from a "Tags" card on their detail screen, after the first save.

One shared filter bar under the tab row applies to every tab. An item shows only if it carries
**all** the selected tags. On Plans, matching plans and tasks show together with the
plans above them, so the tree still reads. Every row shows its tags (`#name`). The selection
is in memory, and selected tags that are no longer in use drop out, so a filter can't get stuck on.

| Alternative | Why rejected |
|---|---|
| Tags as a comma-separated text column on each table | No uniqueness, renames touch every row, and matching needs string parsing in SQL. |
| Tags as a kind of link target | Links are between real items. Tags would show up in "Linked items" and the picker. |
| A filter per tab | The user chose one shared filter. |

### 2.14 Launcher icon

**Chosen:** an adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`).
The foreground is the logo artwork (the Z with the orange corner, book, bulb and calendar) cropped
from the user's design, as one PNG per density (`mipmap-*dpi/ic_launcher_foreground.png`, 108dp
canvas). The artwork fills the middle 66dp, so no launcher mask (circle, squircle, rounded square)
clips it, and its outer band fades out. The background layer is a vector with the logo tile's dark
vertical gradient (`#333741` to `#151822`), extended to the full canvas, so the fade has no visible
edge. The template's robot foreground and the legacy per-density `ic_launcher*.webp` images were
removed: minSdk is 26, so the adaptive icon is always used. There is no monochrome layer yet, so
Android 13+ themed icons show the full-colour logo.

| Alternative | Why rejected |
|---|---|
| Use the whole rounded tile as the foreground | The launcher's own mask would wrap a second, clipped outline around it. |
| Flat background colour | The artwork's background is a gradient, so a flat colour would show a box around it. |
| Redraw the logo as a vector | Loses the original's shading; the bitmap at five densities is sharp on every screen. |
| Monochrome layer from the bitmap | A themed icon is a one-colour silhouette; the bitmap's alpha would give a solid square. |

### 2.15 Excel export and import, and "Delete everything"

**Chosen (confirmed with the user):** Settings has a Data section with three actions.
- **Export to Excel** writes a `.xlsx` workbook through the system "save" picker (Storage Access
  Framework, so no storage permission). One sheet per tab in the user's tab order, then
  Chapters, Habit history, Links and Tags, so the file is a full backup. Each sheet has a bold,
  frozen header. Values are readable: ISO dates, local "yyyy-MM-dd HH:mm" times, enum labels
  ("Medium", "Every N days"), weekdays as "Mon, Sat". Links and tags carry the items' titles as
  read-only context columns.
- **Import from Excel** replaces all data, after a confirmation. The whole workbook is parsed and
  checked first (`BackupFormat.fromSheets`); a problem stops the import with a message naming the
  sheet and row, and the current data stays. Then `BackupDao.replaceAll` deletes and inserts
  everything in one transaction. Columns are found by header, so reordering them in Excel is fine.
  A blank ID gets a new id. Excel date serial numbers are accepted. Rows that point at missing
  items (a link to an unknown task) are skipped and counted. Plans are inserted parents-first
  for the foreign key, and a completed plan with an open child is reopened (§2.11). Alarms are
  cancelled before and re-registered after; collapsed-plan ids are cleared, since they may
  name other plans now.
- **Delete everything** (red, with a confirmation) deletes every row in every table, cancels
  pending reminders and clears all saved settings. Seed data is not re-inserted, so every tab
  starts empty, with the default tab order.

The workbook is read and written by `Xlsx`, about 250 lines over `java.util.zip` and the JDK's DOM
parser. Writing uses inline strings, and whole numbers as numeric cells. Reading handles what
Excel, LibreOffice and Google Sheets save: shared strings, rich text, booleans and gaps. DOCTYPEs
are refused, so a crafted file can't pull in external entities.

| Alternative | Why rejected |
|---|---|
| Apache POI | Several megabytes and many transitive dependencies, some unsuitable for Android, for a few sheets of text. |
| fastexcel and similar small libraries | The reader side relies on StAX, which Android doesn't ship. |
| CSV files | One file per table, and no single file to hand to Excel; the user asked for one workbook. |
| Merge on import | Without stable ids across devices, duplicates can't be detected; the user chose replace. |
| Re-seed after "Delete everything" | The user wanted a truly empty app. |

### 2.16 Build configuration

- Gradle version catalog, AGP 9.4 with its built-in Kotlin, the Compose compiler plugin, and KSP
  for Room. KSP replaces kapt, which is deprecated and slower.
- `allWarningsAsErrors = true`, so the Definition of Done's "no warnings" stays true. Experimental
  APIs in use (Material 3 pickers, `FlowRow`, `flatMapLatest`) are opted in once, module-wide.
- Room exports its schema to `app/schemas/` so future migrations can be written and tested.
- The template's AppCompat/MDC dependencies were removed. The window theme is the platform
  `Theme.Material.Light.NoActionBar`; all styling is Compose Material 3.

---

### 2.17 Projects renamed to Plans, and a fresh schema

**Chosen (confirmed with the user):** the tab formerly called Projects is "Plans" everywhere: UI
texts, the Excel sheet, the `plans` table, the stored `ItemType.PLAN` name, and the code. There is
no backward compatibility: the schema was reset to version 1 with no migrations, and old workbooks
with a "Projects" sheet don't import. A build from before the rename must be uninstalled first.

| Alternative | Why rejected |
|---|---|
| Rename only what the user sees | Leaves two names for one thing in the code and the data. |
| A migration renaming the table and the stored type names | Not needed: the user reinstalls rather than upgrading. |

### 2.18 Manual order in every tab

**Chosen:** a `sortOrder` column (default 0) on each tab's table. Every tab query orders by
`sortOrder` first and then by its old key, so before any reordering nothing moves.
A reorder button in the top bar turns on reorder mode: every row shows a drag handle (§2.20).
When a row is dropped, the new order of the rows shown is applied with `ManualOrder.reorder`, which
keeps rows hidden by a filter in their slots; the whole list is then written back as 0..n-1 in one
transaction (`reorder` in each DAO). Plans move among their sibling
plans; open and completed tasks are moved separately, as the list keeps them apart. Tasks get a
third sort, "Custom", which uses `sortOrder`; the handles only appear in it, and turning on reorder
mode on the Tasks tab switches to it. The chosen task sort is now kept in DataStore.
New items go to the end, except on Notes and Quotes, which list newest first and put them at the
top. Saving an edited item keeps its place (the repositories copy the stored `sortOrder`).

| Alternative | Why rejected |
|---|---|
| One order table keyed by `(type, id)` | Another table to keep in step on delete and import, like links, for a single integer per row. |
| Fractional positions (insert between two values) | Saves a few writes per move, but lists are small and renumbering 0..n-1 keeps the values readable in the Excel export. |

### 2.19 Task checklists

**Chosen:** a `checklist_items` table (`taskId` with `ON DELETE CASCADE`, `position`, `text`,
`done`). The task form edits the checklist like its other fields: items are added, ticked, edited
and removed in the form and written with the task on save (`TaskDao.replaceChecklist`), so a new
task can have a checklist before it has an id. Ticking items never affects the task: a task can be
completed with open items. Completing a repeating task copies the checklist, unticked, to the next
occurrence. The Tasks and Plans tabs show "Checklist 2/5". Export and import use a "Checklists"
sheet.

| Alternative | Why rejected |
|---|---|
| Saving each tick at once | Mixes two save models on one form; the rest of the task form is saved with the ✓ button. |
| Storing the checklist as text in the task row | Can't be counted per task in SQL, and editing one item would rewrite them all anyway. |

### 2.20 Drag and drop

**Chosen (asked for by the user, replacing up/down arrows):** the `sh.calvin.reorderable` library.
Rows are dragged by a handle (a grip icon), so a tap on the row still opens it and scrolling
still works. The tab lists use its `LazyColumn` support; the Settings tab order and a book's
chapters, which sit in scrolling columns, use its `ReorderableColumn`.
- Each list keeps a local copy of its rows that follows the finger; the drop saves the new order,
  and the copy is replaced when the stored order comes back from Room.
- Only valid targets make way while dragging: tasks in the dragged task's group (open or
  completed), and plans with the dragged plan's parent. A dragged plan takes everything under it
  along (`ManualOrder.moveBlock`); the rows under it are hidden during the drag.
- Accessibility services can't drag, so each handle offers "Move up" and "Move down" actions that
  move the row one place (`HomeViewModel.move`, `ManualOrder.move`).

| Alternative | Why rejected |
|---|---|
| Writing the drag gesture by hand over `LazyColumn` | Auto-scroll at the edges, item offsets and animations are a lot of code to get right; the library is small, Compose-only and widely used. |
| Long-press anywhere on a row to drag | Clashes with tapping a row to open it and with the row's checkboxes and chips. |

### 2.21 Item styles per tab

**Chosen (asked for by the user):** each tab has an item style (a color from a fixed palette,
bold, italic, underline, and a font: default, serif, mono or cursive), set in Settings → Item styles.
An item's title is shown in its tab's style everywhere: its own tab, the tab row label, the "Linked:"
line on other tabs' rows (each linked title in its own style), linked-item lists on detail pages,
the link picker, a plan's parent and child lists, and the detail screen's title. Chapters follow
the Books style. Quotes keep italics as their default. Two things stay on top of a style: a done
item is struck through, and a critical open task is shown in red.
- `ItemStyle` (domain) is stored per tab in DataStore (`ItemStyleRepository`, one key per tab, so
  "Delete everything" resets them) and provided once at the root through `LocalItemStyles`, so no
  view model has to pass styles around.
- Each palette color has a darker shade for light surfaces and a lighter one for dark surfaces
  (`StyleColor.toColor`), so text stays readable in both themes and with wallpaper colors.

| Alternative | Why rejected |
|---|---|
| A free color picker | Any color can be unreadable on one of the themes; a fixed palette with two shades each can't. |
| Font size per tab | Rows of different heights make the lists and "Linked:" lines uneven. |
| Styles in the Room database | They are display settings, like the tab order, not data to export or link. |

### 2.22 Linked items as cards

**Chosen (confirmed with the user):** on a detail page, each linked item is shown as the same card
its own tab shows: title in its style, detail lines, tags, its other links, and the same control
(task and plan checkboxes, the habit's "today" chip), plus a button to remove the link. The section
sits right after the item's title and description fields (after Notes for a habit), below the
item's tags. A linked plan
that can't be ticked because of open subplans gets the same "complete all" dialog as on the Plans
tab.
- One `ItemCard` composable and one text builder per type (`taskCardText`, `habitCardText`, …,
  in `ui/common/ItemCard.kt`) are used by both the tabs and the detail pages, so they can't drift
  apart.
- `ItemCardSource` builds every item's card data from the repositories, as the tabs do, and
  `ItemDetailViewModel.linkedCards` picks the linked ones. A card's "Linked:" line leaves out the
  item whose page it is on. Chapters have no tab card; theirs names the book they are in.

| Alternative | Why rejected |
|---|---|
| Building the cards from `HomeViewModel` | It is scoped to the home screen and filtered by the tag filter; detail pages need every linked item. |
| Look-only cards without controls | The user wants linked items to work as they do in their tabs. |

### 2.23 Habit reminders, note lists and priority, book progress, web links

- **Habit reminders:** a habit can have a reminder time (`habits.reminderMinutes`). One alarm per
  habit holds its next reminder: the first scheduled day, from its start date, whose time is still
  ahead (`Recurrence.nextReminder`), so an "every N days" habit reminds only on the days a mark is
  due. When the alarm fires, `HabitRepository.reminderFired` schedules the next one and the
  notification shows only if today has no mark yet. Habit alarms use their own intent action and
  notification channel, are re-registered on boot and after an import, and a tap opens the habit
  (notifications now carry an item type and id, not just a task id).
- **Repeating tasks:** the next occurrence skips dates that have already passed
  (`Recurrence.nextFrom`), so completing an overdue task gives it a future due date, and its
  reminder, moved by the same number of days, keeps its time and its distance from the due date and
  actually fires. Previously a task completed late got a next occurrence, and a reminder, in the past.
- **Note lists:** `note_list_items` (`noteId` with `ON DELETE CASCADE`), edited in the note form and
  saved with it, like a task's checklist but with bullets instead of checkboxes. The two forms share
  `ListEditor`. Notes also have a priority, shown on their card (critical in red, as for tasks).
- **Book progress:** `books.readCount` and `books.internalized`, changed at once from the book page
  (like a chapter's), and never overwritten by saving the book's form. Chapters and books can clear
  their read count.
- **Web links:** `WebLinks.find` spots http(s):// and www. addresses. Card text shows them as
  tappable links; under a description field, the links in it are listed as tappable text, since
  tapping the field itself edits it.
- **Tag filter bar:** chips wrap onto more lines (`FlowRow`) instead of scrolling sideways.
- Schema v2 adds these columns and the table through `AutoMigration(1 → 2)`; the Excel workbook
  gains a "Note lists" sheet and Priority, Reminder, Read count and Internalized columns.

### 2.24 Completed tasks and the Notes filters

- **Completed tasks:** the Tasks tab has a "Completed tasks (n)" chip that shows or hides them;
  the choice is kept in DataStore with the task sort. "Delete completed" (after a confirmation)
  deletes every completed task, including any hidden by the filters, with its links, tags and
  checklist, in one transaction (`TaskDao.deleteCompleted`). A repeating task's open next
  occurrence is not completed, so it stays.
- **Notes:** a sort (Custom, or Priority with Critical first; notes of the same priority keep their
  custom order) and priority filter chips (any combination; none shows every note). As with tasks,
  notes can only be dragged in the custom order, and turning on reorder mode on the Notes tab
  switches to it. Both are in memory, like the repeat filter.
- **Chapters:** the read count can also be cleared from the book page's chapter rows.

## 3. Walkthrough: marking a habit done from the Habits tab

The user taps the **"Done today"** chip on *Read 20 pages*.

1. **Tap → ViewModel.** In `HabitsTab` (`ui/home/TabLists.kt`), the chip's `onClick` calls `HomeViewModel.toggleHabitToday(habit.id)`.
2. **ViewModel → repository.** `toggleHabitToday` launches a coroutine in `viewModelScope` and calls `HabitRepository.toggleMark(habitId, LocalDate.now())`.
3. **Repository → DAO.** This forwards to `HabitDao.toggleMark`, a `@Transaction` method. It counts existing marks for that habit and date. If there are none, it runs `insertMark(HabitMarkEntity(habitId, date))`; otherwise `deleteMark(...)`. Room runs it on its query executor, off the main thread.
4. **Database write.** SQLite inserts a row into `habit_marks`. The `Converters` class stores the date as an epoch day. When the transaction commits, Room's `InvalidationTracker` marks the `habit_marks` table as changed.
5. **Invalidation → Flow.** `HabitDao.observeAllMarks()` watches `habit_marks`, so Room re-runs it and emits the new list. `HabitRepository.observeAllMarks()` maps it to `Map<habitId, Set<LocalDate>>`.
6. **Recompute.** In `HomeViewModel.habits`, `combine(habits, marks)` emits again. For each habit it calls the pure `HabitStats.streak(...)` and counts the marks, producing a new `HabitSummary` (streak 7, done 7 times, `markedToday = true`) in a `StateFlow`.
7. **Screen update.** `HabitsTab` collects that `StateFlow` with `collectAsStateWithLifecycle()`. The new value recomposes the row: the chip shows as selected and the text reads "Streak 7 · done 7 times".

The UI never updates itself optimistically: the database is the single source of truth, and the
same path keeps any other open screen in sync, such as the habit's detail screen, which observes
`observeMarks(habitId)`.

---

## 4. Files

Source paths below are relative to `app/src/main/java/com/example/xcommandcenter/` unless they
start with `app/` or are at the project root. "Created" = new for this app; "Modified" = template
file changed; template files left as they were (backup rules, Gradle wrapper,
`settings.gradle.kts`, `gradle.properties`, `rules.keep`, `strings.xml`) are not listed.

### Build & project (modified / created)
| File | Purpose |
|---|---|
| `build.gradle.kts` (modified) | Declares the Compose compiler, KSP and Room Gradle plugins. |
| `gradle/libs.versions.toml` (modified) | Version catalog: Compose BOM, Room, Navigation, Lifecycle, DataStore, KSP, Reorderable. |
| `app/build.gradle.kts` (modified) | minSdk 26, Compose, warnings-as-errors, opt-ins, Room schema dir, dependencies. |
| `app/src/main/AndroidManifest.xml` (modified) | Application class, launcher activity, reminder receivers, notification / exact-alarm / boot permissions. |
| `app/src/main/res/values/themes.xml` (modified) | Plain platform window theme; real theming is in Compose. |
| `app/schemas/.../AppDatabase/1.json`, `2.json` (created, generated) | Exported Room schemas: version 1 since the reset in §2.17; v2 adds the columns and table of §2.23 via auto-migration. |
| `.gitignore` (modified) | Also ignores `/ignore_this/`. |
| `ARCHITECTURE.md` (created) | This document. |

Removed from the template: `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`, `values/colors.xml`,
`values-night/themes.xml` (MDC theme no longer used), `drawable/ic_launcher_foreground.xml` and the
ten `mipmap-*dpi/ic_launcher*.webp` images (replaced by the logo, §2.14).

### App & wiring
| File | Purpose |
|---|---|
| `XCommandCenterApp.kt` | `Application`: builds the `AppContainer`, creates the notification channel, re-registers pending reminders at start (this also opens and seeds the DB). |
| `AppContainer.kt` | Hand-written DI container: database, repositories (including tags, backup and the two preference stores), reminder scheduler, app-wide coroutine scope. |
| `MainActivity.kt` | Single activity: edge-to-edge Compose host, asks for notification permission, opens a task when launched from its reminder. |

### domain/ (pure Kotlin)
| File | Purpose |
|---|---|
| `domain/ItemType.kt` | Enum of linkable types (seven tabs + `CHAPTER`) and `ItemRef(type, id)`. |
| `domain/Priority.kt` | Low < Medium < High < Critical; ordinal is the sort rank. |
| `domain/Recurrence.kt` | `HabitCategory`, `RecurrenceType`, sealed `Recurrence` with `isScheduled` and `nextAfter`, `Recurrence.of` from stored columns, weekday bitmask helpers. |
| `domain/HabitStats.kt` | Streak rules for positive/negative habits; recent scheduled occurrences. |
| `domain/ItemStyle.kt` | A tab's item style (color, bold, italic, underline, font), its stored form and summary, and `ItemStyles` for all tabs (§2.21). |
| `domain/WebLinks.kt` | Finding web addresses in free text (§2.23). |
| `domain/ManualOrder.kt` | Applying a new order to the visible rows of a list while hidden rows keep their slots, one-step moves, and moving a plan with the rows under it (§2.18, §2.20). |
| `domain/PlanTree.kt` | Flatten a plan tree with depths, skipping below collapsed nodes; compute a subtree (cycle prevention) and ancestors (completion). |
| `domain/TabOrder.kt` | Parse/serialize/move the tab order, tolerant of unknown or missing tabs. |

### data/entity/ (Room tables and view)
| File | Purpose |
|---|---|
| `data/entity/TaskEntity.kt` | `tasks`: title, description, due date, reminder time, priority, completed, optional schedule, next-occurrence flag. |
| `data/entity/TagEntity.kt` | `tags` (unique name ignoring case), `item_tags` (which item carries which tag) and the `ItemTag` projection. |
| `data/entity/GoalEntity.kt` | `goals`: title, strategy. |
| `data/entity/HabitEntity.kt` | `habits`: name, category, flat recurrence columns, start date, notes; maps to `Recurrence`. |
| `data/entity/HabitMarkEntity.kt` | `habit_marks`: one explicit mark per habit per date (cascade on habit delete). |
| `data/entity/ChecklistItemEntity.kt` | `checklist_items`: a task's checklist, and the `ChecklistCount` projection (§2.19). |
| `data/entity/PlanEntity.kt` | `plans`: title, description, self-referencing `parentId`, completed. |
| `data/entity/NoteEntity.kt` | `notes`: optional title, body. |
| `data/entity/BookEntity.kt` | `books`: title, author, description; `BookWithProgress` projection for the list. |
| `data/entity/ChapterEntity.kt` | `chapters`: book, position, title, read count, internalized (cascade on book delete). |
| `data/entity/QuoteEntity.kt` | `quotes`: text, optional source and author. |
| `data/entity/LinkEntity.kt` | `links`: the single normalized many-to-many link table. |
| `data/entity/ItemIndexEntry.kt` | `item_index` view (titles across all tables) and `OwnedLink` projection. |

### data/dao/
| File | Purpose |
|---|---|
| `data/dao/LinkDao.kt` | Insert/delete links; linked items of one item; all links from both ends; picker search. |
| `data/dao/TaskDao.kt` | Task CRUD, completion (creates a repeating task's next occurrence with its links and tags), pending reminders, transactional delete with links and tags. |
| `data/dao/BackupDao.kt` | Whole-database snapshot for export; `replaceAll` (import) and `deleteAll`, each one transaction. |
| `data/dao/TagDao.kt` | Tags in use, all item tags, one item's tags, add (reusing a name) and remove (deleting unused tags). |
| `data/dao/GoalDao.kt` | Goal CRUD, transactional delete with links and tags. |
| `data/dao/HabitDao.kt` | Habit CRUD, marks (observe, toggle), transactional delete with links and tags. |
| `data/dao/PlanDao.kt` | Plan CRUD; completion rule (complete, complete subtree, reopen with ancestors); delete moves children to the grandparent and removes links and tags. |
| `data/dao/NoteDao.kt` | Note CRUD, transactional delete with links and tags. |
| `data/dao/BookDao.kt` | Books with progress, chapters (add, rename, read count, internalized, reorder, delete + renumber), chapter and book deletes with their links and tags. |
| `data/dao/QuoteDao.kt` | Quote CRUD, transactional delete with links and tags. |

### data/db/ and data/prefs/
| File | Purpose |
|---|---|
| `data/backup/Xlsx.kt` | Minimal `.xlsx` writer and reader (zip + XML) returning sheets of cell text. |
| `data/backup/BackupFormat.kt` | Maps all data to and from sheets; validation, id assignment, skipped references. |
| `data/backup/BackupRepository.kt` | Export to and import from a document `Uri`, delete everything; handles alarms and settings. |
| `data/db/AppDatabase.kt` | Room database declaration (12 tables, 1 view) and builder with the seed callback. |
| `data/db/Converters.kt` | `LocalDate` ⇄ epoch day. |
| `data/db/SeedData.kt` | First-launch content: ~5 items per tab, chapters, habit history, 28 cross-tab links, a repeating task and four tags. |
| `data/prefs/SettingsStore.kt` | The single DataStore preferences file shared by the repositories below; clearing all settings or the collapsed set. |
| `data/prefs/TabOrderRepository.kt` | DataStore-backed persisted tab order. |
| `data/prefs/CollapsedPlansRepository.kt` | Which plans are collapsed in the Plans tab. |
| `data/prefs/ItemStyleRepository.kt` | Each tab's item style (§2.21). |
| `data/prefs/TaskSortRepository.kt` | The Tasks tab's chosen sort (§2.18) and whether it shows completed tasks (§2.24). |

### data/repository/
| File | Purpose |
|---|---|
| `data/repository/LinkRepository.kt` | Link/unlink with normalization, linked items, links grouped by owner, per-tab search (Books includes chapters). |
| `data/repository/TaskRepository.kt` | Task save/complete/delete that keeps the alarms (including a new occurrence's) in sync; reschedule all. |
| `data/repository/TagRepository.kt` | Tags in use, tags grouped by item, add (trimmed, `#` dropped) and remove. |
| `data/repository/GoalRepository.kt` | Goal access (insert-or-update save). |
| `data/repository/HabitRepository.kt` | Habits and marks as `Map`/`Set` of dates; toggle marks. |
| `data/repository/PlanRepository.kt` | Plan access; rejects moving a plan under its own subtree. |
| `data/repository/NoteRepository.kt` | Note access. |
| `data/repository/BookRepository.kt` | Books and table-of-contents operations. |
| `data/repository/QuoteRepository.kt` | Quote access. |

### reminder/
| File | Purpose |
|---|---|
| `reminder/ReminderScheduler.kt` | Registers/cancels one `AlarmManager` alarm per task; exact when permitted, otherwise allow-while-idle. |
| `reminder/ReminderReceiver.kt` | Alarm target; posts the notification with the app closed. |
| `reminder/RescheduleReceiver.kt` | Re-registers reminders after boot, app update, or exact-alarm permission grant. |
| `reminder/Notifications.kt` | Notification channel, permission check, reminder notification that opens the task. |

### ui/
| File | Purpose |
|---|---|
| `ui/AppViewModelProvider.kt` | `ViewModelProvider.Factory` that builds every ViewModel from the container. |
| `ui/theme/Theme.kt` | Material 3 theme (dynamic color on Android 12+, light/dark fallback). |
| `ui/navigation/Routes.kt` | Route strings: home, settings, `item/{type}/{id}?parent=`. |
| `ui/navigation/AppNavHost.kt` | Navigation graph; dispatches the item route to the right detail screen; opens a task from a reminder. |
| `ui/common/DragHandle.kt` | The drag handle shown while reordering, with Move up / Move down accessibility actions. |
| `ui/common/ItemStyleUi.kt` | `LocalItemStyles`, and turning a style into Compose text styles with light/dark color shades. |
| `ui/common/ItemDetailViewModel.kt` | Base ViewModel: current id, linked items as cards and their controls, tags, link/unlink, tag/untag, picker search, messages, close-after-delete. |
| `ui/common/Tags.kt` | "Tags" card of every detail screen and its add-tag dialog with suggestions. |
| `ui/common/RecurrenceFields.kt` | Schedule editor and its validation, shared by habits and tasks. |
| `ui/common/DetailScaffold.kt` | Shared detail frame: back/save/delete (with confirmation), snackbar, scrolling content. |
| `ui/common/LinkedItems.kt` | "Linked items" section: linked items as tab cards with their controls, grouped by tab; and the two-step link picker dialog with search. |
| `ui/common/ListEditor.kt` | The bulleted list editor shared by task checklists and note lists. |
| `ui/common/WebLinkText.kt` | Tappable web links in card text and under description fields. |
| `ui/common/ItemCard.kt` | The item card shared by the tabs and the linked items, the per-type card text, and habit summaries (§2.22). |
| `ui/common/ItemCardSource.kt` | Card data (text, control, tags, links) for every item, and the card controls' actions, for detail pages (§2.22). |
| `ui/common/Fields.kt` | Date and date-time fields with Material pickers, date formatting, section title, transparent list-row colors. |
| `ui/home/HomeViewModel.kt` | State for all seven tab lists (sorted and filtered tasks, habit summaries, plan tree, …), the shared tag filter and quick actions. |
| `ui/home/HomeScreen.kt` | Top bar, scrollable tab row in the saved order, tag filter bar, FAB to create an item in the current tab. |
| `ui/home/TabLists.kt` | The seven tab lists; every row shows its tags and what it is linked to. Tasks has sort and repeat filters; Plans is a collapsible outline with linked tasks. In reorder mode every row gets a drag handle. |
| `ui/settings/SettingsViewModel.kt` | Reads/saves tab order; exact-alarm status; export, import and delete with a busy flag and result messages. |
| `ui/settings/SettingsScreen.kt`, `ItemStyleDialog.kt` | Reorder tabs (drag and drop, reset); item styles per tab; notification and exact-alarm status with shortcuts; Data section (export, import, delete) with file pickers and confirmations. |
| `ui/task/TaskDetailViewModel.kt`, `TaskDetailScreen.kt` | Task form: priority, due date, reminder, repeat schedule, completed; tags; links. |
| `ui/goal/GoalDetailViewModel.kt`, `GoalDetailScreen.kt` | Goal form: title, strategy; tags; links. |
| `ui/habit/HabitDetailViewModel.kt`, `HabitDetailScreen.kt` | Habit form with category and recurrence editors; streak, total and tappable recent occurrences; tags; links. |
| `ui/plan/PlanDetailViewModel.kt`, `PlanDetailScreen.kt` | Plan form, completed checkbox, parent picker (no cycles), child list, add child; tags; links. |
| `ui/plan/CompletePlanDialog.kt` | "Complete all" dialog shown when a plan still has open subplans. |
| `ui/note/NoteDetailViewModel.kt`, `NoteDetailScreen.kt` | Simple note editor; tags; links. |
| `ui/book/BookDetailViewModel.kt`, `BookDetailScreen.kt` | Book form and table of contents (add, +1 read, internalized, reorder); tags; links. |
| `ui/book/ChapterDetailViewModel.kt`, `ChapterDetailScreen.kt` | Chapter title, read count ±1, internalized, parent book, delete; tags; links. |
| `ui/quote/QuoteDetailViewModel.kt`, `QuoteDetailScreen.kt` | Quote text, optional author and source; tags; links. |

### Resources
| File | Purpose |
|---|---|
| `app/src/main/res/drawable/ic_add.xml`, `ic_arrow_back.xml`, `ic_check.xml`, `ic_close.xml`, `ic_delete.xml`, `ic_drag_handle.xml`, `ic_expand_less.xml`, `ic_expand_more.xml`, `ic_reorder.xml`, `ic_save.xml`, `ic_settings.xml` | Material icons used in the UI. |
| `app/src/main/res/drawable/ic_notification.xml` | Status-bar icon for reminders. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` (modified) | Adaptive launcher icon: logo foreground over the gradient background, no monochrome layer. |
| `app/src/main/res/mipmap-*dpi/ic_launcher_foreground.png` (created) | Logo artwork for each density (108–432 px), centred in the 66dp safe zone with faded edges. |
| `app/src/main/res/drawable/ic_launcher_background.xml` (modified) | The logo tile's dark vertical gradient across the full icon canvas. |

### Tests (`app/src/test/java/com/example/xcommandcenter/`)
| File | Purpose |
|---|---|
| `domain/HabitStatsTest.kt` | Positive/negative streaks, scheduled-only counting, month-end clamping, recent occurrences. |
| `domain/TabOrderTest.kt` | Default order (Quotes last), parsing, round trip, unknown/missing tabs, moves. |
| `domain/RecurrenceTest.kt` | Next occurrence for each schedule type, month-end clamping, empty weekly rule, building from columns. |
| `domain/PlanTreeTest.kt` | Depth-first flattening (with collapsed nodes), subtree and ancestor computation. |
| `domain/ItemStyleTest.kt` | Style round trip, fallbacks for unreadable values, defaults (italic quotes, chapters as books), summaries. |
| `domain/WebLinksTest.kt` | Links found with sentence punctuation left out, brackets that belong to a link, plain text. |
| `domain/ManualOrderTest.kt` | Dragged orders and one-step moves around hidden rows, refusing moves past the ends, moving a plan with its rows. |
| `data/XlsxTest.kt` | Workbook round trip (escaping, blanks, leading zeros), reading Excel-style shared strings and gaps, column letters. |
| `data/BackupFormatTest.kt` | Data round trip, sheet order, values typed in Excel, skipped references, plan consistency, error messages. |
| `data/LinkEntityTest.kt` | Link normalization in both directions, same-tab ordering, self-link rejection. |

Run them with `./gradlew testDebugUnitTest`.

---

## 5. Verification performed

All checks ran on the `Medium_Phone` AVD (Android API 37). The first version was verified on
2026-10-24; each later feature on 2026-10-25, installing over the previous version's data so the
database migrations were exercised too.

### First version (2026-10-24)

| Definition of Done item | How it was checked | Result |
|---|---|---|
| Builds without errors or warnings | `./gradlew assembleDebug testDebugUnitTest --rerun-tasks --warning-mode all` with `allWarningsAsErrors = true`; the full log was searched for warnings | No Kotlin, KSP, AGP or Gradle warnings. 22/22 unit tests pass. |
| Runs on the AVD without crashing | Installed, launched, then exercised every tab, detail screens, the link picker, Settings, delete, process death and restore; checked the `crash` logcat buffer | No crashes |
| All seven tabs reachable and reorderable | Opened each tab. In Settings moved Quotes to the top and Tasks down, then force-stopped and relaunched | Order persisted (Quotes, Goals, Tasks, …); the app opens on the first tab |
| Seed data visible and linked | Read each tab's rows on screen; opened a task to see links grouped by tab, including a chapter shown with its book | Every tab shows items and "Linked:" lines |
| Two-way links | Linked a quote to a task in the picker (tab → search "finish" → pick), then opened the task | The quote appears on the task's page |
| Task reminder fires with the app closed | Created a task with a 17:59 reminder in the UI, saved it, pressed Home, swiped the app from Recents and confirmed no app process was running | Notification "Reminder test" posted at 18:02:15 by a freshly started process. The 3-minute delay is the inexact window Android allows when exact alarms aren't granted (the default on Android 14+). After granting exact alarms, `dumpsys alarm` shows the pending reminder re-registered with `window=0`. |
| Tapping a reminder opens the task | Cold start, running app, and process-death restore all tested | The task opens in each case |

Two bugs were found during this pass and fixed: a saved new item reopened as a blank form after
process death, and a reminder tap was ignored when Android restored the activity from saved
state. See the git history.

### Later features (2026-10-25)

Each feature came in its own pull request (mailxee/zCommandCenter#1 onwards). Every build ran with
`allWarningsAsErrors = true` and no warnings; the unit tests now number 43 and all pass.

| Feature | How it was checked | Result |
|---|---|---|
| Upgrades keep data (schema v1 to v2 to v3) | Installed each new build over the previous one with data in place | All items, links and settings kept; new columns start empty or false |
| Plan completion | Ticked a leaf; ticked a parent with an open child (dialog, Complete all); unticked a child; added a child under a completed parent; renamed a completed plan | Blocked as designed; Complete all completed the subtree; ancestors reopened; the stored flag survived the form save |
| Plans outline | Linked tasks shown under their plans; both "Completed" chips; collapsed a plan, force-stopped and relaunched; ticked a task in the tree | Collapse survived the restart; chips reset; the task moved to the completed group |
| Repeating tasks | Set "every 2 days" on a task with a reminder and a plan link, ticked it, then unticked and re-ticked the old one | A copy appeared two days later with the reminder moved two days and the tag and link kept; no second copy |
| Tags and the shared filter | Tagged a task; selected the tag on Tasks, Goals and Plans; selected two tags no item shares | Only matching items shown, parents kept on Plans, empty tabs say so; two tags matched nothing |
| Fresh install | Backed up the app's data, cleared it, launched, restored the backup | Seed tags and the weekly task appear; the restored data loaded unchanged |
| Launcher icon | Opened the app drawer after installing | The logo shows inside the launcher's circle with no clipping and no visible box around the artwork |
| Export to Excel | Exported through the system save dialog, pulled the file and checked it with Python's `zipfile` and an XML parser | "Exported 38 items"; a valid zip, every part well-formed, 11 sheets with row counts matching the database |
| Delete everything | Confirmed the dialog, then copied the database off the device | Every table empty, settings file cleared, tabs back to the default order |
| Import from Excel | Imported the exported workbook into the empty app, then compared every table with a copy of the database taken before the delete | "Imported 38 items"; all tables identical (timestamps to the minute), tags and links intact |

No crashes appeared in the `crash` logcat buffer during any of these passes.
