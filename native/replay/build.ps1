param(
    [Parameter(Mandatory = $true)][string]$Output,
    [Parameter(Mandatory = $true)][string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
$install = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (!$install) { throw 'MSVC Build Tools with the Windows SDK are required for Gleam Replay' }
$vcvars = Join-Path $install 'VC/Auxiliary/Build/vcvars64.bat'
$outputPath = [IO.Path]::GetFullPath($Output)
$outputDir = [IO.Path]::GetDirectoryName($outputPath)
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$source = Join-Path $PSScriptRoot 'replay.cpp'
$compile = 'call "{0}" && cl /nologo /std:c++20 /EHsc /O2 /MT /utf-8 /W4 /Fo"{1}" /Fe"{2}" "{3}" /link /INCREMENTAL:NO' -f $vcvars, (Join-Path $outputDir 'replay.obj'), $outputPath, $source
& $env:ComSpec /d /c $compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$captureSource = Join-Path $PSScriptRoot 'game_capture.cpp'
$captureDll = Join-Path $outputDir 'gleam-replay-capture.dll'
$jniInclude = Join-Path $JavaHome 'include'
$jniWindows = Join-Path $jniInclude 'win32'
$compile = 'call "{0}" && cl /nologo /std:c++20 /EHsc /O2 /MT /utf-8 /W4 /LD /I"{1}" /I"{2}" /Fo"{3}" /Fe"{4}" "{5}" /link /INCREMENTAL:NO /IMPLIB:"{6}"' -f $vcvars, $jniInclude, $jniWindows, (Join-Path $outputDir 'game_capture.obj'), $captureDll, $captureSource, (Join-Path $outputDir 'gleam-replay-capture.lib')
& $env:ComSpec /d /c $compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
