package de.wissenshappen.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

internal val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "wissenshappen")

/** Themen und Tagesziel. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.appDataStore

    val topics: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[TOPICS]?.toList()?.sorted() ?: DEFAULT_TOPICS
    }

    val dailyGoal: Flow<Int> = dataStore.data.map { prefs -> prefs[DAILY_GOAL] ?: DEFAULT_GOAL }

    suspend fun setTopics(topics: Collection<String>) {
        dataStore.edit { prefs ->
            prefs[TOPICS] = topics.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    suspend fun setDailyGoal(goal: Int) {
        dataStore.edit { it[DAILY_GOAL] = goal.coerceIn(3, 50) }
    }

    companion object {
        /**
         * Startthemen. Bewusst breit und alltagstauglich — wer die App zum ersten Mal öffnet,
         * soll etwas sehen, nicht erst konfigurieren müssen.
         */
        val DEFAULT_TOPICS = listOf(
            "Astronomie",
            "Geschichte",
            "Psychologie",
            "Technik",
            "Biologie",
        )

        /** Weitere Vorschläge für den Themen-Screen. */
        val SUGGESTED_TOPICS = listOf(
            "Astronomie", "Geschichte", "Psychologie", "Technik", "Biologie",
            "Medizin", "Physik", "Mathematik", "Philosophie", "Wirtschaft",
            "Kunstgeschichte", "Musik", "Architektur", "Chemie", "Geografie",
            "Sprachwissenschaft", "Informatik", "Recht", "Ernährung", "Klima",
        )

        const val DEFAULT_GOAL = 10

        private val TOPICS = stringSetPreferencesKey("topics")
        private val DAILY_GOAL = intPreferencesKey("daily_goal")
    }
}

/** Wie viele Karten heute gesehen wurden. */
class ProgressRepository(context: Context) {

    private val dataStore = context.applicationContext.appDataStore

    val seenToday: Flow<Int> = dataStore.data.map { prefs ->
        if ((prefs[DAY] ?: 0L) != LocalDate.now().toEpochDay()) 0 else prefs[SEEN] ?: 0
    }

    suspend fun countCard() {
        dataStore.edit { prefs ->
            val today = LocalDate.now().toEpochDay()
            if ((prefs[DAY] ?: 0L) != today) {
                prefs[DAY] = today
                prefs[SEEN] = 0
            }
            prefs[SEEN] = (prefs[SEEN] ?: 0) + 1
        }
    }

    private companion object {
        val DAY = longPreferencesKey("progress_day")
        val SEEN = intPreferencesKey("progress_seen")
    }
}

/**
 * Gemerkte Karten.
 *
 * Als JSON-String in DataStore statt in einer Datenbank: Für ein paar hundert Karten ist das
 * völlig ausreichend und spart Room samt Annotationsprozessor. Wenn die Liste je in die
 * Tausende geht, ist das die Stelle, die auf Room umzustellen ist.
 */
class SavedRepository(context: Context) {

    private val dataStore = context.applicationContext.appDataStore

    val saved: Flow<List<Card>> = dataStore.data.map { prefs -> decode(prefs[SAVED]) }

    suspend fun toggle(card: Card) {
        dataStore.edit { prefs ->
            val current = decode(prefs[SAVED])
            val without = current.filterNot { it.id == card.id }
            val next = if (without.size == current.size) listOf(card) + current else without
            prefs[SAVED] = encode(next.take(MAX_SAVED))
        }
    }

    suspend fun remove(cardId: String) {
        dataStore.edit { prefs ->
            prefs[SAVED] = encode(decode(prefs[SAVED]).filterNot { it.id == cardId })
        }
    }

    private fun encode(cards: List<Card>): String {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(
                JSONObject()
                    .put("id", card.id)
                    .put("title", card.title)
                    .put("text", card.text)
                    .put("imageUrl", card.imageUrl ?: JSONObject.NULL)
                    .put("sourceUrl", card.sourceUrl)
                    .put("kind", card.kind.name)
                    .put("topic", card.topic ?: JSONObject.NULL),
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<Card> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            Card(
                id = obj.optString("id"),
                title = obj.optString("title"),
                text = obj.optString("text"),
                imageUrl = obj.optString("imageUrl").ifBlank { null }.takeIf { it != "null" },
                sourceUrl = obj.optString("sourceUrl"),
                kind = runCatching { CardKind.valueOf(obj.optString("kind")) }
                    .getOrDefault(CardKind.TOPIC),
                topic = obj.optString("topic").ifBlank { null }.takeIf { it != "null" },
            )
        }
    }

    private companion object {
        const val MAX_SAVED = 500
        val SAVED = stringPreferencesKey("saved_cards")
    }
}
