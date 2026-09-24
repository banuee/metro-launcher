package dev.metro.launcher.ui.tiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metro.launcher.data.City
import dev.metro.launcher.data.HourPoint
import dev.metro.launcher.data.WeatherRepository
import dev.metro.launcher.data.WeatherState
import dev.metro.launcher.data.weatherText
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.TileFrame
import dev.metro.launcher.ui.theme.metroClickable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Состояние погоды с ручным обновлением (одно на плитку и раскрытие). */
class WeatherUiState(
    val state: WeatherState,
    val refresh: () -> Unit,
)

@Composable
fun rememberWeatherUi(repo: WeatherRepository): WeatherUiState {
    val scope = rememberCoroutineScope()
    val city by repo.city.collectAsState(initial = null)
    var state by remember { mutableStateOf<WeatherState>(WeatherState.Loading) }
    fun refresh() {
        scope.launch { state = repo.refresh() }
    }
    LaunchedEffect(city) {
        if (city != null) refresh()
    }
    return remember(state) { WeatherUiState(state, ::refresh) }
}

/**
 * Погода 2x2 как в шелле: акцент-иконка, температура тонко крупно,
 * описание. Тап — раскрытие вниз.
 */
@Composable
fun WeatherTile(
    ui: WeatherUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = MetroDimens.tileH(2),
    onLongPress: (() -> Unit)? = null,
) {
    val scheme = LocalMetroScheme.current
    TileFrame(
        onClick = onClick,
        modifier = modifier,
        width = width,
        height = height,
        onLongPress = onLongPress,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = ui.state) {
                is WeatherState.Loading -> Text("…", color = scheme.textDim, fontSize = 40.sp)
                is WeatherState.Error -> Text(
                    "нет сети",
                    color = scheme.textDim,
                    fontSize = 14.sp,
                    fontFamily = MetroFonts.text,
                )
                is WeatherState.Data -> {
                    WeatherIcon(
                        code = s.now.code,
                        color = scheme.accent,
                        size = 48.dp,
                        fontSize = 42.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${s.now.temp.roundToInt()}°",
                        color = scheme.text,
                        fontSize = 44.sp,
                        fontFamily = MetroFonts.headline,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        text = weatherText(s.now.code),
                        color = scheme.textDim,
                        fontSize = 12.sp,
                        fontFamily = MetroFonts.text,
                    )
                }
            }
        }
    }
}

/**
 * Раскрытие вниз (как WeatherDetail в шелле, в меньшем масштабе):
 * поиск города, текущая + детали, почасовой, на 7 дней.
 */
