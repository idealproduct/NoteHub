param([Parameter(Mandatory=$true)][string]$Serial, [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference = 'Stop'
$adbPath = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$packageName = 'com.nqutaoitfteikc.notehub'
$runToken = 'TimezoneTest-'+[guid]::NewGuid().ToString()
$notificationBefore = ((& $adbPath -s $Serial shell dumpsys package $packageName) -join "`n") -match 'android.permission.POST_NOTIFICATIONS: granted=true'
$appOpsBefore = (& $adbPath -s $Serial shell cmd appops get $packageName SCHEDULE_EXACT_ALARM) -join "`n"
$exactModeBefore = 'default'
if ($appOpsBefore -match 'SCHEDULE_EXACT_ALARM:\s*(allow|deny|ignore|default)') { $exactModeBefore = $Matches[1] }
$timezoneBefore = ((& $adbPath -s $Serial shell getprop persist.sys.timezone) -join '').Trim()
$runner = 'com.nqutaoitfteikc.notehub.test/androidx.test.runner.AndroidJUnitRunner'
function Read-AlarmTimes {
    (& $adbPath -s $Serial shell dumpsys alarm) | Where-Object { $_ -match 'RTC_WAKEUP #\d+:.*com\.nqutaoitfteikc\.notehub' } | ForEach-Object {
        if ($_ -match 'origWhen (\d+)') { $Matches[1] }
    }
}
try {
    & $adbPath -s $Serial shell pm grant $packageName android.permission.POST_NOTIFICATIONS
    & $adbPath -s $Serial shell cmd appops set $packageName SCHEDULE_EXACT_ALARM allow
    $prepared = & $adbPath -s $Serial shell am instrument -w -e recoveryRun $runToken -e class 'com.nqutaoitfteikc.notehub.ReminderRecoveryTest#aPrepare' $runner
    if (($prepared -join "`n") -notmatch 'OK \(1 test\)') { throw '準備測試資料失敗' }
    $expected = @(Read-AlarmTimes)
    if ($expected.Count -lt 2) { throw '缺少獨立鬧鐘' }
    $beforeCount = @((& $adbPath -s $Serial logcat -d -s NoteHubReminders:I) | Select-String 'TIMEZONE_CHANGED').Count
    $targetTimezone = if ($timezoneBefore -eq 'Asia/Taipei') { 'Europe/London' } else { 'Asia/Taipei' }
    & $adbPath -s $Serial shell cmd alarm set-timezone $targetTimezone
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $observed = $false
    while ($watch.Elapsed.TotalSeconds -lt 60) {
        $count = @((& $adbPath -s $Serial logcat -d -s NoteHubReminders:I) | Select-String 'TIMEZONE_CHANGED').Count
        $current = @(Read-AlarmTimes)
        if ($count -gt $beforeCount -and @($expected | Where-Object { $_ -notin $current }).Count -eq 0) { $observed = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $observed) { throw '時區廣播與排程恢復未驗證成功' }
    Write-Output "已觀察到真實 TIMEZONE_CHANGED，觸發時間仍為 $($expected -join ',')"
    & $adbPath -s $Serial logcat -d -s NoteHubReminders:I | Set-Content phase3-timezone-broadcasts.log
    $verified = & $adbPath -s $Serial shell am instrument -w -e tokensVerifiedExternally true -e recoveryRun $runToken -e class 'com.nqutaoitfteikc.notehub.ReminderRecoveryTest#bVerifyRestoredTokensAndCleanUp' $runner
    $verified | Tee-Object -FilePath phase3-timezone-verify.log
    if (($verified -join "`n") -notmatch 'OK \(1 test\)') { throw '時區資料驗證失敗' }
} finally {
    if ($timezoneBefore) { & $adbPath -s $Serial shell cmd alarm set-timezone $timezoneBefore }
    if (-not $notificationBefore) { & $adbPath -s $Serial shell pm revoke $packageName android.permission.POST_NOTIFICATIONS }
    & $adbPath -s $Serial shell cmd appops set $packageName SCHEDULE_EXACT_ALARM $exactModeBefore
    Write-Output "已還原時區=$timezoneBefore、通知=$notificationBefore、精準鬧鐘=$exactModeBefore"
}
