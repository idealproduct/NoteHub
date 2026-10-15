param([Parameter(Mandatory=$true)][string]$Serial, [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference='Stop'
$adbPath=Join-Path $AndroidSdk 'platform-tools/adb.exe'
$packageName='com.nqutaoitfteikc.notehub'
$runToken=[guid]::NewGuid().ToString()
function Invoke-Proof([string]$method, [string]$log) {
    $output=& $adbPath -s $Serial shell am instrument -w -e googlePersistenceRun $runToken -e class "com.nqutaoitfteikc.notehub.CalendarImportPersistenceTest#$method" "$packageName.test/androidx.test.runner.AndroidJUnitRunner"
    $output | Tee-Object -FilePath (Join-Path (Split-Path $PSScriptRoot -Parent) $log)
    if (($output -join "`n") -notmatch 'OK \(1 test\)' -or ($output -join "`n") -match 'FAILURES|INSTRUMENTATION_FAILED') { throw '真實匯入資料只讀驗證失敗' }
}
# Requires the current app and test APK already installed. Does not access Google or alter account/permissions/data.
Invoke-Proof 'aSnapshot' 'phase5-google-persistence-before.log'
& $adbPath -s $Serial shell am force-stop $packageName
$start=& $adbPath -s $Serial shell am start -W -n "$packageName/.MainActivity"
$start | Tee-Object -FilePath (Join-Path (Split-Path $PSScriptRoot -Parent) 'phase5-google-cold-start.log')
if (($start -join "`n") -notmatch 'Status: ok' -or ($start -join "`n") -notmatch 'COLD') { throw '沒有確認真正冷啟動' }
Invoke-Proof 'bVerifyAfterColdRestart' 'phase5-google-persistence-after.log'
