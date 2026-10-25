# xCommandCenter — Architecture

A personal command center for Android. Seven tabs (Tasks, Goals, Habits, Projects, Notes,
Books, Quotes) share one local Room database, and any item can be linked to any other item.

- Language: Kotlin · UI: Jetpack Compose + Material 3 · Storage: Room (SQLite), local only
- minSdk 26, compile/target SDK 37 (the template's values)
- Reminders: `AlarmManager` → `BroadcastReceiver` → notification

---

## 1. Layers

```
ui/        Compose screens + ViewModels      (state holders, user intents)
  │ calls
data/repository  Repositories               (the only API the UI uses for data)
  │ calls
data/dao + data/entity + data/db  Room      (SQL, schema, seed)
domain/    Pure Kotlin rules                 (streaks, recurrence, tab order, project tree)
reminder/  AlarmManager + receivers          (works with the app closed)
```

- **ui** never touches DAOs. Each screen has one ViewModel; ViewModels expose `StateFlow`s (from
  Room `Flow`s) and Compose state for form fields.
- **data** owns persistence. Repositories add behavior that is more than SQL: `TaskRepository`
  keeps alarms in step with stored reminders, `ProjectRepository` rejects cycles,
  `LinkRepository` normalizes link pairs.
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
and the item together (§2.4).

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

**Chosen:** each DAO has `@Transaction suspend fun delete(id)` that deletes the item's links,
then the item. Special cases:
- **Book:** also deletes the links of all its chapters. The chapters themselves go through the foreign key's `ON DELETE CASCADE`.
- **Habit:** marks cascade through their foreign key.
- **Project:** child projects are moved up to the deleted project's parent, so a subtree is never silently deleted.
- **Chapter:** links removed, then the remaining chapters are renumbered.

| Alternative | Why rejected |
|---|---|
| SQLite triggers | Hidden from the Kotlin code, and their behaviour on cascaded deletes is easy to get wrong. |
| Two repository calls without a transaction | A crash between them would leave orphan links. |
| Cascade-delete child projects | Deleting a parent would silently destroy work. Moving children up loses nothing. |
| Leave child projects at top level (`SET NULL`) | Throws away where they sat in the tree. The FK still uses `SET NULL` as a safety net. |

### 2.5 Habits: streak rules and storage

**Chosen semantics (confirmed with the user):**
- **Positive:** a mark means "done". Unmarked occurrences are *unknown*, never success. The streak is the number of consecutive marked occurrences, walking back from today. If today is unmarked it is skipped, because the day isn't over; any earlier unmarked occurrence ends the streak.
- **Negative:** this is a way of *counting*, not a bad habit. The user marks only **misses**, which suits habits that are already mostly formed. Unmarked occurrences count as done. The streak is the number of scheduled occurrences from today back to the most recent miss, or back to the habit's start date if there has never been one.
- Only **scheduled** occurrences count. Unscheduled days are skipped; they neither count nor break a streak.
- Totals are the number of marks ("done N times" / "missed N times").
- Monthly on day 29–31 falls on the last day of shorter months. "Every N days" counts from the habit's start date.

**Storage:** recurrence is stored as flat columns on `habits` (`recurrenceType`, `weekdays`
bitmask, `dayOfMonth`, `intervalDays`) and exposed as a sealed `Recurrence` type. Marks go in
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

### 2.8 Tab order in DataStore Preferences

**Chosen:** a comma-separated list of tab names under one key. `TabOrder.parse` ignores unknown
names and appends missing tabs, so adding a tab in a later version can't hide it. Settings
reorders with up/down buttons.

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
- **`ItemDetailViewModel` base class:** holds the id, the linked items `Flow`, link/unlink,
  picker search, and the message/closed state that every detail screen shares. `DetailScaffold`
  and `LinkedItemsSection` are the matching UI.
  - *Rejected:* copying this logic into each of the eight screens.
- **Explicit save (✓ button):** a new item gets an id when first saved. Linking is enabled after
  that, because a link needs a real id.
  - *Rejected:* auto-saving every keystroke creates empty items whenever the user backs out of a "new" screen.
  - *Rejected:* creating the item when the screen opens has the same problem.
- **Surviving process death:** after the first save, the new id is written to the screen's
  `SavedStateHandle`, so a restored screen reloads the saved item. A reminder notification's intent
  carries a one-time token, and `MainActivity` remembers the last token it handled. A tap is then
  honoured even when Android recreates the activity from saved state, and isn't replayed on
  rotation.
  - *Rejected:* acting only on a launch without saved state; this missed taps that restore an old task.
  - *Rejected:* clearing the extra from the intent; the system's copy of the intent still has it after process death.
- **Lists show links inline:** every row ends with "Linked: …", from one query over all links, so
  each tab shows what its items are linked to.
- **Chapter reorder** uses up/down buttons and renumbers positions `0..n-1` in a transaction.
- **Icons** are eight vector drawables. *Rejected:* the Compose material-icons artifacts, a large
  dependency for eight glyphs.

### 2.10 Dependency injection by hand

**Chosen:** `AppContainer` builds the database, repositories and scheduler. A
`viewModelFactory` (`AppViewModelProvider`) creates ViewModels from it.

| Alternative | Why rejected |
|---|---|
| Hilt / Dagger | Adds annotation processing and build complexity for about a dozen objects. |
| Koin | A runtime service locator with no compile-time checks. Not needed at this size. |
| Singletons in `object`s | Hidden global state, harder to replace in tests. |

### 2.11 Project completion

**Chosen (confirmed with the user):** a `completed` flag on `projects`, with one rule: a project
is completed only if every project below it is completed.
- Ticking a project with open subprojects is refused, and a dialog offers to complete it
  together with its whole subtree (`ProjectDao.complete` / `completeWithSubprojects`).
- Anything that leaves an open project under a completed one reopens the completed ancestors:
  unticking a subproject, adding a child, or moving an open project under a completed parent
  (`ProjectDao.reopen`, `saveDetails`). The rule therefore always holds.
- Completion is applied at once, from the list checkbox or the detail screen, not on ✓. Saving
  the form keeps the stored flag, so a form opened before a toggle can't undo it.
- The Projects tab hides completed projects unless "Completed projects" is on (not persisted, like
  the task sort). Hiding never orphans an open project, because of the rule.
- Schema v2 adds the column through a Room `AutoMigration(1 → 2)`, so existing data is kept.

**Projects tab as an outline (confirmed with the user):** each project's linked tasks are listed
right below it, ahead of its subprojects, as outlined cards with their own checkbox. A task linked
to several projects appears under each. Open tasks come first, and completed tasks are hidden unless
the "Completed tasks" chip is on. The project's "Linked:" line leaves tasks out, since they are
rows of their own. A chevron on any project with visible tasks or subprojects collapses all of
them; a collapsed project says what it hides ("2 subprojects · 1 task hidden"). Collapsed ids are
kept in DataStore (`CollapsedProjectsRepository`) so the outline survives restarts; the two
"Completed" chips reset each launch, like the task sort. The rows are built in
`HomeViewModel.projectTree` from the existing project, task and link flows, with
`ProjectTree.flatten(…, collapsed)` skipping what is under collapsed projects; no new query.

| Alternative | Why rejected |
|---|---|
| Independent flags per project | The user wants a parent to count as done only when its subprojects are. |
| Completing a parent silently completes its subtree | Too easy to close work by accident; the user asked for a block plus an explicit "complete all". |
| Refusing to reopen a subproject under a completed parent | Forces the user to reopen from the top down; reopening the ancestors is what the rule implies. |
| `fallbackToDestructiveMigration` | Would wipe the user's data on upgrade. |

### 2.12 Repeating tasks: completing creates the next occurrence

**Chosen (confirmed with the user):** a task can carry the same schedule types as a habit (daily,
weekly on chosen days, monthly, every N days), stored as nullable flat columns on `tasks` and
read through the shared `Recurrence.of`. Ticking a repeating task completes it and inserts a copy
due on `Recurrence.nextAfter(dueDate)`, the first scheduled day after the old due date, counting
the schedule from that date. The reminder moves by the same number of days, and the copy gets the
same links and tags, so it stays under its project and in its filters (`TaskDao.complete`, one
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
**all** the selected tags. On Projects, matching projects and tasks show together with the
projects above them, so the tree still reads. Every row shows its tags (`#name`). The selection
is in memory, and selected tags that are no longer in use drop out, so a filter can't get stuck on.
Schema v3 adds the tables and the task schedule columns through `AutoMigration(2 → 3)`.

| Alternative | Why rejected |
|---|---|
| Tags as a comma-separated text column on each table | No uniqueness, renames touch every row, and matching needs string parsing in SQL. |
| Tags as a kind of link target | Links are between real items. Tags would show up in "Linked items" and the picker. |
| A filter per tab | The user chose one shared filter. |

### 2.14 Build configuration

- Gradle version catalog, AGP 9.4 with its built-in Kotlin, the Compose compiler plugin, and KSP
  for Room. KSP replaces kapt, which is deprecated and slower.
- `allWarningsAsErrors = true`, so the Definition of Done's "no warnings" stays true. Experimental
  APIs in use (Material 3 pickers, `FlowRow`, `flatMapLatest`) are opted in once, module-wide.
- Room exports its schema to `app/schemas/` so future migrations can be written and tested.
- The template's AppCompat/MDC dependencies were removed. The window theme is the platform
  `Theme.Material.Light.NoActionBar`; all styling is Compose Material 3.

---

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
file changed; template files left as they were (launcher icons, backup rules, Gradle wrapper,
`settings.gradle.kts`, `gradle.properties`, `rules.keep`, `strings.xml`) are not listed.

### Build & project (modified / created)
| File | Purpose |
|---|---|
| `build.gradle.kts` (modified) | Declares the Compose compiler, KSP and Room Gradle plugins. |
| `gradle/libs.versions.toml` (modified) | Version catalog: Compose BOM, Room, Navigation, Lifecycle, DataStore, KSP. |
| `app/build.gradle.kts` (modified) | minSdk 26, Compose, warnings-as-errors, opt-ins, Room schema dir, dependencies. |
| `app/src/main/AndroidManifest.xml` (modified) | Application class, launcher activity, reminder receivers, notification / exact-alarm / boot permissions. |
| `app/src/main/res/values/themes.xml` (modified) | Plain platform window theme; real theming is in Compose. |
| `app/schemas/.../AppDatabase/1.json`, `2.json`, `3.json` (created, generated) | Exported Room schemas; v2 adds `projects.completed`, v3 adds task schedules and tags, both via auto-migration. |
| `.gitignore` (modified) | Also ignores `/ignore_this/`. |
| `ARCHITECTURE.md` (created) | This document. |

Removed from the template: `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`, `values/colors.xml`,
`values-night/themes.xml` (MDC theme no longer used).

### App & wiring
| File | Purpose |
|---|---|
| `XCommandCenterApp.kt` | `Application`: builds the `AppContainer`, creates the notification channel, re-registers pending reminders at start (this also opens and seeds the DB). |
| `AppContainer.kt` | Hand-written DI container: database, repositories, reminder scheduler, app-wide coroutine scope. |
| `MainActivity.kt` | Single activity: edge-to-edge Compose host, asks for notification permission, opens a task when launched from its reminder. |

### domain/ (pure Kotlin)
| File | Purpose |
|---|---|
| `domain/ItemType.kt` | Enum of linkable types (seven tabs + `CHAPTER`) and `ItemRef(type, id)`. |
| `domain/Priority.kt` | Low < Medium < High < Critical; ordinal is the sort rank. |
| `domain/Recurrence.kt` | `HabitCategory`, `RecurrenceType`, sealed `Recurrence` with `isScheduled` and `nextAfter`, `Recurrence.of` from stored columns, weekday bitmask helpers. |
| `domain/HabitStats.kt` | Streak rules for positive/negative habits; recent scheduled occurrences. |
| `domain/ProjectTree.kt` | Flatten a project tree with depths, skipping below collapsed nodes; compute a subtree (cycle prevention) and ancestors (completion). |
| `domain/TabOrder.kt` | Parse/serialize/move the tab order, tolerant of unknown or missing tabs. |

### data/entity/ (Room tables and view)
| File | Purpose |
|---|---|
| `data/entity/TaskEntity.kt` | `tasks`: title, description, due date, reminder time, priority, completed, optional schedule, next-occurrence flag. |
| `data/entity/TagEntity.kt` | `tags` (unique name ignoring case), `item_tags` (which item carries which tag) and the `ItemTag` projection. |
| `data/entity/GoalEntity.kt` | `goals`: title, strategy. |
| `data/entity/HabitEntity.kt` | `habits`: name, category, flat recurrence columns, start date, notes; maps to `Recurrence`. |
| `data/entity/HabitMarkEntity.kt` | `habit_marks`: one explicit mark per habit per date (cascade on habit delete). |
| `data/entity/ProjectEntity.kt` | `projects`: title, description, self-referencing `parentId`, completed. |
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
| `data/dao/TagDao.kt` | Tags in use, all item tags, one item's tags, add (reusing a name) and remove (deleting unused tags). |
| `data/dao/GoalDao.kt` | Goal CRUD, transactional delete with links. |
| `data/dao/HabitDao.kt` | Habit CRUD, marks (observe, toggle), transactional delete with links. |
| `data/dao/ProjectDao.kt` | Project CRUD; completion rule (complete, complete subtree, reopen with ancestors); delete moves children to the grandparent and removes links. |
| `data/dao/NoteDao.kt` | Note CRUD, transactional delete with links. |
| `data/dao/BookDao.kt` | Books with progress, chapters (add, rename, read count, internalized, reorder, delete + renumber), book delete with chapter links. |
| `data/dao/QuoteDao.kt` | Quote CRUD, transactional delete with links. |

### data/db/ and data/prefs/
| File | Purpose |
|---|---|
| `data/db/AppDatabase.kt` | Room database declaration (12 tables, 1 view) and builder with the seed callback. |
| `data/db/Converters.kt` | `LocalDate` ⇄ epoch day. |
| `data/db/SeedData.kt` | First-launch content: ~5 items per tab, chapters, habit history, 28 cross-tab links, a repeating task and four tags. |
| `data/prefs/SettingsStore.kt` | The single DataStore preferences file shared by the repositories below. |
| `data/prefs/TabOrderRepository.kt` | DataStore-backed persisted tab order. |
| `data/prefs/CollapsedProjectsRepository.kt` | Which projects are collapsed in the Projects tab. |

### data/repository/
| File | Purpose |
|---|---|
| `data/repository/LinkRepository.kt` | Link/unlink with normalization, linked items, links grouped by owner, per-tab search (Books includes chapters). |
| `data/repository/TaskRepository.kt` | Task save/complete/delete that keeps the alarms (including a new occurrence's) in sync; reschedule all. |
| `data/repository/TagRepository.kt` | Tags in use, tags grouped by item, add (trimmed, `#` dropped) and remove. |
| `data/repository/GoalRepository.kt` | Goal access (insert-or-update save). |
| `data/repository/HabitRepository.kt` | Habits and marks as `Map`/`Set` of dates; toggle marks. |
| `data/repository/ProjectRepository.kt` | Project access; rejects moving a project under its own subtree. |
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
| `ui/common/ItemDetailViewModel.kt` | Base ViewModel: current id, linked items and tags, link/unlink, tag/untag, picker search, messages, close-after-delete. |
| `ui/common/Tags.kt` | "Tags" card of every detail screen and its add-tag dialog with suggestions. |
| `ui/common/RecurrenceFields.kt` | Schedule editor and its validation, shared by habits and tasks. |
| `ui/common/DetailScaffold.kt` | Shared detail frame: back/save/delete (with confirmation), snackbar, scrolling content. |
| `ui/common/LinkedItems.kt` | "Linked items" card grouped by tab, and the two-step link picker dialog with search. |
| `ui/common/Fields.kt` | Date and date-time fields with Material pickers, date formatting, section title, transparent list-row colors. |
| `ui/home/HomeViewModel.kt` | State for all seven tab lists (sorted and filtered tasks, habit summaries, project tree, …), the shared tag filter and quick actions. |
| `ui/home/HomeScreen.kt` | Top bar, scrollable tab row in the saved order, tag filter bar, FAB to create an item in the current tab. |
| `ui/home/TabLists.kt` | The seven tab lists; every row shows what it is linked to. Projects is a collapsible outline with linked tasks. |
| `ui/settings/SettingsViewModel.kt` | Reads/saves tab order; exact-alarm status. |
| `ui/settings/SettingsScreen.kt` | Reorder tabs (up/down, reset); notification and exact-alarm status with shortcuts. |
| `ui/task/TaskDetailViewModel.kt`, `TaskDetailScreen.kt` | Task form: priority, due date, reminder, repeat schedule, completed; tags; links. |
| `ui/goal/GoalDetailViewModel.kt`, `GoalDetailScreen.kt` | Goal form: title, strategy; links. |
| `ui/habit/HabitDetailViewModel.kt`, `HabitDetailScreen.kt` | Habit form with category and recurrence editors; streak, total and tappable recent occurrences; links. |
| `ui/project/ProjectDetailViewModel.kt`, `ProjectDetailScreen.kt` | Project form, completed checkbox, parent picker (no cycles), child list, add child; links. |
| `ui/project/CompleteProjectDialog.kt` | "Complete all" dialog shown when a project still has open subprojects. |
| `ui/note/NoteDetailViewModel.kt`, `NoteDetailScreen.kt` | Simple note editor; links. |
| `ui/book/BookDetailViewModel.kt`, `BookDetailScreen.kt` | Book form and table of contents (add, +1 read, internalized, reorder); links. |
| `ui/book/ChapterDetailViewModel.kt`, `ChapterDetailScreen.kt` | Chapter title, read count ±1, internalized, parent book, delete; links. |
| `ui/quote/QuoteDetailViewModel.kt`, `QuoteDetailScreen.kt` | Quote text, optional author and source; links. |

### Resources
| File | Purpose |
|---|---|
| `app/src/main/res/drawable/ic_add.xml`, `ic_arrow_back.xml`, `ic_arrow_up.xml`, `ic_arrow_down.xml`, `ic_check.xml`, `ic_close.xml`, `ic_delete.xml`, `ic_expand_less.xml`, `ic_expand_more.xml`, `ic_settings.xml` | Material icons used in the UI. |
| `app/src/main/res/drawable/ic_notification.xml` | Status-bar icon for reminders. |

### Tests (`app/src/test/java/com/example/xcommandcenter/`)
| File | Purpose |
|---|---|
| `domain/HabitStatsTest.kt` | Positive/negative streaks, scheduled-only counting, month-end clamping, recent occurrences. |
| `domain/TabOrderTest.kt` | Parsing defaults, round trip, unknown/missing tabs, moves. |
| `domain/RecurrenceTest.kt` | Next occurrence for each schedule type, month-end clamping, empty weekly rule, building from columns. |
| `domain/ProjectTreeTest.kt` | Depth-first flattening (with collapsed nodes), subtree and ancestor computation. |
| `data/LinkEntityTest.kt` | Link normalization in both directions, same-tab ordering, self-link rejection. |

Run them with `./gradlew testDebugUnitTest`.

---

## 5. Verification performed

Done on 2026-10-24 on the `Medium_Phone` AVD (Android API 37).

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
