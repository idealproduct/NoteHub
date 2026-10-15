param([Parameter(Mandatory=$true)][string]$Serial, [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference = 'Stop'
$adbPath = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$packageName = 'com.nqutaoitfteikc.notehub'
$runToken = 'PermissionTest-'+[guid]::NewGuid().ToString()
$notificationBefore = ((& $adbPath -s $Serial shell dumpsys package $packageName) -join "`n") -match 'android.permission.POST_NOTIFICATIONS: granted=true'
$appOpsBefore = (& $adbPath -s $Serial shell cmd appops get $packageName SCHEDULE_EXACT_ALARM) -join "`n"
$exactModeBefore = 'default'
if ($appOpsBefore -match 'SCHEDULE_EXACT_ALARM:\s*(allow|deny|ignore|default)') { $exactModeBefore = $Matches[1] }
$runner = 'com.nqutaoitfteikc.notehub.test/androidx.test.runner.AndroidJUnitRunner'
$class = 'com.nqutaoitfteikc.notehub.ReminderPermissionStateTest'
try {
    & $adbPath -s $Serial shell pm revoke $packageName android.permission.POST_NOTIFICATIONS
    & $adbPath -s $Serial shell cmd appops set $packageName SCHEDULE_EXACT_ALARM deny
    foreach ($expected in @('BLOCKED_NOTIFICATION','APPROXIMATE','EXACT')) {
        if ($expected -eq 'APPROXIMATE') { & $adbPath -s $Serial shell pm grant $packageName android.permission.POST_NOTIFICATIONS }
        if ($expected -eq 'EXACT') { & $adbPath -s $Serial shell cmd appops set $packageName SCHEDULE_EXACT_ALARM allow }
        $keep = if ($expected -eq 'EXACT') { 'false' } else { 'true' }
        $output = & $adbPath -s $Serial shell am instrument -w -e permissionRun $runToken -e keepPermissionData $keep -e expectedReminderStatus $expected -e class $class $runner
        $output | Tee-Object -FilePath "phase3-permission-$expected.log"
        if (($output -join "`n") -notmatch 'OK \(1 test\)' -or ($output -join "`n") -match 'FAILURES') { throw "權限測試失敗：$expected" }
    }
} finally {
    # Re-run with no fixed expectation to clean up this run's own data even if a stage failed.
    & $adbPath -s $Serial shell am instrument -w -e permissionRun $runToken -e class $class $runner | Set-Content phase3-permission-cleanup.log
    if (-not $notificationBefore) { & $adbPath -s $Serial shell pm revoke $packageName android.permission.POST_NOTIFICATIONS }
    else { & $adbPath -s $Serial shell pm grant $packageName android.permission.POST_NOTIFICATIONS }
    & $adbPath -s $Serial shell cmd appops set $packageName SCHEDULE_EXACT_ALARM $exactModeBefore
    Write-Output "已還原權限：通知=$notificationBefore，精準鬧鐘=$exactModeBefore"
}
