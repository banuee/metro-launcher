# Metro Launcher

Android-лаунчер в стиле quickshell `metro` (Metro Live): полупрозрачные плитки
с блюром, акцент из обоев, WP-список приложений с Jump Grid по буквам,
виджеты (заметки, часы, погода Open-Meteo, системный плеер).

Пакет: `dev.metro.launcher`, minSdk 26, target/compile 35.
Стек: Kotlin + Jetpack Compose (Material3, без Material-ветки — только Metro Live).

## Статус

- [x] Фаза 0 — SDK 35 + эмулятор Pixel_API_35 + MCP (adb-mcp) + Gradle-скелет
- [x] Фаза 1 — каркас лаунчера (HOME, PackageManager, ViewModel)
- [x] Фаза 2 — главный экран + тема (плитки, стекло, акцент)
- [x] Фаза 3 — меню приложений + Jump Grid (прозрачный фон, свайпы, slide)
- [x] Фаза 4 — виджеты (заметки, погода 2x3 Open-Meteo, системный плеер) + блюр + Segoe UI
- [ ] Фаза 5 — полировка

## Сборка

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
