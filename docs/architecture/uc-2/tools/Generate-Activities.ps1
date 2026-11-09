param(
    [string]$VisualParadigm = 'X:\apps\Visual Paradigm 18.1',
    [string]$Baseline = (Join-Path $PSScriptRoot '..\..\uc-4\SeasonPlan-UC4.vpp'),
    [string]$Output = (Join-Path $PSScriptRoot '..')
)
$ErrorActionPreference = 'Stop'
$Output = [IO.Path]::GetFullPath($Output)
$pluginId = 'seasonplan.uc2.activity.authoring'
$pluginDir = Join-Path $env:APPDATA "VisualParadigm\plugins\$pluginId"
$scratch = Join-Path $env:TEMP ('seasonplan-uc2-' + [Guid]::NewGuid().ToString('N'))
$baselineHash = (Get-FileHash $Baseline -Algorithm SHA256).Hash
if (Test-Path $pluginDir) { throw "Refusing to overwrite existing plugin $pluginDir" }
if (Test-Path (Join-Path $Output 'SeasonPlan-UC2-activities.vpp')) {
    throw 'Output project already exists. Use a new -Output directory to protect manual edits.'
}
New-Item -ItemType Directory -Force -Path $scratch, $pluginDir, $Output | Out-Null
try {
    foreach ($receipt in @('generate-success.txt', 'inspect-success.txt', 'failure.txt', 'validation.json')) {
        $receiptPath = Join-Path $Output $receipt
        if (Test-Path $receiptPath) { Remove-Item $receiptPath }
    }
    & python -B -m unittest discover -s $PSScriptRoot -p test_diagrams.py
    if ($LASTEXITCODE -ne 0) { throw 'Source regression tests failed' }
    & python -B (Join-Path $PSScriptRoot 'diagram_model.py') (Join-Path $scratch 'diagrams.xml')
    if ($LASTEXITCODE -ne 0) { throw 'Source generation failed' }
    & javac --release 11 -encoding UTF-8 -cp "$VisualParadigm\lib\openapi.jar" -d $pluginDir (Join-Path $PSScriptRoot 'ActivityProjectPlugin.java')
    if ($LASTEXITCODE -ne 0) { throw 'Plugin compilation failed' }
    @'
<?xml version="1.0" encoding="UTF-8"?>
<plugin id="seasonplan.uc2.activity.authoring" name="UC2 activity authoring" version="1.0" provider-name="SeasonPlan" class="ActivityProjectPlugin">
  <runtime><library path="."/></runtime>
</plugin>
'@ | Set-Content -Path (Join-Path $pluginDir 'plugin.xml') -Encoding UTF8
    Copy-Item $Baseline (Join-Path $scratch 'input.vpp')
    Push-Location "$VisualParadigm\bin"
    try {
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx2048m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project (Join-Path $scratch 'input.vpp') -pluginid $pluginId -pluginargs ("generate|" + (Join-Path $scratch 'diagrams.xml') + "|" + $Output)
        if ($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'generate-success.txt'))) { throw 'Native authoring failed' }
        # Reopen a copy in a separate process; the delivered project is not mutated by inspection.
        Copy-Item (Join-Path $Output 'SeasonPlan-UC2-activities.vpp') (Join-Path $scratch 'reopen.vpp')
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx2048m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project (Join-Path $scratch 'reopen.vpp') -pluginid $pluginId -pluginargs ("inspect|" + (Join-Path $scratch 'diagrams.xml') + "|" + $Output)
        if ($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'inspect-success.txt'))) { throw 'Native reopen failed' }
    } finally { Pop-Location }
    & python -B (Join-Path $PSScriptRoot 'validate_native.py') $Output
    if ($LASTEXITCODE -ne 0) { throw 'Native validation failed' }
    Remove-Item (Join-Path $Output 'generate-success.txt'), (Join-Path $Output 'inspect-success.txt')
} finally {
    if ((Get-FileHash $Baseline -Algorithm SHA256).Hash -ne $baselineHash) { Write-Error 'Baseline changed unexpectedly' }
    Remove-Item -Recurse -Force $pluginDir
    Remove-Item -Recurse -Force $scratch
}
