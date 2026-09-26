package dev.metro.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import dev.metro.launcher.ui.theme.MetroAnimations
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import dev.metro.launcher.data.AppCustomizationRepository
import dev.metro.launcher.data.AppIconLoader
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.data.DrawerItem
import dev.metro.launcher.data.JumpAlphabets
import dev.metro.launcher.data.sectionApps
import dev.metro.launcher.ui.picker.AppContextMenu
import dev.metro.launcher.ui.picker.AppEditDialog
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.MetroIcon
import dev.metro.launcher.ui.theme.MetroIcons
import dev.metro.launcher.ui.theme.metroClickable
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.Image as ComposeImage

/**
 * Меню приложений: резкие обои + фрост под строкой поиска и иконками,
 * поиск sticky (список едет под ним), WP-список с секциями.
 */
@Composable
fun DrawerScreen(
    apps: List<AppInfo>,
    listState: LazyListState,
    jumpOpen: Boolean,
    onJumpOpenChange: (Boolean) -> Unit,
    onAppClick: (AppInfo, androidx.compose.ui.geometry.Rect?) -> Unit,
    onPickWallpaper: () -> Unit,
    onOpenSettings: () -> Unit = {},
    isAppPinned: ((String) -> Boolean)? = null,
    onPinApp: ((AppInfo) -> Unit)? = null,
    onUnpinApp: ((AppInfo) -> Unit)? = null,
    onRefreshApps: () -> Unit = {},
) {
    val scheme = LocalMetroScheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customizationRepo = remember { AppCustomizationRepository.getInstance(context) }
    val hiddenPackages by customizationRepo.hiddenPackages.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedAppForMenu by remember { mutableStateOf<Pair<AppInfo, androidx.compose.ui.geometry.Rect?>?>(null) }
    var appToEdit by remember { mutableStateOf<AppInfo?>(null) }
    var showHidden by remember { mutableStateOf(false) }

    val (hiddenApps, visibleApps) = remember(apps, hiddenPackages) {
        apps.partition { it.packageName in hiddenPackages }
    }

    val filtered = remember(visibleApps, query) {
        if (query.isBlank()) visibleApps
        else visibleApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val sectioned = remember(filtered) { sectionApps(filtered) }

    val drawerBlur by animateDpAsState(
        targetValue = if (jumpOpen) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = 220, easing = MetroAnimations.OpenEasing),
        label = "drawer-blur",
    )

    BackHandler(enabled = appToEdit != null) { appToEdit = null }
    BackHandler(enabled = selectedAppForMenu != null && appToEdit == null) { selectedAppForMenu = null }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .then(if (drawerBlur > 0.dp) Modifier.blur(drawerBlur) else Modifier),
        ) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 68.dp, bottom = 16.dp),
                ) {
                    items(sectioned.items) { item ->
                        when (item) {
                            is DrawerItem.Header -> LetterHeader(
                                letter = item.letter,
                                onClick = { onJumpOpenChange(true) },
                            )
                            is DrawerItem.Row -> AppRow(
                                app = item.app,
                                onClick = { bounds -> onAppClick(item.app, bounds) },
                                onLongClick = { bounds -> selectedAppForMenu = item.app to bounds },
                            )
                        }
                    }
                    item {
                        HiddenAppsSection(
                            hiddenApps = hiddenApps,
                            expanded = showHidden,
                            onToggleExpand = { showHidden = !showHidden },
                            onAppClick = { app, bounds -> onAppClick(app, bounds) },
                            onAppLongClick = { app, bounds -> selectedAppForMenu = app to bounds },
                        )
                    }
                    item {
                        Text(
                            text = "Параметры лаунчера",
                            color = scheme.textDim,
                            fontSize = 13.sp,
                            fontFamily = MetroFonts.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .metroClickable(targetScale = 0.96f, onClick = onOpenSettings)
                                .padding(vertical = 16.dp),
                        )
                    }
                }
                // Sticky: поиск и кнопка параметров.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetroSearchBar(
                        query = query,
                        onQuery = { query = it },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    FrostedGlassBox(
                        modifier = Modifier
                            .size(52.dp)
                            .metroClickable(targetScale = 0.92f, onClick = onOpenSettings),
                        tint = scheme.glassHover,
                        borderColor = scheme.stroke,
                    ) {
                        MetroIcon(
                            icon = MetroIcons.Settings,
                            color = scheme.text,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = jumpOpen,
            enter = fadeIn(animationSpec = tween(durationMillis = 200, easing = MetroAnimations.OpenEasing)),
            exit = fadeOut(animationSpec = tween(durationMillis = 180, easing = MetroAnimations.CloseEasing)),
        ) {
            JumpGrid(
                available = sectioned.headerIndex.keys,
                onPick = { letter ->
                    onJumpOpenChange(false)
                    val idx = sectioned.headerIndex[letter]
                    if (idx != null) {
                        scope.launch {
                            val targetIdx = if (idx > 0) idx - 1 else 0
                            val current = listState.firstVisibleItemIndex
                            if (abs(targetIdx - current) > 8) {
                                val near = if (targetIdx > current) targetIdx - 4 else targetIdx + 4
                                listState.scrollToItem(near.coerceAtLeast(0))
                            }
                            listState.animateScrollToItem(targetIdx)
                        }
                    }
                },
                onDismiss = { onJumpOpenChange(false) },
            )
        }

        // Контекстное меню приложения при долгом клике
        if (selectedAppForMenu != null) {
            val (targetApp, targetBounds) = selectedAppForMenu!!
            val isHidden = hiddenPackages.contains(targetApp.packageName)
            val pinned = isAppPinned?.invoke(targetApp.packageName) ?: false
            AppContextMenu(
                app = targetApp,
                anchorBounds = targetBounds,
                isPinned = pinned,
                isHidden = isHidden,
                onOpenApp = {
                    val a = targetApp
                    val b = targetBounds
                    selectedAppForMenu = null
                    onAppClick(a, b)
                },
                onTogglePin = {
                    selectedAppForMenu = null
                    if (pinned) {
                        onUnpinApp?.invoke(targetApp)
                    } else {
                        onPinApp?.invoke(targetApp)
                    }
                },
                onEditApp = {
                    val a = targetApp
                    selectedAppForMenu = null
                    appToEdit = a
                },
                onToggleHide = {
                    selectedAppForMenu = null
                    scope.launch {
                        customizationRepo.setHidden(targetApp.packageName, !isHidden)
                        onRefreshApps()
                    }
                },
                onOpenSettings = {
                    selectedAppForMenu = null
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", targetApp.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                onUninstall = {
                    selectedAppForMenu = null
                    try {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:${targetApp.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                onDismiss = {
                    selectedAppForMenu = null
                },
            )
        }

        // Диалог редактирования приложения
        if (appToEdit != null) {
            AppEditDialog(
                app = appToEdit!!,
                customizationRepo = customizationRepo,
                onDismiss = { appToEdit = null },
                onSaved = {
                    appToEdit = null
                    onRefreshApps()
                },
            )
        }
    }
}

/**
 * Стеклянный поиск как в LauncherPanel.qml: стекло, лупа, плейсхолдер,
 * акцентный бордер в фокусе. Крестик очищает.
 */
@Composable
private fun MetroSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "поиск…",
) {
    val scheme = LocalMetroScheme.current
    var focused by remember { mutableStateOf(false) }
    FrostedGlassBox(
        modifier = modifier.height(52.dp),
        tint = scheme.glassHover,
        borderColor = if (focused) scheme.accent else scheme.stroke,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroIcon(
                icon = MetroIcons.Search,
                color = if (focused) scheme.accent else scheme.textDim,
                fontSize = 16.sp,
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = scheme.text,
                        fontSize = 15.sp,
                        fontFamily = MetroFonts.text,
                    ),
                    cursorBrush = SolidColor(scheme.accent),
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = scheme.textDim,
                                fontSize = 15.sp,
                                fontFamily = MetroFonts.text,
                            )
                        }
                        inner()
                    },
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .metroClickable(targetScale = 0.85f) { onQuery("") }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MetroIcon(
                        icon = MetroIcons.Close,
                        color = scheme.textDim,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

/** Заголовок секции: большая буква + accent-линия 3px (как LauncherPanel.qml). */
@Composable
private fun LetterHeader(
    letter: String,
    onClick: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .metroClickable(targetScale = 0.94f, onClick = onClick)
            .padding(top = 12.dp, bottom = 6.dp),
    ) {
        Text(
            text = letter,
            color = scheme.text,
            fontSize = 30.sp,
            fontFamily = MetroFonts.headline,
            fontWeight = FontWeight.Light,
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(30.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(scheme.accent),
        )
    }
}

/** Строка приложения: фрост-квадрат 46 + имя 15px. */
@Composable
private fun AppRow(
    app: AppInfo,
    onClick: (androidx.compose.ui.geometry.Rect?) -> Unit,
    onLongClick: ((androidx.compose.ui.geometry.Rect?) -> Unit)? = null,
) {
    val scheme = LocalMetroScheme.current
    val context = LocalContext.current
    val iconRevision by AppIconLoader.revisions.collectAsState()
    val bitmap by produceState(
        initialValue = AppIconLoader.cachedAppIcon(app.packageName, app),
        app.packageName,
        app.icon,
        iconRevision,
    ) {
        value = AppIconLoader.loadAppIcon(context, app.packageName, app)
    }
    var rowBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onGloballyPositioned { rowBounds = it.boundsInWindow() }
            .metroClickable(
                targetScale = 0.96f,
                onLongClick = onLongClick?.let { { it(rowBounds) } },
                onClick = { onClick(rowBounds) },
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrostedGlassBox(
            modifier = Modifier.size(46.dp),
            shape = 10.dp,
            tint = scheme.glass,
        ) {
            bitmap?.let { icon ->
                ComposeImage(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(38.dp).align(Alignment.Center),
                )
            } ?: Box(
                modifier = Modifier.size(38.dp).align(Alignment.Center),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(app.label, color = scheme.text, fontSize = 15.sp, fontFamily = MetroFonts.text)
    }
}

/**
 * Раскрывающийся блок скрытых приложений в конце списка меню приложений.
 */
@Composable
private fun HiddenAppsSection(
    hiddenApps: List<AppInfo>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onAppClick: (AppInfo, androidx.compose.ui.geometry.Rect?) -> Unit,
    onAppLongClick: (AppInfo, androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val scheme = LocalMetroScheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .metroClickable(targetScale = 0.96f, onClick = onToggleExpand)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.glass),
                contentAlignment = Alignment.Center,
            ) {
                MetroIcon(
                    icon = MetroIcons.EyeSlash,
                    color = scheme.textDim,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Скрытые приложения" + if (hiddenApps.isNotEmpty()) " (${hiddenApps.size})" else "",
                color = scheme.textDim,
                fontSize = 13.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            MetroIcon(
                icon = if (expanded) MetroIcons.ChevronUp else MetroIcons.ChevronDown,
                color = scheme.textDim,
                fontSize = 14.sp,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180, easing = MetroAnimations.OpenEasing)),
            exit = fadeOut(tween(150, easing = MetroAnimations.CloseEasing)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 4.dp),
            ) {
                if (hiddenApps.isEmpty()) {
                    Text(
                        text = "Нет скрытых приложений. Зажмите любое приложение в списке и выберите «Скрыть».",
                        color = scheme.textDim.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontFamily = MetroFonts.text,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                    )
                } else {
                    hiddenApps.forEach { app ->
                        AppRow(
                            app = app,
                            onClick = { bounds -> onAppClick(app, bounds) },
                            onLongClick = { bounds -> onAppLongClick(app, bounds) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Jump Grid на весь экран: матовый фрост + акриловые плитки букв алфавита.
 * Буквы крупно, с тактильным откликом при нажатии.
 */
@Composable
private fun JumpGrid(
    available: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val letters = JumpAlphabets.all
            letters.chunked(6).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { letter ->
                        val has = available.contains(letter)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                                .background(if (has) scheme.accent.copy(alpha = 0.22f) else scheme.glass)
                                .border(
                                    width = 1.dp,
                                    color = if (has) scheme.accent.copy(alpha = 0.55f) else scheme.stroke,
                                    shape = RoundedCornerShape(MetroDimens.radiusSmall),
                                )
                                .metroClickable(
                                    enabled = has,
                                    targetScale = 0.88f,
                                    onClick = { onPick(letter) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                letter,
                                color = if (has) scheme.text
                                else scheme.textDim.copy(alpha = 0.25f),
                                fontSize = 22.sp,
                                fontFamily = MetroFonts.headline,
                                fontWeight = if (has) FontWeight.Normal else FontWeight.Light,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
