package dev.niltsiar.tvhome.feature.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.OverlaySurface
import dev.niltsiar.tvhome.core.designsystem.SCROLL_CUE_ALPHA
import dev.niltsiar.tvhome.core.designsystem.SCROLL_CUE_PILL_ALPHA
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.drawScrollDownCue

private val PickerMaxWidth = 560.dp
private val PickerMargin = 32.dp
private val PickerEdge = 20.dp
private val PickerSide = 28.dp
private val PickerGap = 8.dp

/** Rows kept above the selected entry when the list opens, so that the entries before it show. */
private const val LEAD_ROWS = 2

/**
 * A picker over the page, titled [title], listing [entries]. Focus starts on the entry at [selected] and cannot leave the
 * panel; OK on an entry runs [onPick] with its index, and Back runs [onClose].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BoxScope.PickerPanel(
    @StringRes title: Int,
    entries: List<OptionLabel>,
    selected: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val reveal = rememberReveal()
    val stop = remember { FocusStop(FocusRequester()) }
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
                .revealed(reveal)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
    ) {
        val ink = TvTheme.colors.onSurface
        val cueInk = ink.copy(alpha = SCROLL_CUE_ALPHA)
        val cuePill = ink.copy(alpha = SCROLL_CUE_PILL_ALPHA)
        Column {
            PanelTitle(stringResource(title), "settings:picker:title")
            // The bottom margin is outside the list, so the rows never scroll under the cue drawn in it.
            LazyColumn(
                modifier =
                    Modifier.padding(bottom = PickerSide).drawWithContent {
                        drawContent()
                        if (list.canScrollForward) drawScrollDownCue(size.height + PickerSide.toPx() / 2f, cueInk, cuePill)
                    },
                state = list,
                contentPadding = PaddingValues(horizontal = PickerSide),
                verticalArrangement = Arrangement.spacedBy(PickerGap),
            ) {
                itemsIndexed(entries) { index, label ->
                    ActionRow(
                        label = label.text(),
                        selected = index == selected,
                        onClick = { onPick(index) },
                        tag = "picker:$index",
                        stop = if (index == selected) stop else null,
                    )
                }
            }
        }
    }
}
