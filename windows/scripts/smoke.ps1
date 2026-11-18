# UniFetcher for Windows release-gate smoke test. Runs on a clean Windows machine (GitHub runner).
# Usage: scripts/smoke.ps1 -Arch x64|arm64 -NewSetup <UniFetcher-...-Setup-<arch>.exe> [-StrictRealSite]
#
# 1. Update test: installs the last release, opens it once (so it saves its settings), then installs
#    the new build over it and checks the settings and data are still there.
# 2. Start-up test: open, close, open again (3 rounds); no crash and no process left behind.
# 3. Download test with the bundled yt-dlp + ffmpeg: a test clip made here and served locally
#    (must download, play and not be re-encoded), and a freely licensed file from Wikimedia Commons
#    (a warning on pushes, an error with -StrictRealSite, used by the daily run).
# 4. Uninstall test: the uninstaller removes the app but leaves the Downloads folder alone.
param(
    [Parameter(Mandatory = $true)][ValidateSet("x64", "arm64")][string]$Arch,
    [Parameter(Mandatory = $true)][string]$NewSetup,
    [switch]$StrictRealSite
)
$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"
$failed = $false
$installRoot = Join-Path $env:LOCALAPPDATA "Programs\UniFetcher"
$exe = Join-Path $installRoot "Release\UniFetcher.exe"
$data = Join-Path $env:LOCALAPPDATA "UniFetcher"
$logs = Join-Path $env:RUNNER_TEMP "smoke-logs"
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function Note($level, $title, $lines) {
    $text = (@($lines) | Select-Object -Last 40) -join "%0A"
    Write-Host "::${level} title=${title}::$text"
}

function Install($setup, $name) {
    $log = Join-Path $logs "install-$name.log"
    $p = Start-Process $setup -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/LOG=$log" -Wait -PassThru
    if ($p.ExitCode -ne 0 -or -not (Test-Path $exe)) {
        Note error "Installer ($name)" ((Get-Content $log -Tail 30 -ErrorAction SilentlyContinue) + "Exit code $($p.ExitCode)")
        return $false
    }
    return $true
}

function Show-Logs {
    Note warning "crash.log" (Get-Content (Join-Path $data "crash.log") -ErrorAction SilentlyContinue)
    Note warning "app.log (errors)" (Get-Content (Join-Path $data "app.log") -ErrorAction SilentlyContinue | Select-String -Pattern "fail|crit|error|exception|   at " | ForEach-Object { $_.Line })
    Get-WinEvent -FilterHashtable @{LogName = 'Application'; StartTime = (Get-Date).AddMinutes(-15) } -ErrorAction SilentlyContinue |
        Where-Object { $_.ProviderName -in 'Application Error', '.NET Runtime', 'Windows Error Reporting' } |
        Select-Object -First 5 | ForEach-Object { Write-Host "----- Windows event: $($_.ProviderName) -----"; Write-Host $_.Message }
}

function Open-Close($round) {
    $p = Start-Process $exe -PassThru
    Start-Sleep -Seconds 25
    if ($p.HasExited) {
        Note error "UniFetcher crashed on start (round $round)" "Exit code $($p.ExitCode). See the log below."
        Show-Logs; return $false
    }
    $null = $p.CloseMainWindow()
    if (-not $p.WaitForExit(30000)) {
        Note error "UniFetcher kept running after closing (round $round)" "Still alive 30 s after its window was closed."
        Show-Logs; $p.Kill($true); return $false
    }
    Start-Sleep -Seconds 2
    if (Get-Process UniFetcher -ErrorAction SilentlyContinue) {
        Note error "UniFetcher left a background process (round $round)" ""
        Get-Process UniFetcher -ErrorAction SilentlyContinue | Stop-Process -Force
        return $false
    }
    Write-Host "Round ${round}: opened and closed properly."
    return $true
}

# ---------- 1. Update test ----------
$last = Join-Path $env:RUNNER_TEMP "last-release"
New-Item -ItemType Directory -Force -Path $last | Out-Null
$tag = gh release list -R $env:GITHUB_REPOSITORY --exclude-drafts --limit 1 --json tagName --jq '.[0].tagName' 2>$null
$lastSetup = $null
if ($tag) {
    gh release download $tag -R $env:GITHUB_REPOSITORY -p "*Setup-$Arch.exe" -D $last 2>$null
    $lastSetup = Get-ChildItem $last -Filter "*Setup-$Arch.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
}
if ($lastSetup) {
    Write-Host "Update test: installing the last release ($tag)"
    if (Install $lastSetup.FullName "last") {
        $null = Open-Close "last release"
        $before = Get-ChildItem $data -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch '\.log$' } | ForEach-Object { $_.FullName.Substring($data.Length) }
        Write-Host "Saved by the last release: $($before -join ', ')"
        if (-not (Install $NewSetup "new")) { $failed = $true }
        else {
            $after = Get-ChildItem $data -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName.Substring($data.Length) }
            $lost = @($before | Where-Object { $_ -notin $after })
            if ($lost.Count -gt 0) { Note error "Update test" ("The update removed saved data: " + ($lost -join ', ')); $failed = $true }
            else { Write-Host "PASS: updated over $tag and all saved settings and data were kept." }
        }
    }
    else { Note warning "Update test" "Couldn't install the last release ($tag) here; update test skipped."; if (-not (Install $NewSetup "new")) { exit 1 } }
}
else {
    Write-Host "No last release with a $Arch installer; installing the new build fresh."
    if (-not (Install $NewSetup "new")) { exit 1 }
}

