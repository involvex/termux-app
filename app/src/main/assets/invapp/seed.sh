#!/system/bin/sh
# InVxTermux container seed.
#
# Runs once, inside the termux-docker `bootstrap` build stage, as the `system`
# user (uid 1000) -- the same user the interactive shell runs as. It layers the
# fork's tooling onto a stock termux-docker rootfs.
#
# Note: a termux-docker image has no /bin at all (FROM scratch + $PREFIX/bin +
# /system/bin from aosp-utils), so a `#!/bin/sh` shebang would fail with
# "bad interpreter". The Dockerfile invokes this through $PREFIX/bin/bash; this
# shebang is the fallback.
#
# Invoked from Dockerfile:
#   if [ -f "$TERMUX__PREFIX/tmp/invapp-seed/seed.sh" ]; then ... fi
# which is a no-op when termux-docker was generated without --invapp.
#
# Everything here is driven by the tree next to this script:
#   packages.list packages-extra.list env.sh bin/ libexec/ config/ shortcuts/
#
# Knobs (build args -> env):
#   INVAPP_BUN_VERSION  bun release to install (default matches the APK bundle)
#   INVAPP_BUN_BASELINE set to 1 for CPUs without AVX2
#   INVAPP_WITH_AI      1 = also bake opencode + cursor agent into the image

set -eu

SEED_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=/dev/null
. "$SEED_DIR/env.sh"

INVAPP_BUN_VERSION="${INVAPP_BUN_VERSION:-1.4.2}"
INVAPP_WITH_AI="${INVAPP_WITH_AI:-0}"

log()  { printf '[invapp] %s\n' "$*"; }
warn() { printf '[invapp] WARN: %s\n' "$*" >&2; }
die()  { printf '[invapp] ERROR: %s\n' "$*" >&2; exit 1; }

[ -d "$PREFIX/bin" ] || die "no \$PREFIX/bin at $PREFIX — not a Termux rootfs"

# termux-docker's Dockerfile sources this before touching the package manager;
# pkg/apt need TERMUX_APP_PACKAGE_MANAGER (and friends) to resolve mirrors.
if [ -f "$PREFIX/bin/termux-setup-package-manager" ]; then
    # shellcheck source=/dev/null
    . "$PREFIX/bin/termux-setup-package-manager" || die "termux-setup-package-manager failed"
fi

mkdir -p "$PREFIX/lib/invapp" "$PREFIX/libexec" "$HOME/.termux" "$HOME/.shortcuts" \
         "$HOME/repos" "$HOME/.cache" "$PREFIX/var/lib/invapp"

# ---------------------------------------------------------------------------
# 1. packages
# ---------------------------------------------------------------------------
read_list() {
    # prints non-comment, non-empty lines of $1
    [ -f "$1" ] || return 0
    sed -e 's/#.*$//' -e 's/[[:space:]]//g' "$1" | grep -v '^$' || true
}

