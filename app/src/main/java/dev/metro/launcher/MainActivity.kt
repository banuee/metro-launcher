package dev.metro.launcher

import android.animation.ValueAnimator
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import dev.metro.launcher.data.HomeTileItem
import dev.metro.launcher.data.InternalWidgetType
import dev.metro.launcher.data.NotesRepository
import dev.metro.launcher.data.PlayerRepository
import dev.metro.launcher.data.WallpaperRepository
import dev.metro.launcher.data.WeatherRepository
import dev.metro.launcher.ui.DrawerScreen
import dev.metro.launcher.ui.HomeGrid
import dev.metro.launcher.ui.HomeViewModel
import dev.metro.launcher.ui.picker.AppPickerSheet
import dev.metro.launcher.ui.picker.HomeAddDialog
import dev.metro.launcher.ui.picker.WidgetPickerSheet
import dev.metro.launcher.ui.picker.calculateWidgetSpans
import dev.metro.launcher.ui.theme.LocalBlurredWallpaper
import dev.metro.launcher.ui.theme.MetroTheme
import java.util.UUID

private const val APPWIDGET_HOST_ID = 1024

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()
    private var blurAnim: ValueAnimator? = null
    private var wallpaperRepo: WallpaperRepository? = null

    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager

    private var pendingWidgetId: Int? = null
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null
    private var pendingTargetPosition: Pair<Int, Int>? = null

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = pendingWidgetId
        val info = pendingWidgetInfo
        if (result.resultCode == RESULT_OK && id != null && info != null) {
            checkAndConfigureWidget(id, info)
        } else if (id != null) {
            appWidgetHost.deleteAppWidgetId(id)
            pendingTargetPosition = null
        }
        pendingWidgetId = null
        pendingWidgetInfo = null
    }

    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = pendingWidgetId
        val info = pendingWidgetInfo
        val targetPos = pendingTargetPosition
        if (result.resultCode == RESULT_OK && id != null && info != null) {
            val (cols, rows) = calculateWidgetSpans(info)
            vm.addTile(
                HomeTileItem.AndroidWidget(
                    id = UUID.randomUUID().toString(),
                    appWidgetId = id,
                    packageName = info.provider.packageName,
                    className = info.provider.className,
                    colSpan = cols,
                    rowSpan = rows,
                    col = targetPos?.first,
                    row = targetPos?.second,
                ),
                targetCol = targetPos?.first,
                targetRow = targetPos?.second,
            )
        } else if (id != null) {
            appWidgetHost.deleteAppWidgetId(id)
        }
        pendingWidgetId = null
        pendingWidgetInfo = null
        pendingTargetPosition = null
    }

    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            wallpaperRepo?.reload()
        }
    }

    /** Свои обои через Photo Picker (разрешений не требует вообще). */
    private val pickWallpaper = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) wallpaperRepo?.setCustom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appWidgetManager = AppWidgetManager.getInstance(this)

        runCatching {
            registerReceiver(
                wallpaperReceiver,
                IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
                Context.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { error ->
            Log.w("MetroLauncher", "Не удалось зарегистрировать wallpaper receiver", error)
        }
        setContent {
            MetroTheme {
                val apps by vm.apps.collectAsState()
                val tiles by vm.tiles.collectAsState()

                var drawerOpen by remember { mutableStateOf(false) }
                var jumpOpen by remember { mutableStateOf(false) }

                var showAddMenu by remember { mutableStateOf(false) }
                var showAppPicker by remember { mutableStateOf(false) }
                var showWidgetPicker by remember { mutableStateOf(false) }

                val drawerListState = rememberLazyListState()
                val context = applicationContext
                val notesRepo = remember { NotesRepository(context) }
                val weatherRepo = remember { WeatherRepository(context) }
                val playerRepo = remember { PlayerRepository(context) }
                val wpRepo = remember {
                    WallpaperRepository(context).also { wallpaperRepo = it }
                }
                // У обоих репозиториев свои scope/таймеры и MediaController:
                // без явного закрытия они живут дольше UI и держат ресурсы
                // (декод обоев, опрос медиасессий) после ухода с экрана.
                DisposableEffect(wpRepo, playerRepo) {
                    onDispose {
                        wpRepo.close()
                        playerRepo.close()
                    }
                }
                // Первый запуск без фроста — один раз предлагаем выбрать обои.
                // Системные на API 33+ недоступны, свои — через picker без разрешений.
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(800)
                    if (wpRepo.wallpaper.value == null && wpRepo.consumePickerPrompt()) {
                        try {
                            pickWallpaper.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
                val wallpaper by wpRepo.wallpaper.collectAsState()
                fun closeDrawer() {
                    drawerOpen = false
                    jumpOpen = false
                }

                // Кросфейд резкий<->блюр 250мс вместо щелчка флагом.
                val blurAlpha by animateFloatAsState(
                    targetValue = if (drawerOpen || jumpOpen) 1f else 0f,
                    animationSpec = tween(durationMillis = 250),
                    label = "wp-blur",
                )

                LaunchedEffect(drawerOpen, jumpOpen, wallpaper) {
                    if (wallpaper == null) {
                        // Живые обои: только оконный блюр, тоже с фейдом.
                        animateWindowBlur(if (drawerOpen || jumpOpen) 80 else 0)
                    } else {
                        setWindowBlur(0)
                    }
                }

                BackHandler(enabled = jumpOpen) { jumpOpen = false }
                BackHandler(enabled = drawerOpen && !jumpOpen) { closeDrawer() }

                // Слои обоев — строго в координатах окна (без инсетов),
                // чтобы срезы фроста в плитках совпадали 1:1.
                Box(Modifier.fillMaxSize()) {
                    val wp = wallpaper
                    AnimatedVisibility(
                        visible = wp != null,
                        enter = fadeIn(animationSpec = tween(durationMillis = 400)),
                    ) {
                        if (wp != null) {
                            Image(
                                bitmap = wp.sharp,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            Image(
                                bitmap = wp.blurred,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().alpha(blurAlpha),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    CompositionLocalProvider(
                        LocalBlurredWallpaper provides wallpaper?.blurred,
                    ) {
                        Box(Modifier.fillMaxSize().systemBarsPadding()) {
                            AnimatedVisibility(
                                visible = !drawerOpen,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                HomeGrid(
                                    tiles = tiles,
                                    apps = apps,
                                    notesRepo = notesRepo,
                                    weatherRepo = weatherRepo,
                                    playerRepo = playerRepo,
                                    appWidgetHost = appWidgetHost,
                                    appWidgetManager = appWidgetManager,
                                    onAppClick = { app -> vm.launch(app) },
                                    onOpenDrawer = { drawerOpen = true },
                                    onClockClick = {
                                        val clockApp = apps.find {
                                            it.packageName == "com.google.android.deskclock" ||
                                                it.packageName == "com.android.deskclock" ||
                                                it.packageName.contains("deskclock", ignoreCase = true) ||
                                                it.packageName.contains("clock", ignoreCase = true)
                                        }
                                        if (clockApp != null) {
                                            vm.launch(clockApp)
                                        } else {
                                            try {
                                                val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    onCalendarClick = {
                                        val calendarApp = apps.find {
                                            it.packageName == "com.google.android.calendar" ||
                                                it.packageName == "com.android.calendar" ||
                                                it.packageName.contains("calendar", ignoreCase = true)
                                        }
                                        if (calendarApp != null) {
                                            vm.launch(calendarApp)
                                        } else {
                                            try {
                                                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    onUpdateTileSpan = { id, colSpan, rowSpan ->
                                        vm.updateTileSpan(id, colSpan, rowSpan)
                                    },
                                    onMoveTile = { id, to ->
                                        vm.moveTileTo(id, to)
                                    },
                                    onDropDecision = { decision ->
                                        vm.applyDropDecision(decision)
                                    },
                                    onDeleteTile = { id ->
                                        handleDeleteTile(id, tiles)
                                    },
                                    onEmptyLongClick = {
                                        showAddMenu = true
                                    },
                                    onEmptyCellLongClick = { col, row ->
                                        pendingTargetPosition = Pair(col, row)
                                        showAddMenu = true
                                    },
                                )
                            }
                            AnimatedVisibility(
                                visible = drawerOpen,
                                enter = slideInHorizontally(initialOffsetX = { it }),
                                exit = slideOutHorizontally(targetOffsetX = { it }),
                            ) {
                                // Свайп слева направо закрывает меню.
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            var acc = 0f
                                            detectHorizontalDragGestures(
                                                onDragCancel = { acc = 0f },
                                                onDragEnd = {
                                                    if (acc > 120f) closeDrawer()
                                                    acc = 0f
                                                },
                                            ) { _, dragAmount -> acc += dragAmount }
                                        },
                                ) {
                                    DrawerScreen(
                                        apps = apps,
                                        listState = drawerListState,
                                        jumpOpen = jumpOpen,
                                        onJumpOpenChange = { jumpOpen = it },
                                        onAppClick = { app ->
                                            closeDrawer()
                                            vm.launch(app)
                                        },
                                        onPickWallpaper = {
                                            try {
                                                pickWallpaper.launch(
                                                    PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                    ),
                                                )
                                            } catch (_: Exception) {
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Меню долгого нажатия на пустое место: Добавить виджет / Добавить значок
                if (showAddMenu) {
                    HomeAddDialog(
                        onAddWidgetClick = {
                            showAddMenu = false
                            showWidgetPicker = true
                        },
                        onAddAppClick = {
                            showAddMenu = false
                            showAppPicker = true
                        },
                        onDismiss = {
                            showAddMenu = false
                            pendingTargetPosition = null
                        },
                    )
                }

                // Экран выбора приложения (Добавить значок)
                if (showAppPicker) {
                    AppPickerSheet(
                        apps = apps,
                        onSelectApp = { app ->
                            val pos = pendingTargetPosition
                            vm.addTile(
                                HomeTileItem.AppPin(
                                    id = UUID.randomUUID().toString(),
                                    packageName = app.packageName,
                                    colSpan = 1,
                                    rowSpan = 1,
                                    col = pos?.first,
                                    row = pos?.second,
                                ),
                                targetCol = pos?.first,
                                targetRow = pos?.second,
                            )
                            showAppPicker = false
                            pendingTargetPosition = null
                        },
                        onDismiss = {
                            showAppPicker = false
                            pendingTargetPosition = null
                        },
                    )
                }

                // Экран выбора виджета (Добавить виджет)
                if (showWidgetPicker) {
                    WidgetPickerSheet(
                        apps = apps,
                        onSelectInternalWidget = { type ->
                            val pos = pendingTargetPosition
                            vm.addTile(
                                HomeTileItem.InternalWidget(
                                    id = UUID.randomUUID().toString(),
                                    type = type,
                                    colSpan = 2,
                                    rowSpan = 2,
                                    col = pos?.first,
                                    row = pos?.second,
                                ),
                                targetCol = pos?.first,
                                targetRow = pos?.second,
                            )
                            showWidgetPicker = false
                            pendingTargetPosition = null
                        },
                        onSelectAppWidget = { info ->
                            handleSelectAppWidget(info)
                            showWidgetPicker = false
                        },
                        onDismiss = {
                            showWidgetPicker = false
                            pendingTargetPosition = null
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            appWidgetHost.startListening()
        } catch (_: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            appWidgetHost.stopListening()
        } catch (_: Exception) {
        }
    }

    private fun handleSelectAppWidget(info: AppWidgetProviderInfo) {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        if (canBind) {
            checkAndConfigureWidget(appWidgetId, info)
        } else {
            pendingWidgetId = appWidgetId
            pendingWidgetInfo = info
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            bindWidgetLauncher.launch(intent)
        }
    }

    private fun checkAndConfigureWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            pendingWidgetId = appWidgetId
            pendingWidgetInfo = info
            val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            configureWidgetLauncher.launch(configureIntent)
        } else {
            val (cols, rows) = calculateWidgetSpans(info)
            val targetPos = pendingTargetPosition
            pendingTargetPosition = null
            vm.addTile(
                HomeTileItem.AndroidWidget(
                    id = UUID.randomUUID().toString(),
                    appWidgetId = appWidgetId,
                    packageName = info.provider.packageName,
                    className = info.provider.className,
                    colSpan = cols,
                    rowSpan = rows,
                    col = targetPos?.first,
                    row = targetPos?.second,
                ),
                targetCol = targetPos?.first,
                targetRow = targetPos?.second,
            )
        }
    }

    private fun handleDeleteTile(tileId: String, currentTiles: List<HomeTileItem>) {
        val tile = currentTiles.find { it.id == tileId }
        if (tile is HomeTileItem.AndroidWidget) {
            try {
                appWidgetHost.deleteAppWidgetId(tile.appWidgetId)
            } catch (_: Exception) {
            }
        }
        vm.removeTile(tileId)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        wallpaperRepo?.reload()
    }

    override fun onDestroy() {
        runCatching {
            unregisterReceiver(wallpaperReceiver)
        }.onFailure { error ->
            Log.w("MetroLauncher", "Не удалось снять wallpaper receiver", error)
        }
        super.onDestroy()
    }

    /** Оконный блюр с фейдом (fallback для живых обоев). API 31+. */
    private fun animateWindowBlur(target: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        blurAnim?.cancel()
        val start = try {
            window.attributes.blurBehindRadius
        } catch (_: Exception) {
            0
        }
        if (start == target) {
            applyWindowBlur(target)
            return
        }
        blurAnim = ValueAnimator.ofInt(start, target).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyWindowBlur(it.animatedValue as Int) }
            start()
        }
    }

    fun setWindowBlur(radius: Int) {
        blurAnim?.cancel()
        applyWindowBlur(radius)
    }

    private fun applyWindowBlur(radius: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val lp = window.attributes
            lp.blurBehindRadius = radius
            window.attributes = lp
            if (radius > 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        } catch (_: Exception) {
        }
    }
}
