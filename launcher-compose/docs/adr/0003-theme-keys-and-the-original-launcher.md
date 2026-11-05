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

Keys that this launcher adds:

- `dock_edge_light` (default true): the focused dock tile lights the dock's top rim and floor in its colour. It is off with `dock_band: false`, `animations: false` or `reduced_motion: true`.
- `header_scrim` (default `auto`): `auto` scales the dark scrims behind the title and the top bar to how bright the wallpaper is there, up to 0.70 at the top and 0.60 at the left; `off` keeps the fixed 0.55 and 0.40.
- `capture_input_keys` (default false): see ADR 0001.
- `latitude` and `longitude`: both together, in degrees. The Java launcher reads them from `config.xml` only.
- `settings_key_scancodes`: raw scancodes that open Settings. The Java launcher reads them from `config.xml` only.

Keys of the Java launcher that this launcher ignores are the `unsupportedKeys` set in `TvConfigParser.kt`. A theme with them still loads. `ParsedConfig.unsupportedKeys` lists them apart from the keys that nothing knows.

## Consequences

- A Java theme runs unchanged. It can look different where a default above differs. To get the Java look, the theme sets `focus_overshoot: true`, `app_glow: false` and `dock_opacity: 100`.
- `config.xml` defaults of the Java launcher do not apply here. Only `config.json` is read, and the defaults are in `TvConfig`.
- A theme that sets `dock_row: false` to get one plain grid gets the dock row anyway.
- When this launcher starts to use a key of the `unsupportedKeys` set, the key leaves that set and its behaviour is checked against the Java launcher first.
