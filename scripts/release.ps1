<#
.SYNOPSIS
  Automate an InVxTermux release: version bump, changelog commit, tag, push, GitHub release.

.DESCRIPTION
  Updates versionName / versionCode in app/build.gradle, syncs library publish
  versions (termux-shared / terminal-emulator / terminal-view), README / docs /
  AGENTS.md, commits with a Conventional Commit message, creates annotated tag
  vX.Y.Z, pushes, and publishes a GitHub release.

  Debug APKs are attached by CI (.github/workflows/attach_debug_apks_to_release.yml)
  after the release is published. Pass -WaitForApks to poll until assets land,
  or -LocalBuild to assemble + upload from this machine (slow).

.PARAMETER Version
  Explicit semver versionName (e.g. 0.207.0). Mutually exclusive with -Bump.

.PARAMETER Bump
  Compute next version from app/build.gradle: patch | minor | major.

.PARAMETER VersionCode
  Explicit versionCode. Default: current + 1.

.PARAMETER DryRun
  Print planned actions; change no files and run no git/gh writes.

.PARAMETER NoPush
  Commit + tag locally; do not push or create the GitHub release.

.PARAMETER NoRelease
  Push commit + tag only; skip gh release create.

.PARAMETER WaitForApks
  After publishing, poll until release assets appear (minutes, 0 = skip).

.PARAMETER LocalBuild
  Run assembleDebug and upload APKs with gh (instead of waiting for CI).

.PARAMETER NotesFile
  Markdown file used as the release body. Default: auto from commits since last tag.

.PARAMETER Yes
  Skip the confirmation prompt.

.EXAMPLE
  .\scripts\release.ps1 -Bump patch -Yes
.EXAMPLE
  .\scripts\release.ps1 -Version 0.206.0 -Yes -WaitForApks 45
.EXAMPLE
  .\scripts\release.ps1 -Bump minor -DryRun
#>
[CmdletBinding(DefaultParameterSetName = 'Bump')]
param(
    [Parameter(ParameterSetName = 'Explicit')]
    [ValidatePattern('^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$')]
    [string] $Version,

    [Parameter(ParameterSetName = 'Bump')]
    [ValidateSet('patch', 'minor', 'major')]
    [string] $Bump = 'patch',

    [int] $VersionCode = -1,

    [switch] $DryRun,
    [switch] $NoPush,
    [switch] $NoRelease,
    [int] $WaitForApks = 0,
    [switch] $LocalBuild,
    [string] $NotesFile = '',
    [switch] $Yes
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Step([string] $Message) {
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Warn([string] $Message) {
    Write-Host "WARN: $Message" -ForegroundColor Yellow
}

function Get-RepoRoot {
    $root = git rev-parse --show-toplevel 2>$null
    if (-not $root) {
        throw 'Not inside a git repository.'
    }
    return $root.Trim()
}

function Read-AppVersion([string] $AppGradle) {
    $text = Get-Content -LiteralPath $AppGradle -Raw
    if ($text -notmatch 'versionCode\s+(\d+)') {
        throw "Could not parse versionCode in $AppGradle"
    }
    $code = [int]$Matches[1]
    if ($text -notmatch 'versionName\s+"([^"]+)"') {
        throw "Could not parse versionName in $AppGradle"
    }
    $name = $Matches[1]
    return [pscustomobject]@{ VersionCode = $code; VersionName = $name }
}

function Split-SemVer([string] $Name) {
    if ($Name -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d+)(.*)$') {
        throw "Not a semver versionName: $Name"
    }
    return [pscustomobject]@{
        Major = [int]$Matches[1]
        Minor = [int]$Matches[2]
        Patch = [int]$Matches[3]
        Suffix = $Matches[4]
    }
}

function Get-NextVersion([string] $Current, [string] $Kind) {
    $v = Split-SemVer $Current
    switch ($Kind) {
        'major' { return "$($v.Major + 1).0.0" }
        'minor' { return "$($v.Major).$($v.Minor + 1).0" }
        'patch' { return "$($v.Major).$($v.Minor).$($v.Patch + 1)" }
    }
}

function Write-Utf8NoBom([string] $Path, [string] $Text) {
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $Text, $utf8)
}

function Set-FileText([string] $Path, [string] $Old, [string] $New, [switch] $Regex) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing file: $Path"
    }
    $text = Get-Content -LiteralPath $Path -Raw
    $updated = if ($Regex) {
        [regex]::Replace($text, $Old, $New)
    } else {
        if ($text -notlike "*$Old*") {
            throw "Pattern not found in ${Path}: $Old"
        }
        $text.Replace($Old, $New)
    }
    if ($text -eq $updated) {
        throw "No change applied in $Path"
    }
    if (-not $DryRun) {
        Write-Utf8NoBom $Path $updated
    }
    Write-Host "  updated $Path"
}

