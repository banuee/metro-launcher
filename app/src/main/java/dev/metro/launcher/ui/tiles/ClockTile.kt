package dev.metro.launcher.ui.tiles

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.TileFrame
import dev.metro.launcher.ui.theme.metroClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val DaysRu = arrayOf(
    "воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота",
)
private val MonthsRu = arrayOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)
private val MonthsNominativeRu = arrayOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)
private val MonthsShortRu = arrayOf(
    "янв", "фев", "мар", "апр", "мая", "июн",
    "июл", "авг", "сен", "окт", "ноя", "дек",
)

/** Порт ruDate() из TopPanel.qml: "вторник, 22 сентября". */
fun ruDate(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    return "${DaysRu[cal.get(Calendar.DAY_OF_WEEK) - 1]}, " +
        "${cal.get(Calendar.DAY_OF_MONTH)} ${MonthsRu[cal.get(Calendar.MONTH)]}"
}

fun ruShortDate(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    return "$day ${MonthsShortRu[cal.get(Calendar.MONTH)]}"
}

fun ruWeekday(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    return DaysRu[cal.get(Calendar.DAY_OF_WEEK) - 1]
}

fun ruMonthYear(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    return "${MonthsNominativeRu[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
}

/** Данные календаря на текущий месяц для сетки 7 колонок. */
private data class MonthGridData(
    val title: String,
    val shift: Int,
    val totalDays: Int,
    val currentDay: Int,
)

private fun getMonthGridData(date: Date): MonthGridData {
    val cal = Calendar.getInstance().apply { time = date }
    val currentDay = cal.get(Calendar.DAY_OF_MONTH)
    val currentMonth = cal.get(Calendar.MONTH)
    val currentYear = cal.get(Calendar.YEAR)

    val firstCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, currentYear)
        set(Calendar.MONTH, currentMonth)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val shift = (firstCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    return MonthGridData(
        title = ruMonthYear(date),
        shift = shift,
        totalDays = totalDays,
        currentDay = currentDay,
    )
}

/**
 * Плитка часов / календаря:
 * Адаптивна под все размеры (1x1, 2x1, 2x2, 4x1, 4x2).
 * - В 1x1, 2x1, 2x2: свайп переворачивает (3D flip) между часами и календарем.
 * - В 3x2 / 4x2: одновременный показ часов и полного календаря рядом.
 */
@Composable
fun ClockTile(
    onClickClock: () -> Unit,
    onClickCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    colSpan: Int = 2,
    rowSpan: Int = 2,
    width: Dp = Dp.Unspecified,
    height: Dp = MetroDimens.tileH(2),
    onLongPress: (() -> Unit)? = null,
) {
    val scheme = LocalMetroScheme.current
    var showCal by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (showCal) 180f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "clock-flip",
    )

    val nowMs by produceState(System.currentTimeMillis()) {
        while (isActive) {
            val currentTime = System.currentTimeMillis()
            value = currentTime
            delay(1_000L - currentTime % 1_000L)
        }
    }
    val now = remember(nowMs) { Date(nowMs) }
    val timeFormatter = remember(Locale.getDefault()) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    val time = timeFormatter.format(now)
    val date = ruDate(now)
    val minuteFraction = (nowMs % 60000) / 60000f

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // Для широких больших плиток (3x2, 4x2+) показываем Часы + Календарь одновременно
    val isDualLayout = colSpan >= 3 && rowSpan >= 2

    TileFrame(
        onClick = {
            if (isDualLayout) onClickClock()
            else if (showCal) onClickCalendar()
            else onClickClock()
        },
        modifier = modifier
            .then(
                if (!isDualLayout) {
                    Modifier
                        .pointerInput(showCal) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragAccumulator = 0f },
                                onDragEnd = {
                                    if (abs(dragAccumulator) > 36.dp.toPx()) {
                                        showCal = !showCal
                                    }
                                    dragAccumulator = 0f
                                },
                                onDragCancel = { dragAccumulator = 0f },
                            ) { _, dragAmount ->
                                dragAccumulator += dragAmount
                            }
                        }
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 16f * density
                        }
                } else Modifier
            ),
        width = width,
        height = height,
        background = scheme.accent.copy(alpha = 0.92f),
        onLongPress = onLongPress,
    ) {
        if (isDualLayout) {
            // Комбинированный режим для больших плиток
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Левая половина: часы
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .metroClickable(targetScale = 0.96f) { onClickClock() },
                    contentAlignment = Alignment.Center,
                ) {
                    ClockFace(
                        time = time,
                        date = date,
                        minuteFraction = minuteFraction,
                        fontSize = 50.sp,
                    )
                }

                VerticalDivider(
                    color = Color.White.copy(alpha = 0.20f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )

                // Правая половина: календарь
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .metroClickable(targetScale = 0.96f) { onClickCalendar() },
                    contentAlignment = Alignment.Center,
                ) {
                    CalendarFace(date = now)
                }
            }
        } else if (rotation <= 90f) {
            // Лицевая сторона: Часы
            when {
                colSpan == 1 && rowSpan == 1 -> {
                    ClockFace1x1(
                        time = time,
                        date = now,
                        minuteFraction = minuteFraction,
                    )
                }
                colSpan >= 2 && rowSpan == 1 -> {
                    ClockFaceWide(
                        time = time,
                        date = now,
                        minuteFraction = minuteFraction,
                    )
                }
                else -> {
                    ClockFace(
                        time = time,
                        date = date,
                        minuteFraction = minuteFraction,
                    )
                }
            }
        } else {
            // Обратная сторона: Календарь
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    colSpan == 1 && rowSpan == 1 -> {
                        CalendarFace1x1(date = now)
                    }
                    colSpan >= 2 && rowSpan == 1 -> {
                        CalendarFaceWide(date = now)
                    }
                    else -> {
                        CalendarFace(date = now)
                    }
                }
            }
        }
    }
}

