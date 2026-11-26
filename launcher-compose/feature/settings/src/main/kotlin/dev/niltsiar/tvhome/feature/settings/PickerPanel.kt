package dev.niltsiar.tvhome.feature.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.OverlaySurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.drawScrollDownCue
import dev.niltsiar.tvhome.core.designsystem.growOrigin
import dev.niltsiar.tvhome.core.designsystem.tokenBackground
import dev.niltsiar.tvhome.core.designsystem.tokenBorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PickerMaxWidth = 560.dp
private val PickerMargin = 32.dp
private val PickerEdge = 20.dp
private val PickerSide = 28.dp
private val PickerGap = 8.dp

/** Rows kept above the selected entry when the list opens, so that the entries before it show. */
private const val LEAD_ROWS = 2

private val ArtWidth = 96.dp
private val ArtHeight = 54.dp
private val ArtMargin = 6.dp
private val SwatchOutlineWidth = 1.dp

/**
 * One entry of a picker: its [label], a [detail] line under it or null, and the key of its [art], or null for none. An entry
 * with no art shows its [swatch] colour in the art slot, outlined so a dark one still reads as a slot.
 */
@Immutable
internal data class PickerEntry<out K : Any>(
    val label: OptionLabel,
    val detail: String? = null,
    val art: K? = null,
    val swatch: Color? = null,
)

/**
 * The art the rows of an open picker asked for. Each key loads once, in the picker's [scope], when the first row with it
 * composes, so a row scrolled away before its art arrives does not lose the load. It is all dropped with the picker.
 */
@Stable
private class ArtCache<K : Any>(
    private val scope: CoroutineScope,
    private val load: suspend (K) -> ImageBitmap?,
) {
    val images = mutableStateMapOf<K, ImageBitmap>()
    private val asked = HashSet<K>()

    fun ask(key: K) {
        if (asked.add(key)) scope.launch { load(key)?.let { images[key] = it } }
    }
}

/**
 * A picker over the page, titled [title], listing [entries], each with a 96 x 54 dp slot on its start side for its [art], which
 * loads at that size while the row is shown and draws when it arrives, or its swatch. Focus starts on the entry at [selected]
 * and cannot leave the panel; OK on an entry runs [onPick] with its index, and Back runs [onClose]. The entry at [selected] has
 * the check mark, or with [ticked] every entry at an index in it. In [Look.Expressive] the panel grows out of the row that
 * opened it, at [anchor] in the root's coordinates, as a menu grows out of its anchor.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun <K : Any> BoxScope.PickerPanel(
    @StringRes title: Int,
    entries: List<PickerEntry<K>>,
    selected: Int,
    art: suspend (K, Int, Int) -> ImageBitmap?,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    ticked: Set<Int>? = null,
    anchor: () -> Rect? = { null },
) {
    BackHandler(onBack = onClose)
    val motion = TvTheme.motion
    val expressive = motion.look == Look.Expressive
    val reveal = rememberReveal(if (expressive) motion.menuEnterSpec() else null)
    val origin = remember { mutableStateOf(TransformOrigin.Center) }
    val stop = remember { FocusStop(FocusRequester()) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cache =
        remember(art, density) {
            val width = with(density) { ArtWidth.roundToPx() }
            val height = with(density) { ArtHeight.roundToPx() }
            ArtCache<K>(scope) { art(it, width, height) }
        }
    val list = rememberLazyListState((selected - LEAD_ROWS).coerceAtLeast(0))
    LaunchedEffect(Unit) {
        withFrameNanos { }
        stop.entry.requestFocus()
    }
    OverlaySurface(
        modifier =
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = PickerMargin, vertical = PickerEdge)
                .widthIn(max = PickerMaxWidth)
                .fillMaxWidth()
                .testTag("settings:picker")
                .onPlaced { panel ->
                    val from = anchor() ?: return@onPlaced
                    val at = panel.positionInRoot()
                    origin.value = growOrigin(from, IntOffset(at.x.roundToInt(), at.y.roundToInt()), panel.size.width, panel.size.height)
                }.revealed(reveal, origin = if (expressive) ({ origin.value }) else null, grows = motion.animated)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
    ) {
        val colours = TvTheme.lookColors
        Column {
            PanelTitle(stringResource(title), "settings:picker:title")
            // The bottom margin is outside the list, so the rows never scroll under the cue drawn in it.
            LazyColumn(
                modifier =
                    Modifier.padding(bottom = PickerSide).drawWithContent {
                        drawContent()
                        if (list.canScrollForward) {
                            drawScrollDownCue(size.height + PickerSide.toPx() / 2f, colours.panelCue(), colours.panelCuePill())
                        }
                    },
                state = list,
                contentPadding = PaddingValues(horizontal = PickerSide),
                verticalArrangement = Arrangement.spacedBy(PickerGap),
            ) {
                itemsIndexed(entries) { index, entry ->
                    ActionRow(
                        label = entry.label.text(),
                        selected = ticked?.contains(index) ?: (index == selected),
                        onClick = { onPick(index) },
                        tag = "picker:$index",
                        stop = if (index == selected) stop else null,
                        pillHeight = ArtHeight + ArtMargin * 2,
                        detail = entry.detail,
                        leading = { ArtSlot(entry.art, entry.swatch, cache, "picker:$index:art") },
                    )
                }
            }
        }
    }
}

/** The slot of an entry's art: a faint frame until the art of [key] arrives, then the art; without a [key], the [swatch]. */
@Composable
private fun <K : Any> ArtSlot(
    key: K?,
    swatch: Color?,
    cache: ArtCache<K>,
    tag: String,
) {
    val colours = TvTheme.lookColors
    val shape = TvTheme.shapes.pickerArt
    Box(Modifier.size(ArtWidth, ArtHeight).testTag(tag)) {
        if (key == null) {
            if (swatch != null) {
                Box(Modifier.matchParentSize().background(swatch, shape).tokenBorder(SwatchOutlineWidth, colours.swatchOutline, shape))
            }
        } else {
            LaunchedEffect(cache, key) { cache.ask(key) }
            val image = cache.images[key]
            if (image == null) {
                Box(Modifier.matchParentSize().tokenBackground(colours.pickerArtSlot, shape))
            } else {
                Image(
                    image,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().clip(shape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
