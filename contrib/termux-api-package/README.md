# Upstream contribution: `termux-screenshot`

Ready-to-PR shell CLI for [termux/termux-api-package](https://github.com/termux/termux-api-package).
Matches the style of `scripts/termux-camera-photo.in` (`getopts`, `@TERMUX_PREFIX@`,
`libexec/termux-api`).

## Dependency

Needs Termux:API Java `api_method=Screenshot` (MediaProjection). That receiver
ships in this fork’s `:termux-api` module (`ScreenshotAPI`). Stock upstream
Termux:API does **not** include it yet — land the Java API (or wait for it)
before merging the script alone into `termux-api-package`.

## Apply to a termux-api-package checkout

1. Copy `scripts/termux-screenshot.in` into that repo’s `scripts/`.
2. Add this line to the `script_files` list in `CMakeLists.txt` (alphabetically
   near `termux-sensor` / `termux-share`):

```cmake
 scripts/termux-screenshot
```

3. Rebuild / install the `termux-api` apt package.

## InVxTermux today

The app seeds an equivalent `$PREFIX/bin/termux-screenshot` from
`TermuxBunInstaller` so users do not need a packages rebuild. Keep both in sync
when changing flags or extras.

## KeepAliveService start/stop

Stock upstream scripts use short components (`com.termux.api/.KeepAliveService`).
This fork’s API `applicationId` is `com.involvex.termux_app.api` but the Java
class is `com.invapp.api.KeepAliveService`. Use the FQCN forms in
`scripts/termux-api-start.in` and `scripts/termux-api-stop.in` (also seeded
into `$PREFIX/bin` by the app). Add both to `script_files` in `CMakeLists.txt`
when packaging for this fork (they replace the stock short-form scripts).
