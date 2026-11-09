# Theme keys follow the Java launcher, and the differences are listed

Status: accepted

## Context

Themes are written for the Java launcher (com.gathin.tvhome). Their `config.json` files should keep working in the Compose launcher without edits. A theme author must also be able to see where the two launchers differ.

## Decision

`parseTvConfig` reads each key of the Java launcher's `config.json` with the same name, type, range and meaning. A value of the wrong type or outside its range keeps the default, as in the Java launcher.

A default or a behaviour that differs is listed here. So is every key that this launcher adds. A change to either list changes this ADR in the same commit.

Defaults that differ:

- `focus_overshoot` is false here. It is true in the Java launcher.
- `app_glow` is true here, so grid tiles also get the ring in the app's colour. It is false in the Java launcher, where only the cinematic dock takes the app's colour. `cinematic_white_focus` keeps every ring white.
- With no theme applied, every top-bar button is a round icon at rest and shows its label on focus. A theme without `icon_only_buttons` gets `false`, as in the Java launcher: the Wi-Fi and Settings buttons then keep their labels at rest.
- With no theme applied, the Inputs button is a round icon at rest and shows its label on focus. A theme without `icon_only_inputs` gets `false`, as in the Java launcher: the Inputs button then keeps its label at rest, with or without `icon_only_buttons`.
- An absent `dock_opacity` gives a translucent dock. Its tint lightens on a bright wallpaper. In the Java launcher an absent value is 100, a solid band. A value from 30 to 100 sets the tint to 0.85 times that percent.

Behaviour that differs:

