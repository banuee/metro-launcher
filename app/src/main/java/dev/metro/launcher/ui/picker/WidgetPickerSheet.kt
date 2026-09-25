package dev.metro.launcher.ui.picker

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import dev.metro.launcher.ui.theme.DialogWindowBlurEffect
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.MetroIcon
import dev.metro.launcher.ui.theme.MetroIcons
import dev.metro.launcher.ui.theme.metroClickable
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.metro.launcher.data.AppIconLoader
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.data.InternalWidgetType

data class AppWithWidgets(
    val packageName: String,
    val appLabel: String,
    val appInfo: AppInfo?,
    val widgets: List<AppWidgetProviderInfo>,
    val widgetLabels: Map<String, String> = emptyMap(),
)

fun calculateWidgetSpans(info: AppWidgetProviderInfo): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetCols = info.targetCellWidth
        val targetRows = info.targetCellHeight
        if (targetCols > 0 && targetRows > 0) {
            return Pair(targetCols.coerceIn(1, 4), targetRows.coerceIn(1, 6))
        }
    }
    val cols = ((info.minWidth + 30) / 70).coerceIn(1, 4)
    val rows = ((info.minHeight + 30) / 70).coerceIn(1, 6)
    return Pair(cols, rows)
}

private fun widgetCountText(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod100 in 11..19 -> "виджетов"
        mod10 == 1 -> "виджет"
        mod10 in 2..4 -> "виджета"
        else -> "виджетов"
    }
    return "$count $word"
}

/**
 * Окно выбора виджета:
 * - Сверху: виджеты Metro лаунчера (Часы, Погода, Заметки, Плеер).
 * - Снизу: список приложений с количеством доступных виджетов, при тапе раскрываются доступные варианты.
 */
