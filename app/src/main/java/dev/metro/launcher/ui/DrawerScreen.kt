package dev.metro.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import dev.metro.launcher.data.AppIconLoader
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.data.DrawerItem
import dev.metro.launcher.data.JumpAlphabets
import dev.metro.launcher.data.sectionApps
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroFonts
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
    onAppClick: (AppInfo) -> Unit,
    onPickWallpaper: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val sectioned = remember(filtered) { sectionApps(filtered) }

    val drawerBlur by animateDpAsState(
        targetValue = if (jumpOpen) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "drawer-blur",
    )

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
                                onClick = { onAppClick(item.app) },
                            )
                        }
                    }
                    item {
                        Text(
                            text = "Сменить обои лаунчера",
                            color = scheme.textDim,
                            fontSize = 13.sp,
                            fontFamily = MetroFonts.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .metroClickable(targetScale = 0.96f, onClick = onPickWallpaper)
                                .padding(vertical = 16.dp),
                        )
                    }
                }
                // Sticky: список прокручивается под строкой поиска.
                MetroSearchBar(
                    query = query,
                    onQuery = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .align(Alignment.TopCenter),
                )
            }
        }

        AnimatedVisibility(
            visible = jumpOpen,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
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
            Loupe(color = if (focused) scheme.accent else scheme.textDim)
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
                Text(
                    text = "×",
                    color = scheme.textDim,
                    fontSize = 22.sp,
                    fontFamily = MetroFonts.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .metroClickable(targetScale = 0.85f) { onQuery("") }
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** Лупа контуром (иконочного шрифта нет — рисуем канвой). */
@Composable
private fun Loupe(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val r = size.minDimension * 0.32f
        val c = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color, r, c, style = Stroke(width = size.minDimension * 0.11f))
        val edge = c + Offset(r * 0.707f, r * 0.707f)
        drawLine(
            color, edge,
            Offset(size.width * 0.88f, size.height * 0.88f),
            strokeWidth = size.minDimension * 0.13f,
        )
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
    onClick: () -> Unit,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .metroClickable(targetScale = 0.96f, onClick = onClick)
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
 * Jump Grid на весь экран: матовый фрост + затемнение поверх размытого списка приложений.
 * Буквы крупно, с тактильным откликом при нажатии.
 */
@Composable
private fun JumpGrid(
    available: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    FrostedGlassBox(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        shape = 0.dp,
        tint = Color.Black.copy(alpha = 0.65f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
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
                                .size(52.dp)
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
                                else scheme.textDim.copy(alpha = 0.35f),
                                fontSize = 28.sp,
                                fontFamily = MetroFonts.headline,
                                fontWeight = FontWeight.Light,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
