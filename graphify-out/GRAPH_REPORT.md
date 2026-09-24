# Graph Report - metro-launcher  (2026-09-24)

## Corpus Check
- 39 files · ~27,204 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 13 file(s) not represented in the graph (top: .ttf 6, .xml 2, .properties 2)

## Summary
- 521 nodes · 1435 edges · 33 communities (17 shown, 16 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 21 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- WeatherTile.kt
- MainActivity.kt
- WallpaperRepository.kt
- Metro Launcher — заметки для работы
- MetroTheme.kt
- PlayerRepository.kt
- HomeViewModel
- HomeGrid.kt
- AppIconLoader
- WeatherRepository.kt
- Metro Launcher
- HomeTileItem
- gradlew
- app/build.gradle.kts
- alertdialog
- mutablelongstateof
- InternalWidgetType
- outlinedtextfield
- textbutton
- FrostedGlass.kt
- WeatherExpandedPanel
- MetroFonts.kt
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
- `TileContextMenu()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/edit/TileEditOverlay.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `AppTile()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/AppTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `PlayerTile()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/PlayerTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `DayRow()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/WeatherTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt
- `WeatherExpandedPanel()` --calls--> `Row`  [INFERRED]
  app/src/main/java/dev/metro/launcher/ui/tiles/WeatherTile.kt → app/src/main/java/dev/metro/launcher/data/AppSections.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Widgets Feature Set** — readme_notes_widget, readme_clock_widget, readme_weather_widget, readme_system_player_widget [EXTRACTED 1.00]

## Communities (33 total, 16 thin omitted)

### Community 0 - "WeatherTile.kt"
Cohesion: 0.10
Nodes (79): Alignment, animatable, animatedvisibility, animatefloatasstate, MetroFonts, MetroDimens, Color, Dp (+71 more)

### Community 1 - "MainActivity.kt"
Cohesion: 0.09
Nodes (23): activityresultcontracts, alpha, AppWidgetHost, AppWidgetManager, AppWidgetProviderInfo, MainActivity, calculateWidgetSpans(), AppWidgetProviderInfo (+15 more)

### Community 2 - "WallpaperRepository.kt"
Cohesion: 0.12
Nodes (14): DeviceWallpaper, android, StateFlow, WallpaperRepository, Bitmap, bitmapdrawable, bitmapfactory, booleanpreferenceskey (+6 more)

### Community 3 - "Metro Launcher — заметки для работы"
Cohesion: 0.25
Nodes (7): Metro Launcher — заметки для работы, Пакеты: debug ≠ release (главная ловушка), Проверки на устройстве, Прочее, Релиз на телефон, Тесты, Эмулятор

### Community 4 - "MetroTheme.kt"
Cohesion: 0.22
Nodes (10): androidx, Color, Dp, MetroDefaults, MetroScheme, MetroTheme(), metroTypography(), rememberWallpaperAccent() (+2 more)

### Community 5 - "PlayerRepository.kt"
Cohesion: 0.10
Nodes (16): StateFlow, MetroListener, PlayerRepository, TrackInfo, asstateflow, componentname, coroutinescope, handler (+8 more)

### Community 6 - "HomeViewModel"
Cohesion: 0.09
Nodes (14): AndroidViewModel, AppRepository, IconCache, HomeViewModel, StateFlow, application, broadcastreceiver, dispatchers (+6 more)

### Community 7 - "HomeGrid.kt"
Cohesion: 0.07
Nodes (32): androidx, Modifier, SmartLauncherHandles(), SmartLauncherPopupPositionProvider, TileContextMenu(), detectTilePressDrag(), HomeGrid(), AppWidgetHost (+24 more)

### Community 8 - "AppIconLoader"
Cohesion: 0.14
Nodes (14): AppIconLoader, AppWidgetProviderInfo, StateFlow, LoadedApp, LoadedWidgetApp, AppInfo, asimagebitmap, Context (+6 more)

### Community 9 - "WeatherRepository.kt"
Cohesion: 0.09
Nodes (23): Flow, Flow, Note, NotesRepository, City, Data, DayPoint, Error (+15 more)

### Community 10 - "Metro Launcher"
Cohesion: 0.15
Nodes (15): Clock Widget, Package dev.metro.launcher, Jump Grid by Letters, Kotlin + Jetpack Compose Stack, Metro Launcher, Metro Live Theme on Material3, Notes Widget, Open-Meteo (+7 more)

### Community 11 - "HomeTileItem"
Cohesion: 0.09
Nodes (14): TileDragResizeTest, DropDecision, GridPacker, Move, None, Swap, HomeLayoutRepository, AndroidWidget (+6 more)

### Community 12 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 16 - "InternalWidgetType"
Cohesion: 0.13
Nodes (6): InternalWidgetType, CLOCK, NOTES, PLAYER, WEATHER, GridPackerTest

### Community 21 - "FrostedGlass.kt"
Cohesion: 0.06
Nodes (42): DrawerItem, Header, JumpAlphabets, Row, sectionApps(), SectionedApps, sectionLetter(), AppRow() (+34 more)

### Community 22 - "WeatherExpandedPanel"
Cohesion: 0.13
Nodes (18): WeatherIconTest, HourPoint, weatherText(), CapsHeader(), dayNameRu(), DayRow(), DetailBox(), HourCell() (+10 more)

### Community 23 - "MetroFonts.kt"
Cohesion: 0.50
Nodes (3): font, fontfamily, r

## Knowledge Gaps
- **24 isolated node(s):** `None`, `CLOCK`, `WEATHER`, `NOTES`, `PLAYER` (+19 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 121 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **16 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HomeTileItem` connect `HomeTileItem` to `WeatherTile.kt`, `MainActivity.kt`, `HomeViewModel`, `HomeGrid.kt`, `InternalWidgetType`?**
  _High betweenness centrality (0.100) - this node is a cross-community bridge._
- **Why does `WallpaperRepository` connect `WallpaperRepository.kt` to `MainActivity.kt`, `HomeTileItem`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **Why does `AppIconLoader` connect `AppIconLoader` to `WeatherTile.kt`, `HomeGrid.kt`?**
  _High betweenness centrality (0.056) - this node is a cross-community bridge._
- **What connects `None`, `CLOCK`, `WEATHER` to the rest of the system?**
  _24 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `WeatherTile.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.10248447204968944 - nodes in this community are weakly interconnected._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.08571428571428572 - nodes in this community are weakly interconnected._
- **Should `WallpaperRepository.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.12183908045977011 - nodes in this community are weakly interconnected._