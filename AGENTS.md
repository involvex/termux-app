# Termux App Agents Guide

This guide provides instructions for AI agents working on the Termux application development project. It covers workflows, tools, technologies, and best practices.

## Project Overview

**Termux** is an Android terminal application and Linux environment. This repository (`termux-app`) contains the app itself (UI and terminal emulation). The packages installed inside the app are managed in the separate [`termux-packages`](https://github.com/termux/termux-packages) repository.

### Key Components

The project is organized as follows:

| Module | Description |
|--------|-------------|
| `app` | Core Termux application with terminal emulation |
| `termux-shared` | Shared constants and utilities library for app and plugins |
| `terminal-view` | Terminal view widget for rendering |
| `terminal-emulator` | Native terminal emulator library |
| `termux-api` | Termux:API plugin integration |

### Architecture

- **Target SDKs**: Android 7+ (primary), Android 5-6 support (deprecated)
- **Java Version**: Java 8 (source compatibility)
- **NDK**: Native code using Android NDK for terminal emulation
- **Dependencies**: Google Material Components, Lifecycle, ViewPager, Guava, Markwon (markdown)

## This fork (`com.involvex.termux_app`)

This tree is a renamed Termux fork for **Terminal Dev**: develop on PC, continue on phone.

### Runtime layout

| Piece | Path / behavior |
|-------|-----------------|
| Package id | `com.involvex.termux_app` (`sharedUserId` still `com.invapp`) |
| Path redirector | `LD_PRELOAD=$PREFIX/lib/libinvapp-redirector.so` for apt/SSH/node hardcoded `com.termux` paths |
| Bun real binary | `$PREFIX/libexec/bun` (official `bun-linux-*-android.zip`, currently **1.4.2**) |
| Bun wrapper | `$PREFIX/bin/bun` → `LD_PRELOAD= exec` real binary + OPENSSL + `npm_config_platform=android` |
| bunx | `$PREFIX/bin/bunx` → `bun x` via the wrapper (no per-package mapping) |
| Default cwd | `~/repos` (exec-capable). `~/storage/shared` is browse/sync only (**noexec**) |
| Preview | Drawer **Preview** → `LocalhostPreviewActivity` for `http://127.0.0.1:<port>` |

### Android / Bun pitfalls (do not “fix” with more wrappers)

- **`Unknown signal 31` (SIGSYS)** — usually glibc/Linux Bun, `LD_PRELOAD` + Bun, or optional `linux-*` native bindings (e.g. `@rolldown/binding-linux-arm-gnueabihf`). Use the bundled Android Bun; never `curl … bun.sh/install \| bash`. The `bin/bun` shim injects `--os=android --cpu=arm64` on `install`/`add`/`create`. Windows lockfiles can still pin linux natives — delete `node_modules` + lock and reinstall on phone.
- **`ls` folder names as solid green bars** — hacker theme v1 remapped ANSI blue→green (same as fg). Fixed in theme v2 (cyan/blue slots). New session after upgrade; or replace `~/.termux/colors.properties`.
- **`Permission denied` on `tsc` / package bins** — project is under shared storage (`/storage/emulated/0`). Keep runnable projects in `~/repos`.
- **OpenSSL / node looking at `com.termux`** — shell sets `OPENSSL_CONF` / `SSL_CERT_FILE` to this prefix; redirector covers other hardcoded paths for non-Bun tools.

### Bun build notes

- Gradle task `downloadBunBootstraps` fetches Android zips into `app/src/main/cpp/`.
- NDK module `libinvapp-bun` embeds the zip via `.incbin`; `TermuxBunInstaller` extracts on app start / bootstrap.
- Prefer `./gradlew :app:assembleDebug` after changing Bun version or `bun-bootstrap*`.

### Mobile ↔ PC workflow

```bash
# On phone (new session starts in ~/repos)
cd ~/repos
git clone <repo> && cd <repo>
bun install
bun run dev          # or bun run android / build
# Drawer → Preview → port 3000 / 5000 / 8081
```

On PC: same git remote — push/pull; no special Termux package sync required.

See [ROADMAP.md](ROADMAP.md) for Preview / AI CLI phases.

## Useful Commands

### Git Operations

```bash
# View recent commits
git log --oneline -10

# View current status
git status

# Create feature branch
git checkout -b feature/description

# Commit changes (following conventional commits)
git commit -m "Fixed(something): Fix the issue"

# Push to remote
git push origin feature/description

# Create pull request
gh pr create --title "Description" --body "Details"
```

### Android/Gradle Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean

# Run with specific variant
./gradlew assembleDebug -PpackageVariant=apt-android-7

# Get current version name
./gradlew versionName
```

### GitHub Actions

The project uses GitHub Actions for CI/CD:
- **debug_build.yml**: Builds debug APKs for all architectures
- **run_tests.yml**: Runs unit tests on PRs
- **attach_debug_apks_to_release.yml**: Attaches debug APKs to releases

## Technologies

### Android Development

- **Android SDK**: API level 24+ (minSdkVersion), targetSdk 35 (as of latest)
- **Build Tools**: Android Gradle Plugin 9.4.0
- **Native Development**: C/C++ via NDK, using ndk-build

### Dependencies

**Core Libraries**:
- AndroidX AppCompat 1.6.1
- AndroidX Core 1.13.1
- AndroidX Preference 1.2.1
- Google Material Components 1.12.0
- Guava 24.1-jre

**Terminal Related**:
- Termux AM Library (Android IPC)
- Markwon 4.6.3 (markdown rendering)

**System Services**:
- Lsposed Hidden API Bypass (for Android 10+)

### Project Structure

```
termux-app/
├── .github/               # GitHub workflows, issue templates
│   ├── ISSUE_TEMPLATE/    # Bug report and feature request templates
│   └── workflows/         # CI/CD workflows
├── app/                   # Main application module
│   └── src/main/java/
│       └── com/invapp/app/
├── termux-shared/         # Shared utilities library
├── termux-api/            # Termux:API plugin
├── terminal-view/         # Terminal view component
├── terminal-emulator/     # Native terminal emulator
├── fastlane/              # Play Store deployment configuration
├── build.gradle           # Project-level Gradle config
├── settings.gradle        # Module declarations
└── README.md              # Project documentation
```

## Best Practices and Guidelines

### Commit Message Convention

All commits **must** follow [Conventional Commits](https://www.conventionalcommits.org) spec and [Keep a Changelog](https://github.com/olivierlacan/keep-a-changelog) format:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Allowed Types** (must match changelog headings exactly):
- **Added** - for new features
- **Changed** - for changes in existing functionality
- **Deprecated** - for soon-to-be removed features
- **Removed** - for removed features
- **Fixed** - for bug fixes
- **Security** - for vulnerability fixes

**Examples**:
```
Added: Add new terminal theme support
Fixed(terminal): Fix cursor positioning bug
Changed!: Change API for backward compatibility (breaking)
```

### Version Management

- Version format must follow [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html)
- Format: `major.minor.patch(-prerelease)(+buildmetadata)`
- Example: `0.118.0`, `0.119.0-beta.1`

### Code Quality

#### Code Style
- Java 8 source compatibility
- Use nullable annotations from AndroidX
- Follow Android naming conventions

#### Lint and Analysis
```bash
# Run lint checks
./gradlew lint

# Analyze with Android Studio
# Use "Code Cleanup" and "Inspect Code" features

# Format code
# Use Android Studio's built-in formatter
```

### Testing

- Unit tests are required for new functionality
- Tests run via `./gradlew test`
- Test framework: JUnit 4.13.2 + Robolectric 4.10
- Located in `src/test/java/` directories

### Security Considerations

**Important Security Notes**:
1. APK files on GitHub are signed with a **test key** (`testkey_untrusted.jks`)
   - This key is shared with the community and NOT an official release key
   - Malicious actors can forge APKs with this key
   - Only use builds from official sources

2. Dependencies:
   - Regularly update dependencies via Dependabot
   - Review security advisories for all dependencies

3. Code Review Requirements:
   - All PRs must be reviewed before merging
   - Pay special attention to:
     - Hardcoded paths or values
     - Native code changes
     - Security-sensitive features

### Plugins and Package Variants

Termux has multiple plugin apps that share the `com.invapp` package signature:
- Termux:API
- Termux:Boot
- Termux:Float
- Termux:Styling
- Termux:Tasker
- Termux:Widget

**Important**: All plugins must be installed from the same source to work together due to shared `sharedUserId`.

### Build Variants

Two bootstrap variants are supported:
- `apt-android-7` - For Android 7+ (recommended)
- `apt-android-5` - For Android 5/6 (deprecated)

To build with a specific variant:
```bash
./gradlew assembleDebug -DTERMUX_PACKAGE_VARIANT=apt-android-7
```

## Development Workflow

### Setting Up Development Environment

1. **Install Requirements**:
   - Android Studio Flamingo or later
   - JDK 17
   - Android SDK API 24+

2. **Clone Repository**:
   ```bash
   git clone https://github.com/termux/termux-app.git
   cd termux-app
   ```

3. **Import Project**:
   - Open in Android Studio
   - Let Gradle sync complete

4. **Build Verification**:
   ```bash
   ./gradlew assembleDebug
   ./gradlew test
   ```

### Creating a Pull Request

1. Create a feature branch from `master`
2. Implement changes following the code style
3. Write/update tests as needed
4. Run tests: `./gradlew test`
5. Format code using Android Studio
6. Commit with conventional commit message
7. Push branch and create PR on GitHub
8. Add reviewers and address feedback

### Debugging

1. **Enable Debug Logging**:
   - Go to app settings → `<APP_NAME>` → `Debugging` → `Log Level`
   - Set to `Verbose` for detailed logs

2. **View Logs**:
   ```bash
   # In Termux app
   logcat
   
   # Save to file
   logcat -d > logcat.txt
   ```

3. **Report Issues**:
   - Use the "Report Issue" menu option in the app
   - Include full description and logs
   - Select correct repository (app vs packages)

## Release Process

### GitHub Releases

1. Create a new release from GitHub releases
2. Use semantic version tag (e.g., `v0.118.0`)
3. Attach debug APKs from CI artifacts
4. Update changelog following Keep a Changelog format

### Play Store (Experimental)

The Play Store build:
- Is in a separate repository: `termux-play-store`
- Has different package name: `com.involvex.termux_app`
- Has limited functionality due to policy requirements

### Publishing Artifacts

Debug APKs are automatically attached by `attach_debug_apks_to_release.yml` workflow.
Architecture-specific APKs are built for:
- `universal` (all architectures)
- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`
- `x86`

## Troubleshooting Common Issues

### Build Failures

1. **Wrong Version Format**:
   - Ensure `versionName` in `app/build.gradle` follows semver
   - Check version numbers in all modules match

2. **Missing Dependencies**:
   ```bash
   ./gradlew clean
   ./gradlew --refresh-dependencies
   ```

3. **NDK Issues**:
   - Check `build.gradle` has correct NDK version
   - Ensure `Android.mk` is properly configured

### Runtime Issues

1. **Bootstrap Missing**:
   - Downloaded from `termux-packages` releases
   - Checksum verified automatically during build

2. **Permission Denied**:
   - Check app has storage permissions
   - Verify `$PREFIX` directory is accessible

3. **Package Not Found**:
   - Ensure using correct package source
   - Check network connectivity

## Useful Links

- [Termux Wiki](https://wiki.termux.com/wiki/)
- [Termux Packages Repo](https://github.com/termux/termux-packages)
- [Termux API](https://github.com/termux/termux-api)
- [Build Instructions](https://github.com/termux/termux-app/wiki)
- [Termux Community](https://reddit.com/r/termux)

## Code Review Checklist

When reviewing code, check for:

- [ ] Follows conventional commit format
- [ ] Has appropriate tests
- [ ] Handles errors gracefully
- [ ] No hardcoded paths/values (must use `termux-shared`)
- [ ] Proper nullability annotations
- [ ] No security vulnerabilities
- [ ] Backward compatible (or intentional breaking change marked with `!`)
- [ ] Documentation updated if needed

## Additional Resources

- [LICENSE.md](LICENSE.md) - Project license (GPLv3 with exceptions)
- [SECURITY.md](SECURITY.md) - Security policies and vulnerability reporting
- [termux-shared/LICENSE.md](termux-shared/LICENSE.md) - termux-shared library license