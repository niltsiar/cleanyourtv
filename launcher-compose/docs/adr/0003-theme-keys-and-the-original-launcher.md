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
- An absent `dock_opacity` gives a translucent dock. Its tint lightens on a bright wallpaper. In the Java launcher an absent value is 100, a solid band. A value from 30 to 100 sets the tint to 0.85 times that percent.

Behaviour that differs:

- `single_row` works without `dock_row`. The Java launcher needs both.
- `dock_row` does not change the layout, which always has a dock row. With `dock_row: false`, the tile menu and the Settings menu leave out their dock rows.
- `always_show_names` puts the app's name under every tile of the dock, the grid and the single row, at alpha 0.75 and at 1 on the focused tile, as the Java launcher does. The name is one line with an ellipsis, in the tile label style, and takes 36 dp: a 14 dp gap that clears the focus ring and a 22 dp line. The dock panel and each grid row grow by that only with the key on. The Java launcher takes 29 dp for the name and grows the dock band by 22 dp. With the key off, the Java launcher names the focused grid tile under it. Here the band over the grid names the focused tile, so no tile has a name under it.

Keys that this launcher adds:

- `dock_edge_light` (default true): the focused dock tile lights the dock's top rim and floor in its colour. It is off with `dock_band: false`, `animations: false` or `reduced_motion: true`.
- `header_scrim` (default `auto`): `auto` scales the dark scrims behind the title and the top bar to how bright the wallpaper is there, up to 0.70 at the top and 0.60 at the left; `off` keeps the fixed 0.55 and 0.40.
- `capture_input_keys` (default false): see ADR 0001.
- `input_arrival` (default false): when an HDMI port goes from no device or standby to connected while Home shows, and stays connected for 2 s, the Inputs button grows into its pill without taking focus, names the device and port ("PlayStation 5 · HDMI 2"), and lights its rim once. Up from the dock then goes to the button, and OK on it switches to that input. The offer ends after 20 s, when the port leaves connected, when Home leaves the screen, when the list opens, or after OK. It never switches input by itself. A port connected when Home came on screen is not an arrival. A TV whose ports all report connected, which the Inputs menu already shows without states, has none to tell. It is off with `show_inputs_pill: false`.
- `latitude` and `longitude`: both together, in degrees. The Java launcher reads them from `config.xml` only.
- `settings_key_scancodes`: raw scancodes that open Settings. The Java launcher reads them from `config.xml` only.

Keys of the Java launcher that this launcher ignores are the `unsupportedKeys` set in `TvConfigParser.kt`. A theme with them still loads. `ParsedConfig.unsupportedKeys` lists them apart from the keys that nothing knows.

## Consequences

- A Java theme runs unchanged. It can look different where a default above differs. To get the Java look, the theme sets `focus_overshoot: true`, `app_glow: false` and `dock_opacity: 100`.
- `config.xml` defaults of the Java launcher do not apply here. Only `config.json` is read, and the defaults are in `TvConfig`.
- A theme that sets `dock_row: false` to get one plain grid gets the dock row anyway.
- When this launcher starts to use a key of the `unsupportedKeys` set, the key leaves that set and its behaviour is checked against the Java launcher first.
