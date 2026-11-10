param(
    [string]$VisualParadigm = 'X:\apps\Visual Paradigm 18.1',
    [string]$Baseline = (Join-Path $PSScriptRoot '..\..\uc-4\SeasonPlan-UC4.vpp'),
    [string]$Output = (Join-Path $PSScriptRoot '..')
)
$ErrorActionPreference = 'Stop'
$Output = [IO.Path]::GetFullPath($Output)
$Baseline = [IO.Path]::GetFullPath($Baseline)
$pluginId = 'seasonplan.uc5.communication.authoring'
$pluginDir = Join-Path $env:APPDATA "VisualParadigm\plugins\$pluginId"
$scratch = Join-Path $env:TEMP ('seasonplan-uc5-' + [Guid]::NewGuid().ToString('N'))
$baselineHash = (Get-FileHash $Baseline -Algorithm SHA256).Hash
if (Test-Path $pluginDir) { throw "Refusing to replace installed plugin $pluginDir" }
if (Test-Path (Join-Path $Output 'SeasonPlan-UC5-communications.vpp')) {
    throw 'Output project exists. Use a new -Output directory to protect manual edits.'
}
New-Item -ItemType Directory -Force -Path $scratch, $pluginDir, $Output | Out-Null
try {
    & python -B -m unittest discover -s $PSScriptRoot -p 'test_*.py'
    if ($LASTEXITCODE -ne 0) { throw 'Source/model regression tests failed' }
    & python -B (Join-Path $PSScriptRoot 'communication_model.py') $Output
    if ($LASTEXITCODE -ne 0) { throw 'Communication metadata generation failed' }
    & javac --release 11 -encoding UTF-8 -cp "$VisualParadigm\lib\openapi.jar" -d $pluginDir (Join-Path $PSScriptRoot 'CommunicationProjectPlugin.java')
    if ($LASTEXITCODE -ne 0) { throw 'VP plugin compilation failed' }
    @'
<?xml version="1.0" encoding="UTF-8"?>
<plugin id="seasonplan.uc5.communication.authoring" name="SeasonPlan UC5 communications" version="1.0" provider-name="SeasonPlan" class="CommunicationProjectPlugin">
  <runtime><library path="."/></runtime>
</plugin>
'@ | Set-Content -Path (Join-Path $pluginDir 'plugin.xml') -Encoding UTF8
    Copy-Item $Baseline (Join-Path $scratch 'input.vpp')
    Push-Location "$VisualParadigm\bin"
    try {
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx3072m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project (Join-Path $scratch 'input.vpp') -pluginid $pluginId -pluginargs ("generate|" + (Join-Path $Output 'communications.xml') + "|" + $Output)
        if ($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'generate-success.txt'))) { throw 'Native generation failed' }
        Copy-Item (Join-Path $Output 'SeasonPlan-UC5-communications.vpp') (Join-Path $scratch 'reopen.vpp')
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx3072m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project (Join-Path $scratch 'reopen.vpp') -pluginid $pluginId -pluginargs ("inspect|" + (Join-Path $Output 'communications.xml') + "|" + $Output)
        if ($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'inspect-success.txt'))) { throw 'Native reopen failed' }
    } finally { Pop-Location }
    & python -B (Join-Path $PSScriptRoot 'validate_native.py') $Output
    if ($LASTEXITCODE -ne 0) { throw 'Saved/reopened native validation failed' }
    Remove-Item (Join-Path $Output 'generate-success.txt'), (Join-Path $Output 'inspect-success.txt')
    Get-ChildItem -Path $Output -Filter 'SeasonPlan-UC5-communications.vpp.bak_*' | Remove-Item -Force
} finally {
    if ((Get-FileHash $Baseline -Algorithm SHA256).Hash -ne $baselineHash) { Write-Error 'Baseline was unexpectedly modified' }
    Remove-Item -Recurse -Force $pluginDir
    Remove-Item -Recurse -Force $scratch
}