# ---------- 2. Start-up test ----------
foreach ($round in 1, 2, 3) {
    if (-not (Open-Close $round)) { $failed = $true; break }
}

# ---------- 3. Download test (bundled yt-dlp and ffmpeg) ----------
$bin = Join-Path $installRoot "Release"
$ytdlp = Join-Path $bin "yt-dlp.exe"
$ffmpeg = Join-Path $bin "ffmpeg.exe"
$ffprobe = Join-Path $bin "ffprobe.exe"
$dl = Join-Path $env:RUNNER_TEMP "smoke-downloads"
New-Item -ItemType Directory -Force -Path $dl | Out-Null
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
    $serve = Join-Path $env:RUNNER_TEMP "smoke-serve"
    New-Item -ItemType Directory -Force -Path $serve | Out-Null
    & $ffmpeg -loglevel error -f lavfi -i "testsrc=duration=8:size=640x360:rate=25" -f lavfi -i "sine=frequency=440:duration=8" -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest (Join-Path $serve "unifetcher-smoke-test.mp4")
    $server = Start-Process $python.Source -ArgumentList "-m", "http.server", "8000", "--bind", "127.0.0.1" -WorkingDirectory $serve -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 3
    & $ytdlp --no-config --ffmpeg-location $bin --retries 15 --fragment-retries 20 -o "$dl\%(title)s.%(ext)s" "http://127.0.0.1:8000/unifetcher-smoke-test.mp4" 2>&1 | Tee-Object -FilePath (Join-Path $logs "ytdlp-local.log")
    $file = Get-ChildItem $dl -Filter "unifetcher-smoke-test*" | Select-Object -First 1
    $probe = if ($file) { & $ffprobe -v error -show_entries "stream=codec_type,codec_name,width,height" -of compact $file.FullName 2>&1 | Out-String } else { "" }
    Write-Host $probe
    if ($file -and $probe -match "codec_name=h264\|codec_type=video\|width=640\|height=360" -and $probe -match "codec_name=aac\|codec_type=audio") {
        Write-Host "PASS: the test clip downloaded with the bundled yt-dlp/ffmpeg, plays, and wasn't re-encoded."
    }
    else {
        Note error "Download test" ((Get-Content (Join-Path $logs "ytdlp-local.log") -Tail 20) + $probe)
        $failed = $true
    }
    Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
}
else { Write-Host "Python isn't on this machine; local download test skipped." }

& $ytdlp --no-config --ffmpeg-location $bin --retries 15 -o "$dl\%(title)s.%(ext)s" "https://commons.wikimedia.org/wiki/File:Example.ogg" 2>&1 | Tee-Object -FilePath (Join-Path $logs "ytdlp-commons.log")
$ogg = Get-ChildItem $dl -Filter "Example*" | Select-Object -First 1
if ($ogg -and ((& $ffprobe -v error -show_entries stream=codec_type -of csv=p=0 $ogg.FullName) -match "audio")) {
    Write-Host "PASS: a real freely licensed file from Wikimedia Commons downloaded and plays."
}
else {
    $level = if ($StrictRealSite) { "error" } else { "warning" }
    Note $level "Real-site test" ((Get-Content (Join-Path $logs "ytdlp-commons.log") -Tail 15) + "Downloading from Wikimedia Commons failed (the site may be down, or yt-dlp needs an update).")
    if ($StrictRealSite) { $failed = $true }
}

# ---------- 4. Uninstall test ----------
$downloads = Join-Path $env:USERPROFILE "Downloads"
New-Item -ItemType Directory -Force -Path $downloads | Out-Null
$marker = Join-Path $downloads "unifetcher-smoke-marker.txt"
Set-Content $marker "keep me"
$uninstaller = Get-ChildItem $installRoot -Filter "unins*.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($uninstaller) {
    Start-Process $uninstaller.FullName -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART" -Wait
    Start-Sleep -Seconds 5
    if (Test-Path $exe) { Note error "Uninstall test" "UniFetcher.exe is still there after uninstalling."; $failed = $true }
    elseif (-not (Test-Path $marker)) { Note error "Uninstall test" "Uninstalling removed a file from the Downloads folder."; $failed = $true }
    else { Write-Host "PASS: uninstalled cleanly and the Downloads folder was left alone." }
}
else { Note error "Uninstall test" "No uninstaller found in $installRoot."; $failed = $true }

if ($failed) { exit 1 }
Write-Host "All Windows smoke tests passed."
exit 0
