package dev.metro.launcher.ui.tiles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metro.launcher.data.Note
import dev.metro.launcher.data.NotesRepository
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.MetroIcon
import dev.metro.launcher.ui.theme.MetroIcons
import dev.metro.launcher.ui.theme.MetroScheme
import dev.metro.launcher.ui.theme.TileFrame
import dev.metro.launcher.ui.theme.metroClickable
import kotlinx.coroutines.launch

/**
 * Заметки Metro:
 * Поддерживает адаптивные размеры под 1x1, 2x1, 2x2, 4x1, 4x2+.
 * Быстрый чеклист, добавление задач и счетчик выполненных.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesTile(
    repo: NotesRepository,
    modifier: Modifier = Modifier,
    colSpan: Int = 2,
    rowSpan: Int = 2,
    width: Dp = Dp.Unspecified,
    height: Dp = MetroDimens.tileH(2),
    onLongPress: (() -> Unit)? = null,
) {
    val scheme = LocalMetroScheme.current
    val scope = rememberCoroutineScope()
    val notes by repo.notes.collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    fun submit() {
        if (draft.isNotBlank()) {
            scope.launch { repo.add(draft) }
            draft = ""
        }
    }

    val done = notes.count { it.done }

    TileFrame(
        onClick = { focusRequester.requestFocus() },
        modifier = modifier,
        width = width,
        height = height,
        onLongPress = onLongPress,
    ) {
        when {
            // Компактный 1x1
            colSpan == 1 && rowSpan == 1 -> {
                NotesTile1x1(
                    notes = notes,
                    done = done,
                    scheme = scheme,
                )
            }

            // Горизонтальный 2x1 или 3x1
            colSpan in 2..3 && rowSpan == 1 -> {
                NotesTile2x1(
                    notes = notes,
                    done = done,
                    onToggle = { id -> scope.launch { repo.toggle(id) } },
                    onFocusInput = { focusRequester.requestFocus() },
                    scheme = scheme,
                )
            }

            // Широкий 4x1
            colSpan >= 4 && rowSpan == 1 -> {
                NotesTile4x1(
                    notes = notes,
                    done = done,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSubmit = ::submit,
                    onToggle = { id -> scope.launch { repo.toggle(id) } },
                    focusRequester = focusRequester,
                    scheme = scheme,
                )
            }

            // Широкий большой 3x2 / 4x2+
            colSpan >= 3 && rowSpan >= 2 -> {
                NotesTileLarge(
                    notes = notes,
                    done = done,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSubmit = ::submit,
                    onToggle = { id -> scope.launch { repo.toggle(id) } },
                    onDelete = { id -> scope.launch { repo.delete(id) } },
                    focusRequester = focusRequester,
                    scheme = scheme,
                )
            }

            // Стандартный 2x2
            else -> {
                NotesTile2x2(
                    notes = notes,
                    done = done,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSubmit = ::submit,
                    onToggle = { id -> scope.launch { repo.toggle(id) } },
                    onDelete = { id -> scope.launch { repo.delete(id) } },
                    focusRequester = focusRequester,
                    scheme = scheme,
                )
            }
        }
    }
}

/** 1x1: Компактный статус заметок */
@Composable
private fun NotesTile1x1(
    notes: List<Note>,
    done: Int,
    scheme: MetroScheme,
) {
    val pending = notes.filter { !it.done }

    Column(
        modifier = Modifier.fillMaxSize().padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "ЗАМЕТКИ",
            color = scheme.accent,
            fontSize = 9.5.sp,
            fontFamily = MetroFonts.text,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )

        if (notes.isEmpty()) {
            Text(
                text = "Нет задач",
                color = scheme.textDim,
                fontSize = 11.sp,
                fontFamily = MetroFonts.text,
            )
        } else if (pending.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MetroIcon(icon = MetroIcons.Check, color = scheme.accent, fontSize = 20.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Все готовы!",
                    color = scheme.text,
                    fontSize = 10.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${pending.size}",
                    color = scheme.text,
                    fontSize = 24.sp,
                    fontFamily = MetroFonts.headline,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    text = "осталось",
                    color = scheme.textDim,
                    fontSize = 9.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        }

        Text(
            text = "$done/${notes.size}",
            color = scheme.accent,
            fontSize = 9.5.sp,
            fontFamily = MetroFonts.text,
        )
    }
}

/** 2x1: Горизонтальный ряд с возможностью сразу отметить задачу */
@Composable
private fun NotesTile2x1(
    notes: List<Note>,
    done: Int,
    onToggle: (String) -> Unit,
    onFocusInput: () -> Unit,
    scheme: MetroScheme,
) {
    val firstPending = notes.firstOrNull { !it.done }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ЗАМЕТКИ",
                    color = scheme.accent,
                    fontSize = 10.5.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$done/${notes.size}",
                    color = scheme.accent,
                    fontSize = 10.5.sp,
                    fontFamily = MetroFonts.text,
                )
            }

            MetroIcon(
                icon = MetroIcons.Plus,
                color = scheme.accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .metroClickable(targetScale = 0.85f, onClick = onFocusInput)
                    .padding(4.dp),
            )
        }

        if (firstPending != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .metroClickable(targetScale = 0.96f) { onToggle(firstPending.id) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(scheme.glass)
                        .border(1.dp, scheme.stroke, RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = firstPending.text,
                    color = scheme.text,
                    fontSize = 12.5.sp,
                    fontFamily = MetroFonts.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = if (notes.isEmpty()) "Нажмите +, чтобы добавить задачу" else "Все задачи выполнены!",
                color = scheme.textDim,
                fontSize = 12.sp,
                fontFamily = MetroFonts.text,
            )
        }
    }
}

