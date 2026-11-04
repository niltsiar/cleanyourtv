package dev.niltsiar.tvhome.feature.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassIconButton
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.GlassSurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.glassContainer
import dev.niltsiar.tvhome.core.designsystem.holdOk
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState

private const val ICON_VIEWPORT = 24f
private const val INPUTS_ARROW_SLIDE = 2.5f
private const val SETTINGS_TURN = 90f
private const val ARC_DIM = 0.2f
private const val ARC_DIM_END = 0.15f
private const val ARC_STAGGER = 0.3f
private const val ARC_RELIGHT = 0.35f
private const val APP_ICON_ZOOM = 0.12f
private const val APPS_ICON_GROWTH = 0.18f
private const val DEFAULT_NETWORK_WIDTH_DP = 180
private val NetworkIconRise = 6.dp
private val DigitRise = 10.dp
private val ClockPillPadding = 24.dp
private val OnlineGreen = Color(0xFF34C759)
private val NoInternetOrange = Color(0xFFFF9F0A)
private val OfflineRed = Color(0xFFFF453A)

/**
 * The theme's say over the top bar: [showNetworkPill] and [showInputsPill] hide those buttons. The network button
 * shows its name beside the icon unless [iconOnlyButtons] is set; then a [wifiLabelWidth] over 0 caps the button at
 * that many dp and keeps the name. [wifiStatusColor] tints the icon by the connection ([wifiStatusWarnings] adds orange
 * and red). [vpnApp] says a VPN app is set, so a VPN that is down is worth a badge. [dateBelowClock] stacks the date under the clock.
 * [reorderButton] adds a button that turns reorder mode on and off, [libraryButton] one that opens the App library.
 */
@Immutable
public data class TopBarLayout(
    val showNetworkPill: Boolean = true,
    val showInputsPill: Boolean = true,
    val iconOnlyButtons: Boolean = false,
    val wifiLabelWidth: Int = 0,
    val wifiStatusColor: Boolean = false,
    val wifiStatusWarnings: Boolean = false,
    val vpnApp: Boolean = false,
    val dateBelowClock: Boolean = false,
    val reorderButton: Boolean = false,
    val libraryButton: Boolean = false,
)

public fun TvConfig.topBarLayout(): TopBarLayout =
    TopBarLayout(
        showNetworkPill = showNetworkPill,
        showInputsPill = showInputsPill,
        iconOnlyButtons = iconOnlyButtons,
        wifiLabelWidth = wifiLabelWidth,
        wifiStatusColor = wifiStatusColor,
        wifiStatusWarnings = wifiStatusWarnings,
        vpnApp = vpnApp != null,
        dateBelowClock = dateBelowClock,
        reorderButton = reorderButton,
        libraryButton = appLibrary && libraryButton,
    )

/**
 * The network icon's colour. With wifi_status_color it is green while online; with wifi_status_warnings orange
 * while connected without internet (a captive portal) and red while offline. Otherwise, and offline without warnings, [plain].
 */
internal fun networkTint(
    network: NetworkState,
    layout: TopBarLayout,
    plain: Color,
): Color =
    when {
        !layout.wifiStatusColor -> plain
        network.link == NetworkLink.Offline -> if (layout.wifiStatusWarnings) OfflineRed else plain
        layout.wifiStatusWarnings && !network.validated -> NoInternetOrange
        else -> OnlineGreen
    }

