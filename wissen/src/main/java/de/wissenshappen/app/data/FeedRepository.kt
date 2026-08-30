package de.wissenshappen.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hält die Karten des Feeds und lädt rechtzeitig nach.
 *
 * Zwei Dinge, die den Unterschied zwischen "fühlt sich an wie Reels" und "ruckelt" ausmachen:
 * nachladen, **bevor** das Ende erreicht ist ([PREFETCH_THRESHOLD]), und keine Karte zweimal
 * zeigen — Wikipedia liefert bei überlappenden Suchoffsets zwangsläufig Dubletten.
 */
class FeedRepository(private val source: WikipediaSource = WikipediaSource()) {

    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val seenIds = HashSet<String>()
    private val mutex = Mutex()
    private var topicCursor = 0
    private var todayLoaded = false

    /** Erster Aufbau: Tageskarten zuerst, dann Themenkarten. */
    suspend fun start(topics: List<String>) {
        if (_cards.value.isNotEmpty()) return
        loadMore(topics)
    }

    /** Ruft nach, wenn der Nutzer sich dem Ende nähert. */
    suspend fun onPageReached(index: Int, topics: List<String>) {
        if (index >= _cards.value.size - PREFETCH_THRESHOLD) loadMore(topics)
    }

    suspend fun refresh(topics: List<String>) {
        mutex.withLock {
            seenIds.clear()
            topicCursor = 0
            todayLoaded = false
            _cards.value = emptyList()
        }
        loadMore(topics)
    }

    private suspend fun loadMore(topics: List<String>) {
        if (!mutex.tryLock()) return
        try {
            _loading.value = true
            val fresh = mutableListOf<Card>()

            if (!todayLoaded) {
                todayLoaded = true
                fresh += runCatching { source.todayCards() }.getOrElse { emptyList() }
            }

            if (topics.isNotEmpty()) {
                // Reihum durch die Themen, damit der Feed durchmischt bleibt statt
                // erst zwanzig Karten Astronomie und dann zwanzig Geschichte zu zeigen.
                repeat(TOPICS_PER_LOAD) {
                    val topic = topics[topicCursor % topics.size]
                    topicCursor++
                    fresh += runCatching { source.topicCards(topic) }
                        .getOrElse { failure ->
                            _error.value = failure.message
                            emptyList()
                        }
                }
            }

            val additions = fresh.filter { seenIds.add(it.id) }
            if (additions.isNotEmpty()) {
                _error.value = null
                _cards.value = _cards.value + additions.shuffled()
            }
        } finally {
            _loading.value = false
            mutex.unlock()
        }
    }

    private companion object {
        const val PREFETCH_THRESHOLD = 4
        const val TOPICS_PER_LOAD = 2
    }
}
