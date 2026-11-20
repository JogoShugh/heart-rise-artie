# Sets up the kit on Windows, step by step: WSL 2 and Ubuntu, WSL's memory, this repository cloned inside Ubuntu,
# then kit/setup.sh there, which installs Docker if need be, asks for your GitHub repository and keys, and builds
# the kit. Run it again any time; it skips what is done already.
#
# In PowerShell (not as administrator; it asks when it needs to):
#   powershell -ExecutionPolicy Bypass -File setup.ps1
# or, without a copy of the repository yet:
#   irm https://raw.githubusercontent.com/JogoShugh/heart-rise-artie/claude/auth-branch-4bnmhc/kit/windows/setup.ps1 | iex

$ErrorActionPreference = 'Stop'
$Distro = 'Ubuntu'
$Branch = 'claude/auth-branch-4bnmhc'
$Url = 'https://github.com/JogoShugh/heart-rise-artie.git'
$script:Step = 0

function Say($text) { $script:Step++; Write-Host ""; Write-Host "$($script:Step). $text" -ForegroundColor White }
function Ok($text) { Write-Host "   ok  " -ForegroundColor Green -NoNewline; Write-Host $text }
function Note($text) { Write-Host "   $text" }
function Stop-Setup($text) { Write-Host ""; Write-Host "Stopped: $text" -ForegroundColor Red; exit 1 }
function Ask-Yes($question) {
    $answer = Read-Host "   $question [Y/n]"
    return -not ($answer -match '^[nN]')
}
# wsl.exe writes UTF-16; this reads it as text.
function Wsl-Text { param([string[]]$WslArgs) $old = [Console]::OutputEncoding; [Console]::OutputEncoding = [Text.Encoding]::Unicode
    try { (& wsl.exe @WslArgs 2>$null) -join "`n" } finally { [Console]::OutputEncoding = $old } }

Say "WSL 2"
$wslReady = $false
try { & wsl.exe --status *> $null; $wslReady = ($LASTEXITCODE -eq 0) } catch { $wslReady = $false }
if ($wslReady) {
    Ok "WSL is installed"
} else {
    Note "WSL isn't installed. Installing it needs administrator rights (Windows asks), then a restart."
    if (-not (Ask-Yes "Install WSL and Ubuntu now?")) { Stop-Setup "WSL is needed. Run this again when ready." }
    Start-Process wsl.exe -ArgumentList "--install -d $Distro" -Verb RunAs -Wait
    Note "Restart Windows. After the restart, Ubuntu opens and asks for a user name and password: choose them."
    Note "Then run this script again."
    exit 0
}

Say "Ubuntu"
$distros = Wsl-Text @('--list', '--quiet')
if ($distros -notmatch "(?m)^$Distro") {
    Note "Ubuntu isn't installed in WSL. Installing it; it opens a window asking for a user name and password."
    & wsl.exe --install -d $Distro
    Note "When Ubuntu has your user name and password, close its window and run this script again."
    exit 0
}
$user = (& wsl.exe -d $Distro -- whoami).Trim()
if (-not $user -or $user -eq 'root') {
    Stop-Setup "Ubuntu has no user of its own yet. Open Ubuntu from the Start menu, choose a user name and password, then run this again."
}
Ok "Ubuntu, as $user"
$version = Wsl-Text @('--list', '--verbose')
if ($version -match "(?m)$Distro\s+\S+\s+1\s*$") {
    Note "Ubuntu runs as WSL 1; the kit needs WSL 2. Converting it (a few minutes)."
    & wsl.exe --set-version $Distro 2
}
Ok "WSL 2"

Say "Memory for WSL"
$config = Join-Path $env:USERPROFILE '.wslconfig'
if ((Test-Path $config) -and (Select-String -Path $config -Pattern '^\s*memory\s*=' -Quiet)) {
    Ok "set in $config"
} else {
    Note "The kit's build wants about 6 GB. WSL's memory is set in $config."
    if (Ask-Yes "Give WSL 8 GB?") {
        if (Test-Path $config) {
            $text = Get-Content $config -Raw
            if ($text -match '(?m)^\[wsl2\]') { $text = $text -replace '(?m)^\[wsl2\]', "[wsl2]`r`nmemory=8GB" }
            else { $text = $text + "`r`n[wsl2]`r`nmemory=8GB`r`n" }
            Set-Content -Path $config -Value $text -Encoding ASCII
        } else {
            Set-Content -Path $config -Value "[wsl2]`r`nmemory=8GB" -Encoding ASCII
        }
        Note "restarting WSL so it counts"
        & wsl.exe --shutdown
        Ok "WSL has 8 GB"
    }
}

Say "Docker"
$desktop = Test-Path "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
if ($desktop) {
    Ok "Docker Desktop is installed"
    Note "In Docker Desktop: Settings, Resources, WSL integration: turn Ubuntu on, and Apply."
    Read-Host "   Press Enter when it's on" | Out-Null
} else {
    Ok "no Docker Desktop: kit/setup.sh offers to install Docker inside Ubuntu instead"
}

Say "This repository, inside Ubuntu"
$clone = @"
set -e
command -v git > /dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq git > /dev/null; }
cd ~
if [ -d heart-rise-artie/.git ]; then
  git -C heart-rise-artie pull -q --ff-only && echo '   ok  updated ~/heart-rise-artie'
else
  git clone -q --branch $Branch $Url && echo '   ok  cloned into ~/heart-rise-artie'
fi
"@
& wsl.exe -d $Distro -- bash -c ($clone -replace "`r", '')
if ($LASTEXITCODE -ne 0) { Stop-Setup "the clone failed; see above." }

Say "The kit, set up inside Ubuntu"
Note "kit/setup.sh takes it from here; it asks for your GitHub repository and keys."
& wsl.exe -d $Distro --cd '~/heart-rise-artie' -- sh kit/setup.sh
if ($LASTEXITCODE -ne 0) { Stop-Setup "kit/setup.sh stopped; see above. Fix that, then run this again." }

Write-Host ""
Write-Host "From now on, work in Ubuntu: open it from the Start menu, then: cd ~/heart-rise-artie" -ForegroundColor White
