# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A JavaFX desktop app (German UI/comments, code identifiers in English) for viewing and bulk-editing
[iTrain](https://itrain.download/) `.tcd`/`.tcdz` model-railroad configuration files, and for
CSV-based import/export of individual `control-items` entries (e.g. copying blocks/locomotives
between layouts). It is not a general XML editor — it specifically understands the iTrain file
structure well enough to preserve everything it doesn't touch.

## Build & run

```
./gradlew run              # launch the app
./gradlew build             # compile + package
./gradlew jlink              # produce a self-contained runtime image (zip under build/distributions)
```

There are no tests (`src/test` does not exist) and no lint/format tooling configured — don't invent
commands for either.

Requires JDK 21 (Gradle toolchain auto-provisions it). The module is
`com.example.itrain_import_export`, launched via `Launcher` (a non-`Application` subclass wrapper
required by the `javafx` Gradle plugin) which calls into `HelloApplication`.

`translations.properties` is looked up first in the working directory and falls back to the bundled
resource copy — this lets translation edits take effect under `gradle run` without a rebuild.

## Architecture

**Generic XML tree, not per-category models.** iTrain files have 15 `control-items` categories
(functions, interfaces, feedbacks, accessories, memory, boosters, blocks, train-types, stations,
train-routes, locomotives, wagons, trains, actions, measurements), each with a different internal
shape. Rather than modeling each with dedicated Java classes, the whole file is parsed into a
generic, order-preserving `XmlNode` tree (`XmlNode.java`). All UI (`CategoryEditor`) operates on
`XmlNode` generically — a table of entries per category, and a tree view of an entry's raw
attributes/children. This is why adding a new *category* generally needs no new code, but adding
category-specific UI behavior means special-casing on `categoryName`/tag name (see the
`configuration`-table special case for locomotives in `CategoryEditor`).

**Round-trip fidelity is the core constraint.** `TcdDocument` loads/saves the file. Anything outside
the 15 known categories (settings, clock, control-state, switchboards, unknown future categories) is
carried through untouched. On save:
- `reorderChildren` re-sorts only the *known* tag names into `ROOT_ORDER`/`CONTROL_ITEM_ORDER`;
  unknown tags stay exactly where they were (moving unknown categories, e.g. `boosters`, was a real
  bug — iTrain refused to reopen the file because references pointed at elements now out of order).
- `count` attributes on known categories are recomputed to match actual child count.
- Before touching disk: the new XML is serialized to memory, re-parsed, and checked
  (`verifyWellFormed`) for a valid root and matching per-category counts — only then is it written via
  temp-file + atomic rename, so a bug never corrupts/truncates the on-disk file.
- The internal `.tcd` entry name inside a `.tcdz` (zip) always matches the *target* filename, not the
  originally-loaded name.

**`.tcd` vs `.tcdz`**: plain XML vs. that same XML zipped with one `.tcd` entry. `TcdDocument.load`/
`save` handle both transparently based on file extension.

**CSV import/export** (`CsvUtil`, used from `CategoryEditor.onExport`/`onImport`) round-trips
individual entries as full XML fragments in a cell (`TcdDocument.nodeToXmlString`/`xmlStringToNode`),
alongside category/type/name/description columns, using `;`-delimited, always-quoted, UTF-8-BOM CSV
(Excel/German-locale-friendly). Import validates the category column matches the current tab and
rejects files with the wrong number of columns rather than guessing.

**Backups**: `HelloController` copies the file to the configured backup directory *before* opening it
(numbered `<name>.<1-10>.bak`, oldest generation recycled), and deletes that backup again on
close/replace if the document was never modified nor saved — see `createBackup`/
`cleanupUnusedBackup` in `HelloController.java`.

**UI structure**: `hello-view.fxml` only defines the static shell (menu bar, file/status labels, an
empty `TabPane`). `HelloController.rebuildTabs()` populates one tab per category at runtime — always
all of `TcdDocument.TAB_DISPLAY_ORDER`, even for categories absent from the loaded file (the tab stays
empty and the XML element is created lazily on first add/import, so unused categories never get
written back into files that didn't have them). Rebuilt on file load, language change, and view-setting
change.

**Settings & i18n**: `AppSettings` persists via Java `Preferences` (Windows registry under
`HKCU\Software\JavaSoft\Prefs\...`) — default TCD/export/backup directories, theme, column visibility.
`I18n` loads `key.langcode=value` pairs from `translations.properties` for 6 languages
(`de en nl fr es it`); `I18n.setLanguage` fires listeners that trigger a full UI rebuild rather than
live-patching labels. `ThemeManager` just toggles `dark-theme.css` on/off the `Scene`.

## Notes for making changes

- When editing `TcdDocument.save`/`reorderChildren`, remember: unknown-tag ordering must never change
  relative position, and this has broken real files before.
- `ZipEntryRow.java` exists but has no remaining callers in the current UI — likely a leftover from an
  earlier raw zip-browsing feature.
- Comments and many identifiers/strings in this codebase are German; keep new code/comments
  consistent with the surrounding file's language rather than switching to English mid-file.