@Composable
fun WeatherExpandedPanel(
    repo: WeatherRepository,
    ui: WeatherUiState,
) {
    val scheme = LocalMetroScheme.current
    val scope = rememberCoroutineScope()
    val hourFormatter = remember(Locale.getDefault()) {
        SimpleDateFormat("HH", Locale.getDefault())
    }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    FrostedGlassBox(
        modifier = Modifier.fillMaxWidth(),
        tint = scheme.glass,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.padding(14.dp)) {
            // Поиск города.
            Box {
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        searchJob?.cancel()
                        searchJob = scope.launch {
                            delay(400)
                            results = repo.search(query)
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = scheme.text,
                        fontSize = 14.sp,
                        fontFamily = MetroFonts.text,
                    ),
                    cursorBrush = SolidColor(scheme.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "Поиск города…",
                                color = scheme.textDim,
                                fontSize = 14.sp,
                                fontFamily = MetroFonts.text,
                            )
                        }
                        inner()
                    },
                )
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.height(96.dp)) {
                    items(results) { c ->
                        Text(
                            text = "${c.name}, ${c.country}",
                            color = scheme.text,
                            fontSize = 14.sp,
                            fontFamily = MetroFonts.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .metroClickable(targetScale = 0.96f) {
                                    scope.launch { repo.setCity(c) }
                                    query = ""
                                    results = emptyList()
                                    ui.refresh()
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }

            when (val s = ui.state) {
                is WeatherState.Data -> {
                    Spacer(Modifier.height(10.dp))
                    // Текущая.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherIcon(
                            code = s.now.code,
                            color = scheme.accent,
                            size = 56.dp,
                            fontSize = 50.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${s.now.temp.roundToInt()}°",
                                color = scheme.text,
                                fontSize = 46.sp,
                                fontFamily = MetroFonts.headline,
                                fontWeight = FontWeight.Light,
                            )
                            Text(
                                text = weatherText(s.now.code),
                                color = scheme.text,
                                fontSize = 13.sp,
                                fontFamily = MetroFonts.text,
                            )
                            Text(
                                text = s.city.name,
                                color = scheme.textDim,
                                fontSize = 12.sp,
                                fontFamily = MetroFonts.text,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Детали 2x2.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailBox("ОЩУЩАЕТСЯ", "${s.now.feelsLike.roundToInt()}°", Modifier.weight(1f))
                        DetailBox("ВЕТЕР", "${s.now.wind.roundToInt()} м/с", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailBox("ВЛАЖНОСТЬ", "${s.now.humidity}%", Modifier.weight(1f))
                        DetailBox("ДАВЛЕНИЕ", "${s.now.pressure} гПа", Modifier.weight(1f))
                    }
                    // Почасовой.
                    if (s.hourly.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        CapsHeader("ПОЧАСОВОЙ ПРОГНОЗ")
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(s.hourly) { h -> HourCell(h, hourFormatter) }
                        }
                    }
                    // На 5 дней (сегодня + 4): компакт под телефон.
                    if (s.daily.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        CapsHeader("НА 5 ДНЕЙ")
                        Spacer(Modifier.height(4.dp))
                        val days = s.daily.take(5)
                        val wMin = days.minOf { it.tMin }
                        val wMax = days.maxOf { it.tMax }
                        days.forEachIndexed { i, d ->
                            DayRow(
                                name = dayNameRu(d.date, i),
                                highlight = i == 0,
                                code = d.code,
                                tMin = d.tMin,
                                tMax = d.tMax,
                                wMin = wMin,
                                wMax = wMax,
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun CapsHeader(text: String) {
    Text(
        text = text,
        color = LocalMetroScheme.current.textDim,
        fontSize = 10.sp,
        fontFamily = MetroFonts.text,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun DetailBox(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = LocalMetroScheme.current
    FrostedGlassBox(modifier = modifier, shape = 8.dp, tint = scheme.glass, contentAlignment = Alignment.TopStart) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                color = scheme.textDim,
                fontSize = 9.sp,
                fontFamily = MetroFonts.text,
                letterSpacing = 1.sp,
            )
            Text(
                text = value,
                color = scheme.text,
                fontSize = 14.sp,
                fontFamily = MetroFonts.text,
            )
        }
    }
}

@Composable
private fun HourCell(h: HourPoint, formatter: SimpleDateFormat) {
    val scheme = LocalMetroScheme.current
    Column(
        modifier = Modifier.width(48.dp).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatter.format(Date(h.epochSec * 1000)),
            color = scheme.textDim,
            fontSize = 11.sp,
            fontFamily = MetroFonts.text,
        )
        Spacer(Modifier.height(2.dp))
        WeatherIcon(
            code = h.code,
            color = scheme.text,
            size = 20.dp,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${h.temp.roundToInt()}°",
            color = scheme.text,
            fontSize = 12.sp,
            fontFamily = MetroFonts.text,
        )
    }
}

@Composable
private fun DayRow(
    name: String,
    highlight: Boolean,
    code: Int,
    tMin: Double,
    tMax: Double,
    wMin: Double,
    wMax: Double,
) {
    val scheme = LocalMetroScheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = if (highlight) scheme.accent else scheme.text,
            fontSize = 13.sp,
            fontFamily = MetroFonts.text,
            modifier = Modifier.width(92.dp),
        )
        WeatherIcon(
            code = code,
            color = scheme.text,
            size = 20.dp,
            fontSize = 16.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${tMin.roundToInt()}°",
            color = scheme.textDim,
            fontSize = 12.sp,
            fontFamily = MetroFonts.text,
            modifier = Modifier.width(30.dp),
        )
        val span = (wMax - wMin).takeIf { it > 0.5 } ?: 1.0
        val left = ((tMin - wMin) / span).toFloat().coerceIn(0f, 1f)
        val width = ((tMax - tMin) / span).toFloat().coerceIn(0.03f, 1f)
        Row(Modifier.weight(1f).height(3.dp)) {
            Spacer(Modifier.weight(left.coerceAtLeast(0.001f)))
            Box(
                Modifier.weight(width)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(scheme.accent),
            )
            Spacer(Modifier.weight((1f - left - width).coerceAtLeast(0.001f)))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${tMax.roundToInt()}°",
            color = scheme.text,
            fontSize = 12.sp,
            fontFamily = MetroFonts.text,
            modifier = Modifier.width(30.dp),
        )
    }
}

private val WeekdaysRu = arrayOf(
    "воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота",
)

private fun dayNameRu(dateIso: String, index: Int): String {
    if (index == 0) return "сегодня"
    return try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateIso) ?: return ""
        val c = Calendar.getInstance().apply { time = d }
        WeekdaysRu[c.get(Calendar.DAY_OF_WEEK) - 1]
    } catch (_: Exception) {
        ""
    }
}

fun weatherGlyph(code: Int): String {
    val hex = when (code) {
        0 -> 0xF0599 // Clear sun
        1, 2 -> 0xF0595 // Partly cloudy
        3 -> 0xF0C2 // Overcast cloud
        45, 48 -> 0xF0591 // Fog
        51, 53, 55, 56, 57, 61 -> 0xF0592 // Drizzle / light rain
        63, 65, 66, 67, 80, 81, 82 -> 0xF0596 // Heavy rain
        71, 73, 75, 77, 85, 86 -> 0xF2DC // Snow
        95, 96, 99 -> 0xF0E7 // Thunderstorm
        else -> 0xF0C2
    }
    return String(Character.toChars(hex))
}

/** Иконки погоды из NotoSans Nerd Font (как в quickshell / WeatherCodes.js). */
@Composable
fun WeatherIcon(
    code: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    fontSize: TextUnit? = null,
) {
    val fs = fontSize ?: (size.value * 0.85f).sp
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val glyph = weatherGlyph(code)
    val style = TextStyle(
        color = color,
        fontFamily = MetroFonts.icon,
        fontSize = fs,
    )
    // Рисуем на канве и центрируем ЧЕРНИЛА глифа, а не строку: у нерда
    // естественная строка 1.36em, а lineHeight меньше естественной Compose
    // игнорирует (кламп к natural — проверено матрицей на устройстве:
    // голый Text 157px, с lineHeight=1em 158px, без fontPadding 157px).
    // Text'ом это не чинится: он распухает за Box и режется плиткой.
    // Здесь метрики не важны: меряем ink-бокс глифа и ставим его ровно
    // в центр Box'а; резать может только сама плитка по своим краям.
    val boxPx = with(density) { size.toPx() }
    val layout = remember(glyph, style, density) {
        measurer.measure(AnnotatedString(glyph), style)
    }
    Canvas(modifier = modifier.size(size)) {
        val ink = layout.getBoundingBox(0)
        if (!ink.isEmpty) {
            val topLeft = Offset(
                (boxPx - ink.width) / 2 - ink.left,
                (boxPx - ink.height) / 2 - ink.top,
            )
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(topLeft.x, topLeft.y)
                TextPainter.paint(canvas, layout)
                canvas.restore()
            }
        }
    }
}
