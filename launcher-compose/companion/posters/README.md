# Poster helper

Home has no `INTERNET` permission, so an App highlights card whose poster is a web URL is a text card. This small app,
`dev.niltsiar.tvhome.posters`, fetches those pictures for Home when `app_shelf_web_posters` is on
([ADR 0003](../../docs/adr/0003-theme-keys-and-the-original-launcher.md)). It has no screen, no service, no boot receiver and
no schedule: it runs only while Home asks it for a poster, and holds only the `INTERNET` permission.

## Build and install

Build it in the same checkout, on the same machine, as Home:

```shell
./gradlew :app:assembleRelease :companion:posters:assembleRelease
adb install -r companion/posters/build/outputs/apk/release/posters-release.apk
```

Both release builds are signed with the machine's debug key. Home sends poster URLs only to a helper signed with its own
key, and the helper answers only a caller signed with its key, so a helper built on another machine gets no URLs and
Launcher settings says Helper signed with another key. Uninstall the helper to go back to text cards; Clear storage in its
App info empties its cache.

## What it sends where

Home asks for `content://dev.niltsiar.tvhome.posters/poster?u=<poster URL>&w=<px>&h=<px>`, plus `lan=1` for an `http`
poster when `app_shelf_lan_posters` is on. Only poster URLs of the cards Home is about to show reach the helper. A poster
that is not cached yet is a miss at once, and the helper queues a fetch; when the picture lands it tells Home, which asks
again.

For each poster the helper sends one `GET` to the poster's host: no cookies, no Referer, the User-Agent `Mozilla/5.0`, no
proxy, no redirect followed. The host sees the TV's address and the poster URL, as it would for the app that wrote the row.

- Without `lan=1`: `https` on port 443 only, to a host name, never an IP literal. Every address the name resolves to must be
  public.
- With `lan=1`: `http` or `https` on any port, to a host name or an IP literal, and every address must be private (10/8,
  172.16/12, 192.168/16, fc00::/7).
- Never: user info in the URL, a URL over 2048 characters, loopback, link-local, CGNAT, multicast, or any of those as an
  IPv4-mapped address. The helper connects only to the addresses it checked.
- The response must be status 200 with `Content-Type` `image/jpeg`, `image/png` or `image/webp`, at most 2 MiB, and a
  picture between 16 and 4096 pixels a side.

The helper crops the picture to the card's shape, scales it down to the card's size within 640 x 360 pixels, and stores it
as a JPEG in its cache: 24 MiB at most, least recently used first out. A picture older than 7 days is still served and
fetched again. It runs 2 fetches at a time with at most 32 waiting, and a URL whose fetch failed waits 1 hour before the
next try.
