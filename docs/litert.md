# LiteRT-LM (on-device LLM)

InVxTermux can install [Google AI Edge LiteRT-LM](https://ai.google.dev/edge/litert)
on demand for local inference. Nothing is baked into the APK beyond small GL
stub libraries the engine needs to `dlopen()` on Android.

## Install

```bash
td-resource-litert-lm          # create venv + CLI + native engine
td-resource-litert-lm --reinstall
td-resource-litert-lm run --help
```

Requires `pkg install python python-pip`. The helper:

1. Creates `$HOME/litert-lm.venv` with `--without-pip --system-site-packages`
2. Installs the pure-Python CLI wheel
3. Fetches the Android-tagged `litert-lm-api` wheel from PyPI and unpacks it
4. Writes `$PREFIX/bin/litert-lm` that preloads GL stubs from
   `$PREFIX/lib/litert-lm-gl/`

## GPU / GL stubs

Every Android `litert-lm-api` wheel links `libEGL` / `libGLESv2` / `libGLESv3`.
The system `libGLESv3.so` needs a vendor-only EGL symbol that an app process
cannot resolve, so the engine fails at `dlopen()` even for `--help`.

The APK ships no-op stubs under `assets/litert-lm-gl/<abi>/`;
`TermuxBunInstaller` copies them to `$PREFIX/lib/litert-lm-gl/` on each start.
The CPU backend never calls GL; `--backend=gpu` stays unsupported.

If stubs are missing:

```text
td-resource-litert-lm: GL stubs missing in $PREFIX/lib/litert-lm-gl
```

Swipe the app away and reopen so the installer can reseed them, then re-run
`td-resource-litert-lm`.

## `os.link` on Termux Python

Termux’s Android `python3` has no `os.link`. The installer seeds
`invapp_android_compat.py` + `invapp-android.pth` into the venv so
`litert-lm import` can hardlink models (with a copy fallback).

## Related

- NetHunter / proot guests: [nethunter.md](nethunter.md)
- Troubleshooting matrix: [troubleshooting.md](troubleshooting.md)
