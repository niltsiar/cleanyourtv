package dev.niltsiar.tvhome.feature.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.ChromeShadow
import dev.niltsiar.tvhome.core.designsystem.GlassIconButton
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.GlassSurface
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.holdOk
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.sin

private const val ICON_VIEWPORT = 24f
private const val INPUTS_ARROW_SLIDE = 2.5f
private const val SETTINGS_TURN = 90f
private const val TIMELINE_SETTLE_MS = 250
private const val ARC_DIM = 0.2f
private const val ARC_DIM_END = 0.2f
private const val ARC_FIRST_LIGHT = 0.26f
private const val ARC_STAGGER = 0.2f
private const val ARC_RELIGHT = 0.34f
private const val APP_ICON_ZOOM = 0.12f
private const val APPS_ICON_GROWTH = 0.18f
private const val DEFAULT_NETWORK_WIDTH_DP = 180
private const val CLOCK_SLOTS = 5
private const val DIVIDER_ALPHA = 0.2f
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
 * [animated] is false with animations off or reduced_motion.
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
    /** The Inputs list's moving parts run; off with animations off or reduced_motion. */
    val animated: Boolean = true,
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
        animated = animations && !reducedMotion,
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

/** What a top-bar button is, so the band can name it while it has focus. */
internal sealed interface BarControl {
    data object Inputs : BarControl

    data object Network : BarControl

    data object Settings : BarControl

    /** Any other button, named by its [label] alone. */
    data class Named(
        val label: String,
    ) : BarControl
}

/** How many of the TV's ports have a device on them, as the band says it: none, the one (by its label), or a count. */
internal sealed interface InputsSummary {
    data object None : InputsSummary

    data class One(
        val label: String,
    ) : InputsSummary

    data class Many(
        val count: Int,
    ) : InputsSummary
}

internal fun List<InputRow>.connectedSummary(): InputsSummary {
    val connected = filterIsInstance<InputRow.Port>().filter { it.connection == InputConnection.Connected }
    return when (connected.size) {
        0 -> InputsSummary.None
        1 -> InputsSummary.One(connected.single().label)
        else -> InputsSummary.Many(connected.size)
    }
}

private fun Modifier.nameInBand(
    input: HomeInput,
    control: BarControl,
): Modifier = onFocusChanged { if (it.isFocused) input.topBarControl = control }

@Composable
internal fun TopBar(
    glass: GlassState,
    dockFocus: FocusRequester,
    state: () -> HomeUiState,
    layout: TopBarLayout,
    onAppClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    input: HomeInput,
    modifier: Modifier = Modifier,
) {
    val inputsAvailable by remember(state) { derivedStateOf { state().inputs.available } }
    Row(
        modifier =
            modifier
                .padding(top = TopBarTop, start = ScreenMargin, end = ScreenMargin)
                .fillMaxWidth()
                .height(TopBarHeight)
                .focusProperties { down = dockFocus }
                .onFocusChanged { input.topBarFocused = it.hasFocus },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClockPill(glass, state, layout.dateBelowClock)
        Spacer(Modifier.weight(1f))
        if (layout.showInputsPill && inputsAvailable) {
            val inputsSource = remember { MutableInteractionSource() }
            val slide = rememberFocusTimeline(inputsSource, layout.animated)
            val inputs = stringResource(R.string.inputs)
            GlassIconButton(
                glass,
                inputs,
                onClick = input::openInputs,
                modifier =
                    Modifier
                        .focusRequester(input.requester(INPUTS_FOCUS))
                        .nameInBand(input, BarControl.Inputs)
                        .onGloballyPositioned { input.inputsBounds.value = it.boundsInRoot() },
                interactionSource = inputsSource,
                focusLabel = inputs,
            ) { _, ink ->
                IconPart(R.drawable.ic_inputs_frame, ink)
                IconPart(R.drawable.ic_inputs_arrow, ink) {
                    translationX = inputsArrowSlide(slide.value) * INPUTS_ARROW_SLIDE * size.width / ICON_VIEWPORT
                }
            }
        }
        TopBarApps(glass, state, input, onAppClick)
        if (layout.showNetworkPill) NetworkButton(glass, state, layout, input, onOpenNetwork, onOpenWifiSettings)
        if (layout.reorderButton) ReorderButton(glass, input)
        if (layout.libraryButton) {
            val library = stringResource(R.string.library_entry)
            GlassIconButton(
                glass,
                library,
                onClick = onOpenLibrary,
                modifier = Modifier.focusRequester(input.requester(LIBRARY_BUTTON_FOCUS)).nameInBand(input, BarControl.Named(library)),
                focusLabel = library,
            ) { focus, ink -> AppsIcon(ink, focus) }
        }
        val settings = stringResource(R.string.settings)
        GlassIconButton(
            glass,
            settings,
            onClick = onOpenSettings,
            modifier =
                Modifier
                    .focusRequester(
                        input.requester(SETTINGS_FOCUS),
                    ).nameInBand(input, BarControl.Settings)
                    .holdOk(input.hold, onHold = { bounds ->
                        input.anchor.value = bounds
                        input.actions.onSettingsMenu()
                    }),
            focusLabel = settings,
        ) { focus, ink ->
            IconPart(R.drawable.ic_settings, ink) { rotationZ = SETTINGS_TURN * focus() }
        }
    }
}

