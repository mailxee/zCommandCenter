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
  "Every N weeks" and "Every N months" (see 2.28) count weeks or months from the start date's week or month.

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
exact-alarm permission being granted. The app also does this at every start. Since §2.30 every
item can have any number of reminders, one alarm each (`ReminderRepository`).
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
right below it, ahead of its subplans, as rows with their own checkbox (see §2.50 for the look). A task linked
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
bold, italic, underline, and a font: default, serif, mono or cursive), set in Settings → Tabs (Item styles until §2.31).
An item's title is shown in its tab's style everywhere: its own tab, the tab row label, the "Linked:"
line on other tabs' rows (each linked title in its own style), linked-item lists on detail pages,
the link picker, a plan's parent and child lists, and the detail screen's title. Chapters follow
the Books style. Quotes keep italics as their default. Two things stay on top of a style: a done
item is struck through, and a critical open task is shown in red.
- `ItemStyle` (domain) is stored per tab in DataStore (`ItemStyleRepository`, one key per tab, so
  "Delete everything" resets them) and provided once at the root through `LocalItemStyles`, so no
  view model has to pass styles around.
- Each palette color has a darker shade for light surfaces and a lighter one for dark surfaces
  (`StyleColor.toColor`), so text stays readable in both themes.

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

- **Habit reminders** (superseded by §2.30, which allows any number): a habit could have a reminder time (`habits.reminderMinutes`). One alarm per
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

- **Completed tasks:** shown or hidden with the Tasks tab's "Completed" chip (§2.56); hiding
  them is kept in DataStore with the task sort. "Delete completed" (after a confirmation)
  deletes every completed task, including any hidden by the filters, with its links, tags and
  checklist, in one transaction (`TaskDao.deleteCompleted`). A repeating task's open next
  occurrence is not completed, so it stays.
- **Notes:** a sort (Custom, or Priority with Critical first; notes of the same priority keep their
  custom order) and priority filter chips (any combination; none shows every note). As with tasks,
  notes can only be dragged in the custom order, and turning on reorder mode on the Notes tab
  switches to it. Both are in memory, like the repeat filter.
- **Chapters:** the read count can also be cleared from the book page's chapter rows.

### 2.25 Priority on every tab, and tags per tab

- **Priority:** goals, habits, plans, books and quotes have a priority too (schema v3,
  `AutoMigration(2 → 3)`, Medium for existing items), set on the item's page and shown on its card
  (critical in red). The Excel sheets gain a Priority column.
- **Sorting and filtering:** those tabs and Notes share one control pair (`priorityControls`): a sort,
  Custom or Priority (Critical first, stable, so the custom order holds within a priority), and
  priority filter chips (any combination; none shows everything). `HomeViewModel` keeps one sort and
  one filter per tab, in memory. On Plans, sorting orders siblings, so the outline stays a tree, and
  the filter keeps the plans above a match, as the tag filter does. Items can only be dragged in the
  custom order; turning on reorder mode switches the current tab to it.
- **Tag filter bar:** shows only the tags on the current tab's items (on Plans, also those on tasks
  linked to plans), plus any tag the filter uses, so it can always be turned off. The filter still
  applies to every tab.
- **Hiding a tag:** tapping a tag shows only items with it; pressing and holding it hides items with
  it instead (`TagFilter.excluded`), and the chip turns grey and struck through. Holding it again, or
  tapping it, makes it plain; "Clear" resets both. Material's chips take no long press, so the bar
  draws its own (`TagChip`), with a long-press label for accessibility services. On Plans, a plan with
  a hidden tag is not shown for its tasks' sake, only as the parent of another match.

### 2.26 Reordering lists, and leaving with unsaved changes

