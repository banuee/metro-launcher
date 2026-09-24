# Metro Launcher — заметки для работы

Android-лаунчер в стиле quickshell `metro`: полупрозрачные плитки с блюром,
WP-список приложений с Jump Grid, виджеты (заметки, часы, погода Open-Meteo,
системный плеер). Kotlin + Jetpack Compose (Material3), minSdk 26,
target/compileSdk 35. Подробнее — `README.md`, карта кода — `graphify-out/`.

Структура `app/src/main/java/dev/metro/launcher/`:
`MainActivity.kt`, `data/` (репозитории: layout, notes, weather, player,
wallpaper, apps, иконки `AppIconLoader`), `ui/` (`HomeGrid`, `HomeViewModel`,
`DrawerScreen`, `edit/` — drag/resize-детекторы, `picker/` — диалоги,
`tiles/` — плитки, `theme/`). Типы плиток (`data/HomeTileItem.kt`):
`AppPin`, `InternalWidget` (CLOCK/WEATHER/NOTES/PLAYER), `AndroidWidget`.
Сетка 4 колонки; порядок/размеры — DataStore `metro_layout`.

## Пакеты: debug ≠ release (главная ловушка)

| Сборка | package | Откуда берётся |
|---|---|---|
| debug (`assembleDebug`, тесты) | `dev.metro.launcher.debug` | `applicationIdSuffix = ".debug"` |
| release (`assembleRelease`) | `dev.metro.launcher` | подписан релизным ключом, ставится на телефон |

Проверяй визуально и руками **только `.debug`**. Пакет без суффикса на
эмуляторе — старый релиз, его код не соответствует исходникам. Если
сомневаешься, что стоит: `list_packages` с фильтром `metro`, отсутствие
`.debug` после вайпа эмулятора — нормально, появится при установке/тестах.

## Эмулятор

- AVD: `Pixel_API_35`, серийник обычно `emulator-5554`.
- Старт: `boot_emulator`. Частый кейс: вызов падает по таймауту, но девайс
  всё равно поднимается — проверь `list_devices` + `wait_for_boot`, не грузи
  второй эмулятор.
- Установка: `install_app` с `app/build/outputs/apk/debug/app-debug.apk`
  (после `assembleDebug`) либо `build_and_run` с package
  `dev.metro.launcher.debug`. `connectedDebugAndroidTest` и так ставит
  свежий debug + тестовый APK перед прогоном.
- Чистый старт: `clear_app_data` для `.debug`. Внимание: на свежей установке
  через ~800 мс после старта сам открывается системный photo picker
  (first-run промпт обоев) и перекрывает активность — для ручных проверок
  закрой его через back, в тестах он уже погашен (см. ниже).

## Проверки на устройстве

- Скриншот даунскейлится; полный размер — `adb -s emulator-5554 exec-out
  screencap -p > /tmp/x.png`. Координаты тапов — только из `describe_ui`,
  не со скриншота.
- Логи падений: сначала `last_crash` (DropBox), потом `logcat` с фильтром.
  Кадры/лаги: `render_stats` (с `reset=true` перед измеряемым действием).
- **Drag пальцем через MCP не работает**: `android_drag` не испускает
  промежуточные move-события — сетка не скроллится, детекторы молчат.
  Перетаскивание проверяется только инструментальными тестами с настоящей
  инжекцией (`performTouchInput`: `longClick` + `swipe`), руками через
  MCP — максимум тап/скролл/скриншот.

## Тесты

- Все: `./gradlew :app:connectedDebugAndroidTest` (9 тестов, эмулятор нужен).
- Один: `-Pandroid.testInstrumentationRunnerArguments.class=dev.metro.launcher.<Class>[#method]`.
- Файлы: `app/src/androidTest/.../TileDragResizeTest.kt` (drag/drop/resize/меню),
  `WeatherIconTest.kt` (кроп nerd-иконок).
- Конвенции в тестах: `@Before resetLayout()` сбрасывает сетку к дефолту и
  гасит first-run промпт обоев через `WallpaperRepository.suppressPickerPromptForTests()`
  (иначе picker роняет прогон потерей иерархии); порядок читается из того же
  `HomeLayoutRepository`, что и UI. Временные пины именуй `tmp_pin_*` —
  `resetToDefault()` их сносит.

## Релиз на телефон

Подпись: `local.properties` (`metro.storePassword`, `metro.keyAlias`,
`metro.keyPassword`, опционально `metro.storeFile`; дефолт —
`~/.config/metro-launcher/metro-release.keystore`). Значения секретов никуда
не копировать. Сборка: `./gradlew :app:assembleRelease -x test` →
`app/build/outputs/apk/release/app-release.apk`, ставить на телефон вручную.

## Прочее

- Погода: Open-Meteo (`https://api.open-meteo.com`), время парсится в UTC.
  Nerd-иконки рисуются на канве через `TextPainter` с центрированием ink-бокса:
  `lineHeight` меньше естественной высоты шрифта Compose игнорирует, текстом
  кроп не чинится — не возвращать `Text` в `WeatherIcon`.
- Drag-архитектура: коммит один, в `endTileDrag` (`moveTileTo` по id, не по
  индексам); mid-drag коммиты запрещены (пинг-понг рефлоу при разных спанах).
  `tileSlotRect` видит только скомпонованные плитки — вне вьюпорта решаем
  по порядку (см. `endTileDrag`).
- После правок кода: `graphify update` в корне проекта.
