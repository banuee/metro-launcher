package dev.metro.launcher.ui.tiles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import dev.metro.launcher.ui.theme.TileFrame
import dev.metro.launcher.ui.theme.metroClickable
import kotlinx.coroutines.launch

/**
 * Заметки 2x2 как в шелле: капс-заголовок + счетчик, инлайн-поле
 * «Новая задача…» + «+», чеклист (тап — toggle, лонг — удалить).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesTile(
    repo: NotesRepository,
    modifier: Modifier = Modifier,
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
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.Start,
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
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = scheme.text,
                            fontSize = 12.sp,
                            fontFamily = MetroFonts.text,
                        ),
                        cursorBrush = SolidColor(scheme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
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
                Text(
                    text = "+",
                    color = scheme.textDim,
                    fontSize = 18.sp,
                    fontFamily = MetroFonts.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .metroClickable(targetScale = 0.85f) { submit() }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onToggle = { scope.launch { repo.toggle(note.id) } },
                        onDelete = { scope.launch { repo.delete(note.id) } },
                    )
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
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
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
