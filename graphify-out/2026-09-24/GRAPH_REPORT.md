# Graph Report - metro-launcher  (2026-09-24)

## Corpus Check
- 39 files · ~26,742 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 13 file(s) not represented in the graph (top: .ttf 6, .xml 2, .properties 2)

## Summary
- 518 nodes · 1432 edges · 32 communities (16 shown, 16 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 21 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- DrawerScreen.kt
- MainActivity.kt
- WallpaperRepository
- Metro Launcher — заметки для работы
- ClockTile.kt
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
- NotesRepository
- outlinedtextfield
- textbutton
- sectionApps
- WeatherIconTest
- gridcells
- griditemspan
- draweritem
- iconcache
- jumpalphabets
- localconfiguration
- sectionapps
- lazyverticalgrid
- rememberlazygridstate

## God Nodes (most connected - your core abstractions)
1. `AppIconLoader` - 29 edges
2. `HomeTileItem` - 29 edges
3. `AppInfo` - 24 edges
4. `WallpaperRepository` - 23 edges
5. `HomeGrid()` - 23 edges
6. `HomeViewModel` - 21 edges
7. `MainActivity` - 20 edges
8. `HomeLayoutRepository` - 19 edges
9. `Row` - 18 edges
10. `WeatherRepository` - 18 edges

## Surprising Connections (you probably didn't know these)
- `AppPickerSheet()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/picker/AppPickerSheet.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `CalendarFace()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/ClockTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `DayRow()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/WeatherTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `WeatherExpandedPanel()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/WeatherTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `TileDragResizeTest` --references--> `HomeLayoutRepository`  [EXTRACTED]
  app/src/androidTest/java/dev/metro/launcher/TileDragResizeTest.kt → app/src/main/java/dev/metro/launcher/data/HomeLayoutRepository.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Widgets Feature Set** — readme_notes_widget, readme_clock_widget, readme_weather_widget, readme_system_player_widget [EXTRACTED 1.00]

## Communities (32 total, 16 thin omitted)

### Community 0 - "DrawerScreen.kt"
Cohesion: 0.06
Nodes (107): Alignment, animatable, animatedvisibility, animatefloatasstate, Row, AppRow(), DrawerScreen(), JumpGrid() (+99 more)

### Community 1 - "MainActivity.kt"
Cohesion: 0.08
Nodes (25): activityresultcontracts, alpha, AppWidgetHost, AppWidgetManager, AppWidgetProviderInfo, MainActivity, AppPickerSheet(), backhandler (+17 more)

### Community 2 - "WallpaperRepository"
Cohesion: 0.21
Nodes (5): DeviceWallpaper, android, WallpaperRepository, Bitmap, kotlinx

### Community 3 - "Metro Launcher — заметки для работы"
Cohesion: 0.25
Nodes (7): Metro Launcher — заметки для работы, Пакеты: debug ≠ release (главная ловушка), Проверки на устройстве, Прочее, Релиз на телефон, Тесты, Эмулятор

### Community 4 - "ClockTile.kt"
Cohesion: 0.09
Nodes (31): androidx, Color, Dp, MetroDefaults, MetroDimens, MetroScheme, MetroTheme(), metroTypography() (+23 more)

### Community 5 - "PlayerRepository.kt"
Cohesion: 0.10
Nodes (16): StateFlow, MetroListener, PlayerRepository, TrackInfo, componentname, coroutinescope, delay, handler (+8 more)

### Community 6 - "HomeViewModel"
Cohesion: 0.09
Nodes (14): AndroidViewModel, AppRepository, IconCache, HomeViewModel, StateFlow, application, asstateflow, broadcastreceiver (+6 more)

### Community 7 - "HomeGrid.kt"
Cohesion: 0.13
Nodes (17): TileDragResizeTest, androidx, SmartLauncherHandles(), SmartLauncherPopupPositionProvider, tileTag(), detectTilePressDrag(), HomeGrid(), AppWidgetHost (+9 more)

### Community 8 - "AppIconLoader"
Cohesion: 0.10
Nodes (24): AppIconLoader, AppWidgetProviderInfo, StateFlow, LoadedApp, LoadedWidgetApp, AppInfo, StateFlow, asimagebitmap (+16 more)

### Community 9 - "WeatherTile.kt"
Cohesion: 0.10
Nodes (33): City, Data, DayPoint, Error, HourPoint, Flow, JSONObject, Loading (+25 more)

### Community 10 - "Metro Launcher"
Cohesion: 0.15
Nodes (15): Clock Widget, Package dev.metro.launcher, Jump Grid by Letters, Kotlin + Jetpack Compose Stack, Metro Launcher, Metro Live Theme on Material3, Notes Widget, Open-Meteo (+7 more)

### Community 11 - "HomeTileItem"
Cohesion: 0.08
Nodes (17): DropDecision, GridPacker, Move, None, Swap, HomeLayoutRepository, AndroidWidget, AppPin (+9 more)

### Community 12 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 16 - "NotesRepository"
Cohesion: 0.22
Nodes (9): Flow, Flow, Note, NotesRepository, edit, jsonarray, map, preferencesdatastore (+1 more)

### Community 21 - "sectionApps"
Cohesion: 0.43
Nodes (6): DrawerItem, Header, JumpAlphabets, sectionApps(), SectionedApps, sectionLetter()

### Community 22 - "WeatherIconTest"
Cohesion: 0.29
Nodes (4): WeatherIconTest, InterceptingWidgetContainer, android, FrameLayout

## Knowledge Gaps
- **24 isolated node(s):** `None`, `CLOCK`, `WEATHER`, `NOTES`, `PLAYER` (+19 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 118 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **16 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HomeTileItem` connect `HomeTileItem` to `DrawerScreen.kt`, `MainActivity.kt`, `HomeViewModel`, `HomeGrid.kt`?**
  _High betweenness centrality (0.096) - this node is a cross-community bridge._
- **Why does `WallpaperRepository` connect `WallpaperRepository` to `AppIconLoader`, `MainActivity.kt`, `HomeGrid.kt`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **Why does `AppIconLoader` connect `AppIconLoader` to `DrawerScreen.kt`, `HomeGrid.kt`?**
  _High betweenness centrality (0.056) - this node is a cross-community bridge._
- **What connects `None`, `CLOCK`, `WEATHER` to the rest of the system?**
  _24 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `DrawerScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.057703081232493 - nodes in this community are weakly interconnected._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.07807807807807808 - nodes in this community are weakly interconnected._
- **Should `ClockTile.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.08907563025210084 - nodes in this community are weakly interconnected._