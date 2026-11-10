param(
    [string]$VisualParadigm = 'X:\apps\Visual Paradigm 18.1',
    [string]$Baseline = (Join-Path $PSScriptRoot '..\..\uc-4\SeasonPlan-UC4.vpp'),
    [string]$Output = (Join-Path $PSScriptRoot '..')
)
$ErrorActionPreference = 'Stop'
$Output = [IO.Path]::GetFullPath($Output)
$Baseline = [IO.Path]::GetFullPath($Baseline)
$pluginId = 'seasonplan.package.authoring'
$pluginDir = Join-Path $env:APPDATA "VisualParadigm\plugins\$pluginId"
$scratch = Join-Path $env:TEMP ('seasonplan-packages-' + [Guid]::NewGuid().ToString('N'))
$baselineHash = (Get-FileHash $Baseline -Algorithm SHA256).Hash
if (Test-Path $pluginDir) { throw "Refusing to replace installed plugin $pluginDir" }
if (Test-Path (Join-Path $Output 'SeasonPlan-packages.vpp')) {
    throw 'Output project exists. Use a new -Output directory to protect manual edits.'
}
New-Item -ItemType Directory -Force -Path $scratch, $pluginDir, $Output | Out-Null
try {
    & python -B -m unittest discover -s $PSScriptRoot -p test_packages.py
    if ($LASTEXITCODE -ne 0) { throw 'Source/geometry regression tests failed' }
    & python -B (Join-Path $PSScriptRoot 'package_model.py') $Output
    if ($LASTEXITCODE -ne 0) { throw 'Package metadata generation failed' }
    & javac --release 11 -encoding UTF-8 -cp "$VisualParadigm\lib\openapi.jar" -d $pluginDir (Join-Path $PSScriptRoot 'PackageProjectPlugin.java')
    if ($LASTEXITCODE -ne 0) { throw 'VP plugin compilation failed' }
    @'
<?xml version="1.0" encoding="UTF-8"?>
<plugin id="seasonplan.package.authoring" name="SeasonPlan package authoring" version="1.0" provider-name="SeasonPlan" class="PackageProjectPlugin">
  <runtime><library path="."/></runtime>
</plugin>
'@ | Set-Content -Path (Join-Path $pluginDir 'plugin.xml') -Encoding UTF8
    Copy-Item $Baseline (Join-Path $scratch 'input.vpp')
    Push-Location "$VisualParadigm\bin"
    try {
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx2048m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project (Join-Path $scratch 'input.vpp') -pluginid $pluginId -pluginargs ("generate|" + (Join-Path $Output 'packages.xml') + "|" + $Output)
        if ($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'generate-success.txt'))) { throw 'Native generation failed' }
        Copy-Item (Join-Path $Output 'SeasonPlan-packages.vpp') (Join-Path $scratch 'reopen.vpp')
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx2048m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project (Join-Path $scratch 'reopen.vpp') -pluginid $pluginId -pluginargs ("inspect|" + (Join-Path $Output 'packages.xml') + "|" + $Output)
        if ($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'inspect-success.txt'))) { throw 'Native reopen failed' }
    } finally { Pop-Location }
    & python -B (Join-Path $PSScriptRoot 'validate_native.py') $Output
    if ($LASTEXITCODE -ne 0) { throw 'Saved/reopened native project validation failed' }
    Remove-Item (Join-Path $Output 'generate-success.txt'), (Join-Path $Output 'inspect-success.txt')
} finally {
    if ((Get-FileHash $Baseline -Algorithm SHA256).Hash -ne $baselineHash) { Write-Error 'Baseline was unexpectedly modified' }
    Remove-Item -Recurse -Force $pluginDir
    Remove-Item -Recurse -Force $scratch
}