install_packages() {
    list=$1
    set --
    for p in $(read_list "$list"); do
        set -- "$@" "$p"
    done
    [ $# -gt 0 ] || return 0

    log "pkg install ($# packages)"
    if pkg install -y "$@" >/dev/null 2>&1; then
        return 0
    fi

    # Bulk install failed: retry one by one so a package that does not exist
    # for this ABI (arm/i686) does not sink the whole image. dpkg may still be
    # settling from the aborted bulk run, hence the sleep.
    warn "bulk install failed -- retrying per package"
    sleep 3
    missing=''
    for p in "$@"; do
        if ! pkg install -y "$p" >/dev/null 2>&1; then
            warn "not installed: $p"
            missing="$missing $p"
        fi
    done
    if [ -n "$missing" ]; then
        warn "missing packages (harmless unless you need them):$missing"
    fi
    return 0
}

install_packages "$SEED_DIR/packages.list"

# ---------------------------------------------------------------------------
# 2. helpers, config, shortcuts
# ---------------------------------------------------------------------------
BASH_BIN="$PREFIX/bin/bash"
[ -x "$BASH_BIN" ] || BASH_BIN="$PREFIX/bin/sh"

install_tree() {
    # install_tree <src-dir> <dst-dir> <mode>
    src=$1 dst=$2 mode=$3
    [ -d "$src" ] || return 0
    mkdir -p "$dst"
    for f in "$src"/*; do
        [ -e "$f" ] || continue
        name=$(basename "$f")
        [ -f "$f" ] || continue
        # '#!@BASH@' is resolved to this container's shell at install time, so
        # the same file works for any prefix layout.
        sed -e "1s|^#!@BASH@|#!$BASH_BIN|" "$f" > "$dst/$name"
        chmod "$mode" "$dst/$name"
    done
}

install_tree "$SEED_DIR/bin"       "$PREFIX/bin"     700
install_tree "$SEED_DIR/libexec"  "$PREFIX/libexec" 700
install_tree "$SEED_DIR/config/bash_completion.d" "$PREFIX/etc/bash_completion.d" 644

# td-install-extras reads this list from inside the image.
if [ -f "$SEED_DIR/packages-extra.list" ]; then
    mkdir -p "$PREFIX/etc/invapp"
    cp -f "$SEED_DIR/packages-extra.list" "$PREFIX/etc/invapp/packages-extra.list"
    chmod 644 "$PREFIX/etc/invapp/packages-extra.list"
fi

if [ -f "$SEED_DIR/config/termux.properties" ]; then
    cp -f "$SEED_DIR/config/termux.properties" "$HOME/.termux/termux.properties"
fi
if [ -f "$SEED_DIR/config/colors.properties" ]; then
    cp -f "$SEED_DIR/config/colors.properties" "$HOME/.termux/colors.properties"
fi
# profile.d snippet: exports the CA/bun env for every login shell
if [ -d "$SEED_DIR/config/profile.d" ]; then
    mkdir -p "$PREFIX/etc/profile.d"
    for f in "$SEED_DIR/config/profile.d"/*; do
        [ -f "$f" ] || continue
        sed -e "1s|^#!@BASH@|#!$BASH_BIN|" "$f" > "$PREFIX/etc/profile.d/$(basename "$f")"
        chmod 644 "$PREFIX/etc/profile.d/$(basename "$f")"
    done
fi
install_tree "$SEED_DIR/shortcuts" "$HOME/.shortcuts" 700
# ~/.shortcuts must stay private: it holds scripts, not data.
chmod 700 "$HOME/.shortcuts" 2>/dev/null || true

# Seeded library shared by every helper (env + arch capability table).
if [ -f "$SEED_DIR/env.sh" ]; then
    sed -e "1s|^#!@BASH@|#!$BASH_BIN|" "$SEED_DIR/env.sh" > "$PREFIX/lib/invapp/env.sh"
    chmod 644 "$PREFIX/lib/invapp/env.sh"
fi
chmod 700 "$PREFIX/libexec"/* 2>/dev/null || true

# ---------------------------------------------------------------------------
# 3. bun
#
# The APK-bundled *android* builds on purpose. A Termux container is still
# bionic (the rootfs links /system -> $PREFIX/opt/aosp), so the plain
# bun-linux-<arch>.zip glibc build dies with "required file not found": its ELF
# interpreter is /lib64/ld-linux-x86-64.so.2, which does not exist here. Same
# reason app/build.gradle says "Official Bionic Android builds only".
#
# Pinned digests mirror app/build.gradle downloadBunBootstraps() so both sides
# ship a byte-identical bun.
# ---------------------------------------------------------------------------
install_bun() {
    if ! td_supports_ai_abi; then
        warn "$(uname -m): bun publishes x64/aarch64 only -- base image, node fallback"
        printf 'unsupported\n' > "$PREFIX/var/lib/invapp/bun.version"
        return 0
    fi

    # sha256 of the APK's bundles (bun 1.4.2)
    case "$(td_arch)" in
        aarch64) asset=bun-linux-aarch64-android.zip; pinned=a1c7e2983f1bb65146beb256a4d72449f23042412bc2cf278aa6397ba27e0274 ;;
        x64)     asset=bun-linux-x64-android.zip;  pinned=1e90fc0d3b83cb847b34338aff2765a7034bf53c50414344b3b0bf07a330a144 ;;
    esac
    if [ "${INVAPP_BUN_BASELINE:-0}" = 1 ]; then
        asset=$(printf '%s' "$asset" | sed -e 's/\.zip$/-baseline.zip/')
        pinned="${INVAPP_BUN_SHA256:-}"
    fi
    pinned="${INVAPP_BUN_SHA256:-$pinned}"

    url="https://github.com/oven-sh/bun/releases/download/bun-v$INVAPP_BUN_VERSION/$asset"
    zip="$TMPDIR/$asset"
    log "bun v$INVAPP_BUN_VERSION ($asset)"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 3 --retry-delay 2 -o "$zip" "$url" \
            || die "bun download failed: $url"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$zip" "$url" || die "bun download failed: $url"
    else
        die "need curl or wget"
    fi

    got=$(sha256sum "$zip" | awk '{print $1}')
    if [ -n "$pinned" ]; then
        [ "$got" = "$pinned" ] || { rm -f "$zip"; die "bun checksum mismatch for $asset (want=$pinned got=$got)"; }
        log "bun sha256 verified (pinned, matches the APK bundle)"
    else
        # Cross-check against the release manifest when no pin applies
        # (baseline builds, custom versions).
        sums="https://github.com/oven-sh/bun/releases/download/bun-v$INVAPP_BUN_VERSION/SHASUMS256.txt"
        if command -v curl >/dev/null 2>&1 && curl -fsSL --max-time 30 -o "$TMPDIR/SHASUMS256.txt" "$sums" 2>/dev/null; then
            want=$(grep " $asset\$" "$TMPDIR/SHASUMS256.txt" | awk '{print $1}' | head -1)
            if [ -z "$want" ] || [ "$want" != "$got" ]; then
                rm -f "$zip"
                die "bun checksum mismatch for $asset (want=$want got=$got)"
            fi
            log "bun sha256 verified (release manifest)"
        else
            warn "no pinned digest and no SHASUMS256.txt -- checksum NOT verified"
        fi
    fi

    rm -rf "$PREFIX/libexec/bun.new"
    mkdir -p "$PREFIX/libexec/bun.new"
    unzip -q "$zip" -d "$PREFIX/libexec/bun.new" || die "unzip failed for $asset"
    rm -f "$zip" "$TMPDIR/SHASUMS256.txt"
    # The archive holds bun-linux-<arch>/bun: flatten it to the single file the
    # wrapper expects, otherwise $PREFIX/libexec/bun ends up a directory.
    found=$(find "$PREFIX/libexec/bun.new" -type f -name bun | head -1)
    [ -n "$found" ] || die "no bun binary in $asset"
    chmod 755 "$found"
    rm -rf "$PREFIX/libexec/bun"
    mv "$found" "$PREFIX/libexec/bun"
    rm -rf "$PREFIX/libexec/bun.new"
    printf '%s\n' "$INVAPP_BUN_VERSION" > "$PREFIX/var/lib/invapp/bun.version"

    # Prove it actually runs -- an unusable bun would otherwise only surface on
    # the first `bun` call inside a container.
    if ver=$("$PREFIX/libexec/bun" --version 2>/dev/null) && [ -n "$ver" ]; then
        log "bun $ver installed -> $PREFIX/libexec/bun"
    else
        warn "bun was installed but does not run here."
        warn "  If it is 'Bad system call' / unknown syscall, the host seccomp"
        warn "  profile rejects openat2: start the container with"
        warn "  --security-opt seccomp=unconfined (bun-doctor prints this too)."
    fi
}

install_bun

# ---------------------------------------------------------------------------
# 4. optional AI CLIs (same installers as the app, minus the Android-only shims)
# ---------------------------------------------------------------------------
if [ "$INVAPP_WITH_AI" = 1 ]; then
    if [ -x "$PREFIX/bin/opencode-setup" ]; then
        log "INVAPP_WITH_AI=1 -- installing opencode"
        "$PREFIX/bin/opencode-setup" || warn "opencode-setup failed (image still usable)"
    fi
    if [ -x "$PREFIX/bin/cursor-agent-setup" ]; then
        log "INVAPP_WITH_AI=1 -- installing cursor agent"
        "$PREFIX/bin/cursor-agent-setup" || warn "cursor-agent-setup failed"
    fi
fi

# ---------------------------------------------------------------------------
# 5. cleanup
# ---------------------------------------------------------------------------
rm -rf "$SEED_DIR"
rm -rf "$PREFIX/var/lib/apt/lists/"* \
       "$PREFIX/var/log/apt/"* \
       "$PREFIX/var/cache/apt/archives/"*.deb 2>/dev/null || true

log "done -- run 'td-info' inside the container for a capability report"
