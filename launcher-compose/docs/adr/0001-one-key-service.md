# One merged key service in the Home process

Status: accepted

## Context

The Java launcher (com.gathin.tvhome) ships two opt-in accessibility services. HomeKeyService handles the Home button, the Home redirects and the power-on Home. InputKeyService handles the Input button and the app keys. Each is enabled on its own.

The Compose launcher needs the same remote behaviour: `app_keys`, the Settings and Input buttons, `home_long_press_home`, `stock_home_redirect`, `power_on_home` and `power_on_home_always`. These features share the key-press timing code and the config. They also read state that the launcher already holds in memory.

## Decision

`KeyService` in `service/keys` does all of it. It runs in the Home app's own process, so it reads the shared app-scope state directly, with no IPC. One service means one entry in `enabled_accessibility_services`.

KEYCODE_TV and KEYCODE_TV_INPUT are taken only when `capture_input_keys` is true. The default is false. Without this switch, enabling the service for `app_keys` would also take the TV's own input buttons. Buttons named in `input_key_keycodes` are always taken.

## Consequences

- `scripts/switch-home.sh new` removes the two Java services from `enabled_accessibility_services` and adds `KeyService`. `old` puts the values saved before the first change back. `KEYS=keep` leaves the list alone.
- The script waits for `KeyService` to bind. If it does not bind, the script restores the key settings.
- A force stop of the launcher turns the service off, because Android removes a force-stopped app's accessibility services from the list. With `restore_key_services` in the theme, `KeyService` records itself once it runs and drops the record when Android unbinds it. When Home starts again, `ServiceRepair` puts a recorded service back on the list. This needs `WRITE_SECURE_SETTINGS`, which `RESTORE_KEY_SERVICES=1 scripts/switch-home.sh new` grants and a run without it revokes. Without the key, nothing re-adds the service.
- A TV whose power key cannot wake the set while `KeyService` stays bound with the screen off (TCL tc8000_ay30a2) can opt in to a standby guard: `GuardConfig` (broadcast `dev.niltsiar.tvhome.GUARD`, protected by `WRITE_SECURE_SETTINGS`) sets the flag, `KeyService` then takes itself off the enabled list at screen-off, and `GuardKeep` lists it again at screen-on. `GuardRevival`, a notification listener that reads no notification, makes the system bind the process again after a firmware kill. The guard is off by default, has no theme key and does nothing without the permission; `GUARD=1 scripts/switch-home.sh new` sets all three parts up.
- `scripts/spike-gate.sh` refuses to run while `KeyService` is enabled, as a precaution: a reinstall of the real package would drop the entry, and the gate installs only the separate benchmark package.