@Composable
fun WidgetPickerSheet(
    apps: List<AppInfo>,
    onSelectInternalWidget: (InternalWidgetType) -> Unit,
    onSelectAppWidget: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = LocalMetroScheme.current
    val iconRevision by AppIconLoader.revisions.collectAsState()
    var query by remember { mutableStateOf("") }
    val expandedApps = remember { mutableStateMapOf<String, Boolean>() }

    val allAppWidgets by produceState(
        initialValue = emptyList<AppWithWidgets>(),
        apps,
        context,
        iconRevision,
    ) {
        value = AppIconLoader.loadWidgetApps(context, apps).map { loaded ->
            AppWithWidgets(
                packageName = loaded.packageName,
                appLabel = loaded.appLabel,
                appInfo = loaded.appInfo,
                widgets = loaded.widgets,
                widgetLabels = loaded.widgetLabels,
            )
        }
    }

    val filteredAppsWithWidgets = remember(query, allAppWidgets) {
        if (query.isBlank()) allAppWidgets else allAppWidgets.filter { item ->
            item.appLabel.contains(query, ignoreCase = true) ||
                item.widgets.any { widgetInfo ->
                    val label = item.widgetLabels[AppIconLoader.widgetId(widgetInfo)]
                        ?: item.appLabel
                    label.contains(query, ignoreCase = true)
                }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Аппаратный блюр окна SurfaceFlinger на Android 12+
        DialogWindowBlurEffect(blurRadiusPx = 65, dimAmount = 0.20f)

        FrostedGlassBox(
            modifier = Modifier.fillMaxSize(),
            shape = 0.dp,
            tint = scheme.glassDeep,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "ДОБАВИТЬ ВИДЖЕТ",
                            color = scheme.text,
                            fontSize = 22.sp,
                            fontFamily = MetroFonts.headline,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 28.dp, height = 2.5.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(scheme.accent),
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        MetroIcon(
                            icon = MetroIcons.Close,
                            color = scheme.text,
                            fontSize = 16.sp,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(MetroDimens.radius))
                        .background(scheme.glass)
                        .border(
                            width = 1.dp,
                            color = if (query.isNotEmpty()) scheme.accent else scheme.stroke,
                            shape = RoundedCornerShape(MetroDimens.radius),
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetroIcon(
                        icon = MetroIcons.Search,
                        color = if (query.isNotEmpty()) scheme.accent else scheme.textDim,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(
                            color = scheme.text,
                            fontSize = 15.sp,
                            fontFamily = MetroFonts.text,
                        ),
                        cursorBrush = SolidColor(scheme.accent),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Поиск виджетов…",
                                    color = scheme.textDim,
                                    fontSize = 15.sp,
                                    fontFamily = MetroFonts.text,
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                            MetroIcon(
                                icon = MetroIcons.Close,
                                color = scheme.textDim,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            // СЕКЦИЯ 1: ВИДЖЕТЫ METRO (показываем, если не отфильтровано)
                            if (query.isBlank() || "metro часы погода заметки плеер".contains(query, ignoreCase = true)) {
                                item(key = "section_metro_header") {
                                    Text(
                                        text = "ВИДЖЕТЫ METRO",
                                        color = scheme.accent,
                                        fontSize = 12.sp,
                                        fontFamily = MetroFonts.text,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.5.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                    )
                                }

                                item(key = "metro_widgets_grid") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MetroWidgetCard(
                                            title = "Часы и календарь",
                                            subtitle = "Сетка 2×2 • 3D переворот",
                                            iconLabel = "13:26",
                                            onClick = {
                                                onSelectInternalWidget(InternalWidgetType.CLOCK)
                                                onDismiss()
                                            },
                                        )
                                        MetroWidgetCard(
                                            title = "Погода",
                                            subtitle = "Сетка 2×2 • Прогноз Open-Meteo",
                                            iconLabel = "22°",
                                            onClick = {
                                                onSelectInternalWidget(InternalWidgetType.WEATHER)
                                                onDismiss()
                                            },
                                        )
                                        MetroWidgetCard(
                                            title = "Заметки",
                                            subtitle = "Сетка 2×2 • Чеклист задач",
                                            iconLabel = "LIST",
                                            onClick = {
                                                onSelectInternalWidget(InternalWidgetType.NOTES)
                                                onDismiss()
                                            },
                                        )
                                        MetroWidgetCard(
                                            title = "Плеер",
                                            subtitle = "Сетка 2×2 • Управление музыкой",
                                            iconLabel = "PLAY",
                                            onClick = {
                                                onSelectInternalWidget(InternalWidgetType.PLAYER)
                                                onDismiss()
                                            },
                                        )
                                    }
                                }

                                item(key = "divider_after_metro") {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }

                            // СЕКЦИЯ 2: ПРИЛОЖЕНИЯ С ВИДЖЕТАМИ
                            item(key = "section_apps_header") {
                                Text(
                                    text = "ПРИЛОЖЕНИЯ С ВИДЖЕТАМИ",
                                    color = scheme.accent,
                                    fontSize = 12.sp,
                                    fontFamily = MetroFonts.text,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                )
                            }

                            items(filteredAppsWithWidgets, key = { it.packageName }) { appItem ->
                                val isExpanded = expandedApps[appItem.packageName] == true
                                val appBitmap by produceState(
                                    initialValue = AppIconLoader.cachedAppIcon(
                                        appItem.packageName,
                                        appItem.appInfo,
                                    ),
                                    appItem.packageName,
                                    appItem.appInfo?.icon,
                                    iconRevision,
                                ) {
                                    value = AppIconLoader.loadAppIcon(
                                        context,
                                        appItem.packageName,
                                        appItem.appInfo,
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(MetroDimens.radius))
                                        .background(scheme.glass)
                                        .border(1.dp, scheme.stroke, RoundedCornerShape(MetroDimens.radius))
                                        .clickable {
                                            expandedApps[appItem.packageName] = !isExpanded
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    val appIcon = appBitmap
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon,
                                                contentDescription = appItem.appLabel,
                                                modifier = Modifier.size(40.dp),
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.1f)),
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = appItem.appLabel,
                                                color = scheme.text,
                                                fontSize = 15.sp,
                                                fontFamily = MetroFonts.text,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = widgetCountText(appItem.widgets.size),
                                                color = scheme.textDim,
                                                fontSize = 12.sp,
                                                fontFamily = MetroFonts.text,
                                            )
                                        }
                                        MetroIcon(
                                            icon = if (isExpanded) MetroIcons.ChevronUp else MetroIcons.ChevronDown,
                                            color = scheme.textDim,
                                            fontSize = 14.sp,
                                        )
                                    }

                                    // Раскрытый список виджетов приложения
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            appItem.widgets.forEach { widgetInfo ->
                                                val (cols, rows) = calculateWidgetSpans(widgetInfo)
                                                val widgetLabel = appItem.widgetLabels[
                                                    AppIconLoader.widgetId(widgetInfo)
                                                ] ?: appItem.appLabel
                                                val previewBitmap by produceState(
                                                    initialValue = AppIconLoader.cachedWidgetPreview(widgetInfo),
                                                    widgetInfo,
                                                    iconRevision,
                                                ) {
                                                    value = AppIconLoader.loadWidgetPreview(context, widgetInfo)
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                                                        .background(Color.White.copy(alpha = 0.04f))
                                                        .border(1.dp, scheme.stroke, RoundedCornerShape(MetroDimens.radiusSmall))
                                                        .metroClickable(targetScale = 0.97f) {
                                                            onSelectAppWidget(widgetInfo)
                                                            onDismiss()
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    val preview = previewBitmap
                                                    val appIcon = appBitmap
                                                    if (preview != null) {
                                                        Image(
                                                            bitmap = preview,
                                                            contentDescription = widgetLabel,
                                                            modifier = Modifier.size(36.dp),
                                                        )
                                                    } else if (appIcon != null) {
                                                        Image(
                                                            bitmap = appIcon,
                                                            contentDescription = widgetLabel,
                                                            modifier = Modifier.size(36.dp),
                                                        )
                                                    }
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = widgetLabel,
                                                            color = scheme.text,
                                                            fontSize = 14.sp,
                                                            fontFamily = MetroFonts.text,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            text = "$cols × $rows",
                                                            color = scheme.accent,
                                                            fontSize = 11.sp,
                                                            fontFamily = MetroFonts.text,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    }
                                                    MetroIcon(
                                                        icon = MetroIcons.ChevronRight,
                                                        color = scheme.textDim.copy(alpha = 0.4f),
                                                        fontSize = 14.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
    }
}

@Composable
private fun MetroWidgetCard(
    title: String,
    subtitle: String,
    iconLabel: String,
    onClick: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MetroDimens.radius))
            .background(scheme.glass)
            .border(1.dp, scheme.stroke, RoundedCornerShape(MetroDimens.radius))
            .metroClickable(targetScale = 0.97f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                .background(scheme.accent.copy(alpha = 0.14f))
                .border(1.dp, scheme.accent.copy(alpha = 0.35f), RoundedCornerShape(MetroDimens.radiusSmall)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = iconLabel,
                color = scheme.accent,
                fontSize = 12.sp,
                fontFamily = MetroFonts.headline,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = scheme.text,
                fontSize = 15.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = scheme.textDim,
                fontSize = 12.sp,
                fontFamily = MetroFonts.text,
            )
        }
        MetroIcon(
            icon = MetroIcons.ChevronRight,
            color = scheme.textDim.copy(alpha = 0.4f),
            fontSize = 14.sp,
        )
    }
}
