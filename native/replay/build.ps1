param([Parameter(Mandatory = $true)][string]$Output)

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
