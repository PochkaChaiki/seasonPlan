param(
    [string]$VisualParadigm = 'X:\apps\Visual Paradigm 18.1',
    [string]$Baseline = (Join-Path $PSScriptRoot '..\SeasonPlan-UC2-activities.vpp'),
    [string]$Output = (Join-Path $PSScriptRoot '..\implementation-revised')
)
$ErrorActionPreference='Stop'
$Output=[IO.Path]::GetFullPath($Output)
$pluginId='seasonplan.uc2.implementation.revision'
$plugin=Join-Path $env:APPDATA "VisualParadigm\plugins\$pluginId"
$scratch=Join-Path $env:TEMP ('seasonplan-uc2-revise-'+[Guid]::NewGuid().ToString('N'))
$baselineHash=(Get-FileHash $Baseline -Algorithm SHA256).Hash
if(Test-Path $plugin){throw 'Revision plugin is already installed'}
if(Test-Path (Join-Path $Output 'SeasonPlan-UC2-implementation.vpp')){throw 'Refusing to overwrite an existing edited project. Choose a new -Output directory.'}
New-Item -ItemType Directory -Force $scratch,$plugin,$Output | Out-Null
try {
    & python -B -m unittest discover -s $PSScriptRoot -p test_implementation.py
    if($LASTEXITCODE -ne 0){throw 'Implementation source regressions failed'}
    $source=Join-Path $scratch 'implementation.xml'
    & python -B (Join-Path $PSScriptRoot 'implementation_model.py') $source
    if($LASTEXITCODE -ne 0){throw 'Source generation failed'}
    $java=Join-Path $scratch 'ImplementationRevisionPlugin.java'
    & python -B (Join-Path $PSScriptRoot 'revision_plugin.py') (Join-Path $PSScriptRoot 'ActivityProjectPlugin.java') $java
    if($LASTEXITCODE -ne 0){throw 'Plugin generation failed'}
    & javac --release 11 -encoding UTF-8 -cp "$VisualParadigm\lib\openapi.jar" -d $plugin $java
    if($LASTEXITCODE -ne 0){throw 'Plugin compilation failed'}
    '<plugin id="seasonplan.uc2.implementation.revision" name="UC2 implementation revision" version="1.0" provider-name="SeasonPlan" class="ImplementationRevisionPlugin"><runtime><library path="."/></runtime></plugin>' | Set-Content (Join-Path $plugin 'plugin.xml') -Encoding UTF8
    $input=Join-Path $scratch 'input.vpp'
    Copy-Item $Baseline $input
    Push-Location "$VisualParadigm\bin"
    try {
        $before=Join-Path $scratch 'before'
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx2048m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project $input -pluginid $pluginId -pluginargs ("inspect|unused|"+$before)
        if($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $before 'inspect-success.txt'))){throw 'Original snapshot inspection failed'}
        Copy-Item (Join-Path $before 'native-reopened.xml') (Join-Path $Output 'original-native.xml')
        # Reload an untouched copy, not the inspection session's lazy diagram state.
        Copy-Item $Baseline $input -Force
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx2048m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project $input -pluginid $pluginId -pluginargs ("generate|"+$source+"|"+$Output)
        if($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'generate-success.txt'))){throw 'Native implementation revision failed'}
        $reopen=Join-Path $scratch 'reopen.vpp'
        Copy-Item (Join-Path $Output 'SeasonPlan-UC2-implementation.vpp') $reopen
        & "$VisualParadigm\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx2048m' -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin -project $reopen -pluginid $pluginId -pluginargs ("inspect|unused|"+$Output)
        if($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $Output 'inspect-success.txt'))){throw 'Native reopen failed'}
    } finally {Pop-Location}
    & python -B (Join-Path $PSScriptRoot 'validate_implementation.py') $Output
    if($LASTEXITCODE -ne 0){throw 'Edited-copy validation failed'}
    $baselineHash.ToLower() | Set-Content (Join-Path $Output 'original-project.sha256') -Encoding ASCII
    Remove-Item (Join-Path $Output 'generate-success.txt'),(Join-Path $Output 'inspect-success.txt')
    # Only backups created by this new output's own save operations.
    Get-ChildItem $Output -Filter 'SeasonPlan-UC2-implementation.vpp.bak_*' | Remove-Item
} finally {
    if((Get-FileHash $Baseline -Algorithm SHA256).Hash -ne $baselineHash){Write-Error 'Input project changed during revision'}
    if(Test-Path $plugin){Remove-Item -Recurse -Force $plugin}
    if(Test-Path $scratch){Remove-Item -Recurse -Force $scratch}
}
