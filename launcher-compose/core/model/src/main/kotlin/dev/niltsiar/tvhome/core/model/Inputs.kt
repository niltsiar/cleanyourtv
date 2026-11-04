package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable

/** What an Inputs menu row opens. */
public sealed interface InputTarget {
    /** A passthrough input of the TV (HDMI, AV, a CEC device) by its TvInputManager id. */
    public data class TvInput(
        val id: String,
    ) : InputTarget

    /** A Coocaa OS source: its [name] (HDMI, AV, VOD, ...) and [index]. */
    public data class Coocaa(
        val name: String,
        val index: Int,
    ) : InputTarget

    /** The TV's own source panel. */
    public data object SourcePanel : InputTarget

    /** The Live TV app, on its tuner where the TV has one. */
    public data object LiveTv : InputTarget
}

public enum class InputIcon {
    Hdmi,
    Av,
    LiveTv,
}

/** What the TV says about a port. The dot shows for [Connected] only; [Unknown] covers standby and "no state worth showing". */
public enum class InputConnection {
    Unknown,
    Connected,
    Disconnected,
}

/** One row of the Inputs menu, or a line between groups. A [folded] row waits behind "More inputs". */
@Immutable
public sealed interface InputRow {
    public data class Port(
        val label: String,
        val icon: InputIcon,
        val connection: InputConnection,
        val target: InputTarget,
        val folded: Boolean = false,
    ) : InputRow

    /** One "Inputs" row that opens the TV's source panel, where nothing could be listed. */
    public data object Panel : InputRow

    public data object LiveTv : InputRow

    public data class Separator(
        val folded: Boolean = false,
    ) : InputRow

    /** Shows the folded rows. */
    public data object More : InputRow
}

/**
 * What the Inputs button and its menu show. [available] is whether the button shows at all. [panelOnly] says the
 * button opens the TV's source panel and no menu: a panel is set and there is no Live TV row to list beside it.
 */
@Immutable
public data class InputsMenu(
    val available: Boolean = false,
    val rows: List<InputRow> = emptyList(),
    val panelOnly: Boolean = false,
)

/** The rows shown now: folded ones only once [expanded], and "More inputs" only until then. */
public fun List<InputRow>.visible(expanded: Boolean): List<InputRow> =
    filter { row ->
        when (row) {
            is InputRow.Port -> expanded || !row.folded
            is InputRow.Separator -> expanded || !row.folded
            InputRow.More -> !expanded
            else -> true
        }
    }