- **Lists:** a task's checklist and a note's list can be reordered by dragging a handle on each
  item (`ListEditor`, with the library's `ReorderableColumn`, as for chapters); the order is part of
  the form and saved with it. The handle offers Move up / Move down to accessibility services.
- **Unsaved changes:** each detail view model returns its editable fields as one comparable value
  (`formState`); `ItemDetailViewModel` keeps the value last loaded or saved (`markClean`) and
  compares (`hasUnsavedChanges`). Leaving with a difference, by the back arrow or system back
  (`BackHandler`), asks: Save (saves and closes, or shows why it can't), Discard, or Keep editing.
  What is saved at once (tags, links, read counts, a plan's completion) is not part of the form, so
  it never counts as unsaved.

### 2.27 Priority filter on the Tasks tab

Under the All / Repeating / One-off row, the Tasks tab has the same priority chips as the other
tabs (Low, Medium, High, Critical; any combination, none shows every task). They share
`HomeViewModel.priorityFilters` (keyed by tab) and `priorityFilterChips` with those tabs, and
combine with the repeat, completed and tag filters. Tasks keep their own sorts (Due date, Priority,
Custom).

### 2.28 Every N months and every N weeks

Tasks and habits can repeat **Every N months** (N from 1 to 120) on a chosen day of the month
(`Recurrence.EveryNMonths`). Months are counted from the start date's month: for a task, the due
date, so a task due 15 March every 3 months comes back on 15 June. As with Monthly, a day past the
end of a short month falls on its last day, and the next occurrence keeps the chosen day (31 Jan →
30 Apr → 31 Jul). N is stored in the existing `intervalDays` column, so the database is unchanged.
The backup has a new "Every N months" column at the end of the Tasks and Habits sheets; older
backups without it still import. The search for the next date looks ahead `(N + 1) × 31` days
instead of the usual 400 (`Recurrence.searchDays`).

**Every N weeks** (N from 1 to 104) works the same way on chosen weekdays
(`Recurrence.EveryNWeeks`): weeks run Monday to Sunday and are counted from the start date's week,
so a task due on a Wednesday, every 2 weeks on Monday and Friday, comes back that Friday, then on
Monday and Friday two weeks on. It uses the weekday and `intervalDays` columns and adds an
"Every N weeks" backup column.

### 2.29 Multi-line boxes start at one line

The longer text boxes (a task's, plan's or book's description, a goal's strategy, a habit's notes,
a note's body, a quote's text) set no `minLines`, so they start one line high and grow as the text
wraps or gains lines.

### 2.30 Any number of reminders on any item

- **Data:** reminders have their own table (`ReminderEntity`: item type, item id, time), so every
  item, chapters included, can have as many as wanted. For a habit, `time` is a minute of the day,
  used on each scheduled day (unless the day is already marked); for anything else it is a date and
  time, once. Schema v4 adds the table and copies each task's and habit's single reminder into it
  (`AppDatabase.CopyReminders`); v5 drops the old `tasks.reminderAt` and `habits.reminderMinutes`
  columns. Two steps, because Room's auto-migration runs the copy after the schema change, and the
  copy needs the old columns. Checked by replaying the generated migration SQL on a v3 database
  with sample data: reminders and checklists kept, tables equal to `5.json`.
- **Alarms:** one per reminder, keyed by its id (`ReminderScheduler`). When one fires,
  `ReminderRepository.fired` decides what to show: a habit's reminder schedules its next day and
  notifies only if today is scheduled and unmarked; anything else's notifies with the item's title
  unless it is a completed task or plan. Alarms are set even for completed items, so reopening one
  needs no rescheduling. A notification opens the item; an item's later reminder replaces its
  earlier notification. Alarms from older versions use other intent actions and are ignored; the
  app re-registers every reminder at start.
- **Editing:** every detail page has a Reminders section (`RemindersSection`): a list with a
  remove button per reminder, and Add reminder (a time for a habit, a date and then a time for
  anything else). The list belongs to the form: `ItemDetailViewModel` loads it, counts changes to
  it as unsaved, and stores it on save (`onSaved`), except for tasks, whose repository stores it
  before completing the task, so a repeating task's next occurrence gets the new reminders.
- **Repeating tasks:** the next occurrence gets a copy of every reminder, each moved by the same
  number of days as the due date (`TaskDao.complete`).
- **Deleting:** each repository's delete removes the reminders (and alarms) of whatever is gone,
  including subplans and chapters (`ReminderRepository.removeOrphans`).
- **Cards:** a habit's card lists its reminder times and the date and time of the next one
  (skipping today once it is marked; `nextReminder`), as does each time on the habit's page; any
  other card shows the next reminder still to come and how many more follow (`reminderLine`); a
  done item shows none.
- **Backup:** a Reminders sheet (Type, ID, Title, Reminder). A workbook without it (from an older
  version) takes the single Reminder column of its Tasks and Habits sheets instead.

### 2.31 Search, Untagged, and one Tabs list in Settings

- **Search:** a search box under the tabs. Every tab shows only the items whose text contains each
  word typed, ignoring case (`Search.matches`): a task's title and description, a goal's title and
  strategy, a habit's name and notes, a plan's title and description, a note's title and body, a
  book's title, author and description, a quote's text, author and source. The text stays when
  switching tabs and combines with the other filters. On the Plans tab it works like the tag filter:
  matching plans and tasks show, with the plans above them.
  The search also looks through text kept beside an item (§2.51): a task's checklist, a note's
  list, a book's chapter titles, and every item's tag names.
- **Untagged:** the first chip of the tag bar. Tap it to show only items without tags; press and
  hold to hide them. It is a pseudo-tag (`TagFilter.UNTAGGED`, id -1) that an item without tags
  carries, so it combines with tags like any other.
- **Settings:** Tab order and Item styles are one Tabs list: drag a row's handle to reorder, tap the
  row (or Style) to edit that tab's look.

### 2.32 Every N days habits catch up after a miss

When an "every N days" habit misses a due day (a positive habit left unmarked, a negative one
marked as missed), it is due again the next day, and every day until it is done; the N days then
count from the day it was done (`HabitStats.dueDates`). The missed day still breaks the streak.
Every other schedule (Daily, Weekly, Monthly, Every N weeks, Every N months) keeps its days.
Everything that asks whether a habit is due goes through the due days: the streak, the card's
Done today chip (`isDue`), the history on the habit's page (`recentDueDates`), and the reminders
(`HabitStats.nextReminder`, which treats today as missed until it is marked, so a reminder is
also set for tomorrow; marking a day re-sets the habit's alarms). The habit's page explains the
rule under the schedule.

### 2.33 Settings in the backup; chapters "Done"

- **Backup:** the workbook ends with a Settings sheet: one row per tab with its position and item
  style (Tab, Position, Color, Bold, Italic, Underline, Font). Importing it sets the tab order and
  every tab's style; tabs left out follow the listed ones and keep their default style; a blank
  cell takes the tab's default. A workbook without the sheet leaves the current settings alone.
- **"Done":** what was labelled "Internalized" now reads "Done": each chapter's checkbox on its
  book's page and on its own page, the whole book's checkbox, and the book card ("5 chapters · 2
  done", "Book done"). The data (`internalized` columns) and the backup's "Internalized" columns
  keep their names, so older workbooks still import.

### 2.34 A book's page shows what its chapters are linked to

Below the book's own linked items, "Linked to chapters" lists, per chapter in table-of-contents
order, the items linked to that chapter, as cards with their usual controls
(`BookDetailViewModel.chapterLinks`, `LinkedCardsGroup`). Chapters with no links are left out.
These links are added and removed on the chapter's page, so the cards have no remove button.

A book's card, on the Books tab and wherever it is shown as a linked item, lists in its "Linked:"
line what its chapters are linked to as well as what the book is, each item once and without
naming the chapters (`LinkDao.observeChapterLinksByBook`, merged in
`LinkRepository.observeLinksByOwner`).

### 2.35 Links on cards, tab by tab

A card's single "Linked: …" line is now one line per tab, in the user's tab order
(`LocalTabOrder`, provided at the root like the item styles): "Tasks: A · B", then "Notes: C", the
tab name and the items in that tab's style. A linked chapter is shown as its book on the "Books:"
line, and each book only once, however many of its chapters are linked
(`LinkDao.observeChapterBooks`, applied in `LinkRepository.observeLinksByOwner`); the item's own
page still lists the chapters in Linked items.

### 2.36 Link mode

Pressing and holding a card on any tab (a Schedule block or a Plans outline row included) turns
link mode on with that item as the root (`ItemCard(onLongClick = …)` → `HomeViewModel.showNetwork`,
with a vibration and a "Show linked items" accessibility action); pressing and holding another card
while it is on moves the root there. A tap still opens the item. Reordering mode ignores the press.
This replaced an earlier link button beside the search box and its "tap a card" picking step.
The network bar's Map button opens that network on the map (§2.57).
Every tab then shows only the root's network: the items
linked to it, directly or through any number of other links (`domain/LinkNetwork`, a breadth-first
walk over the undirected links). Notes, quotes, books and people (§2.53) are end points, never followed: the walk
goes only through tasks, goals, habits and plans, and an end point (a book with all its chapters; a
chapter's links count as its book's) shows when it is linked to one of them. An end point as the
root starts the walk from the items linked to it, and also shows the end points linked to it
directly (one step), so a new note, quote or book saved from it stays visible. A banner under the search box names the root; its cross goes
back to normal mode.

The state lives in `LinkRepository` (in memory: the root and the `network` flow), so
both the home screen and the detail screens see it. `HomeViewModel` keeps only network items in
every tab, the plan tree (plans above a network plan stay as headings), the tag chips, the
completed counts and "Delete completed"; the link picker searches only the network. A new item
saved in link mode is linked to the root (`ItemDetailViewModel.onSaved` → `joinNetwork`), so it
doesn't disappear. Link lines and Linked items need no filtering: every link of a network item
leads into the network. Reminders are unaffected. A fresh start of `MainActivity` turns link mode
off; deleting the root does too, since the root's index entry then emits null.

### 2.37 Tab counters

Each tab shows its name with "shown/total" below it (`HomeViewModel.tabCounts`). Shown is what
the tab lists after every filter, completed tasks hidden by their toggle included; total is all
the tab's items, or in link mode all the network's. The Plans tab counts plans that pass its
filters, collapsed or not, leaving out headings outside the network; the Books tab counts books.

### 2.38 Fixed signing key

Debug builds are signed with `app/signing/debug.keystore` (standard debug passwords, set in
`app/build.gradle.kts`) instead of the per-machine `~/.android/debug.keystore`, which a fresh
build machine would generate anew. Android only installs an update signed with the same key as
the installed app, so a new key would force an uninstall, losing the app's data. Keep this file:
it is the key the installed app was signed with (SHA-256
`10:2F:74:41:06:D8:76:25:35:03:0D:F9:8F:00:C1:42:59:B3:C9:18:91:34:55:A1:E1:9E:D4:F5:E9:46:50:36`).

### 2.39 Habit calendar

Each card in the Habits tab ends with this week's days (`HabitWeek`, passed as `ItemCard`'s
full-width `footer`; see §2.49), and the habit's page shows a month calendar
(`ui/common/HabitCalendar`) in place of the old history chips. It is a `HorizontalPager` of months from the habit's first month (its start
date, or an earlier mark) to the current one, opening on the current one; weeks start on Monday.

Every habit follows one rule (`dayStatus`): a due day from the start through today is done
(tinted in the Habits tab's color) or missed (tinted red). A positive habit stores its done
days, so an unmarked past due day is missed and today stays pending; a negative habit stores its
missed days, so an unmarked due day is done. On the habit's page, tapping a due day toggles its
mark, switching it between done and missed for either kind; the card's week only shows. The
card's button for today is a check for a positive habit and a cross for a negative one, filled
red once a miss is marked (`HabitTodayButton`).

### 2.40 Picking a book, then a chapter

The link picker's Books tab lists books only (`LinkRepository.search`). Tapping a book opens a
second step: "Whole book", then its chapters in the book's order (`LinkDao.observeChapters`,
searchable when the book has many), each linked with one tap; "Back to books" returns. In link
mode both steps list only the network's items.

### 2.41 Schedule tab

A plan for every day (`ItemType.BLOCK`, tab "Schedule", item "Block"; `blocks` table, database
version 6, an auto-migration that also rebuilds `item_index`). A block has a title, a start and an
end in minutes of the day (`endMinute` 1440 = midnight), notes, tags, links and reminders; nothing is
dated or completed. `ui/home/ScheduleTab` draws the day: hours down a gutter (64 dp an hour),
blocks placed by start and sized by length, overlapping ones side by side (`domain/ScheduleLayout`),
a red line at the time now, opened scrolled to it. Tapping an empty half hour opens a new block
there (`Routes.newBlock`, `ARG_START`); the + button starts one at the next half hour.

A block's reminders are times of day, every day, like a habit's (`ItemType.remindsDaily`;
`ReminderRepository.scheduleDaily` sets the next one and re-arms on firing). "Add reminder" offers
"At start", 5–30 minutes or an hour before, or any time; moving the block's start moves its
reminders by the same amount (`ItemDetailViewModel.shiftReminders`). Blocks are link-mode end
points, appear in search, tag filters, the tab counter and link pickers, and have their own
"Schedule" sheet in the Excel backup (optional on import; End "24:00" is midnight). The card's link
lines are shared with every other card (`LinkLines`).

### 2.42 Several schedules

Blocks belong to a schedule (`schedules` table, `blocks.scheduleId`; database version 7, whose
`AddSchedules` spec creates "My schedule", id 1, for existing blocks; a new database gets it too,
and "Delete everything" puts it back, so there is always one). The tab's `ScheduleSwitcher` shows
the active schedule and a menu to switch, add, rename, duplicate (`ScheduleDao.duplicate` copies
every block with its links, tags and reminders), set the weekdays each is used on, delete (not the
last), and turn automatic switching on.

Which schedule is active is `domain/ScheduleChoice`: the last pick (`ScheduleChoiceRepository`, with
the day it was made), except that with automatic switching on a new day starts on the schedule set
for its weekday; a pick made today wins until tomorrow. A weekday belongs to at most one schedule
(`assignDays`). The tab, its counter, search and new blocks use the active schedule. Every block
keeps its alarm, and `ReminderRepository.fired` shows a block's reminder only when its schedule is
active, so switching needs no rescheduling. Elsewhere, block cards and the link picker name the
block's schedule when there are several. The backup's Schedules sheet holds the schedules, the
one on show and the automatic switch; the Schedule sheet's "Schedule ID" places each block.

### 2.43 Durations and folded free time

Blocks show their length ("8:00 – 10:00 AM · 2 h", `durationText`), as does the block's page.
Free stretches of two hours or more (`ScheduleAxis.gaps`: before, between and after the blocks;
none on an empty day) fold into a slim bar ("12:00 AM – 7:00 AM · 7 h free"); a tap opens one and
a tab at its top folds it again. `ScheduleAxis` maps minutes to positions with the folded gaps
shrunk to their bars, and back for taps (none on a bar). Opened gaps are kept in
`HomeViewModel.openGaps` while the app runs, keyed by schedule and times, so every launch starts
folded. The now line becomes a red "now" on a bar that holds it, and the tab scrolls there.

### 2.44 Default tab order, narrower tabs, new sample data

Until the user reorders, the tabs run Schedule, Goals, Plans, Tasks, Habits, Notes, Books, Quotes
(`TabOrder.default`, no longer the enum order). Tabs are narrower (`minTabWidth = 0.dp`, 10 dp
padding) so the first five fit across a phone. A new install's sample data (`SeedData`) follows
one person's goals: live as a Stoic, learn the piano, run a half marathon, with a Weekday and a
Weekend schedule (days set, automatic switching off).

### 2.45 App ID and release signing

The application ID is `com.xeeshan.manage_everything` for debug and release builds alike (the
Kotlin `namespace` stays `com.example.xcommandcenter`; only the ID is the app's identity). Debug
builds keep the public debug key (2.38). Release builds are signed with the Play upload key, read
from environment variables that `.github/workflows/release.yml` sets from the repository secrets
`RELEASE_KEYSTORE_BASE64` and `RELEASE_KEYSTORE_PASSWORD`; without them a release build is simply
unsigned, so the public repository builds for anyone. `.gitignore` keeps any other keystore out.
`RELEASING.md` has the steps.

### 2.46 A separate debug app; versionCode from the workflow

Debug builds add `applicationIdSuffix = ".debug"` and are named "Manage Everything Debug"
(`src/debug/res/values/strings.xml`), so they install beside the Play app, which Play signs with
its own key and nothing else can update. Release builds take `versionCode` from `VERSION_CODE`,
which the release workflow sets to its run number; local builds use 1.

### 2.47 Schedule polish

Hour labels are just the hour ("7 AM", or "07:00" on a 24-hour phone), so they fit the gutter. A
block shorter than about 25 minutes is drawn 24 dp tall; it is laid out by that drawn length, so
the block after it sits beside it rather than under it, and on its single line the title gets two
thirds of the room and the time shortens. In link lines, blocks of the same name from different
schedules carry their schedule's name ("Morning reflection (Weekday)").

### 2.48 Look and feel

A visual pass only; nothing works differently.

- **Colors.** A fixed palette taken from the app icon (an orange accent on cool charcoal greys),
  light or dark with the system, replacing the wallpaper-based colors (`ui/theme/Theme.kt`).
- **Tab colors and icons** (`ui/common/Accents.kt`). Every tab has an icon and a color: Schedule
  teal, Goals purple, Plans indigo, Tasks blue, Habits green, Notes amber, Books brown, Quotes pink.
  A color set for a tab's items in Settings takes its place. The color tints the selected tab's
  icon and indicator, the add button, the "Plans:" names in link lines, the empty-list icon,
  Schedule blocks and the habit calendar.
- **Cards.** A strip down the left edge in the priority's color (Critical red, High orange, Medium
  blue-grey, Low grey). Under the title, the priority as a small colored label, then the short
  facts as quiet chips (due date, repeat, checklist, habit kind and streak, list size, chapters):
  `CardText.priority` and `CardText.meta`, so the cards read the same on the tabs and on detail
  pages. The reminder line has a bell, and a thin rule sets the link lines apart.
- **Filters fold away.** A filter button beside the search box shows or hides the tag chips and
  the tab's sort and filter rows (`showFilters`, kept until the app closes); a dot on it says a
  filter is hiding items. Opening them scrolls the list to the top, where the rows are. The
  completed-items chips stay in the list.
- **Top bar and add button.** The title bar slides away as the list scrolls down and comes back
  on the way up; the add button says "New goal" and so on, shrinking to its icon while scrolling.
- **Link mode** shows a banner ("Linked to …") with a cross that leaves link mode, as the link
  button does.
- **Empty lists** show the tab's icon, why the list is empty, and a "New …" button; tabs fade
  into each other.
- **Habit calendar.** Smaller days with soft tints: green for done, red for missed, today ringed.

### 2.49 Compact cards

A second visual pass, to fit more on the screen; nothing works differently.

- **Priority letter.** The priority shows as its letter (C, H, M or L) on a tint of its color,
  just before the title on every tab (`CardTitle`, an inline text placeholder), in place of the
  "High" label. The colored strip down the edge stays.
- **Tags** follow the title on the same line, smaller, and wrap with it; a tag never splits.
- **Links fold away.** A card with links shows a small button with the link icon and their count;
  tapping it shows or hides the link lines (kept per card until the app closes). A card without
  links has no button.
- **Facts on one line.** The short facts are plain text joined by " · " rather than chips, and a
  due date in the current year leaves out the year ("Due Nov 1").
- **Tighter spacing.** 8 dp inside cards and at the list's edges, 6 dp between cards, 12 dp
  corners, and a compact checkbox (40 dp touch area).
- **Habit cards** show this week only (`HabitWeek`, Monday to Sunday) instead of the month; the
  habit's page keeps the full calendar. Marking today is a round check button (a cross for a
  negative habit) rather than a "Done today" chip, on the tab and on detail pages.
- **Search box** is 44 dp tall rather than 56 (a `BasicTextField` with the outlined field's look).

### 2.50 Plans outline as one card per plan

A visual pass on the Plans tab; nothing works differently. Each top-level plan and everything
shown under it (subplans and their linked tasks) read as one card: the rows sit with no gap, only
the first has rounded top corners and only the last rounded bottom ones (`shape`), and the gap
falls between top-level plans (`ItemList(spacing = 0.dp)`, 6 dp above each top-level row). The
rows cast no shadows, so no seams show where they join.

Rows below the top level (`ItemCard(outline = …)`) are smaller: a smaller title, a one-line
smaller description, less padding, a thin divider above them from their indent, and no priority
strip (the letter stays). They are indented 28 dp a level, and tree lines run from each plan's
checkbox down past its children with a short branch to each child's checkbox.
`outlineGuides` works out, for each row and each level above it, whether a later row still hangs
from that level's line, so a line stops at its last child.

### 2.51 Search covers sub-items and tags; deleting tags

- **Search** (`SearchScope`) carries, besides the query, the text each item keeps in other
  tables: checklist items (Tasks), list items (Notes), chapter titles (Books) and tag names (all
  tabs, Schedule blocks included). A word may come from the item or from that text, so "milk"
  finds the Groceries task whose checklist has Milk. The extra text is only gathered while
  something is typed.
- **Deleting tags:** Settings has a Tags section listing every tag in use with its item count.
  Deleting one (after a confirmation) takes it off every item in one transaction
  (`TagDao.deleteTag`); the items stay. A tag filter using it falls away, because the filter only
  keeps tags still in use.

### 2.52 Checklist and list rows

A task's checklist and a note's list (`ListEditor`) show each item in its own soft rounded box
(`surfaceContainerLow`, 4 dp apart) so items read separately. The row is top-aligned: the
checkbox (or bullet), the remove button and the drag handle sit level with the first line of the
text instead of the middle of a long item. The remove button and the grip are smaller and drawn in
the muted colour (`DragHandle(compact = true)`), leaving more width for the text.
Drag handles only show in move mode (§2.54).

### 2.53 People tab

A tab for people worth keeping track of (`ItemType.PERSON`, orange, a person icon), added last in
the default tab order so an existing order gets it at the end (`TabOrder.parse`).

- **Data** (database version 8, an automatic migration): `people` (name, bio, priority, order),
  `person_profiles` (web addresses such as "x.com/name", stored without "https://") and
  `person_items` (free text: an idea, or a video or post with its link), both ordered and
  deleted with the person through their foreign keys. People are in `item_index`, so links, the
  link picker, tags and reminders work as for every other item.
- **Profiles** (`domain/Profiles`): "@name" becomes an X profile and "youtube @name" (any
  platform's name or badge, such as "yt: @name") that platform's; an address loses its scheme,
  "www." and trailing slash. The platform comes from the host (`platformOf`, matching whole
  domains so "notx.com" isn't X) and shows as a small badge, or a globe for a website; tapping it
  opens the profile.
- **Items:** the kind comes from the first link (`kindOf`): video sites give a play icon, social
  networks a post icon, any other link a link icon, and no link a lightbulb (an idea). Tapping the
  icon opens the link. Both lists use the checklist row layout (`ListEditor(leading = …)`).
- **Card:** name, tags, the platforms and item count on the meta line, two lines of bio.
- **Search** also looks through profiles and items (`SearchScope`).
- **Links:** a person is an end point of link mode's network, like notes, quotes and books
  (`LinkNetwork`): holding a goal shows its people, but not what those people link to.
- **Backup:** a People sheet, plus Profiles and Person items sheets. All three are optional on
  import, so older workbooks still load.

### 2.54 Moving list items

`ListEditor` (checklists, note lists, a person's profiles and items) has a move button beside its
title, like the main screen's Reorder button, once it has two items. Normally rows show no drag
handles, so the text gets the room and nothing is dragged by accident. In move mode the title
reads "… · moving", rows show only their text (and a person's badges) with a handle, the add field
is hidden, and the dragged row lifts; ✓ ends it, as does leaving the page.

### 2.55 Reminder carry-over

When a task's or plan's reminder goes off and the item is still open, with no other reminder to
come, the reminder is repeated a number of days later at the same time of day (Settings →
Reminders: Off, 1, 2, 3 or 7 days; default 1; `ReminderPrefsRepository`). The rules are in
`domain/ReminderCarry` (tested); `ReminderRepository.carryOver` applies them when an alarm fires.

- An item keeps **one** carried-over reminder (`ReminderEntity.carried`, database version 9, an
  automatic migration): it moves forward each time it goes off, and a new carry replaces an older
  one, so an ignored task doesn't pile up reminders. Completing the task or plan removes it
  (`dropCarriedOfCompleted`); a repeating task's next occurrence doesn't copy it.
- It shows on the item's page under its time as "Carried over…", and can be removed like any
  reminder; saving the form keeps the flag (`ReminderDao.replace` matches it by time).
- A reminder that went off while the phone was off gets its carry-over on restart, if it was
  within the last carry period (`ReminderCarry.missed`); older reminders are left alone, so turning
  the feature on doesn't revive old ones.
- Goals, habits, blocks and the rest are never carried: goals can't be completed, and habits and
  blocks repeat anyway.

### 2.56 Past due and Completed chips

The Tasks tab's filter bar has two built-in chips after "Untagged", each with a small icon:
"Past due" (open tasks due before today) and "Completed". They work like tags: tap to show only
those tasks, press and hold to hide them (`domain/TaskStatusFilter`, tested). A task can't be
both, so showing only one turns the other's "only" off; hiding combines freely. They combine with
tags, priority, repeat and search, and "Clear" turns them off too. The chips appear on the Tasks
tab only; the Plans tab keeps its switches, since showing only completed plans would break up the
plan tree.

- "Completed" replaces the old "Completed tasks (n)" switch: hiding completed tasks is still
  remembered across launches. Showing only completed tasks offers "Delete completed (n)".
- A past-due task's card reads "Past due · date" instead of "Due date".

### 2.57 Map of links, Tidy links and the suggested route

Three parts, all pure logic in `domain/` (tested) with thin screens in `ui/links/`.

- **Suggested route** (`domain/LinkRoute`, Settings → Links): a layer, 1 to 4, for each linking
  type; by default Goals 1, Plans 2, Tasks and Habits 3. End points (notes, quotes, books,
  people, blocks) are always the last layer. A link between neighbouring layers is on route, one
  within a layer runs sideways, one that jumps a layer skips it; a link to an end point is always
  on route. Stored in DataStore (`RoutePrefsRepository`). Linking is never blocked: the route only
  feeds the map's rows and the suggestions below.
- **Tidy links** (`domain/LinkAnalysis`, `TidyScreen`): routes run through goals, plans, tasks and
  habits (chapters count as their books). A spanning forest keeps on-route links first, then
  layer-skipping, then sideways ones, older before newer; every link left over closes a loop (two
  routes between the same items) and is the one suggested for removal: a **shortcut** (goal to
  task beside goal to plan to task), an **off-route link in a loop**, or a **second route** (a
  task under two plans of one goal). The count of these is the network's extra routes. A
  layer-skipping link with no loop is suggested a better place (**skips a layer**: link the task
  or habit to a plan already under the goal, then remove the direct link) when there is one. An
  end point linked to several items of one network is noted for information only.
  Each card has its fix (Remove link, or Apply), **Keep as is** (stored in `kept_findings`,
  database version 10, by a key naming the link, so it returns if the link is made again) and
  **Show on map**. Every change offers Undo. Importing or deleting everything clears kept findings.
- **Map** (`domain/GraphLayout`, `MapScreen`): one item's network, or every linked item
  ("Everything"). **By route** puts each layer in a row (wide rows wrap), ordered by the average
  place of what each item links to so lines cross less; lines within a row bow up and lines that
  jump a row bow outwards, so they never seem to pass through an item. **Free layout** is a
  force-directed layout started from those places, so it is the same every time. Items are pills
  in their tab's colour; lines are grey on route, amber off route, red dashed where Tidy links
  suggests removing, dotted for a shared end point. Pinch to zoom, drag to move; tapping an item
  highlights it and its links, with Open, Its network and Link to… (then tap the other item);
  tapping a line selects it, with its finding, Remove link, Apply and Keep as is. Opened from the
  map button in the main screen's top bar (the shown network, or everything) and the Map button on
  the network bar; Tidy links is reached from the map and from Settings.
- `LinkRepository.observeGraph` gathers labels, links and the analysis off the main thread for both
  screens.

### 2.58 Ideas tab

A tab for the thinking side of the app (`ItemType.IDEA`, amber, a lightbulb), added last in the
default tab order, so an existing order gets it at the end.

- **Data** (database version 11, an automatic migration): `ideas` with a title (the thought in one
  line), an optional explanation, a **kind** (`domain/IdeaKind`: Vision, Belief, Principle, Idea,
  Question, broadest first), priority, order and a `parentId`. Ideas form a tree like plans: a
  missing or deleted parent makes an idea top level, deleting an idea moves the ones under it up a
  level, and an idea can't be put under itself or anything below it. Ideas are in `item_index`
  (with the kind as subtitle), so links, the picker, tags, reminders and search work as for any item.
- **One tab, not one per kind:** thoughts change kind as they mature, and kinds keep the tab bar
  short. Each kind has an icon and colour (`ui/common/IdeaKinds`); cards elsewhere show the kind's
  icon before the title (`CardText.kind`).
- **Ideas tab:** an outline like the Plans tab (`domain/IdeaOutline`), the kind's icon leading each
  row where the outline's lines meet. A chevron collapses what is under an idea (kept in DataStore,
  `CollapsedPlansRepository(IDEAS_KEY)`). Reordering moves an idea among its siblings with
  everything under it. The filter panel has a chip per kind (`IdeaKindFilter`): tap to show only
  that kind (several can be on), hold to hide it; with any filter on, the ideas above a match stay so
  the tree reads.
- **Idea page:** the kind as chips with its definition below (from the glossary, §2.60), the idea, the explanation, **Under** (a picker showing the tree) and the ideas
  under this one with "Add an idea under this" (one kind narrower than the parent by default).
- **Turn into idea** (a note's page): saves the note, then makes it an idea of the chosen kind and
  deletes the note (`IdeaRepository.fromNote`). The title (or the note's first line) becomes the
  idea; the rest of the text and the list become the explanation. Links are made again from the
  idea (pairs are stored in order, `LinkEntity.between`), tags and reminders move, priority stays.
  A dialog confirms first, as there is no undo.
- **Links:** in Do mode an idea is an end point of link mode's network, like a note; in Think mode
  networks run through ideas (§2.61).
- **Backup:** an optional Ideas sheet (Idea, Kind, Explanation, Parent ID, …); parents are
  inserted first, and a missing parent or a loop makes an idea top level.
- **Demo data:** two visions with beliefs, principles, an idea and a question, linked to the
  goals, habits, books and people.

### 2.59 Link labels

A link can carry a label, so the networks say how things relate, not only that they do.

- **Data** (database version 12, an automatic migration that also inserts the starting labels):
  `link_labels` (a wording each way, `forward` "supports" and `backward` "supported by"; no
  `backward` reads the same both ways, like "contradicts"; a `key` for the starting ones, so the
  app can tell a contradiction). `links` gains `labelId` (a foreign key that sets it back to null
  when the label is deleted, so the link stays, plain) and `labelFromA`, which end the label reads
  forward from. Pairs stay stored in order (§2.1); `LinkEntity.labelled(subject, object, label)`
  works out the flag. No label reads "relates to".
- **Starting labels** (`domain/LinkLabels`): supports · supported by, contradicts, inspired ·
  inspired by, leads to · comes from, example of · has example, source of · from, depends on ·
  needed by. Settings › Link labels lists them with how many links use each; tap one to rename or
  delete it, or add your own.
- **Linking:** after an item is picked, the label picker offers "relates to" and both wordings of
  every label, so picking a wording also sets the direction; a line reads the link back ("This
  belief — supported by — …"). Pressing and holding a linked card opens the same picker to change
  it. Labels work between any items.
- **Detail pages:** labelled links come first, grouped by their wording read from this item
  ("Supported by", "Leads to"), then the rest by tab as before (`LinkRepository.observeLabelsOf`).
- **Map:** a label sits on the middle of its line; a label that reads differently each way has an
  arrow from its subject. A contradiction is a purple dotted line, not a route. The line's panel
  reads it as a sentence.
- **Tidy links:** a labelled link counts as intentional. It is kept ahead of unlabelled links when
  loops are broken, so an unlabelled one is suggested first, and it gets no "skips a layer" advice.
  A contradiction never takes part in a route, so it is never suggested for removal.
- **Backup:** the Links sheet gains Label ID and "Label reads from" (A or B as written on the row),
  and a Link labels sheet holds the labels. Without that sheet, links come in plain and the app
  keeps its labels; "Delete everything" puts back the starting labels, as it resets settings.

### 2.60 Glossary

Settings › Glossary keeps an editable definition for every term: the five idea kinds, every tab
(Doing: goal, plan, task, habit, schedule block; Thinking and sources: idea, note, book, chapter,
quote, person) and every link label.

- **Defaults** live in code (`domain/Glossary`); only rewritten definitions are stored, in the
  `glossary` table by key (`KIND:BELIEF`, `TYPE:GOAL`, `LABEL:3`), database version 13. Saving the
  starting text or a blank one removes the row, which is what "Reset to default" does too.
  `Glossary.Terms` merges the two. A label the user adds starts with no definition.
- **Where definitions show:** under the kind chips on an idea's page; in the label picker under the
  chosen label; and on an empty tab, under "No … yet" (through `LocalTerms`). Holding a tab name was
  in the plan, but Material's tabs take no long press, so it was left out.
- `ItemCardSource.terms` hands the definitions to every detail page, as they all have one.
- **Backup:** a Glossary sheet (Term, Definition) with the rewritten definitions only. Definitions are kept on
  importing a workbook without the sheet; "Delete everything" resets them, as it resets settings.

### 2.61 Do and Think modes

The app has two views of the same items (`domain/AppMode`): **Do** manages goals, plans, tasks and
habits; **Think** ponders visions, beliefs and ideas with their sources.

- **Switch:** a Do/Think control stands in the main screen's top bar where the app's name was
  (the name stays in its description for screen readers). The mode is kept across launches
  (`ModeRepository`, DataStore), and each mode remembers the tab last open in it. Switching leaves
  link mode, as its network ran through the other mode's items.
- **Tabs per mode** (`TabModes`): Settings › Tabs gives each tab Do, Both or Think. By default Schedule,
  Goals, Plans, Tasks and Habits are Do; Ideas, Books and Quotes are Think; Notes and People are
  both. The default tab order now puts Ideas after Habits, so Think mode opens on them; an order the
  user already set keeps its place and gets Ideas at the end. If a mode would have no tabs, it shows
  them all. The Settings sheet of the backup gains a Mode column (optional on import).
- **Networks per mode:** each mode's networks run through its own types (`AppMode.through`): Do
  through goals, plans, tasks and habits; Think through ideas. Everything else is an end point of
  that mode, so **any** item of the other mode ends a route: a principle linked to a plan under an
  unrelated goal shows the plan as an end point in Think mode, and the plan shows the principle as
  an end point in Do mode. Neither network gets a loop from it. Link mode follows the current mode
  (`LinkNetwork.of(…, mode)`).
- **⇄ marks the other mode:** on a detail page, a group of linked items that all belong only to
  the other mode says so ("Leads to ⇄ Do", "Ideas ⇄ Think"); on the Ideas tab, each idea counts
  its links into Do ("⇄ 2 in Do").
- The map and Tidy links follow the mode (§2.62).

### 2.62 Mind map, Think route and Think checks

The map and Tidy links follow the mode (§2.61).

- **Think route** (`domain/ThinkRoute`, Settings › Links): a layer, 1 to 4, for each idea kind
  (visions 1, beliefs and principles 2, ideas and questions 3). On Think mode's "By route" map,
  sources (notes, quotes, books, people) come after the ideas and Do mode's items last.
- **Map in Think mode:** a network runs through ideas, along links and from each idea to the one it
  is under (`LinkGraph.nodes(root, mode)`, `LinkGraph.under`); "Everything" is every idea and what
  they link to. Only links touching an idea are drawn. **Under** lines (the tree) are thick and grey;
  links are thin, with their labels; Do mode's items are green pills marked ⇄, ideas are in their
  kind's colour and icon. The same marking shows Think's items on Do mode's map.
- **Mind map** (`GraphLayout.radial`), Think mode's default layout beside By route and Free: the item
  in the middle (or, for Everything, the top-level ideas on the first ring), then rings by how many
  steps away each item is, following the tree before other links. Each branch gets a share of the
  circle by how many items end it, so branches don't overlap, and a ring moves out when it has more
  items than fit round it. Rings are kept round on screen (`MIND_MAP_ASPECT`).
- **Tidy links in Think mode** (`domain/ThinkAnalysis`): loops between ideas are normal, so none are
  reported. Instead: **Author is in People** (a quote's author matches a person it isn't linked to;
  "Link" links the person as its source), **Unplaced quote** (no idea linked), **Ungrounded idea**
  (a belief, principle or idea with no quote, book, person or note linked), **Not acted on** (a belief
  or principle with nothing in Do mode linked, not even through the ideas under it) and
  **Contradiction** (each "contradicts" link, as a note; Hide keeps it out of the list). Cards
  without a fix have Open. A Do/Think switch at the top shows each mode's findings and counts; it
  starts on the app's mode.
- **Goals without a why** (Do mode): a goal no idea links to, with Open and Keep as is. On by default;
  Settings › Links has a switch for it.
- The Tidy links buttons count findings that ask for something (`Finding.suggests`), so notes such
  as a contradiction or a shared end point don't add to them.

### 2.63 Reminder chip

Every tab's filter panel has a **Reminder** chip beside Untagged: tap to show only items with a
reminder going off today, hold to hide them, like Past due. It is part of the shared `TagFilter`
(`reminder`, `remindedToday`), so it works on every tab, the Plans outline and link mode alike, and
Clear turns it off. Today counts (`domain/ReminderToday`): a one-off reminder (carried-over ones
included) on today's date; a habit's reminder on a day the habit is due; a block's when its
schedule is the one in use today.

### 2.64 Daily backup

Settings › Daily backup saves the Export workbook once a day to a folder the user picks with
Android's folder picker: on the phone, or in a cloud app that offers folders (OneDrive, Nextcloud,
and so on; Google Drive's app offers none to other apps, so it can't be used this way).

- `AutoBackupRepository` keeps the settings in DataStore (on/off, the folder's tree URI with a
  persisted read and write grant, how many to keep: 3, 7, 14 or 30, and how the last backup went).
- WorkManager (`AutoBackupWorker`, a new dependency, `androidx.work`) runs it once a day when the
  battery isn't low, also with the app closed; it is registered at start while backups are on.
  "Back up now" runs it once at once. A failure is retried twice and shown in Settings.
- Each day's file is "Manage Everything backup YYYY-MM-DD.xlsx", replacing that day's if it ran
  before; then the oldest backups beyond the number to keep are deleted (`domain/BackupNames`).
  Other files in the folder are never touched.
- "Delete everything" clears settings, the backup folder included, so daily backups stop.

## 3. Walkthrough: marking a habit done from the Habits tab

The user taps the **"Done today"** check button on *Read 20 pages*.

1. **Tap → ViewModel.** In `HabitsTab` (`ui/home/TabLists.kt`), the button's `onClick` calls `HomeViewModel.toggleHabitToday(habit.id)`.
2. **ViewModel → repository.** `toggleHabitToday` launches a coroutine in `viewModelScope` and calls `HabitRepository.toggleMark(habitId, LocalDate.now())`.
3. **Repository → DAO.** This forwards to `HabitDao.toggleMark`, a `@Transaction` method. It counts existing marks for that habit and date. If there are none, it runs `insertMark(HabitMarkEntity(habitId, date))`; otherwise `deleteMark(...)`. Room runs it on its query executor, off the main thread.
4. **Database write.** SQLite inserts a row into `habit_marks`. The `Converters` class stores the date as an epoch day. When the transaction commits, Room's `InvalidationTracker` marks the `habit_marks` table as changed.
5. **Invalidation → Flow.** `HabitDao.observeAllMarks()` watches `habit_marks`, so Room re-runs it and emits the new list. `HabitRepository.observeAllMarks()` maps it to `Map<habitId, Set<LocalDate>>`.
6. **Recompute.** In `HomeViewModel.habits`, `combine(habits, marks)` emits again. For each habit it calls the pure `HabitStats.streak(...)` and counts the marks, producing a new `HabitSummary` (streak 7, done 7 times, `markedToday = true`) in a `StateFlow`.
7. **Screen update.** `HabitsTab` collects that `StateFlow` with `collectAsStateWithLifecycle()`. The new value recomposes the row: the button shows as checked and the text reads "Streak 7 · done 7 times".

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
| `app/schemas/.../AppDatabase/1.json` … `5.json` (created, generated) | Exported Room schemas: version 1 since the reset in §2.17; v2 adds the columns and table of §2.23, v3 the priorities of §2.25, v4 and v5 the reminders table of §2.30, all via auto-migration. |
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
| `data/db/SeedData.kt` | First-launch content for a Stoic, piano-learning, half-marathon-training life: ~5 items per tab, chapters, habit history, a note list, two schedules of blocks, reminders, ~40 cross-tab links, a repeating task and four tags. |
| `data/prefs/SettingsStore.kt` | The single DataStore preferences file shared by the repositories below; clearing all settings or the collapsed set. |
| `data/prefs/TabOrderRepository.kt` | DataStore-backed persisted tab order. |
| `data/prefs/CollapsedPlansRepository.kt` | Which plans are collapsed in the Plans tab. |
| `data/prefs/ItemStyleRepository.kt` | Each tab's item style (§2.21). |
| `data/prefs/TaskSortRepository.kt` | The Tasks tab's chosen sort (§2.18) and whether it shows completed tasks (§2.24). |
| `data/prefs/ReminderPrefsRepository.kt` | Days until a reminder is carried over (§2.55). |

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
| `ui/theme/Theme.kt` | Material 3 theme in the app icon's colors, light and dark (§2.48). |
| `ui/common/Accents.kt` | Each tab's color and icon, and the priority colors (§2.48). |
| `ui/navigation/Routes.kt` | Route strings: home, settings, `item/{type}/{id}?parent=`. |
| `ui/navigation/AppNavHost.kt` | Navigation graph; dispatches the item route to the right detail screen; opens a task from a reminder. |
| `ui/common/DragHandle.kt` | The drag handle shown while reordering, with Move up / Move down accessibility actions. |
| `ui/common/ItemStyleUi.kt` | `LocalItemStyles`, and turning a style into Compose text styles with light/dark color shades. |
| `ui/common/ItemDetailViewModel.kt` | Base ViewModel: current id, linked items as cards and their controls, tags, link/unlink, tag/untag, picker search, messages, close-after-delete. |
| `ui/common/Tags.kt` | "Tags" card of every detail screen and its add-tag dialog with suggestions. |
| `ui/common/RecurrenceFields.kt` | Schedule editor and its validation, shared by habits and tasks. |
| `ui/common/DetailScaffold.kt` | Shared detail frame: back/save/delete (with confirmation), snackbar, scrolling content. |
| `ui/common/LinkedItems.kt` | "Linked items" section: linked items as tab cards with their controls, grouped by tab; and the two-step link picker dialog with search. |
| `ui/common/ListEditor.kt` | The list editor shared by task checklists, note lists and a person's lists, with its move mode (§2.54). |
| `ui/common/WebLinkText.kt` | Tappable web links in card text and under description fields. |
| `ui/common/ItemCard.kt` | The item card shared by the tabs and the linked items, the per-type card text, and habit summaries (§2.22, §2.49). |
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
| `ui/person/PersonDetailViewModel.kt`, `PersonDetailScreen.kt` | A person's name, bio, profiles (platform badges) and items (kind icons); tags; links (§2.53). |
| `data/entity/PersonEntity.kt`, `data/dao/PersonDao.kt`, `data/repository/PersonRepository.kt` | People with their profiles and items (§2.53). |
| `domain/Profiles.kt` | Profile handles to addresses, platform from host, item kind from its link (§2.53). |
| `domain/ReminderCarry.kt` | When a reminder is carried over, and to when (§2.55). |
| `domain/TaskStatusFilter.kt` | The Tasks tab's Past due and Completed chips (§2.56). |
| `domain/LinkRoute.kt` | The suggested route of links: a layer per type (§2.57). |
| `domain/LinkAnalysis.kt` | Tidy links: loops, skipped layers and shared end points, with fixes (§2.57). |
| `domain/GraphLayout.kt` | The map's layered and free layouts (§2.57). |
| `data/prefs/RoutePrefsRepository.kt` | Stores the suggested route (§2.57). |
| `data/entity/KeptFindingEntity.kt` | Tidy links findings kept as they are (§2.57). |
| `ui/links/MapScreen.kt`, `MapViewModel.kt` | The map of links (§2.57). |
| `ui/links/TidyScreen.kt`, `TidyViewModel.kt`, `FindingText.kt` | Tidy links and the findings in words (§2.57). |
| `domain/IdeaKind.kt`, `domain/IdeaOutline.kt`, `domain/Glossary.kt` | Idea kinds, the Ideas outline and kind chips (§2.58); the glossary's terms and starting definitions (§2.60). |
| `data/entity/IdeaEntity.kt`, `data/dao/IdeaDao.kt`, `data/repository/IdeaRepository.kt` | Ideas as a tree; turning a note into an idea (§2.58). |
| `ui/idea/IdeaDetailViewModel.kt`, `IdeaDetailScreen.kt` | An idea's kind, text, Under and the ideas under it; tags; links (§2.58). |
| `ui/common/IdeaKinds.kt` | Each kind's icon and colour (§2.58). |
| `data/entity/LinkLabelEntity.kt`, `domain/LinkLabels.kt` | Link labels with a wording each way, and the starting set (§2.59). |
| `ui/common/LinkLabelDialog.kt` | Picking a link's label and direction (§2.59). |
| `data/entity/GlossaryEntity.kt`, `data/dao/GlossaryDao.kt`, `data/repository/GlossaryRepository.kt` | Rewritten definitions (§2.60). |
| `ui/settings/GlossaryScreen.kt` | Settings › Glossary and its view model (§2.60). |
| `domain/ReminderToday.kt` | Which items have a reminder today, for the Reminder chip (§2.63). |
| `domain/BackupNames.kt`, `data/backup/AutoBackupRepository.kt`, `AutoBackupWorker.kt` | Daily backups to a chosen folder (§2.64). |
| `domain/ThinkRoute.kt`, `domain/ThinkAnalysis.kt` | Think mode's route and checks; goals without a why (§2.62). |
| `domain/AppMode.kt`, `data/prefs/ModeRepository.kt` | Do and Think: the mode, each tab's mode, what each mode's networks run through (§2.61). |

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
| `ui/home/TagFilterTest.kt` | Selected tags (all required), hidden tags (any hides), and both together. |
| `data/LinkEntityTest.kt` | Link normalization in both directions, same-tab ordering, self-link rejection. |
| `domain/ReminderTodayTest.kt`, `domain/BackupNamesTest.kt` | Reminders today; backup names and which old ones go. |
| `domain/ThinkAnalysisTest.kt`, `domain/ThinkRouteTest.kt` | Think checks (acted on through children, sources, authors, contradictions, goals' why) and the Think route. |
| `domain/TabModesTest.kt` | Default tabs per mode, marking the other mode, stored form. |
| `domain/GlossaryTest.kt` | Every term has a starting definition; rewritten ones win. |
| `domain/IdeaOutlineTest.kt` | The Ideas outline (depths, collapsing, filtering keeps ancestors) and the kind chips. |

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