/** 1x1: Компактные часы */
@Composable
private fun ClockFace1x1(
    time: String,
    date: Date,
    minuteFraction: Float,
) {
    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = MetroFonts.headline,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = ruShortDate(date),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.5.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.Medium,
            )
        }

        // Секундная полоска
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
                .fillMaxWidth(0.8f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = minuteFraction)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
    }
}

/** 1x1: Календарь с крупным числом дня */
@Composable
private fun CalendarFace1x1(date: Date) {
    val cal = Calendar.getInstance().apply { time = date }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val monthName = MonthsShortRu[cal.get(Calendar.MONTH)].uppercase(Locale.getDefault())
    val weekday = ruWeekday(date).uppercase(Locale.getDefault())

    Column(
        modifier = Modifier.fillMaxSize().padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$day",
            color = Color.White,
            fontSize = 32.sp,
            fontFamily = MetroFonts.headline,
            fontWeight = FontWeight.Light,
        )
        Text(
            text = monthName,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 10.sp,
            fontFamily = MetroFonts.text,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Text(
            text = weekday,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontFamily = MetroFonts.text,
        )
    }
}

/** 2x1 / 3x1: Горизонтальные часы */
@Composable
private fun ClockFaceWide(
    time: String,
    date: Date,
    minuteFraction: Float,
) {
    Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 38.sp,
                fontFamily = MetroFonts.headline,
                fontWeight = FontWeight.Light,
            )

            val cal = remember(date) { Calendar.getInstance().apply { setTime(date) } }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = ruWeekday(date),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${cal.get(Calendar.DAY_OF_MONTH)} ${MonthsRu[cal.get(Calendar.MONTH)]}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = minuteFraction)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
    }
}

/** 2x1: Горизонтальный календарь с текущей неделей */
@Composable
private fun CalendarFaceWide(date: Date) {
    val cal = Calendar.getInstance().apply { time = date }
    val currentDay = cal.get(Calendar.DAY_OF_MONTH)
    val weekHeaders = remember { listOf("П", "В", "С", "Ч", "П", "С", "В") }

    // Вычисляем дни текущей недели
    val currentDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 0 = пн
    val calWeek = Calendar.getInstance().apply {
        time = date
        add(Calendar.DAY_OF_MONTH, -currentDayOfWeek)
    }
    val weekDays = (0..6).map {
        val d = calWeek.get(Calendar.DAY_OF_MONTH)
        calWeek.add(Calendar.DAY_OF_MONTH, 1)
        d
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = ruMonthYear(date),
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = MetroFonts.headline,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            weekHeaders.forEachIndexed { idx, h ->
                val dayNum = weekDays.getOrElse(idx) { 0 }
                val isToday = idx == currentDayOfWeek
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = h,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontFamily = MetroFonts.text,
                    )
                    Spacer(Modifier.height(2.dp))
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$dayNum",
                                color = LocalMetroScheme.current.accent,
                                fontSize = 11.sp,
                                fontFamily = MetroFonts.text,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "$dayNum",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = MetroFonts.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockFace(
    time: String,
    date: String,
    minuteFraction: Float,
    fontSize: androidx.compose.ui.unit.TextUnit = 54.sp,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = Color.White,
                fontSize = fontSize,
                fontFamily = MetroFonts.headline,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = date,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontFamily = MetroFonts.text,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // Секундная полоска
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = minuteFraction)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
    }
}

@Composable
private fun CalendarFace(date: Date) {
    val scheme = LocalMetroScheme.current
    val grid = remember(date) { getMonthGridData(date) }
    val weekHeaders = remember { listOf("П", "В", "С", "Ч", "П", "С", "В") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = grid.title,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = MetroFonts.headline,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // Дни недели
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            weekHeaders.forEach { h ->
                Box(
                    modifier = Modifier.size(width = 20.dp, height = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = h,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = MetroFonts.text,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Числа месяца
        val totalCells = grid.shift + grid.totalDays
        val rows = (totalCells + 6) / 7

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (r in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        val dayNum = cellIndex - grid.shift + 1
                        val isDay = dayNum in 1..grid.totalDays
                        val isToday = isDay && dayNum == grid.currentDay

                        Box(
                            modifier = Modifier.size(width = 20.dp, height = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isDay) {
                                if (isToday) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            color = scheme.accent,
                                            fontSize = 10.sp,
                                            fontFamily = MetroFonts.text,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "$dayNum",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = MetroFonts.text,
                                        textAlign = TextAlign.Center,
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