@Composable
internal fun TopBar(
    glass: GlassState,
    dockFocus: FocusRequester,
    state: () -> HomeUiState,
    layout: TopBarLayout,
    onAppClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenLibrary: () -> Unit,
    input: HomeInput,
    modifier: Modifier = Modifier,
) {
    val ink = rememberUpdatedState(TvTheme.colors.onSurface)
    Row(
        modifier =
            modifier
                .padding(top = TopBarTop, start = ScreenMargin, end = ScreenMargin)
                .fillMaxWidth()
                .height(TopBarHeight)
                .focusProperties { down = dockFocus }
                .glassContainer(glass),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClockPill(glass, state, layout.dateBelowClock)
        Spacer(Modifier.weight(1f))
        if (layout.showInputsPill) {
            // TODO(M6): Inputs opens the source list; its visibility rule and action come with Inputs and Live TV.
            GlassIconButton(glass, stringResource(R.string.inputs), onClick = {}) { focus ->
                IconPart(R.drawable.ic_inputs_frame, ink)
                IconPart(R.drawable.ic_inputs_arrow, ink) {
                    translationX = focus() * INPUTS_ARROW_SLIDE * size.width / ICON_VIEWPORT
                }
            }
        }
        TopBarApps(glass, state, onAppClick)
        if (layout.showNetworkPill) NetworkButton(glass, state, layout, ink, onOpenNetwork)
        if (layout.reorderButton) ReorderButton(glass, input, ink)
        if (layout.libraryButton) {
            GlassIconButton(
                glass,
                stringResource(R.string.library_entry),
                onClick = onOpenLibrary,
                modifier = Modifier.focusRequester(input.requester(LIBRARY_BUTTON_FOCUS)),
            ) { focus -> AppsIcon(ink, focus) }
        }
        GlassIconButton(
            glass,
            stringResource(R.string.settings),
            onClick = onOpenSettings,
            modifier =
                Modifier.focusRequester(input.requester(SETTINGS_FOCUS)).holdOk(input.hold, onHold = { bounds ->
                    input.anchor.value = bounds
                    input.actions.onSettingsMenu()
                }),
        ) { focus ->
            IconPart(R.drawable.ic_settings, ink) { rotationZ = SETTINGS_TURN * focus() }
        }
    }
}

/** The nine-square icon of the App library; it grows with focus. */
@Composable
internal fun AppsIcon(
    ink: State<Color>,
    focus: () -> Float,
) {
    IconPart(R.drawable.ic_apps, ink) {
        val grow = 1f + APPS_ICON_GROWTH * focus()
        scaleX = grow
        scaleY = grow
    }
}

/** Turns reorder mode on and off; its icon takes the accent colour while the mode is on. */
@Composable
private fun ReorderButton(
    glass: GlassState,
    input: HomeInput,
    ink: State<Color>,
) {
    val accent = TvTheme.colors.accent
    val reordering by input.reordering
    val tint = animateColorAsState(if (reordering) accent else ink.value, tween(NETWORK_FADE_MS), label = "reorderTint")
    GlassIconButton(glass, stringResource(R.string.reorder), onClick = input.actions.onToggleReorder) {
        IconPart(R.drawable.ic_reorder, tint)
    }
}

/** The clock, the AM/PM marker, the greeting and the date. Absent until the TV's time is valid. */
@Composable
private fun ClockPill(
    glass: GlassState,
    state: () -> HomeUiState,
    dateBelowClock: Boolean,
) {
    val hasTime by remember(state) { derivedStateOf { state().clock.isNotEmpty() } }
    if (!hasTime) return
    val typography = TvTheme.typography
    val clockStyle =
        remember(typography, dateBelowClock) { if (dateBelowClock) typography.clock.copy(fontSize = 28.sp) else typography.clock }
    val dateStyle = remember(typography, dateBelowClock) { if (dateBelowClock) typography.date.copy(fontSize = 14.sp) else typography.date }
    GlassSurface(glass = glass, modifier = Modifier.fillMaxHeight(), shape = CircleShape) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = ClockPillPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (dateBelowClock) {
                Column(verticalArrangement = Arrangement.Center) {
                    ClockTime(state, clockStyle)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateText(state, dateStyle)
                        GreetingText(state)
                    }
                }
            } else {
                ClockTime(state, clockStyle)
                Spacer(Modifier.width(1.dp).height(24.dp).drawBehind { drawRect(Color(0x33FFFFFF)) })
                Column(verticalArrangement = Arrangement.Center) {
                    GreetingText(state)
                    DateText(state, dateStyle)
                }
            }
        }
    }
}

