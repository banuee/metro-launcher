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

## Дизайн-система Metro (Quickshell Metro Style Guide)

Визуальный ДНК проекта — строгий Fluent/Acrylic Metro в духе Windows Phone и quickshell `metro` (десктопная среда в `~/.config/quickshell`).
Никакого Material 3 / Android-дефолта: пастельных пилюль, разноцветных подложек или массивных закруглений.

### 1. Цветовые токены (`MetroScheme`)
- `accent`: живой акцент из обоев (`WallpaperColors`, фолбэк `#00ABA9` — quickshell teal). Должен сохранять контраст и насыщенность (lightness 0.60–0.85, saturation >= 0.5).
- `glass`: `rgba(255, 255, 255, 0.07)` — монохромная подложка плиток по умолчанию поверх блюра обоев.
- `glassHover`: `rgba(255, 255, 255, 0.12)` — поля ввода, активные области.
- `glassDeep`: `rgba(20, 20, 20, 0.90)` — глубокий темный акрил для оверлеев, диалогов (`HomeAddDialog`), контекстных меню (`TileContextMenu`) и шторки приложений.
- `stroke`: `rgba(255, 255, 255, 0.08)` — тонкая граница 1px для стеклянных элементов.
- `strokeStrong`: `rgba(255, 255, 255, 0.15)` — выделенные разделители и рамки карточек в шторках.
- `red`: `#E51400` — фирменный crimson red Windows Phone (для деструктивных действий: «Удалить виджет», «Удалить значок»). Ни в коем случае не Material `#FF5252` или розовый.
- `text`: `#F7F7F7` (100% чёткость), `textDim`: `rgba(255, 255, 255, 0.60)`.

### 2. Геометрия и сетка
- Сетка: 4 колонки, базовый юнит `unit = 84dp`, зазор `gap = 8dp`.
- Скругления:
  - `radius = 10dp` — плитки на рабочем столе (`MetroDimens.radius`).
  - `radiusSmall = 6..8dp` — кнопки действий, чекбоксы, мелкие контролы.
  - `radiusPanel = 16dp` — карточки диалогов (`HomeAddDialog`), контекстное меню, шторки выбора.
- Границы: ровно 1px (`stroke`), при фокусе/выделении — 1.5–2px `accent`.
- Оверлеи и диалоги:
  - Верхняя акцентная полоска (`Top accent glow` — 2..3dp плашка или градиент вверху окна/меню).
  - Никаких стандартных Android/Material drag-handle "сосисок" по центру шапки.
  - Акцентный пилл-индикатор заголовка (`width: 24..30dp, height: 2.5..3dp, radius: 1.5dp`).

### 3. Типографика
- Семейство шрифтов: `Segoe UI Variable` (файлы в `res/font/`).
  - `MetroFonts.text`: `segoe_text_light`, `segoe_text`, `segoe_text_semibold`.
  - `MetroFonts.headline`: `segoe_display_light`, `segoe_display_semibold`.
- Иерархия:
  - Заголовки экранов и секций: крупный `FontWeight.Light` (20..30sp) либо капс `FontWeight.SemiBold` с разреженным межбуквенным интервалом (`letterSpacing = 1.5..2sp`).
  - Цифры и счетчики (часы, градусы погоды): очень тонкие и крупные (`FontWeight.Light`, 44..54sp).
  - Подписи плиток и списки: `11..14sp` Regular/Medium, аккуратный `Ellipsis`.

### 4. Микроанимации и тактильный отклик
- Тактильный клик (`metroClickable` и `TileFrame`):
  - При тапе: мгновенный упругий отскок (`0.93f -> 1.0f` через `Spring`).
  - При зажатии: плавное сжатие вглубь экрана (`0.95f` за 100–130мс).
  - При клике на плитку: кратковременный световой импульс акцентного бордера (`pulseBorder`).
- Кривые Безье (аналоги Hyprland/Quickshell):
  - Открытие/появление: `CubicBezierEasing(0.05f, 0.9f, 0.1f, 1.0f)` (быстрый выброс с мягким торможением).
  - Перемещение: `CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)`.
  - Отскок/overshoot: `CubicBezierEasing(0.34f, 1.3f, 0.64f, 1.0f)`.

---

## Аудит соответствия стилю Metro (Где лаунчер еще расходится со стилем)

1. **Композитный живой блюр (Android vs Hyprland)**:
   - *В десктопном шелле*: Hyprland накладывает аппаратный двупроходный блюр (`passes = 2, size = 3, vibrancy = 0.55`) в реальном времени на всё, что находится под полупрозрачными окнами.
   - *В лаунчере*: Блюр реализован статическим предрендером обоев (`LocalBlurredWallpaper`). Это даёт идеальный 1:1 фрост для статичных картинок, но для живых обоев и динамических слоев (например, список приложений под шторкой поиска или Jump Grid) используется Compose `Modifier.blur()` или темная подложка `Color.Black.copy(0.65f)`. На Android 12+ (API 31+) можно задействовать `RenderEffect.createBlurEffect` для живого размытия любых View.
2. **Jump Grid в меню приложений**:
   - В текущем `DrawerScreen.kt` буквы в Jump Grid отображаются как парящий текст на затемненном фоне. В аутентичном Windows Phone и quickshell Launcher это сетка аккуратных квадратных стеклянных плиток с рамками (`stroke`), где доступные буквы подсвечиваются акцентом/стеклом, а пустые остаются темными.
3. **Плавность и кривые анимаций перехода**:
   - Часть анимаций Compose (`AnimatedVisibility`, кроссфейды) использует дефолтные `FastOutSlowInEasing` или линейный `tween`. Перевод их на безье-кривые quickshell (`0.05, 0.9, 0.1, 1.0`) придаст фирменную резкую и гладкую динамику Metro.
4. **Унификация иконок (Nerd Font vs Canvas vs Material)**:
   - В `PlayerTile` используются хардкод-глифы Nerd Font (`\uF001`, `\uF048` и т.д.), в `DrawerScreen` лупа рисуется через `Canvas`, в `NotesTile` используется `Icons.Default.Check`, а в стрелках — Material Icons. Стоит свести все иконки к единому набору Nerd Font / чистым минималистичным SVG-контурам.
5. **3D-наклон плиток при нажатии (Tile Tilt)**:
   - В Windows Phone плитки при тапе наклонялись в сторону точки касания (3D perspective tilt по осям X/Y). В лаунчере сейчас только равномерный `scale(0.95f)`. Добавление легкого `rotationX/rotationY` в зависимости от точки тапа завершит тактильный образ Live Tiles.
6. **Полноэкранный иммерсивный статус-бар**:
   - Сейчас в `MainActivity` задан глобальный `systemBarsPadding()`, из-за чего плитки обрезаются под системными часами и навбаром. В настоящем Metro плитки прокручиваются под прозрачным статус-баром, создавая эффект бескрайней стеклянной поверхности.

---

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

