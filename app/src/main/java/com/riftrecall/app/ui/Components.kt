package com.riftrecall.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.riftrecall.app.data.Card
import com.riftrecall.app.data.Crop
import com.riftrecall.app.logic.AbilityText
import kotlin.math.roundToInt

/** Crops the artwork out of the full card image using fractional bounds. */
class FractionalCrop(private val c: Crop) : Transformation {
    override val cacheKey: String = "crop:${c.l}:${c.t}:${c.r}:${c.b}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val x = (input.width * c.l).roundToInt().coerceIn(0, input.width - 1)
        val y = (input.height * c.t).roundToInt().coerceIn(0, input.height - 1)
        val w = (input.width * (c.r - c.l)).roundToInt().coerceIn(1, input.width - x)
        val h = (input.height * (c.b - c.t)).roundToInt().coerceIn(1, input.height - y)
        return Bitmap.createBitmap(input, x, y, w, h)
    }
}

private fun cardWidth(card: Card) = if (card.landscape) 1039f else 744f
private fun cardHeight(card: Card) = if (card.landscape) 744f else 1039f

/** Only the artwork - no name, cost or text. The content description is deliberately empty. */
@Composable
fun CardArt(card: Card, crop: Crop, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val ratio = ((crop.r - crop.l) * cardWidth(card)) / ((crop.b - crop.t) * cardHeight(card))
    val request = remember(card.imageUrl, crop) {
        ImageRequest.Builder(ctx)
            .data(card.imageUrl)
            .size(Size.ORIGINAL)
            .transformations(FractionalCrop(crop))
            .crossfade(true)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier
            .aspectRatio(ratio.coerceIn(0.2f, 5f))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentScale = ContentScale.Crop,
        loading = { Centered { CircularProgressIndicator(Modifier.size(28.dp)) } },
        error = {
            Centered {
                Text(
                    "Couldn't load the art.\nCheck your connection.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** The whole card, shown after answering or on study screens. */
@Composable
fun CardFull(card: Card, modifier: Modifier = Modifier) {
    val ratio = cardWidth(card) / cardHeight(card)
    SubcomposeAsyncImage(
        model = card.imageUrl,
        contentDescription = card.name,
        modifier = modifier
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentScale = ContentScale.Fit,
        loading = { Centered { CircularProgressIndicator(Modifier.size(28.dp)) } },
        error = { Centered { Text("Couldn't load the card.", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = color) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun MasteryBar(value: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { value.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
fun CenterMessage(text: String, loading: Boolean = false) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator()
        Text(text, Modifier.padding(top = 16.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Every fact about a card as text - used on study screens, feedback and the collection. */
@Composable
fun CardFacts(card: Card) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(card.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        FactRow("Type", listOfNotNull(card.supertype, card.type).joinToString(" "))
        if (card.domains.isNotEmpty()) FactRow("Domain", card.domains.joinToString(", "))
        card.energy?.let { FactRow("Energy", it.toString()) }
        card.might?.let { FactRow("Might", it.toString()) }
        card.power?.let { FactRow("Power", it.toString()) }
        FactRow("Set", listOf(card.setLabel, card.rarity).filter { it.isNotBlank() }.joinToString(" - "))
        if (card.text.isNotBlank()) {
            Text(
                "Ability",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(AbilityText.display(card.text), style = MaterialTheme.typography.bodyLarge)
        }
        card.flavour?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