function Update-Versions(
    [string] $Root,
    [string] $OldName,
    [string] $NewName,
    [int] $OldCode,
    [int] $NewCode
) {
    Write-Step "Bump $OldName ($OldCode) -> $NewName ($NewCode)"

    $appGradle = Join-Path $Root 'app/build.gradle'
    $appText = Get-Content -LiteralPath $appGradle -Raw
    $appUpdated = [regex]::Replace(
        $appText,
        "versionCode\s+$OldCode(\r?\n\s*)versionName\s+`"$([regex]::Escape($OldName))`"",
        "versionCode $NewCode`$1versionName `"$NewName`""
    )
    if ($appUpdated -eq $appText) {
        throw "Failed to update versionCode/versionName in $appGradle"
    }
    if (-not $DryRun) { Write-Utf8NoBom $appGradle $appUpdated }
    Write-Host "  updated $appGradle"

    foreach ($mod in @(
            'termux-shared/build.gradle',
            'terminal-emulator/build.gradle',
            'terminal-view/build.gradle'
        )) {
        $path = Join-Path $Root $mod
        Set-FileText $path "version = '$OldName'" "version = '$NewName'"
    }

    Set-FileText (Join-Path $Root 'README.md') `
        "Latest: **v$OldName**" `
        "Latest: **v$NewName**"

    Set-FileText (Join-Path $Root 'docs/index.md') `
        "| Latest | **v$OldName** |" `
        "| Latest | **v$NewName** |"

    $agents = Join-Path $Root 'AGENTS.md'
    $agentsText = Get-Content -LiteralPath $agents -Raw
    # Table cell: `versionCode 207`, `versionName 0.206.0`
    $agentsUpdated = [regex]::Replace(
        $agentsText,
        "``versionCode\s+\d+``, ``versionName\s+$([regex]::Escape($OldName))``",
        "``versionCode $NewCode``, ``versionName $NewName``"
    )
    $agentsUpdated = [regex]::Replace(
        $agentsUpdated,
        "versions\s+\(``$([regex]::Escape($OldName))``\)",
        "versions (``$NewName``)"
    )
    if ($agentsText -eq $agentsUpdated) {
        Write-Warn "AGENTS.md version strings not updated (pattern drift) — edit manually if needed."
    } elseif (-not $DryRun) {
        Write-Utf8NoBom $agents $agentsUpdated
        Write-Host "  updated $agents"
    } else {
        Write-Host "  would update $agents"
    }
}

function Get-LastVersionTag {
    $tag = git describe --tags --abbrev=0 --match 'v*' 2>$null
    if (-not $tag) {
        return $null
    }
    return $tag.Trim()
}

function New-ChangelogFromCommits([string] $SinceTag, [string] $OldName, [string] $NewName, [int] $NewCode) {
    $range = if ($SinceTag) { "$SinceTag..HEAD" } else { 'HEAD' }
    $raw = git --no-pager log $range --pretty=format:'%s' 2>$null
    $lines = @()
    if ($raw) {
        $lines = @($raw -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    }

    $buckets = [ordered]@{
        Added      = New-Object System.Collections.Generic.List[string]
        Changed    = New-Object System.Collections.Generic.List[string]
        Deprecated = New-Object System.Collections.Generic.List[string]
        Removed    = New-Object System.Collections.Generic.List[string]
        Fixed      = New-Object System.Collections.Generic.List[string]
        Security   = New-Object System.Collections.Generic.List[string]
        Docs       = New-Object System.Collections.Generic.List[string]
        Other      = New-Object System.Collections.Generic.List[string]
    }

    $typeRe = '^(Added|Changed|Deprecated|Removed|Fixed|Security)(?:\|(?:Added|Changed|Deprecated|Removed|Fixed|Security))*(?:!\([^)]+\))?(!)?(?:\([^)]+\))?:\s+(.+)$'

    foreach ($subject in $lines) {
        # Skip pure version-bump commits only (not feature commits that mention a bump).
        if ($subject -match '^(Changed|Added\|Changed):\s+Bump version to\s') {
            continue
        }
        if ($subject -match $typeRe) {
            $type = $Matches[1]
            $desc = $Matches[3].Trim()
            if ($subject -match 'Docs?[:\(/]' -or $desc -match '^(Docs|ROADMAP|documentation)') {
                [void]$buckets.Docs.Add($desc)
            } else {
                [void]$buckets[$type].Add($desc)
            }
        } else {
            [void]$buckets.Other.Add($subject)
        }
    }

    $sb = New-Object System.Text.StringBuilder
    if ($OldName -ne $NewName -or $buckets.Changed.Count -gt 0) {
        [void]$sb.AppendLine('## Changed')
        if ($OldName -ne $NewName) {
            [void]$sb.AppendLine("- Version **$OldName -> $NewName** (``versionCode`` $NewCode)")
        }
        foreach ($item in $buckets.Changed) {
            [void]$sb.AppendLine("- $item")
        }
        [void]$sb.AppendLine()
    }

    foreach ($key in @('Added', 'Fixed', 'Security', 'Deprecated', 'Removed', 'Docs', 'Other')) {
        $items = $buckets[$key]
        if ($items.Count -eq 0) { continue }
        $heading = if ($key -eq 'Other') { 'Other' } else { $key }
        [void]$sb.AppendLine("## $heading")
        foreach ($item in $items) {
            [void]$sb.AppendLine("- $item")
        }
        [void]$sb.AppendLine()
    }

    [void]$sb.AppendLine('Debug APKs attach automatically via CI after publish.')
    return $sb.ToString().TrimEnd() + "`n"
}

function Assert-CleanTree {
    $dirty = git status --porcelain
    if ($dirty) {
        throw "Working tree is not clean:`n$dirty"
    }
}

function Assert-Tools {
    foreach ($cmd in @('git', 'gh')) {
        if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
            throw "Required tool not found on PATH: $cmd"
        }
    }
    gh auth status 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'gh is not authenticated. Run: gh auth login'
    }
}

