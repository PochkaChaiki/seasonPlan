param(
    [string]$VisualParadigm = 'X:\apps\Visual Paradigm 18.1',
    [string]$Baseline = (Join-Path $PSScriptRoot '..\..\uc-4\SeasonPlan-UC4.vpp'),
    [string]$Output = (Join-Path $PSScriptRoot '..')
)
$ErrorActionPreference = 'Stop'
$Output = [IO.Path]::GetFullPath($Output)
$Baseline = [IO.Path]::GetFullPath($Baseline)
$pluginId = 'seasonplan.classes.authoring'
$pluginDir = Join-Path $env:APPDATA "VisualParadigm\plugins\$pluginId"
$scratch = Join-Path $env:TEMP ('seasonplan-classes-' + [Guid]::NewGuid().ToString('N'))
$baselineHash = (Get-FileHash $Baseline -Algorithm SHA256).Hash
if (Test-Path $pluginDir) { throw "Refusing to replace installed plugin $pluginDir" }
if (Test-Path (Join-Path $Output 'SeasonPlan-all-classes.vpp')) { throw 'Output exists; use a fresh -Output directory to preserve manual edits.' }
New-Item -ItemType Directory -Force -Path $scratch, $pluginDir, $Output | Out-Null
try {
    & python -B -m unittest discover -s $PSScriptRoot -p 'test_*.py'
    if ($LASTEXITCODE -ne 0) { throw 'Source/validator regression tests failed' }
    & python -B (Join-Path $PSScriptRoot 'class_model.py') $Output
    if ($LASTEXITCODE -ne 0) { throw 'Source metadata generation failed' }
    & javac --release 11 -encoding UTF-8 -cp "$VisualParadigm\lib\openapi.jar" -d $pluginDir (Join-Path $PSScriptRoot 'ClassProjectPlugin.java')
    if ($LASTEXITCODE -ne 0) { throw 'VP plugin compilation failed' }
    @'
<?xml version="1.0" encoding="UTF-8"?>
<plugin id="seasonplan.classes.authoring" name="SeasonPlan complete class model" version="1.0" provider-name="SeasonPlan" class="ClassProjectPlugin">
  <runtime><library path="."/></runtime>
</plugin>
'@ | Set-Content -Path (Join-Path $pluginDir 'plugin.xml') -Encoding UTF8
    Copy-Item $Baseline (Join-Path $scratch 'input.vpp')
    Push-Location "$VisualParadigm\bin"
    try {
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx3072m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project (Join-Path $scratch 'input.vpp') -pluginid $pluginId -pluginargs ("generate|" + (Join-Path $Output 'classes.xml') + "|" + $Output)
        if ($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'generate-success.txt'))) { throw 'Native generation failed; inspect failure.txt and command output.' }
        Copy-Item (Join-Path $Output 'SeasonPlan-all-classes.vpp') (Join-Path $scratch 'reopen.vpp')
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx3072m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project (Join-Path $scratch 'reopen.vpp') -pluginid $pluginId -pluginargs ("inspect|" + (Join-Path $Output 'classes.xml') + "|" + $Output)
        if ($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'inspect-success.txt'))) { throw 'Separate-process native reopen/export failed' }
    } finally { Pop-Location }
    & python -B (Join-Path $PSScriptRoot 'validate_native.py') $Output
    if ($LASTEXITCODE -ne 0) { throw 'Saved/reopened native model validation failed' }
    Remove-Item (Join-Path $Output 'generate-success.txt'), (Join-Path $Output 'inspect-success.txt')
} finally {
    if ((Get-FileHash $Baseline -Algorithm SHA256).Hash -ne $baselineHash) { Write-Error 'Baseline unexpectedly changed' }
    Remove-Item -Recurse -Force $pluginDir
    Remove-Item -Recurse -Force $scratch
}
