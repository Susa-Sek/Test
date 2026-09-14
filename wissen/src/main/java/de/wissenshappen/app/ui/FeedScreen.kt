package de.wissenshappen.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.wissenshappen.app.R
import de.wissenshappen.app.data.Card
import de.wissenshappen.app.data.CardKind

/**
 * Der Feed: Vollbild-Karten, vertikal gewischt.
 *
 * Die Wischgeste ist Absicht und der eigentliche Trick der App. Der Reflex, der einen sonst
 * durch Reels zieht, bleibt derselbe — nur das Futter ändert sich. Eine Liste zum Scrollen
 * hätte diese Wirkung nicht.
 */
@Composable
fun FeedScreen(
    cards: List<Card>,
    loading: Boolean,
    error: String?,
    savedIds: Set<String>,
    seenToday: Int,
    dailyGoal: Int,
    onPageReached: (Int) -> Unit,
    onCardSeen: (Card) -> Unit,
    onToggleSave: (Card) -> Unit,
    onRetry: () -> Unit,
    onOpenTopics: () -> Unit,
    onOpenSaved: () -> Unit,
) {
    if (cards.isEmpty()) {
        EmptyState(loading, error, onRetry, onOpenTopics)
        return
    }

    val pagerState = rememberPagerState(pageCount = { cards.size })

    // Nachladen und Zählen hängen an der Seite, auf der man wirklich zur Ruhe kommt —
    // nicht an jeder Zwischenposition während des Wischens.
    LaunchedEffect(pagerState, cards.size) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onPageReached(page)
            cards.getOrNull(page)?.let(onCardSeen)
        }
    }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            CardPage(
                card = cards[page],
                isSaved = cards[page].id in savedIds,
                onToggleSave = { onToggleSave(cards[page]) },
            )
        }

        TopBar(
            seenToday = seenToday,
            dailyGoal = dailyGoal,
            onOpenTopics = onOpenTopics,
            onOpenSaved = onOpenSaved,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 24.dp)
                    .height(24.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun CardPage(card: Card, isSaved: Boolean, onToggleSave: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (card.imageUrl != null) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f),
            )
            // Verlauf nach unten, damit der Text auf jedem Bild lesbar bleibt — ohne ihn
            // verschwindet weiße Schrift auf einem hellen Foto.
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .padding(top = if (card.imageUrl != null) 260.dp else 96.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            KindLabel(card)

            Text(
                text = card.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = card.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleSave) {
                    Text(stringResource(if (isSaved) R.string.saved else R.string.save))
                }
            }

            card.topic?.let { topic ->
                Text(
                    text = topic,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun KindLabel(card: Card) {
    val label = when (card.kind) {
        CardKind.TODAY -> stringResource(R.string.card_today)
        CardKind.ON_THIS_DAY -> stringResource(R.string.card_on_this_day)
        CardKind.TOPIC -> null
    } ?: return

    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TopBar(
    seenToday: Int,
    dailyGoal: Int,
    onOpenTopics: () -> Unit,
    onOpenSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenTopics) { Text(stringResource(R.string.topics)) }
            Text(
                text = if (seenToday >= dailyGoal) {
                    stringResource(R.string.goal_reached, seenToday)
                } else {
                    stringResource(R.string.goal_progress, seenToday, dailyGoal)
                },
                style = MaterialTheme.typography.labelMedium,
            )
            TextButton(onClick = onOpenSaved) { Text(stringResource(R.string.my_cards)) }
        }
        LinearProgressIndicator(
            progress = { (seenToday.toFloat() / dailyGoal).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun EmptyState(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenTopics: () -> Unit,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                loading -> CircularProgressIndicator(strokeWidth = 2.dp)
                error != null -> {
                    Text(stringResource(R.string.error_generic), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                else -> {
                    Text(stringResource(R.string.empty_feed), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onOpenTopics, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.topics))
                    }
                }
            }
        }
    }
}