- `single_row` works without `dock_row`. The Java launcher needs both.
- `dock_row` does not change the layout, which always has a dock row. With `dock_row: false`, the tile menu and the Settings menu leave out their dock rows.
- `always_show_names` puts the app's name under every tile of the dock, the grid and the single row, at alpha 0.85 on every tile, focused or not (the Java launcher uses 0.75 and 1). The name is one line with an ellipsis, in the tile label style, and takes 38 dp: a 14 dp gap under the tile, then a 24 dp pill (a 20 dp line and 2 dp of padding above and below). Every pill of a row has the same top, and none moves with focus: a focused tile grows about its bottom edge, so its ring ends 9.7 dp under the tile and stays 4 dp clear of the pill. A tile taller than 128.5 dp (three columns or fewer) grows about a point 128.5 dp under its top instead, which keeps the ring 4 dp under the rim of the dock panel and the pills of the row above, and adds 0.08 of its height past 128.5 dp to the gap. The space under a pill is at least 1.5 times its gap, so the room under the pill grows when 24 dp is less. The dock panel and each grid row grow by that only with the key on. The Java launcher takes 29 dp for the name and grows the dock band by 22 dp. With the key off, the Java launcher names the focused grid tile under it. Here the band over the grid names the focused tile, so no tile has a name under it.
- `dock_even_padding` makes the dock panel's padding the same on all four sides, but the amount is the room a focused end tile's ring needs, not the gap between the tiles as in the Java launcher. The panel pads its tiles on the side by the ring's reach plus 12 dp (26.8 dp in a 6-column dock and 29.8 dp in a 4-column dock, on a 960 dp wide screen), and 24 dp above and below. With the key on, the top and bottom take the larger of the side padding and 24 dp, and the side padding does not shrink. The panel is then taller by up to 12 dp, and the grid's pivot, the rows that must fit and the dock tile menu follow the top padding.
- `glass_style` styles the focus ring each tile draws, not a floating slab. `glass` and a missing or unknown value keep the full look: lit top, ring with its dark keyline, glow, and on a library card the plate that takes the place of its idle fill. `rim` draws the ring and its keyline alone, so a library card keeps its idle fill while focused. `halo` adds the glow at half its alpha to that. The keyline and the 3 dp ring are drawn in all three (the Java launcher's rim is a 2 dp stroke), and a value that is not `glass`, `rim` or `halo` in any case is logged as rejected, where the Java launcher falls back silently.
- `launcher_settings_button` makes the header Settings button open the Launcher settings page, as in the Java launcher, with its Reorder row first and focused. The Settings menu (held OK on that button) lists its "Launcher settings" row first with the key on or off. The Java launcher lists that row only with the key off.
- `dock_title: false` hides only the focused tile's name and status line above the dock, and the scrim under it. The reorder, dock-edit, library pill and top-bar lines still show, and the layout does not change. The Java launcher hides the whole hero view and gives its room to the tiles.
- `wallpaper_choice` (the Wallpaper row of the Launcher settings page) offers and applies the theme's single `wallpaper.jpg` only when the file has content, and a pick of it applies with `wallpaper: false` too, as a pick from the `wallpapers/` folder or `none` does. The Java launcher lists the file when it exists, empty or not, and ignores a pick of it under `wallpaper: false`. A stored choice whose file is gone shows as Theme default in the row and the picker, and the theme decides what Home shows. The Java launcher shows the stale file name in the row.

Keys that this launcher adds:

- `dock_edge_light` (default true): the focused dock tile lights the dock's top rim and floor in its colour. It is off with `dock_band: false`, `animations: false` or `reduced_motion: true`.
- `header_scrim` (default `auto`): `auto` scales the dark scrims behind the title and the top bar to how bright the wallpaper is there, up to 0.70 at the top and 0.60 at the left; `off` keeps the fixed 0.55 and 0.40.
- `capture_input_keys` (default false): see ADR 0001.
- `input_arrival` (default false): when an HDMI port goes from no device or standby to connected while Home shows, and stays connected for 2 s, the Inputs button grows into its pill without taking focus, names the device and port ("PlayStation 5 · HDMI 2"), and lights its rim once. Up from the dock then goes to the button, and OK on it switches to that input. The offer ends after 20 s, when the port leaves connected, when Home leaves the screen, when the list opens, or after OK. It never switches input by itself. A port connected when Home came on screen is not an arrival. A TV whose ports all report connected, which the Inputs menu already shows without states, has none to tell. It is off with `show_inputs_pill: false`.
- `habit_hint` (default false): a quiet mark under the app the household usually opens at this time of day. With the key on, Home keeps a private log of the launches it makes (package and time, eight weeks, at most 800 records) and reads nothing from other apps. The habit is the app with at least 4 launches on at least 3 different dates, and at least half of all launches, among those of the same kind of day (Monday to Friday, or Saturday and Sunday, in local time) within 45 minutes of the time of day. Two apps with half each give none. Home works it out off the main thread when it comes to the front and every 15 minutes while it shows. The mark is a 20 x 3 dp bar in a light neutral with a dark edge, centred in the gap under that app's tile (under the name with `always_show_names`). It fades in and out over 400 ms, and appears or goes at once with reduced motion. No tile moves. While that app's tile has focus, the line under the title adds "You usually open it now" after the last-opened time. With the key off nothing is recorded and nothing runs.
- `library_least_used` (default false): with `app_library` on, the MENU key in the App library switches between the usual order and a least used first order. In the second, apps Home never opened come first (by label), then the others by the time Home last opened them, oldest first, with ties by label; each card keeps its status line and adds when the app was last opened ("Never opened" for an app Home never opened). The header names the order on, and the hint line names the key and the other order. Focus stays on the same app and the grid scrolls to it. The library opens in the usual order every time. With the key off the MENU key does nothing in the library. Home notes the time of each launch it makes, from a tile or from the library, in the same record `cinematic_home` uses; it reads nothing from other apps and asks for no permission. An app never opened counts from the start of that record, which is the first launch it noted (or, with this key or `unused_fade_days` on, the first start with the key on), so a new install does not call every app unused on day one.
- `unused_fade_days` (default 0, which is off; 0 to 3650): a tile in the app grid, not in the dock and not in the library, whose app Home last opened more than this many calendar days ago is drawn at 55% saturation and 75% opacity. An app never opened counts from the start of the record, as above. While the tile has focus it returns to full colour over 150 ms (at once with reduced motion). Home works the set out when the grid loads and at each local midnight while it shows, with one timer and no polling. The fade is a colour filter and alpha on the tile's layer, so a focus move recomposes nothing.
- `latitude` and `longitude`: both together, in degrees. The Java launcher reads them from `config.xml` only.
- `settings_key_scancodes`: raw scancodes that open Settings. The Java launcher reads them from `config.xml` only.

Keys of the Java launcher that this launcher ignores are the `unsupportedKeys` set in `TvConfigParser.kt`. A theme with them still loads. `ParsedConfig.unsupportedKeys` lists them apart from the keys that nothing knows.

## Consequences

- A Java theme runs unchanged. It can look different where a default above differs. To get the Java look, the theme sets `focus_overshoot: true`, `app_glow: false` and `dock_opacity: 100`.
- `config.xml` defaults of the Java launcher do not apply here. Only `config.json` is read, and the defaults are in `TvConfig`.
- A theme that sets `dock_row: false` to get one plain grid gets the dock row anyway.
- When this launcher starts to use a key of the `unsupportedKeys` set, the key leaves that set and its behaviour is checked against the Java launcher first.
