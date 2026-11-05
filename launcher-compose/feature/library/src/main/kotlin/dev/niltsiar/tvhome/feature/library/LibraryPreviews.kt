package dev.niltsiar.tvhome.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.OverlayHost
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.LibraryArt

private val PreviewNames =
    listOf("Movies", "Music", "Games", "Photos", "Weather", "Browser", "Files", "Radio", "Clock", "Maps", "Notes", "Podcasts")
private val PreviewAccents = listOf(0xFFE53935, 0xFFE91E63, 0xFF8E24AA, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1)

@Preview(name = "Library 1080p", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun LibraryPreview() {
    val cards =
        PreviewNames.mapIndexed { i, name ->
            val id = "example.${name.lowercase()}"
            LibraryCard(
                app = AppEntry(key = id, packageName = id, label = name, accent = Color(PreviewAccents[i % PreviewAccents.size])),
                onHome = i % 3 == 0,
                recent = i == 4 || i == 5,
            )
        }
    val focused = cards[1].app.key
    LibraryPreviewFrame(
        LibraryUiState(
            open = true,
            shown = true,
            cards = cards,
            focusedKey = focused,
            hint = LibraryHint.Add,
            focusRequest = LibraryFocus(focused, 1),
        ),
    )
}

@Preview(name = "Library 1080p empty", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun LibraryEmptyPreview() {
    LibraryPreviewFrame(LibraryUiState(open = true, shown = true))
}

/** The library in Home's overlay host, over a stand-in for Home: a bright picture. */
@Composable
private fun LibraryPreviewFrame(state: LibraryUiState) {
    TvTheme(glassKind = GlassKind.Refractive) {
        val layout = LibraryLayout(animated = false)
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF0B3D66), Color(0xFF7A2E8E), Color(0xFFE5642B)))),
            )
            OverlayHost(open = { true }) {
                LibraryScreen(
                    state = { state },
                    layout = layout,
                    actions = LibraryActions(),
                    art = { key ->
                        previewArt(
                            state.cards
                                .first { it.app.key == key }
                                .app.accent,
                        )
                    },
                )
            }
        }
    }
}

/** The tile art of a colourful app: a diagonal blend, as the 112 by 63 picture a card shows. */
private fun previewArt(accent: Color): LibraryArt {
    val image = ImageBitmap(112, 63)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(112f, 63f)) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF00BCD4), accent, Color(0xFF1B1F2A)), start = Offset.Zero, end = Offset(112f, 63f)))
    }
    return LibraryArt(image, accent)
}