@Composable
private fun ClockTime(
    state: () -> HomeUiState,
    style: TextStyle,
) {
    val clock by remember(state) { derivedStateOf { state().clock } }
    val amPm by remember(state) { derivedStateOf { state().amPm } }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ClockDigits(clock, style)
        if (amPm.isNotEmpty()) BasicText(amPm, style = TvTheme.typography.amPm, modifier = Modifier.padding(bottom = 4.dp))
    }
}

/** The clock's characters. One that changes rises into place and fades in again; the others stay. */
@Composable
private fun ClockDigits(
    text: String,
    style: TextStyle,
) {
    Row {
        text.forEachIndexed { index, char -> key(index) { ClockDigit(char, style) } }
    }
}

@Composable
private fun ClockDigit(
    char: Char,
    style: TextStyle,
) {
    val reveal = remember(char) { Animatable(0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) }
    BasicText(
        text = char.toString(),
        style = style,
        modifier =
            Modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = reveal.value.coerceIn(0f, 1f)
                translationY = (1f - reveal.value) * DigitRise.toPx()
            },
    )
}

@Composable
private fun DateText(
    state: () -> HomeUiState,
    style: TextStyle,
) {
    val date by remember(state) { derivedStateOf { state().date } }
    BasicText(date, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun GreetingText(state: () -> HomeUiState) {
    val greeting by remember(state) { derivedStateOf { state().greeting } }
    val shown = greeting ?: return
    val part = stringResource(shown.part.label())
    val text = if (shown.name == null) part else stringResource(R.string.greeting_with_name, part, shown.name)
    BasicText(
        text.uppercase(LocalLocale.current.platformLocale),
        style = TvTheme.typography.greeting,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@StringRes
private fun DayPart.label(): Int =
    when (this) {
        DayPart.Night -> R.string.good_night
        DayPart.Morning -> R.string.good_morning
        DayPart.Forenoon -> R.string.good_forenoon
        DayPart.Afternoon -> R.string.good_afternoon
        DayPart.Evening -> R.string.good_evening
    }

/** One round button per top_bar_apps app, showing its own icon. */
@Composable
private fun TopBarApps(
    glass: GlassState,
    state: () -> HomeUiState,
    onClick: (String) -> Unit,
) {
    val apps by remember(state) { derivedStateOf { state().topBarApps } }
    for (app in apps) {
        key(app.key) {
            GlassIconButton(glass, app.label, onClick = { onClick(app.key) }) { focus ->
                val icon = app.image ?: return@GlassIconButton
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            val zoom = 1f + APP_ICON_ZOOM * focus()
                            scaleX = zoom
                            scaleY = zoom
                        },
                )
            }
        }
    }
}

/**
 * The network button: Wi-Fi arcs, an Ethernet plug or a struck-through Wi-Fi, by the link. A change of link fades
 * one icon out while the other fades in and rises; a change of colour fades too. Both run in the draw phase.
 */
@Composable
private fun NetworkButton(
    glass: GlassState,
    state: () -> HomeUiState,
    layout: TopBarLayout,
    ink: State<Color>,
    onClick: () -> Unit,
) {
    val network by remember(state) { derivedStateOf { state().network } }
    val tint = animateColorAsState(networkTint(network, layout, ink.value), tween(NETWORK_FADE_MS), label = "networkTint")
    val wifi = animateFloatAsState(if (network.link == NetworkLink.Wifi) 1f else 0f, tween(NETWORK_FADE_MS), label = "wifi")
    val ethernet = animateFloatAsState(if (network.link == NetworkLink.Ethernet) 1f else 0f, tween(NETWORK_FADE_MS), label = "ethernet")
    val offline = animateFloatAsState(if (network.link == NetworkLink.Offline) 1f else 0f, tween(NETWORK_FADE_MS), label = "offline")
    val name = networkName(network)
    val badge = vpnBadge(network, layout)
    val showLabel = !layout.iconOnlyButtons || layout.wifiLabelWidth > 0
    val maxWidth = if (layout.iconOnlyButtons) layout.wifiLabelWidth else DEFAULT_NETWORK_WIDTH_DP
    GlassIconButton(
        glass = glass,
        contentDescription = listOfNotNull(name, badge?.first).joinToString(" "),
        onClick = onClick,
        modifier = if (showLabel) Modifier.widthIn(max = maxWidth.dp) else Modifier,
        label =
            if (showLabel) {
                {
                    BasicText(
                        text = name,
                        style = TvTheme.typography.pillLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        BasicText(
                            badge.first,
                            style = TvTheme.typography.pillLabel.copy(color = badge.second, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
            } else {
                null
            },
    ) { focus ->
        IconPart(R.drawable.ic_wifi_dot, tint) { fade(wifi.value) }
        IconPart(R.drawable.ic_wifi_arc_inner, tint) {
            fade(wifi.value)
            alpha *= wifiArcAlpha(focus(), arc = 0)
        }
        IconPart(R.drawable.ic_wifi_arc_outer, tint) {
            fade(wifi.value)
            alpha *= wifiArcAlpha(focus(), arc = 1)
        }
        IconPart(R.drawable.ic_ethernet, tint) { fade(ethernet.value) }
        IconPart(R.drawable.ic_wifi_off, tint) { fade(offline.value) }
    }
}

private const val NETWORK_FADE_MS = 320

/** An icon that fades in as [shown] goes to 1 and rises into place, and fades out and sinks as it goes to 0. */
private fun GraphicsLayerScope.fade(shown: Float) {
    alpha = shown.coerceIn(0f, 1f)
    translationY = (1f - shown) * NetworkIconRise.toPx()
}

@Composable
@ReadOnlyComposable
private fun networkName(network: NetworkState): String =
    when (network.link) {
        NetworkLink.Ethernet -> stringResource(R.string.ethernet)
        NetworkLink.Wifi -> network.ssid ?: stringResource(R.string.wifi)
        NetworkLink.Offline -> stringResource(R.string.offline)
    }

/** "VPN" in green while a VPN is up; "VPN off" in red while none is and a VPN app is set; else none. */
@Composable
@ReadOnlyComposable
private fun vpnBadge(
    network: NetworkState,
    layout: TopBarLayout,
): Pair<String, Color>? =
    when {
        network.vpn && network.link != NetworkLink.Offline -> stringResource(R.string.vpn) to OnlineGreen
        layout.vpnApp && network.link != NetworkLink.Offline -> stringResource(R.string.vpn_off) to OfflineRed
        else -> null
    }

/**
 * One layer of a top bar icon, drawn over the others in the same 24-unit viewport. [layer] runs in the layer
 * phase and [tint] is read in the draw phase, so neither a focus change nor a colour change recomposes.
 */
@Composable
internal fun IconPart(
    @DrawableRes icon: Int,
    tint: State<Color>,
    layer: GraphicsLayerScope.() -> Unit = {},
) {
    val painter = painterResource(icon)
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                layer()
            }.drawBehind { with(painter) { draw(size, colorFilter = ColorFilter.tint(tint.value)) } },
    )
}

/**
 * The Wi-Fi arcs on focus [progress] (0 idle, 1 focused): both dim at once, then light again one after another,
 * the inner arc first. Both are fully lit at 0 and from 0.8 on.
 */
private fun wifiArcAlpha(
    progress: Float,
    arc: Int,
): Float {
    val p = progress.coerceIn(0f, 1f)
    val start = ARC_DIM_END + arc * ARC_STAGGER
    return when {
        p < ARC_DIM_END -> 1f - (1f - ARC_DIM) * p / ARC_DIM_END
        else -> (ARC_DIM + (1f - ARC_DIM) * (p - start) / ARC_RELIGHT).coerceIn(ARC_DIM, 1f)
    }
}
