param([Parameter(Mandatory=$true)][string]$Serial, [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference = 'Stop'
$adbPath = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$packageName = 'com.nqutaoitfteikc.notehub'
$runner = 'com.nqutaoitfteikc.notehub.test/androidx.test.runner.AndroidJUnitRunner'
$cameraBefore = ((& $adbPath -s $Serial shell dumpsys package $packageName) -join "`n") -match 'android.permission.CAMERA: granted=true'
$runToken = 'RealCameraRestart-'+[guid]::NewGuid().ToString()
$prepared = $false
$verified = $false
function Assert-TestResult($output) {
    if (($output -join "`n") -notmatch 'OK \(1 test\)' -or ($output -join "`n") -match 'FAILURES') { throw '實拍照片重啟驗證失敗' }
}
try {
    & $adbPath -s $Serial shell pm grant $packageName android.permission.CAMERA
    $capture = & $adbPath -s $Serial shell am instrument -w -e photoPersistenceRun $runToken -e keepPhotoData true -e class 'com.nqutaoitfteikc.notehub.PhotoCaptureUiTest' $runner
    $capture | Tee-Object -FilePath phase4-real-camera-stage.log
    Assert-TestResult $capture
    $prepared = $true
    & $adbPath -s $Serial shell am force-stop $packageName
    & $adbPath -s $Serial shell am start -W -n "$packageName/.MainActivity"
    if ($LASTEXITCODE -ne 0) { throw 'App 重啟失敗' }
    $check = & $adbPath -s $Serial shell am instrument -w -e photoPersistenceRun $runToken -e class 'com.nqutaoitfteikc.notehub.PhotoRestartTest#bVerifyAndCleanUp' $runner
    $check | Tee-Object -FilePath phase4-real-camera-restart.log
    Assert-TestResult $check
    $verified = $true
} finally {
    if ($prepared -and -not $verified) {
        & $adbPath -s $Serial shell am instrument -w -e photoPersistenceRun $runToken -e class 'com.nqutaoitfteikc.notehub.PhotoRestartTest#bVerifyAndCleanUp' $runner | Set-Content phase4-restart-cleanup.log
    }
    if (-not $cameraBefore) { & $adbPath -s $Serial shell pm revoke $packageName android.permission.CAMERA }
    Write-Output "已還原相機權限：$cameraBefore"
}
