package dev.metro.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

private val Context.notesStore by preferencesDataStore(name = "metro_notes")

data class Note(
    val id: String,
    val text: String,
    val done: Boolean,
)

/**
 * Заметки, совместимые по формату с шеллом (notes.json: id/text/done).
 * Хранятся в DataStore одной JSON-строкой.
 */
class NotesRepository(private val context: Context) {
    private val key = stringPreferencesKey("notes_json")

    val notes: Flow<List<Note>> = context.notesStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs ->
        parse(prefs[key]) ?: defaultNotes()
    }

    suspend fun toggle(id: String) = update { list ->
        list.map { if (it.id == id) it.copy(done = !it.done) else it }
    }

    suspend fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        update { list ->
            list + Note(id = UUID.randomUUID().toString(), text = clean, done = false)
        }
    }

    suspend fun delete(id: String) = update { list -> list.filter { it.id != id } }

    private suspend fun update(fn: (List<Note>) -> List<Note>) {
        context.notesStore.edit { prefs ->
            val cur = parse(prefs[key]) ?: defaultNotes()
            prefs[key] = serialize(fn(cur))
        }
    }

    private fun parse(json: String?): List<Note>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Note(
                    id = o.optString("id", i.toString()).ifBlank { i.toString() },
                    text = o.optString("text", ""),
                    done = o.optBoolean("done", false),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun serialize(list: List<Note>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
        return arr.toString()
    }

    private fun defaultNotes(): List<Note> = listOf(
        Note(id = "default-1", text = "Настроить Metro Launcher", done = true),
        Note(id = "default-2", text = "Проверить Jump Grid", done = false),
    )
}
