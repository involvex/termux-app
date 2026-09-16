# Install

Install **only** from this fork’s
[GitHub Releases](https://github.com/involvex/termux-app/releases)
(first release: **v0.200.0**).

## Which APK?

For Android **7+**, pick an `apt-android-7` build:

| File fragment | Use when |
|---|---|
| `arm64-v8a` | Most modern phones |
| `armeabi-v7a` | Older 32-bit ARM |
| `x86_64` / `x86` | Emulators |
| `universal` | One APK for all ABIs (larger) |

Ignore `apt-android-5` unless you are on Android 5/6 (no package updates).

## Signing note

CI attaches **debug** APKs signed with a **public test key**. Anyone can forge
updates with that key — only install from
[`involvex/termux-app`](https://github.com/involvex/termux-app) if you trust that
source.

Before switching from another Termux / InVxTermux build:

1. Back up if needed ([Termux wiki: Backing up](https://wiki.termux.com/wiki/Backing_up_Termux))
2. Uninstall **all** related apps that share the same `sharedUserId` / signature set
3. Install the new APK

## Not on F-Droid / Play

This fork is **not** published on F-Droid or Google Play. Official Termux from
other stores is a different package id and will not share data with InVxTermux.

## Build from source

JDK 17, Android SDK (compile 36), NDK `29.0.14206865`:

```bash
./gradlew assembleDebug
```

See [README](https://github.com/involvex/termux-app#building) and `AGENTS.md` in
the repo.