# --- main --------------------------------------------------------------------

$root = Get-RepoRoot
Set-Location $root
Assert-Tools

$appGradle = Join-Path $root 'app/build.gradle'
$current = Read-AppVersion $appGradle
$oldName = $current.VersionName
$oldCode = $current.VersionCode

if ($PSCmdlet.ParameterSetName -eq 'Explicit') {
    $newName = $Version
} else {
    $newName = Get-NextVersion $oldName $Bump
}

if ($VersionCode -lt 0) {
    if ($newName -eq $oldName) {
        $newCode = $oldCode
    } else {
        $newCode = $oldCode + 1
    }
} else {
    $newCode = $VersionCode
}

$tag = "v$newName"
$lastTag = Get-LastVersionTag

Write-Step "Release plan"
Write-Host "  repo:         $root"
Write-Host "  from:         $oldName (code $oldCode)"
Write-Host "  to:           $newName (code $newCode)"
Write-Host "  tag:          $tag"
Write-Host "  previous tag: $(if ($lastTag) { $lastTag } else { '(none)' })"
Write-Host "  dry-run:      $DryRun"
Write-Host "  push:         $(-not $NoPush)"
Write-Host "  gh release:   $(-not $NoPush -and -not $NoRelease)"

if (-not $Yes -and -not $DryRun) {
    $answer = Read-Host "Continue? [y/N]"
    if ($answer -notmatch '^[Yy]') {
        Write-Host 'Aborted.'
        exit 1
    }
}

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branch -ne 'master' -and $branch -ne 'main') {
    Write-Warn "Current branch is '$branch' (expected master/main)."
}

if (-not $DryRun) {
    Assert-CleanTree
}

$versionAlready = ($newName -eq $oldName -and $newCode -eq $oldCode)
if (-not $versionAlready) {
    Update-Versions -Root $root -OldName $oldName -NewName $newName -OldCode $oldCode -NewCode $newCode
} else {
    Write-Step "Version already at $newName ($newCode) — skipping file edits"
}

if ($NotesFile -and (Test-Path -LiteralPath $NotesFile)) {
    $notes = Get-Content -LiteralPath $NotesFile -Raw
} else {
    $notes = New-ChangelogFromCommits -SinceTag $lastTag -OldName $oldName -NewName $newName -NewCode $newCode
}

$notesPath = Join-Path $env:TEMP "invx-release-$newName.md"
if (-not $DryRun) {
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($notesPath, $notes, $utf8)
}
Write-Step 'Release notes'
Write-Host $notes

$commitMsg = "Changed: Bump version to $newName"
if (-not $versionAlready) {
    Write-Step "Commit: $commitMsg"
    if (-not $DryRun) {
        git add -- `
            app/build.gradle `
            termux-shared/build.gradle `
            terminal-emulator/build.gradle `
            terminal-view/build.gradle `
            README.md `
            docs/index.md `
            AGENTS.md
        git commit -m $commitMsg
        if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
    }
} else {
    Write-Step 'No version-bump commit (already at target)'
}

Write-Step "Tag $tag"
if (-not $DryRun) {
    $existing = git rev-parse -q --verify "refs/tags/$tag" 2>$null
    if ($existing) {
        throw "Tag already exists: $tag"
    }
    git tag -a $tag -m "InVxTermux $tag"
    if ($LASTEXITCODE -ne 0) { throw 'git tag failed' }
}

if ($NoPush) {
    Write-Step 'Skipping push / GitHub release (-NoPush)'
    Write-Host "Local tag $tag ready. Notes at: $notesPath"
    exit 0
}

Write-Step 'Push branch + tag'
if (-not $DryRun) {
    git push origin HEAD
    if ($LASTEXITCODE -ne 0) { throw 'git push HEAD failed' }
    git push origin $tag
    if ($LASTEXITCODE -ne 0) { throw 'git push tag failed' }
}

if ($NoRelease) {
    Write-Step 'Skipping GitHub release (-NoRelease)'
    exit 0
}

Write-Step "Create GitHub release $tag"
if (-not $DryRun) {
    gh release create $tag `
        --title "InVxTermux $tag" `
        --notes-file $notesPath `
        --verify-tag
    if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }
    Write-Host "Release URL: $(gh release view $tag --json url -q .url)"
}

