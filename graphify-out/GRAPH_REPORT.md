# Graph Report - metro-launcher  (2026-09-24)

## Corpus Check
- 39 files · ~29,357 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 13 file(s) not represented in the graph (top: .ttf 6, .xml 2, .properties 2)

## Summary
- 532 nodes · 1459 edges · 34 communities (16 shown, 18 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 21 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `824ae505`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- DrawerScreen.kt
- MainActivity.kt
- WallpaperRepository
- Metro Launcher — заметки для работы
- NotesRepository
- PlayerRepository.kt
- HomeViewModel
- HomeGrid.kt
- AppIconLoader
- WeatherTile.kt
- Metro Launcher
- HomeTileItem
- gradlew
- app/build.gradle.kts
- alertdialog
- mutablelongstateof
- GridPackerTest
- outlinedtextfield
- textbutton
- FrostedGlass.kt
- WeatherIconTest
- MetroFonts.kt
- gridcells
- TileDragResizeTest
- griditemspan
- draweritem
- iconcache
- jumpalphabets
- localconfiguration
- sectionapps
- lazyverticalgrid
- rememberlazygridstate

## God Nodes (most connected - your core abstractions)
1. `HomeTileItem` - 31 edges
2. `AppIconLoader` - 29 edges
3. `AppInfo` - 24 edges
4. `WallpaperRepository` - 23 edges
5. `HomeGrid()` - 23 edges
6. `HomeViewModel` - 21 edges
7. `MainActivity` - 20 edges
8. `HomeLayoutRepository` - 19 edges
9. `Row` - 18 edges
10. `WeatherRepository` - 18 edges

## Surprising Connections (you probably didn't know these)
- `TileContextMenu()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/edit/TileEditOverlay.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `AppPickerSheet()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/picker/AppPickerSheet.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `AppTile()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/AppTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `NoteRow()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/NotesTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `NotesTile()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/NotesTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Widgets Feature Set** — readme_notes_widget, readme_clock_widget, readme_weather_widget, readme_system_player_widget [EXTRACTED 1.00]

## Communities (34 total, 18 thin omitted)

### Community 0 - "DrawerScreen.kt"
Cohesion: 0.11
Nodes (71): Alignment, animatable, animatedvisibility, animatefloatasstate, MetroFonts, MetroDimens, Color, Dp (+63 more)

### Community 1 - "MainActivity.kt"
Cohesion: 0.05
Nodes (42): activityresultcontracts, alpha, AppWidgetHost, AppWidgetManager, AppWidgetProviderInfo, MainActivity, AppPickerSheet(), calculateWidgetSpans() (+34 more)

### Community 2 - "WallpaperRepository"
Cohesion: 0.21
Nodes (5): DeviceWallpaper, android, WallpaperRepository, Bitmap, kotlinx

### Community 3 - "Metro Launcher — заметки для работы"
Cohesion: 0.14
Nodes (13): 1. Цветовые токены (`MetroScheme`), 2. Геометрия и сетка, 3. Типографика, 4. Микроанимации и тактильный отклик, Metro Launcher — заметки для работы, Аудит соответствия стилю Metro (Где лаунчер еще расходится со стилем), Дизайн-система Metro (Quickshell Metro Style Guide), Пакеты: debug ≠ release (главная ловушка) (+5 more)

### Community 4 - "NotesRepository"
Cohesion: 0.29
Nodes (6): Note, NotesRepository, Dp, Modifier, NoteRow(), NotesTile()

### Community 5 - "PlayerRepository.kt"
Cohesion: 0.10
Nodes (16): StateFlow, MetroListener, PlayerRepository, TrackInfo, componentname, coroutinescope, delay, handler (+8 more)

### Community 6 - "HomeViewModel"
Cohesion: 0.13
Nodes (9): AndroidViewModel, IconCache, HomeViewModel, StateFlow, application, broadcastreceiver, intentfilter, launch (+1 more)

### Community 7 - "HomeGrid.kt"
Cohesion: 0.08
Nodes (31): androidx, Modifier, SmartLauncherHandles(), SmartLauncherPopupPositionProvider, TileContextMenu(), detectTilePressDrag(), HomeGrid(), AppWidgetHost (+23 more)

### Community 8 - "AppIconLoader"
Cohesion: 0.07
Nodes (36): AppIconLoader, AppWidgetProviderInfo, StateFlow, LoadedApp, LoadedWidgetApp, AppInfo, AppRepository, Flow (+28 more)

### Community 9 - "WeatherTile.kt"
Cohesion: 0.09
Nodes (35): City, Data, DayPoint, Error, HourPoint, Flow, JSONObject, Loading (+27 more)

### Community 10 - "Metro Launcher"
Cohesion: 0.15
Nodes (15): Clock Widget, Package dev.metro.launcher, Jump Grid by Letters, Kotlin + Jetpack Compose Stack, Metro Launcher, Metro Live Theme on Material3, Notes Widget, Open-Meteo (+7 more)

### Community 11 - "HomeTileItem"
Cohesion: 0.10
Nodes (16): DropDecision, GridPacker, Move, None, Swap, HomeLayoutRepository, AndroidWidget, AppPin (+8 more)

### Community 12 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 21 - "FrostedGlass.kt"
Cohesion: 0.08
Nodes (33): DrawerItem, Header, JumpAlphabets, Row, sectionApps(), SectionedApps, sectionLetter(), AppRow() (+25 more)

### Community 23 - "MetroFonts.kt"
Cohesion: 0.50
Nodes (3): font, fontfamily, r

### Community 25 - "TileDragResizeTest"
Cohesion: 0.32
Nodes (3): TileDragResizeTest, tileTag(), Offset

## Knowledge Gaps
- **29 isolated node(s):** `None`, `CLOCK`, `WEATHER`, `NOTES`, `PLAYER` (+24 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 129 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **18 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HomeTileItem` connect `HomeTileItem` to `DrawerScreen.kt`, `MainActivity.kt`, `HomeViewModel`, `HomeGrid.kt`, `TileDragResizeTest`?**
  _High betweenness centrality (0.106) - this node is a cross-community bridge._
- **Why does `WallpaperRepository` connect `WallpaperRepository` to `AppIconLoader`, `TileDragResizeTest`, `MainActivity.kt`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **Why does `AppIconLoader` connect `AppIconLoader` to `DrawerScreen.kt`, `HomeGrid.kt`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **What connects `None`, `CLOCK`, `WEATHER` to the rest of the system?**
  _29 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `DrawerScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.11196003526300323 - nodes in this community are weakly interconnected._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.05137844611528822 - nodes in this community are weakly interconnected._
- **Should `Metro Launcher — заметки для работы` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._