# Fresh start without importing the Java launcher's data

Status: accepted

## Context

The Java launcher keeps the owner's tile order in `order.txt` and the app library choices in `library_apps.txt` and `library_home_removed.txt`. They live in the private files directory of com.gathin.tvhome. The Compose launcher has a different application id (dev.niltsiar.tvhome) and is installed beside it.

## Decision

The Compose launcher starts with its own empty `order.txt`, `library_apps.txt` and `library_home_removed.txt`. It does not import the Java files.

Another app's private files are unreadable without root, and this project never uses root. The dock, hidden apps and library lists come from the theme `config.json` that `tools/theme.sh` pushes with `TV_PACKAGE=dev.niltsiar.tvhome`, so the first start still shows the owner's dock.

## Consequences

- An order set with the reorder button in the Java launcher is lost. So is any app added to or removed from Home in its library. The owner sets them again in the Compose launcher.
- The two launchers keep separate files from then on. A change in one does not reach the other, and `switch-home.sh old` returns to the Java launcher's own order.
- Nothing in the Java launcher exports these files, so an import needs an export added there first.
