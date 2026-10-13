package de.wissenshappen.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wissenshappen.app.data.FeedRepository
import de.wissenshappen.app.data.ProgressRepository
import de.wissenshappen.app.data.SavedRepository
import de.wissenshappen.app.data.SettingsRepository
import kotlinx.coroutines.launch

enum class Screen { FEED, TOPICS, SAVED }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsRepository = remember(context) { SettingsRepository(context) }
    val savedRepository = remember(context) { SavedRepository(context) }
    val progressRepository = remember(context) { ProgressRepository(context) }
    val feedRepository = remember { FeedRepository() }

    val topics by settingsRepository.topics.collectAsStateWithLifecycle(SettingsRepository.DEFAULT_TOPICS)
    val dailyGoal by settingsRepository.dailyGoal.collectAsStateWithLifecycle(SettingsRepository.DEFAULT_GOAL)
    val saved by savedRepository.saved.collectAsStateWithLifecycle(emptyList())
    val seenToday by progressRepository.seenToday.collectAsStateWithLifecycle(0)

    val cards by feedRepository.cards.collectAsStateWithLifecycle()
    val loading by feedRepository.loading.collectAsStateWithLifecycle()
    val error by feedRepository.error.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.FEED) }

    // Zurueckwischen auf eine schon gesehene Karte darf das Tagesziel nicht hochtreiben.
    val countedIds = remember { mutableSetOf<String>() }

    LaunchedEffect(topics) {
        if (topics.isNotEmpty()) feedRepository.start(topics)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (screen) {
            Screen.FEED -> FeedScreen(
                cards = cards,
                loading = loading,
                error = error,
                savedIds = saved.mapTo(HashSet()) { it.id },
                seenToday = seenToday,
                dailyGoal = dailyGoal,
                onPageReached = { index -> scope.launch { feedRepository.onPageReached(index, topics) } },
                onCardSeen = { card ->
                    if (countedIds.add(card.id)) {
                        scope.launch { progressRepository.countCard() }
                    }
                },
                onToggleSave = { card -> scope.launch { savedRepository.toggle(card) } },
                onRetry = { scope.launch { feedRepository.refresh(topics) } },
                onOpenTopics = { screen = Screen.TOPICS },
                onOpenSaved = { screen = Screen.SAVED },
            )

            Screen.TOPICS -> TopicsScreen(
                selected = topics,
                dailyGoal = dailyGoal,
                onToggleTopic = { topic ->
                    val next = if (topic in topics) topics - topic else topics + topic
                    scope.launch { settingsRepository.setTopics(next) }
                },
                onAddTopic = { topic ->
                    scope.launch { settingsRepository.setTopics(topics + topic) }
                },
                onGoalChange = { goal -> scope.launch { settingsRepository.setDailyGoal(goal) } },
                onBack = {
                    scope.launch { feedRepository.refresh(topics) }
                    screen = Screen.FEED
                },
            )

            Screen.SAVED -> SavedScreen(
                cards = saved,
                onRemove = { id -> scope.launch { savedRepository.remove(id) } },
                onBack = { screen = Screen.FEED },
            )
        }
    }
}
