param([Parameter(Mandatory=$true)][string]$Serial, [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw '此腳本僅允許對模擬器執行重開機驗證' }
$adbPath = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$packageName = 'com.nqutaoitfteikc.notehub'
$runToken = 'RebootTest-'+[guid]::NewGuid().ToString()
$notificationBefore = ((& $adbPath -s $Serial shell dumpsys package $packageName) -join "`n") -match 'android.permission.POST_NOTIFICATIONS: granted=true'
$appOpsBefore = (& $adbPath -s $Serial shell cmd appops get $packageName SCHEDULE_EXACT_ALARM) -join "`n"
$exactModeBefore = 'default'
if ($appOpsBefore -match 'SCHEDULE_EXACT_ALARM:\s*(allow|deny|ignore|default)') { $exactModeBefore = $Matches[1] }
$timezoneBefore = ((& $adbPath -s $Serial shell getprop persist.sys.timezone) -join '').Trim()
function Assert-TestResult($output) {
    if (($output -join "`n") -notmatch 'OK \(1 test\)' -or ($output -join "`n") -match 'FAILURES') { throw '恢復排程測試失敗' }
}
function Read-AlarmTimes {
    $lines = & $adbPath -s $Serial shell dumpsys alarm
    $lines | Where-Object { $_ -match 'RTC_WAKEUP #\d+:.*com\.nqutaoitfteikc\.notehub' } | ForEach-Object {
        if ($_ -match 'origWhen (\d+)') { $Matches[1] }
    }
}
function Wait-RestoredAlarms($expectedTimes, $logPath) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt 60) {
        $current = @(Read-AlarmTimes)
        if (@($expectedTimes | Where-Object { $_ -notin $current }).Count -eq 0) {
            $current | Set-Content $logPath
            Write-Output "系統 AlarmManager 已恢復原始觸發時間：$($expectedTimes -join ',')"
            return
        }
        Start-Sleep -Seconds 1
    }
    throw '系統未在 60 秒內恢復原始鬧鐘；尚未啟動驗證 instrumentation'
}
try {
    & $adbPath -s $Serial shell pm grant $packageName android.permission.POST_NOTIFICATIONS
    & $adbPath -s $Serial shell cmd appops set $packageName SCHEDULE_EXACT_ALARM allow
    $prepared = & $adbPath -s $Serial shell am instrument -w -e recoveryRun $runToken -e class 'com.nqutaoitfteikc.notehub.ReminderRecoveryTest#aPrepare' com.nqutaoitfteikc.notehub.test/androidx.test.runner.AndroidJUnitRunner
    $prepared | Tee-Object -FilePath phase3-reboot-prepare.log
    Assert-TestResult $prepared
    $expectedTimes = @(Read-AlarmTimes)
    if ($expectedTimes.Count -lt 2) { throw '重開機前缺少獨立鬧鐘' }
    $expectedTimes | Set-Content phase3-alarms-before-reboot.log
    & $adbPath -s $Serial reboot
    if ($LASTEXITCODE -ne 0) { throw '模擬器重開機命令失敗' }
    $bootWatch = [Diagnostics.Stopwatch]::StartNew()
    $booted = $false
    while ($bootWatch.Elapsed.TotalSeconds -lt 90) {
        Start-Sleep -Seconds 1
        $bootState = (& $adbPath -s $Serial shell getprop sys.boot_completed 2>$null) -join ''
        if ($bootState.Trim() -eq '1') { $booted = $true; break }
    }
    if (-not $booted) { throw '模擬器未在 90 秒內完成開機' }
    & $adbPath -s $Serial shell input keyevent 82
    Wait-RestoredAlarms $expectedTimes phase3-alarms-after-reboot.log
    $timezoneLogsBefore = @((& $adbPath -s $Serial logcat -d -s NoteHubReminders:I) | Select-String 'TIMEZONE_CHANGED').Count
    & $adbPath -s $Serial shell cmd alarm set-timezone Asia/Taipei
    if ($LASTEXITCODE -ne 0) { throw '模擬器時區切換失敗' }
    $timezoneWatch = [Diagnostics.Stopwatch]::StartNew()
    while ($timezoneWatch.Elapsed.TotalSeconds -lt 60) {
        $timezoneLogs = @((& $adbPath -s $Serial logcat -d -s NoteHubReminders:I) | Select-String 'TIMEZONE_CHANGED').Count
        if ($timezoneLogs -gt $timezoneLogsBefore) { break }
        Start-Sleep -Seconds 1
    }
    if ($timezoneLogs -le $timezoneLogsBefore) { throw '尚未觀察到時區廣播，停止驗證' }
    Wait-RestoredAlarms $expectedTimes phase3-alarms-after-timezone.log
    $verified = & $adbPath -s $Serial shell am instrument -w -e tokensVerifiedExternally true -e recoveryRun $runToken -e class 'com.nqutaoitfteikc.notehub.ReminderRecoveryTest#bVerifyRestoredTokensAndCleanUp' com.nqutaoitfteikc.notehub.test/androidx.test.runner.AndroidJUnitRunner
    $verified | Tee-Object -FilePath phase3-reboot-verify.log
    Assert-TestResult $verified
    & $adbPath -s $Serial logcat -d -s NoteHubReminders:I | Set-Content phase3-recovery-broadcasts.log
} finally {
    if ($timezoneBefore) { & $adbPath -s $Serial shell cmd alarm set-timezone $timezoneBefore }
    if (-not $notificationBefore) { & $adbPath -s $Serial shell pm revoke $packageName android.permission.POST_NOTIFICATIONS }
    & $adbPath -s $Serial shell cmd appops set $packageName SCHEDULE_EXACT_ALARM $exactModeBefore
    Write-Output "已還原時區=$timezoneBefore、通知=$notificationBefore、精準鬧鐘=$exactModeBefore"
}


