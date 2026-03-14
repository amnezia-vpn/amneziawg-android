param(
    [Parameter(Mandatory = $true)][string]$AndroidArchName,
    [Parameter(Mandatory = $true)][string]$AndroidPackageName,
    [Parameter(Mandatory = $true)][string]$GradleUserHome,
    [Parameter(Mandatory = $true)][string]$CC,
    [Parameter(Mandatory = $true)][string]$CFlags,
    [Parameter(Mandatory = $true)][string]$LdFlags,
    [Parameter(Mandatory = $true)][string]$Sysroot,
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][string]$DestDir,
    [Parameter(Mandatory = $true)][string]$BuildDir
)

$ErrorActionPreference = "Stop"

switch ($AndroidArchName) {
    "x86"    { $goArch = "386" }
    "x86_64" { $goArch = "amd64" }
    "arm"    { $goArch = "arm" }
    "arm64"  { $goArch = "arm64" }
    default  { throw "Unsupported ANDROID_ARCH_NAME: $AndroidArchName" }
}

$goVersion = "1.24.2"
$goZip = "go$goVersion.windows-amd64.zip"
$goUrl = "https://dl.google.com/go/$goZip"

$cacheRoot = Join-Path $GradleUserHome "caches/golang"
$zipPath = Join-Path $cacheRoot $goZip
$goRoot = Join-Path $cacheRoot "go$goVersion-windows-amd64"
$extractRoot = Join-Path $cacheRoot "__extract_tmp"

function Resolve-GoExe([string]$rootPath) {
    $candidates = @(
        (Join-Path $rootPath "bin/go.exe"),
        (Join-Path $rootPath "go/bin/go.exe")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }
    return $null
}

New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

if (-not (Test-Path $zipPath)) {
    Invoke-WebRequest -Uri $goUrl -OutFile $zipPath
}

$goExe = Resolve-GoExe -rootPath $goRoot
if (-not $goExe) {
    if (Test-Path $extractRoot) { Remove-Item -Recurse -Force $extractRoot }
    if (Test-Path $goRoot) { Remove-Item -Recurse -Force $goRoot }
    New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
    Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force
    Move-Item -Path (Join-Path $extractRoot "go") -Destination $goRoot
    Remove-Item -Recurse -Force $extractRoot
}

$goExe = Resolve-GoExe -rootPath $goRoot
if (-not $goExe) {
    throw "Go binary not found under '$goRoot' after extraction"
}
$goBinDir = Split-Path -Parent $goExe

$env:CGO_CFLAGS = "--target=$Target --sysroot=$Sysroot $CFlags"
$cleanLdFlags = $LdFlags -replace "--build-id=[^ ]+", "--build-id=none"
$env:CGO_LDFLAGS = "--target=$Target --sysroot=$Sysroot $cleanLdFlags -Wl,-soname=libwg-go.so"
$env:GOARCH = $goArch
$env:GOOS = "android"
$env:CGO_ENABLED = "1"
$env:CC = $CC
$env:PATH = "$goBinDir;$env:PATH"

$ld = "-X github.com/amnezia-vpn/amneziawg-go/ipc.socketDirectory=/data/data/$AndroidPackageName/cache/amneziawg -buildid="
$outPath = Join-Path $DestDir "libwg-go.so"

& $goExe build -tags linux -ldflags $ld -v -trimpath -buildvcs=false -o $outPath -buildmode c-shared