# apt-android-7/5 app APKs (5 + sha each) + 3 plugin APKs ≈ 15 assets when CI finishes both matrix legs.
$script:MinReleaseAssets = 12

if ($LocalBuild) {
    Write-Step 'Local assembleDebug + upload (app + plugins; this can take a long time)'
    if (-not $DryRun) {
        $env:TERMUX_PACKAGE_VARIANT = 'apt-android-7'
        $env:TERMUX_APK_VERSION_TAG = "$tag+apt-android-7-github-debug"
        $env:TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS = '1'
        & .\gradlew.bat assembleDebug
        if ($LASTEXITCODE -ne 0) { throw 'assembleDebug failed' }

        $apkDir = Join-Path $root 'app/build/outputs/apk/debug'
        $prefix = "termux-app_$($env:TERMUX_APK_VERSION_TAG)"
        $files = @(
            "$apkDir\${prefix}_universal.apk",
            "$apkDir\${prefix}_arm64-v8a.apk",
            "$apkDir\${prefix}_armeabi-v7a.apk",
            "$apkDir\${prefix}_x86_64.apk",
            "$apkDir\${prefix}_x86.apk"
        )
        foreach ($f in $files) {
            if (-not (Test-Path -LiteralPath $f)) {
                throw "Missing APK: $f"
            }
        }

        function Get-FirstApk([string] $Dir) {
            $hit = Get-ChildItem -LiteralPath $Dir -Filter '*.apk' -ErrorAction SilentlyContinue |
                Select-Object -First 1
            if (-not $hit) { throw "Missing plugin APK under $Dir" }
            return $hit.FullName
        }

        $apiSrc = Get-FirstApk (Join-Path $root 'termux-api/build/outputs/apk/debug')
        $twSrc = Get-FirstApk (Join-Path $root 'termux-terminal-widget/build/outputs/apk/debug')
        $widgetSrc = Get-FirstApk (Join-Path $root 'termux-widget/build/outputs/apk/debug')
        $apiOut = Join-Path $root "termux-api_${tag}-github-debug.apk"
        $twOut = Join-Path $root "termux-terminal-widget_${tag}-github-debug.apk"
        $widgetOut = Join-Path $root "termux-widget_${tag}-github-debug.apk"
        Copy-Item -LiteralPath $apiSrc -Destination $apiOut -Force
        Copy-Item -LiteralPath $twSrc -Destination $twOut -Force
        Copy-Item -LiteralPath $widgetSrc -Destination $widgetOut -Force
        $files += @($apiOut, $twOut, $widgetOut)

        gh release upload $tag @files --clobber
        if ($LASTEXITCODE -ne 0) { throw 'gh release upload failed' }
    }
}
elseif ($WaitForApks -gt 0) {
    Write-Step "Waiting up to $WaitForApks minute(s) for CI to attach APKs (app + plugins)"
    if (-not $DryRun) {
        $deadline = (Get-Date).AddMinutes($WaitForApks)
        do {
            Start-Sleep -Seconds 30
            $count = [int](gh release view $tag --json assets -q '.assets | length')
            Write-Host "  assets: $count  ($(Get-Date -Format HH:mm:ss))"
            if ($count -ge $script:MinReleaseAssets) {
                Write-Host 'APKs attached.'
                break
            }
        } while ((Get-Date) -lt $deadline)
        $final = [int](gh release view $tag --json assets -q '.assets | length')
        if ($final -lt $script:MinReleaseAssets) {
            Write-Warn "Timed out with $final assets (want >= $script:MinReleaseAssets). Check Actions: attach_debug_apks_to_release.yml"
        }
    }
} else {
    Write-Host 'CI will attach debug APKs (app + api/widget/terminal-widget) shortly (attach_debug_apks_to_release.yml).'
    Write-Host 'Re-run with -WaitForApks 45 to poll, or -LocalBuild to upload from this PC.'
}

Write-Step 'Done'