/** 4x1: Полноразмерный однострочный виджет заметок */
@Composable
private fun NotesTile4x1(
    notes: List<Note>,
    done: Int,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggle: (String) -> Unit,
    focusRequester: FocusRequester,
    scheme: MetroScheme,
) {
    val pending = notes.filter { !it.done }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(110.dp)) {
            Text(
                text = "ЗАМЕТКИ",
                color = scheme.accent,
                fontSize = 11.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "$done/${notes.size} готово",
                color = scheme.textDim,
                fontSize = 11.sp,
                fontFamily = MetroFonts.text,
            )
        }

        // Поле ввода
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.glass)
                .border(1.dp, scheme.stroke, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = scheme.text,
                            fontSize = 12.5.sp,
                            fontFamily = MetroFonts.text,
                        ),
                        cursorBrush = SolidColor(scheme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) {
                                Text(
                                    text = "Новая задача…",
                                    color = scheme.textDim,
                                    fontSize = 12.5.sp,
                                    fontFamily = MetroFonts.text,
                                )
                            }
                            inner()
                        },
                    )
                }
                MetroIcon(
                    icon = MetroIcons.Plus,
                    color = scheme.accent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .metroClickable(targetScale = 0.85f) { onSubmit() }
                        .padding(horizontal = 4.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Ближайшая задача
        val nextNote = pending.firstOrNull()
        if (nextNote != null) {
            Row(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .metroClickable(targetScale = 0.96f) { onToggle(nextNote.id) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(scheme.glass)
                        .border(1.dp, scheme.stroke, RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = nextNote.text,
                    color = scheme.text,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 2x2: Стандартный чеклист */
@Composable
private fun NotesTile2x2(
    notes: List<Note>,
    done: Int,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    focusRequester: FocusRequester,
    scheme: MetroScheme,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ЗАМЕТКИ",
                    color = scheme.accent,
                    fontSize = 11.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$done/${notes.size}",
                    color = scheme.accent,
                    fontSize = 11.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.glass)
                .border(1.dp, scheme.stroke, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = scheme.text,
                        fontSize = 12.sp,
                        fontFamily = MetroFonts.text,
                    ),
                    cursorBrush = SolidColor(scheme.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                text = "Новая задача…",
                                color = scheme.textDim,
                                fontSize = 12.sp,
                                fontFamily = MetroFonts.text,
                            )
                        }
                        inner()
                    },
                )
            }
            MetroIcon(
                icon = MetroIcons.Plus,
                color = scheme.accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .metroClickable(targetScale = 0.85f) { onSubmit() }
                    .padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(notes, key = { it.id }) { note ->
                NoteRow(
                    note = note,
                    onToggle = { onToggle(note.id) },
                    onDelete = { onDelete(note.id) },
                )
            }
        }
    }
}

/** 3x2 / 4x2+: Большой информативный экран заметок */
@Composable
private fun NotesTileLarge(
    notes: List<Note>,
    done: Int,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    focusRequester: FocusRequester,
    scheme: MetroScheme,
) {
    val pending = notes.filter { !it.done }
    val completed = notes.filter { it.done }

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ЗАМЕТКИ И ДЕЛА",
                    color = scheme.accent,
                    fontSize = 11.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$done выполнено из ${notes.size}",
                    color = scheme.textDim,
                    fontSize = 11.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Строка добавления
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.glass)
                .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = scheme.text,
                        fontSize = 13.sp,
                        fontFamily = MetroFonts.text,
                    ),
                    cursorBrush = SolidColor(scheme.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                text = "Добавить новую задачу…",
                                color = scheme.textDim,
                                fontSize = 13.sp,
                                fontFamily = MetroFonts.text,
                            )
                        }
                        inner()
                    },
                )
            }
            MetroIcon(
                icon = MetroIcons.Plus,
                color = scheme.accent,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .metroClickable(targetScale = 0.85f) { onSubmit() }
                    .padding(horizontal = 6.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Две колонки: активные задачи слева, завершенные справа
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = "АКТИВНЫЕ (${pending.size})",
                    color = scheme.textDim,
                    fontSize = 10.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(pending, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            onToggle = { onToggle(note.id) },
                            onDelete = { onDelete(note.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = "ВЫПОЛНЕННЫЕ (${completed.size})",
                    color = scheme.textDim,
                    fontSize = 10.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(completed, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            onToggle = { onToggle(note.id) },
                            onDelete = { onDelete(note.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: Note,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onToggle, onLongClick = onDelete)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (note.done) scheme.accent.copy(alpha = 0.85f)
                    else scheme.glass,
                )
                .border(1.dp, scheme.stroke, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (note.done) {
                MetroIcon(
                    icon = MetroIcons.Check,
                    color = Color.White,
                    fontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = note.text,
            color = if (note.done) scheme.textDim else scheme.text,
            fontSize = 12.sp,
            fontFamily = MetroFonts.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (note.done) TextDecoration.LineThrough else null,
        )
    }
}
