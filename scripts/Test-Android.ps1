param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [switch]$GrantReminderTestPermissions,
    [switch]$GrantCameraTestPermissions
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path $PSScriptRoot -Parent
Push-Location $projectPath
try {
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw '建置或電腦端測試失敗' }
    $adbPath = Join-Path $AndroidSdk 'platform-tools/adb.exe'
    $packageName = 'com.nqutaoitfteikc.notehub'
    & $adbPath -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'App 安裝失敗' }
    & $adbPath -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw '測試 APK 安裝失敗' }
    $cameraBefore = ((& $adbPath -s $Serial shell dumpsys package $packageName) -join "`n") -match 'android.permission.CAMERA: granted=true'
    $notificationBefore = ((& $adbPath -s $Serial shell dumpsys package $packageName) -join "`n") -match 'android.permission.POST_NOTIFICATIONS: granted=true'
    $appOpsBefore = (& $adbPath -s $Serial shell cmd appops get $packageName SCHEDULE_EXACT_ALARM) -join "`n"
    $exactModeBefore = 'default'
    if ($appOpsBefore -match 'SCHEDULE_EXACT_ALARM:\s*(allow|deny|ignore|default)') { $exactModeBefore = $Matches[1] }
    try {
        if ($GrantCameraTestPermissions) { & $adbPath -s $Serial shell pm grant $packageName android.permission.CAMERA }
        if ($GrantReminderTestPermissions) {
            & $adbPath -s $Serial shell pm grant $packageName android.permission.POST_NOTIFICATIONS
            & $adbPath -s $Serial shell cmd appops set $packageName SCHEDULE_EXACT_ALARM allow
        }
        $testOutput = & $adbPath -s $Serial shell am instrument -w com.nqutaoitfteikc.notehub.test/androidx.test.runner.AndroidJUnitRunner 2>&1
        $testOutput | Tee-Object -FilePath android-acceptance-device.log
        if (($testOutput -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($testOutput -join "`n") -match 'FAILURES|INSTRUMENTATION_FAILED') { throw '裝置測試未通過，請查看 android-acceptance-device.log' }
    } finally {
        if ($GrantCameraTestPermissions -and -not $cameraBefore) { & $adbPath -s $Serial shell pm revoke $packageName android.permission.CAMERA }
        if ($GrantCameraTestPermissions) { Write-Output "已還原相機權限：$cameraBefore" }
        if ($GrantReminderTestPermissions) {
            if (-not $notificationBefore) { & $adbPath -s $Serial shell pm revoke $packageName android.permission.POST_NOTIFICATIONS }
            & $adbPath -s $Serial shell cmd appops set $packageName SCHEDULE_EXACT_ALARM $exactModeBefore
            Write-Output "已還原權限：通知=$notificationBefore，精準鬧鐘=$exactModeBefore"
        }
    }
} finally { Pop-Location }