/** The nine-square icon of the App library; it grows with focus. */
@Composable
internal fun AppsIcon(
    ink: ColorProducer,
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
) {
    val accent = TvTheme.colors.accent
    val reordering by input.reordering
    val on = animateFloatAsState(if (reordering) 1f else 0f, tween(NETWORK_FADE_MS), label = "reorderTint")
    val reorder = stringResource(R.string.reorder)
    GlassIconButton(
        glass,
        reorder,
        onClick = input.actions.onToggleReorder,
        modifier = Modifier.nameInBand(input, BarControl.Named(reorder)),
        focusLabel = reorder,
    ) { _, ink ->
        IconPart(R.drawable.ic_reorder, { lerp(ink(), accent, on.value) })
    }
}

/** The clock, the AM/PM marker, the greeting and the date. Absent until the TV's time is valid. */
@Composable
internal fun ClockPill(
    glass: GlassState,
    state: () -> HomeUiState,
    dateBelowClock: Boolean,
) {
    val hasTime by remember(state) { derivedStateOf { state().clock.isNotEmpty() } }
    if (!hasTime) return
    GlassSurface(
        glass = glass,
        modifier = Modifier.fillMaxHeight().testTag("clock").clearAndSetSemantics {},
        shape = CircleShape,
        shadow = ChromeShadow.Pill,
    ) {
        val typography = TvTheme.typography
        val clockStyle =
            remember(typography, dateBelowClock) { if (dateBelowClock) typography.clock.copy(fontSize = 28.sp) else typography.clock }
        val dateStyle =
            remember(typography, dateBelowClock) { if (dateBelowClock) typography.date.copy(fontSize = 14.sp) else typography.date }
        val divider = TvTheme.colors.onSurface.copy(alpha = DIVIDER_ALPHA)
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
                Spacer(Modifier.width(1.dp).height(24.dp).drawBehind { drawRect(divider) })
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

/**
 * The clock's characters, each in a slot of its own width. One that changes rises into place and fades in again; the
 * others stay. A time with a one-digit hour gets an empty slot in front, so the pill is as wide as with two.
 */
@Composable
private fun ClockDigits(
    text: String,
    style: TextStyle,
) {
    Row {
        text.padStart(CLOCK_SLOTS).forEachIndexed { index, char -> key(index) { ClockDigit(char, style) } }
    }
}

@Composable
private fun ClockDigit(
    char: Char,
    style: TextStyle,
) {
    val motion = TvTheme.motion
    val reveal = remember(char) { Animatable(if (motion.animations) 0f else 1f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, motion.springSpec(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) }
    val blank = char == ' '
    BasicText(
        text = if (blank) "0" else char.toString(),
        style = style,
        modifier =
            Modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = if (blank) 0f else reveal.value.coerceIn(0f, 1f)
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
    input: HomeInput,
    onClick: (String) -> Unit,
) {
    val apps by remember(state) { derivedStateOf { state().topBarApps } }
    for (app in apps) {
        key(app.key) {
            GlassIconButton(
                glass,
                app.label,
                onClick = { onClick(app.key) },
                modifier = Modifier.nameInBand(input, BarControl.Named(app.label)),
                focusLabel = app.label,
            ) { focus, _ ->
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
 * one icon out while the other fades in and rises; a change of colour fades too. Both run in the draw phase. A held OK
 * calls [onHold], which opens Wi-Fi settings where [onClick] may open the VPN app.
 */
@Composable
private fun NetworkButton(
    glass: GlassState,
    state: () -> HomeUiState,
    layout: TopBarLayout,
    input: HomeInput,
    onClick: () -> Unit,
    onHold: () -> Unit,
) {
    val network by remember(state) { derivedStateOf { state().network } }
    val plain = networkTint(network, layout, Color.Unspecified).isUnspecified
    val status = animateColorAsState(networkTint(network, layout, TvTheme.colors.onSurface), tween(NETWORK_FADE_MS), label = "networkTint")
    val wifi = animateFloatAsState(if (network.link == NetworkLink.Wifi) 1f else 0f, tween(NETWORK_FADE_MS), label = "wifi")
    val ethernet = animateFloatAsState(if (network.link == NetworkLink.Ethernet) 1f else 0f, tween(NETWORK_FADE_MS), label = "ethernet")
    val offline = animateFloatAsState(if (network.link == NetworkLink.Offline) 1f else 0f, tween(NETWORK_FADE_MS), label = "offline")
    val name = networkName(network)
    val badge = vpnBadge(network, layout)
    val showLabel = !layout.iconOnlyButtons || layout.wifiLabelWidth > 0
    val maxWidth = if (layout.iconOnlyButtons) layout.wifiLabelWidth else DEFAULT_NETWORK_WIDTH_DP
    val interactionSource = remember { MutableInteractionSource() }
    val light = rememberFocusTimeline(interactionSource, layout.animated)
    GlassIconButton(
        glass = glass,
        contentDescription = listOfNotNull(name, badge?.first).joinToString(" "),
        onClick = onClick,
        modifier =
            (if (showLabel) Modifier.widthIn(max = maxWidth.dp) else Modifier)
                .nameInBand(input, BarControl.Network)
                .holdOk(input.hold, onHold = { onHold() }),
        interactionSource = interactionSource,
        focusLabel = linkName(network.link),
        label =
            if (showLabel) {
                { ink ->
                    BasicText(
                        text = name,
                        style = TvTheme.typography.pillLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = { ink.content().let { it.copy(alpha = it.alpha * unfocusedLabelAlpha(ink.progress())) } },
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
    ) { _, ink ->
        val tint = ColorProducer { if (plain) ink() else status.value }
        IconPart(R.drawable.ic_wifi_dot, tint) {
            fade(wifi.value)
            alpha *= wifiLight(light.value, part = 0)
        }
        IconPart(R.drawable.ic_wifi_arc_inner, tint) {
            fade(wifi.value)
            alpha *= wifiLight(light.value, part = 1)
        }
        IconPart(R.drawable.ic_wifi_arc_outer, tint) {
            fade(wifi.value)
            alpha *= wifiLight(light.value, part = 2)
        }
        IconPart(R.drawable.ic_ethernet, tint) { fade(ethernet.value) }
        IconPart(R.drawable.ic_wifi_off, tint) { fade(offline.value) }
    }
}

private const val NETWORK_FADE_MS = 320
private const val UNFOCUSED_LABEL_ALPHA = 0.75f

/** The network label's alpha at a button focus [progress] of 0 to 1: three quarters while idle, full once focused. */
private fun unfocusedLabelAlpha(progress: Float): Float = UNFOCUSED_LABEL_ALPHA + (1f - UNFOCUSED_LABEL_ALPHA) * progress

/** An icon that fades in as [shown] goes to 1 and rises into place, and fades out and sinks as it goes to 0. */
private fun GraphicsLayerScope.fade(shown: Float) {
    alpha = shown.coerceIn(0f, 1f)
    translationY = (1f - shown) * NetworkIconRise.toPx()
}

@Composable
@ReadOnlyComposable
private fun networkName(network: NetworkState): String = network.ssid?.takeIf { network.link == NetworkLink.Wifi } ?: linkName(network.link)

/** The kind of link, never the network's own name, so a focused button does not show the Wi-Fi name on screen. */
@Composable
@ReadOnlyComposable
private fun linkName(link: NetworkLink): String =
    when (link) {
        NetworkLink.Ethernet -> stringResource(R.string.ethernet)
        NetworkLink.Wifi -> stringResource(R.string.wifi)
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
    tint: ColorProducer,
    layer: GraphicsLayerScope.() -> Unit = {},
) {
    val painter = painterResource(icon)
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                layer()
            }.drawBehind { with(painter) { draw(size, colorFilter = ColorFilter.tint(tint())) } },
    )
}

/**
 * One run of an icon's own motion on a [timeline] from 0 to 1: the dot (part 0), the inner arc (1) and the outer arc (2)
 * of the Wi-Fi icon. All three fade down together, then light again one after another, each with an ease-out. Fully lit
 * at 0 and at 1.
 */
internal fun wifiLight(
    timeline: Float,
    part: Int,
): Float {
    val t = timeline.coerceIn(0f, 1f)
    if (t < ARC_DIM_END) return 1f - (1f - ARC_DIM) * FastOutSlowInEasing.transform(t / ARC_DIM_END)
    val start = ARC_FIRST_LIGHT + part * ARC_STAGGER
    return ARC_DIM + (1f - ARC_DIM) * EaseOutCubic.transform(((t - start) / ARC_RELIGHT).coerceIn(0f, 1f))
}

/** How far the Inputs arrow has slid on a [timeline] from 0 to 1: out and back once, 0 at both ends and 1 at the middle. */
internal fun inputsArrowSlide(timeline: Float): Float = sin(PI.toFloat() * timeline.coerceIn(0f, 1f))

/**
 * A value that sits at 1 and runs from 0 to 1 over [Motion.ICON_MS] each time the button behind [interactionSource]
 * gains focus, as long as the button's own icon motion, so every top-bar button settles together. Losing focus mid-run
 * settles it to 1, so nothing replays on blur. It stays at 1 while [animated] is false. Read the value in a layer or
 * draw lambda.
 */
@Composable
internal fun rememberFocusTimeline(
    interactionSource: MutableInteractionSource,
    animated: Boolean,
): Animatable<Float, AnimationVector1D> {
    val timeline = remember { Animatable(1f) }
    LaunchedEffect(interactionSource, animated) {
        if (!animated) {
            timeline.snapTo(1f)
            return@LaunchedEffect
        }
        interactionSource.interactions.collectLatest {
            when (it) {
                is FocusInteraction.Focus -> {
                    timeline.snapTo(0f)
                    timeline.animateTo(1f, tween(Motion.ICON_MS))
                }

                is FocusInteraction.Unfocus -> {
                    if (timeline.value < 1f) timeline.animateTo(1f, tween(TIMELINE_SETTLE_MS, easing = LinearEasing))
                }
            }
        }
    }
    return timeline
}
