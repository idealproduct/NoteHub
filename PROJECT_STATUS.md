# 專案進度

更新：2026-10-15（Asia/Taipei）

目前：Phase 1–4 已完成對應模擬器驗證；Phase 5 真實 Google 登入、唯讀 API、預覽／匯入／去重與已刪除副本手動重匯已通過，真正冷啟動只讀核對亦通過。最新單元 22 項、一般裝置 36 項與真實資料兩階段驗證通過。下一階段 Phase 6；實體裝置、大量真實分頁等限制見文末。

## Phase 1 範圍

保留現有 Android Compose 專案、套件名稱與資源；建立可操作的記事／設定導覽、MVVM / Repository / Use Cases、Room、DataStore、集中版本與驗證。未開始 Phase 2–6 的完整功能。

## 環境檢查

- 原始目錄沒有 Git 儲存庫，因此無法取得 Git diff 或既有提交紀錄。
- 原本已有 app 模組與 Compose Hello Android 範本。
- 原本只有 wrapper properties，缺少 gradlew、gradlew.bat、wrapper JAR；已由本機 Gradle 9.3.1 正式產生。
- PATH 無 java、gradle、adb，JAVA_HOME / ANDROID_HOME 未設定；找到 JDK 21.0.12.1 及 SDK 36.1，以明確路徑執行。
- SDK 路徑由既有 local.properties 提供，保留不變。
- 模擬器 emulator-5554 已連線。
- 修改前 main 原始碼／資源備份在 .phase1-backup/main；既有 IDE 設定保留。

## 已實作

- [x] Wrapper 與 libs.versions.toml 集中管理。
- [x] AGP 內建 Kotlin 與 Compose compiler 對齊 2.2.10。
- [x] Compose Material 3、記事／設定導覽、ViewModel、依賴容器。
- [x] Room Note Entity / DAO / Repository，匯出 schema v1。
- [x] Use Cases：新增記事、觀察記事、觀察與修改主題。
- [x] DataStore 系統／淺色／深色設定。
- [x] 輸入驗證、非同步儲存、載入與繁體中文錯誤提示。
- [x] 禁用系統備份與資料轉移。
- [x] 業務單元測試、Room / DataStore 持久化測試、Compose 導覽測試原始碼。

## 建置與測試

Phase 1 驗收通過：可建置、可安裝、可啟動，記事／設定導覽與基本持久化已在模擬器驗證。
- 初次受限環境 Gradle native DLL 載入失敗；提高執行權限後 tasks 與 wrapper 成功。
- 初次 KSP 2.2.10-2.0.2 不支援 AGP 9 sourceSets 限制；改為 KSP 2.3.9，已通過該步驟。
- `:app:assembleDebug`：成功；APK 27,995,358 bytes。
- `:app:testDebugUnitTest`：4/4 通過（3 個實際 Use Case 測試 + 1 個原有範例測試）。
- `:app:lintDebug`：成功，0 errors / 23 warnings；包含版本更新提示、原有未使用顏色與多餘 Activity label，未關閉檢查或隱藏警告。
- `:app:connectedDebugAndroidTest`：4/4 通過（導航／新增／主題／Activity 重建整合測試 1 個、持久化測試 2 個、原有套件名稱測試 1 個）。
- 實際環境：Pixel_8_Pro AVD，Android 17 / API 37，sdk_gphone16k_x86_64；不是實體裝置測試。
- 舊 Espresso 3.6.1 在 API 37 因 InputManager 反射 API 失敗；依官方修正更新至 Espresso 3.7.0 / AndroidX Test JUnit 1.3.0。
- 導覽測試曾在 DataStore 寫入完成前斷言；改為等待真實可觀察狀態，不使用固定延遲。重跑全套通過。
- `adb install -r`：Success；`am start -W`：Status ok / COLD。
- 另透過畫面新增 `Phase1-persistence-check`，force-stop 後重新啟動，在 UI hierarchy 確認該記事仍顯示，程序重啟驗證通過。
- 注意：Gradle connected test 會管理測試安裝與清理；最後重新安裝 APK 並另做上述程序重啟驗證，未假設測試資料必然保留。
- 尚未測試 API 28 實機、其他 Android 版本或裝置廠牌，也沒有執行提醒／照片／Google 功能驗證。
- 完整記錄：phase1-baseline.log、phase1-build.log、phase1-verification.log、phase1-final.log、phase1-acceptance.log；最後一份為成功驗收。
- 報告：app/build/reports/tests/testDebugUnitTest/index.html、app/build/reports/androidTests/connected/debug/index.html、app/build/reports/lint-results-debug.html。
- 驗收截圖：phase1-screen.png；程序重啟畫面結構：phase1-restarted.xml。
- APK SHA-256：`0B03EFB4FBA11A2494CCD639BC47C45F057194725C2D025CEAEDB4865CFBF9E3`。

## 待辦與驗收界線

- [x] Phase 2：完整 CRUD、完成、分類、搜尋、月／週／日／清單、全天／跨日／無時間待辦與重啟驗證。
- [ ] Phase 3：獨立 Reminder、多提醒、AlarmManager、權限、重啟／時間／時區恢復與真機驗證。
- [x] Phase 4（模擬器驗收，真機待測）：CameraX、多張照片、描述、以拍攝時間保存、檔案生命週期。
- [ ] Phase 5：真實 Google 登入、唯讀授權、選擇、範圍、分頁、預覽、確認、去重與重複事件。
- [ ] Phase 6：完整設定、Migration 升級測試、錯誤流程、品質與 APK 驗收。

Phase 1 的一般記事新增僅供端到端基礎驗證，並非宣稱完整 Phase 2 已完成。沒有測試提醒、拍照或 Google 匯入。

## 外部設定

Phase 1 不需 OAuth 或 API 憑證。Phase 5 仍需使用者的 Google Cloud 專案、啟用 Calendar API、OAuth 同意畫面與測試使用者、Android OAuth 用戶端（本專案套件名稱與實際簽署 SHA-1），以及登入流程需要的 Web client ID。不得編造憑證。
屆時由 signingReport 取得實際指紋；使用測試帳戶完成真實授權，列出行事曆、讀取指定範圍事件，再重複匯入驗證去重。僅要求 Calendar 唯讀 scope，不寫回 Google。


## 實際執行的主要命令

```powershell
Get-Location
Get-ChildItem -Force
git status --short
java -version
# 找到 JDK 後，設定當次命令的 JAVA_HOME（不變更系統環境變數）
$env:JAVA_HOME='C:/Users/USER/.jdks/jdk-21.0.12.101-hotspot'
& 'C:/Users/USER/.gradle/wrapper/dists/gradle-9.3.1-bin/23ovyewtku6u96viwx3xl3oks/gradle-9.3.1/bin/gradle.bat' wrapper tasks --all --console=plain
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain
# adb 使用 SDK platform-tools 的完整路徑
adb devices
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am force-stop com.nqutaoitfteikc.notehub
adb -s emulator-5554 shell am start -W -n com.nqutaoitfteikc.notehub/.MainActivity
```

亦執行了 adb getprop、畫面輸入、uiautomator dump、screencap / pull 及 APK Get-FileHash。

## 實際檔案變更

- 建置：build.gradle.kts、app/build.gradle.kts、gradle.properties、gradle/libs.versions.toml、gradle/wrapper/gradle-wrapper.properties、gradle/wrapper/gradle-wrapper.jar、gradlew、gradlew.bat、.gitignore。
- 主程式（app/src/main/java/com/nqutaoitfteikc/notehub/ 下）：MainActivity.kt、NoteHubApplication.kt、domain/Notes.kt、data/NoteDatabase.kt、data/LocalSettingsRepository.kt、ui/HubViewModel.kt、ui/NoteHubApp.kt。
- 設定：app/src/main/AndroidManifest.xml、app/src/main/res/xml/backup_rules.xml、app/src/main/res/xml/data_extraction_rules.xml。
- 結構：app/schemas/com.nqutaoitfteikc.notehub.data.NoteDatabase/1.json。
- 測試：NoteUseCasesTest.kt、NavigationTest.kt、PersistenceTest.kt（各自位於 app/src/test 或 app/src/androidTest 同套件目錄）。
- 文件：README.md、PROJECT_STATUS.md；另保留建置日誌、畫面結構與驗收截圖。

下一階段：Phase 2。先擴充事件時間／分類／完成狀態模型並設計 v1→v2 Migration，再實作 CRUD、搜尋及四種行事曆視圖，不先引入通知或 Google API。

## Phase 2 開始時的範圍（已完成）
範圍：v1→v2 無損 Migration、事項 CRUD／完成／分類／搜尋、全天／跨日／無時間待辦、月／週／日／清單。先驗證資料與日期邏輯，再實作 UI 與模擬器驗收。


## Phase 2 完成紀錄（2026-10-15）

- 已完成 CRUD、完成狀態、自訂分類、搜尋、月／週／日／清單視圖、全天／跨日／無時間待辦與 v1→v2 Migration。
- 單元測試 10/10；模擬器測試 10/10（Android 17 / API 37）。真正跨程序重啟的兩次獨立 instrumentation 測試各 1/1 通過。
- 最後建置 `phase2-final-build.log` 成功，Lint 0 錯誤／21 警告；模擬器結果 `phase2-acceptance-device.log`。
- 已修正搜尋框與卡片測試選擇衝突、原生選擇器視窗綁定、語系資源封裝警告。
- APK 0.2.0：28,569,174 bytes，SHA256 F5918CA04C365A99BDDBA983A69BE17F80CED04178CBA0122302CDC71B2BCF7D。
- 修改：domain/Notes.kt、domain/CalendarRules.kt、data/NoteDatabase.kt、NoteHubApplication.kt、ui/HubViewModel.kt、ui/NoteHubApp.kt、ui/CalendarScreen.kt、ui/NoteEditor.kt、ui/PickerContext.kt、schema v2、單元與 Android 測試、scripts/Test-Android.ps1、README、建置版本。
- 未測試實體裝置與其他 Android 版本；無新增外部依賴。

## Phase 3 實作範圍

範圍：獨立 Reminder 資料列與修訂識別、多提醒 UI、AlarmManager、通知權限、精準鬧鐘限制、取消／重排、重開機／時間／時區／套件更新恢復。每個提醒以絕對時間儲存；無精準鬧鐘權限時明確標示為可能延遲的非精準排程。實體裝置驗收仍需外部裝置。


## Phase 3 實作與驗收結果

- [x] Reminder 獨立資料表、UUID / revision / 觸發時間／啟用狀態／送出時間／排程狀態；Room v2→v3 Migration 與外鍵 CASCADE。
- [x] 多提醒新增、修改、停用、移除與個別狀態 UI；設定頁的通知／精準鬧鐘／省電提示。
- [x] 唯一 URI 的 immutable PendingIntent、AlarmManager exact／inexact 明確區分、通知頻道、通知點擊導覽。
- [x] 事項更新、完成、恢復、刪除的排程與通知處理；資料庫條件領取防止重複投遞；revision 拒絕過期廣播。
- [x] 開機、套件更新、時間／時區變更、精準鬧鐘授權、前景恢復；WorkManager 僅持久重試恢復工作。
- [x] 過期 15 分鐘的處理、取消已顯示通知、權限拒絕與可能延遲的狀態。
- [ ] 實體裝置／其他 Android 版本／真實 Doze 與廠牌省電驗收；沒有對這些環境宣稱通過。

### 真實執行結果

- `phase3-final-build.log`：assembleDebug、testDebugUnitTest、lintDebug 全部成功。
- 單元測試 **14/14**：既有 10 項 + 提醒權限策略、完成／停用、過期、舊修訂／重複／提早廣播 4 項。
- 完整模擬器測試 **17/17**，結果 `phase3-acceptance.log`／`android-acceptance-device.log`；實際授權環境執行，沒有以假 scheduler 冒充系統回傳。
- 實際 AlarmManager：兩筆分別約 3 秒、7 秒後觸發，系統通知清單各有獨立通知；重複 deliver 未改變通知 postTime，完成事項後通知移除。此測試確認 30 秒內收到通知，未宣稱毫秒級準時。
- 實際修改提醒：revision 更新、舊 PendingIntent 消失、晚到舊廣播不發送；刪除一筆不影響另一筆，完成／恢復與刪除事項狀態正確。
- 提醒 UI 測試：新增兩筆、編輯停用一筆、移除一筆，其他資料保留。
- 資料庫測試：實際 v1→v3、v2→v3 升級保留舊記事，外鍵連動刪除提醒。
- 權限三情境各 **1/1 通過**：BLOCKED_NOTIFICATION → APPROXIMATE → EXACT，同一筆持久化提醒恢復；結果 `phase3-permission-*.log`。
- **實際重開 emulator-5554**：開機前後 AlarmManager 的兩個 origWhen 相同。準備與資料驗證各 **1/1**，結果 `phase3-recovery-final.log`、`phase3-alarms-before-reboot.log`、`phase3-alarms-after-reboot.log`；`phase3-recovery-broadcasts.log` 觀察到真實 BOOT_COMPLETED。
- **實際切換 GMT→Asia/Taipei**：觀察到 App 處理 TIMEZONE_CHANGED，兩個原始觸發時間不變，資料驗證 **1/1**；結果 `phase3-timezone-run.log`、`phase3-timezone-broadcasts.log`。
- 所有權限／時區測試皆在 finally 還原原始狀態（通知未授權、精準鬧鐘 default、時區 GMT），測試記事已清理。
- Lint：**0 errors / 23 warnings**，為依賴版本更新與原有範本資源／label 警告；沒有停用 Lint 或隱藏新錯誤。
- APK 0.3.0：SHA256 `1BC10D1FE91506C0F49F26B1091A71C20C81352513A3B3BB55C7F3350FFBD493`。

### 曾失敗的檢查與修正

- 核心編譯發現 nullable let 導致 delete 回傳 Unit?；已修正為 Unit 並重建成功。
- 通知取消的系統狀態為非同步，第一次測試過早斷言；改等待 NotificationManager 實際移除後通過，未以固定延遲假裝成功。
- 第一次開機測試被 instrumentation 的強制停止行為干擾：系統顯示 mBroadcastConsumerSkip，開機廣播遭取消。測試改為先由外部 dumpsys alarm 確認，再開啟資料驗證，重開機實測通過。
- 初始權限測試命令遭自動審查拒絕，因未包含承諾的還原步驟；改成先保存狀態與 finally 還原後獲准並執行，沒有繞過拒絕。
- 修正 API 31 精準鬧鐘廣播常數在低版本的使用條件，Lint 不再出現該新警告。

### 修改檔案

- domain/Reminders.kt、domain/Notes.kt：提醒模型、Use Cases 與序列化事項副作用。
- data/ReminderDao.kt、data/NoteDatabase.kt、schema 3.json：提醒表、Migration 與原子領取。
- reminders/ReminderPlatform.kt、ReminderCoordinator.kt、ReminderReceivers.kt：真實 AlarmManager／NotificationManager、恢復與 WorkManager 重試。
- MainActivity.kt、NoteHubApplication.kt、AndroidManifest.xml、ic_notification.xml：依賴容器、權限／廣播接收器、通知導覽與圖示。
- ui/RemindersScreen.kt、HubViewModel.kt、NoteHubApp.kt、CalendarScreen.kt、NoteEditor.kt：多提醒 UI 與權限導覽。
- ReminderRulesTest、ReminderDeviceTest、ReminderMigrationTest、ReminderPermissionStateTest、ReminderRecoveryTest、ReminderUiTest，及更新既有 MigrationCrudTest。
- gradle/libs.versions.toml、app/build.gradle.kts：集中新增 WorkManager 2.10.5；版本 0.3.0。
- scripts/Test-Android.ps1、Test-ReminderPermissions.ps1、Test-ReminderRecovery.ps1、Test-ReminderTimezone.ps1，README.md、PROJECT_STATUS.md、.gitignore。
- 修改前備份在 `.phase3-backup-*`，仍無 Git 儲存庫。

### 驗收限制與外部項目

- 沒有實體裝置，不能宣稱真機或廠牌省電測試通過。實際系統時間跳動、通知頻道手動關閉、其他 API 版本仍待驗證；已實作對應處理。
- 資料庫先領取發送權以避免重複，若程序恰好在領取與系統通知呼叫之間被終止，可能少一次通知；不承諾跨資料庫與系統服務的原子 exactly-once。
- 強制停止與撤回精準鬧鐘權限可能讓系統取消排程／停止程序，使用者需重新開啟 App 恢復；介面已提示。
- 下一階段 Phase 4：CameraX、多張照片、預覽／描述、拍攝瞬間時間、檔案保存與重啟驗證。Phase 3 不需 OAuth 外部設定。

## Phase 4 開始時的範圍（已實作）
範圍：Room v4 照片／清理佇列、拍攝時間不可變、CameraX、多張照片與草稿、預覽／描述／清理、重啟與 Migration 驗證。



## Phase 4 實作與驗收結果（2026-10-15）

- [x] CameraX 1.5.3：Preview／ImageCapture、相機權限與設定、鏡頭切換、生命週期綁定、拍攝錯誤與逾時處理。
- [x] 拍攝時間由 sensor realtime＋拍攝時 clock 校準，否則使用 onCaptureStarted；拒絕無有效拍攝時間的影像，沒有使用儲存時間替代。
- [x] 一篇多張照片、持久化草稿、JPEG 原圖、方向資訊、縮圖與完整預覽、每張說明、追加／移除與記事描述編輯。
- [x] 記事時間首次建立後不可改寫，新增／刪除照片或修改描述皆保留。
- [x] Room v4 Migration、照片外鍵、交易式清理佇列、WorkManager 清理重試、殘留檔案清理；與提醒取消整合。
- [x] 實際檔案與資料庫測試、實拍整合測試、App 冷啟動後的原圖與畫面驗證。
- [ ] 實體裝置、其他 API、前鏡頭與裝置旋轉、相機佔用／撤權、實際磁碟耗盡測試。

### 真實結果與命令

- `phase4-data-build.log`：先完成照片資料層並建置通過，才加入相機 UI。
- `phase4-final-build.log`：`:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` 全部成功。
- 單元測試 **17/17**，新增 3 項檢查 sensor clock 換算、fallback 拍攝開始時間、以最早拍攝時間建立記事。
- 完整模擬器測試 **23/23**，`phase4-acceptance.log`／`android-acceptance-device.log`；實拍測試有授權相機，未跳過。既有記事、行事曆、提醒測試亦通過。
- CameraX 實拍流程：拍兩張→Activity 重建→稍後輸入描述→儲存→編輯照片說明與記事描述→追加第三張→移除最早一張，原記事拍攝時間保持不變。
- **真正跨程序重啟**：`scripts/Test-PhotoRestart.ps1` 使用真實相機另拍一組，準備 **1/1**，force-stop 後 am start 回報 COLD／Status ok，另一個測試程序核對 **1/1**。
- 冷啟動驗證逐張核對 capturedAt、方向、timeSource、caption、JPEG SHA-256，並實際解碼、於 Compose 顯示。結果 `phase4-photo-restart-run.log`、`phase4-real-camera-stage.log`、`phase4-real-camera-restart.log`。
- 資料／檔案測試使用明確 JPEG fixture，另行測試延遲儲存、冪等提交、修改／追加／刪除、草稿保留、孤立檔案清理、清理失敗後重試、越界檔名與非法 JPEG 拒絕；未將 fixture 當成相機輸出。
- Migration 驗證包含 v1→v4、v2→v4 及實際依 v3 schema 建庫升級，舊記事與提醒保留。
- Lint：**0 errors / 28 warnings**，主要依賴更新提示及既有範本資源／label；未略過 Lint 或隱藏編譯錯誤。
- APK **0.4.0**（versionCode 4），**34,240,917 bytes**。
- SHA-256：`1482759F0B8FFA8005B80376FD7FB96F56ADC61D5A0FD73561978A6161178AB7`。
- 相機／提醒測試會保存原權限並於 finally 還原；修正過測試腳本還原區段的一行重複授權，並核對實際狀態。現有通知／精準鬧鐘授權不被假設為上一階段的狀態。

### 實際修改

- `domain/Photos.kt`、`domain/Notes.kt`：照片模型、拍攝時間規則、Use Cases、照片記事時間不可變。
- `data/PhotoDao.kt`、`data/NoteDatabase.kt`、schema `4.json`：照片／清理佇列、Migration、caption-only 更新。
- `photos/PhotoFiles.kt`、`RoomPhotoRepository.kt`、`PhotoCleanupWorker.kt`：私有檔案寫入、交易、冪等提交、路徑檢查與可重試清理。
- `ui/CameraScreen.kt`、`PhotoScreens.kt`、`HubViewModel.kt`、`NoteHubApp.kt`、`NoteEditor.kt`、`CalendarScreen.kt`：相機、照片頁、草稿、預覽、編輯與導覽。
- `NoteHubApplication.kt`、`AndroidManifest.xml`：容器、拍攝執行緒、相機權限與非必要 camera feature。
- `gradle/libs.versions.toml`、`app/build.gradle.kts`：集中 CameraX 相依與 0.4.0 版本。
- `PhotoTimeRulesTest.kt`、`PhotoRepositoryTest.kt`、`PhotoCaptureUiTest.kt`、`PhotoRestartTest.kt`，並更新既有 Migration 測試。
- `scripts/Test-Android.ps1`、`scripts/Test-PhotoRestart.ps1`、README、PROJECT_STATUS、.gitignore；備份在 `.phase4-backup-*`。

### 限制與下一階段

- 真機／廠牌相機、其他 Android 版本與實際儲存耗盡尚未驗證；不宣稱已通過。
- 裝置不提供可換算感光元件時間時，以開始拍攝回呼時間為準，可能有回呼延遲；仍不以存檔或描述儲存時間替代。
- 照片草稿持久化，尚未儲存的文字僅透過畫面狀態保留，不能保證手動強制停止後還在。
- 原圖只存在 App 私有資料，解除安裝會刪除；目前沒有匯出、外部相簿或雲端備份。
- 下一階段 Phase 5：Google Identity Services 登入、唯讀 Calendar 授權、行事曆與日期範圍選擇、事件預覽確認、分頁、去重與重複事件處理。需真實 Google Cloud／OAuth 設定，不能編造憑證或連線成功結果。


## Phase 5 實作與驗證（2026-10-15）

範圍：Google 登入與獨立唯讀授權、真實 HTTPS Calendar API、行事曆／日期選擇、全分頁預覽、選取確認、重複事件展開與去重、Room v5 原子匯入、設定說明。尚未進入 Phase 6。

### 已完成

- [x] Credential Manager Google 登入與 GIS AuthorizationClient；明確綁定登入帳號，只要求 calendarlist.readonly／events.readonly。
- [x] 使用者提供的真實 Web client ID 已存於 local.properties；缺設定時可建置，UI 明確提示並停用登入。沒有編造 ID、secret 或 API key。
- [x] CalendarList／Events 真實 GET、分頁 token、迴圈／頁數／筆數／回應大小上限、逾時與取消、401／403／429 等繁體中文錯誤。無寫入／刪除 Google API。
- [x] 行事曆多選、可輸入／選取起訖日期、時區與範圍顯示、預覽勾選及二次確認、匯入結果。
- [x] singleEvents 展開重複事件、取消事件略過、原始發生時間作移動例外去重鍵、全天 exclusive end、跨日與時差保留。
- [x] Room v5 來源複合主鍵與交易式匯入；重匯不覆蓋本機編輯；已刪除副本預設不選，可手動勾選重新匯入。沒有將匯入實作成自動雙向同步。
- [x] 登出清除記憶體存取狀態；本機記事／來源識別保留。權杖不寫入資料庫、設定或日誌。
- [x] GOOGLE_SETUP.md 提供實際套件／簽章、Cloud 設定、連線驗收步驟與限制。
- [x] 真實帳號登入、唯讀同意、CalendarList／Events API、確認匯入、重匯去重；後續手動重匯與冷啟動亦已完成。
- [ ] 真實大資料分頁、撤銷 Google 同意、其他 API／實體裝置測試。

### 真實建置與測試

- 資料層先驗證：`phase5-data-build.log`，`:app:assembleDebug :app:testDebugUnitTest` 成功後才串接 UI。
- 最新 `phase5-final-build.log`：`:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` 全部成功。
- 單元測試 **20/20**，新增日期範圍、DST 日界線、反向／過大範圍測試。
- `phase5-acceptance.log`：未設定 OAuth 的整套模擬器測試 **30/30**，包含缺設定 UI、6 項匯入 fixture／Room 測試，以及既有記事、提醒、真實 CameraX 流程。
- `phase5-configured-acceptance.log`：加入使用者 Web client ID 後，JUnit 回報 **OK (32 tests)**；其中原本的「缺設定」UI 測試因條件不符跳過 1 項，**31 項實際執行通過**。新增格式限制／瞬間事件提示與資料庫觸發器故障整批回滾，8 項匯入資料測試全部執行。
- 後續已將入口 UI 測試改為兼容有／無設定、驗證登入按鈕狀態；安裝後獨立執行 **1/1 通過**（phase5-configured-ui-test.log），不再跳過已設定入口。
- 匯入測試的 JSON／權杖名稱均為明確 fixture，只注入測試 transport，不在正式程式提供模擬成功。
- 分頁 fixture 驗證第二頁、重複頁拒絕、後續頁 403 不回傳半批結果；Room 驗證去重、本機修改、刪除後重匯、關庫重開、帳號分離與整批回滾。
- Migration：v4 真實 schema 建庫後升級，保留照片 metadata／記事時間；既有 v1／v2／v3 升級測試亦通過。
- Lint **0 errors / 29 warnings**；未略過 Lint。新增的一項為相容舊版 googleid 更新提示，其他主要為既有資源與套件更新提示。
- 先嘗試 googleid 1.2.1，實際錯誤是 Kotlin metadata 2.4 與專案 2.2 不相容；改用官方穩定 1.1.1。沒有強制跳過 metadata 驗證。
- 修正過 Gradle java extension 與 Properties 名稱解析衝突、InputStream.readNBytes 僅 API 33 可用問題；改用 min API 28 可用的有界讀取，重建／Lint 成功。
- 設定後 APK 已安裝於 emulator-5554，實際冷啟動 Status ok，從設定進入匯入頁並呼叫 Credential Manager，Google 顯示「選擇帳戶／新增其他帳戶」，之後進入 Google 帳戶新增流程。**沒有取得真實 access token 或 Calendar 回應，因此不宣稱真實匯入通過。**
- 最新錯誤診斷版已安裝並再次冷／暖啟動；最後針對 reauth 細分的一句錯誤文案已重新建置、單元測試、Lint 成功，尚未覆蓋使用者正在重試的版本。
- 裝置測試腳本保存並於 finally 還原相機／通知／精準鬧鐘狀態：camera=True、notification=True、exact=allow。
- 最新 APK **0.5.0 / versionCode 5**，**36,753,427 bytes**，SHA-256 `F65D8D6E216695943A7AB27CD280DC8C874ED75B431FF1F3E8F0B29865FF267B`。

### 實際檔案

- 新增 `domain/CalendarImport.kt`：來源模型、日期範圍、確認匯入 Use Case。
- 新增 `data/CalendarImportDao.kt`；修改 `data/NoteDatabase.kt`，新增匯入識別表、Migration 4→5，產生 schema `5.json`。
- 新增 `calendar/GoogleIdentity.kt`、`GoogleCalendarApi.kt`：真實登入／唯讀授權、固定 HTTPS GET 與事件解析。
- 新增 `ui/CalendarImportViewModel.kt`、`CalendarImportScreen.kt`；修改 NoteHubApp／HubViewModel／NoteHubApplication 完成依賴與設定導覽。
- 修改 AndroidManifest 的 INTERNET 權限、app/build.gradle.kts 的 Web client ID 設定與版本、libs.versions.toml 的集中相依。
- 新增 ImportRangeTest、CalendarImportTest、CalendarImportUiTest；更新 MigrationCrudTest、ReminderMigrationTest、PhotoRepositoryTest 的升級鏈。
- 修改 README、PROJECT_STATUS、.gitignore；新增 GOOGLE_SETUP.md。既有資料／程式未重建，備份 `.phase5-backup-20261015-203807`；工作目錄仍無 Git。

### 外部阻礙與下一步

- 使用者已提供 Web client ID 並實際選取帳號；SDK 回傳 GetCredentialCancellationException: [16] Account reauth failed.，因此不能宣稱是使用者主動取消。已修正提示並加入僅 debug 的遮蔽診斷。校準時鐘後等待重新驗證，接著仍需真實唯讀同意與 Calendar API 驗收。
- Cloud 擁有者仍須確認：Calendar API 啟用、與 Web client 同專案的 Android OAuth client、套件 `com.nqutaoitfteikc.notehub`、實際 debug SHA-1、OAuth 測試使用者。指紋與完整步驟見 GOOGLE_SETUP.md，不假設這些設定已完成。
- 下一步：完成登入後執行授權→行事曆選擇→真實預覽→確認匯入→重匯驗證，並補驗最新 APK 入口。Phase 5 不能在這些步驟完成前標為完整驗收通過。
- Phase 6 待此階段回報後進行：設定／錯誤流程整體檢查、權限／主題、必要 Migration 與核心流程回歸、最終 APK；真機限制仍須保留。


### Phase 5 登入失敗實際診斷補記

- 使用者回報選取帳號後自動返回且顯示取消；重試後 debug 專用 NoteHubGoogle 標籤實際記錄 `[16] Account reauth failed.`，見 phase5-google-diagnostic.log。未取得 access token，尚未呼叫正式 Calendar API。
- Google 官方文件說明 CancellationException 也可能由技術／設定問題造成；不再把它單純翻成「已取消 Google 登入」，不自動循環重試。
- 實際量測主機 UTC 12:57、模擬器 UTC 20:57，差約八小時；模擬器 auto_time=0、時區 GMT。shell 手動 suggest_manual_time 被 Android SUGGEST_MANUAL_TIME_AND_ZONE 權限拒絕，未生效；隨後暫開自動校時並 force_refresh 成功，兩者 UTC 已對齊，finally 還原 auto_time=0，時區未變更。
- 已請使用者在校時後手動重新點登入，不能在重試結果出現前把時鐘誤差認定為唯一根因。若仍失敗，應核對 Cloud Android OAuth 的 package／SHA-1 與 Web client 是否同專案，再檢查帳戶重新驗證狀態；不需要刪除帳號或提供密碼。
- Google Play 服務實際 versionName 26.39.33；未清除 Google 服務資料、未移除 Google 帳號、未修改 Cloud 授權設定。
- 最新 20 項單元測試與 Lint 通過；裝置驗證累計覆蓋 32 項（完整執行 31＋補跑設定入口 1），最後只有錯誤文字細分尚未再次裝置測試。Phase 5 真實登入與匯入依然未通過，不能列為已完成驗收。

### 2026-10-15 21:27 登入與時間補驗

- 使用者回報再次失敗且畫面時間未修正。實際主機 UTC 13:26:19、模擬器 UTC 13:26:18，epoch 正確；畫面仍是 GMT，這是先前保留時區導致的顯示落差。
- 已永久改為 Asia/Taipei，自動校時開啟（auto_time=1）、自動時區關閉（auto_time_zone=0）。裝置顯示 21:27 CST、UTC 13:27，與主機臺北時間一致；不再恢復會造成困惑的 GMT。
- 校時後 Google 仍實際回傳 `[16] Account reauth failed.`（UTC 13:24:52／13:25:35），因此校時不是足以解決登入的修正。未取得 access token，也未通過真實 Calendar 匯入。
- 直接以 SDK apksigner 驗證 APK SHA-1 為 69:F1:3B:65:8A:7B:E6:1E:83:82:C7:20:39:A1:6A:0F:F7:2B:2F:6A；BuildConfig 使用的 Web client ID 與使用者提供值相符。
- 最新細分 reauth 錯誤文字的 APK 已 install -r 成功，MainActivity 在前景；未再次自動觸發登入。沒有修改業務程式或資料庫，未重跑已通過的單元／資料測試。
- 已詢問 Cloud 中是否存在同專案、正確 package 與 SHA-1 的 Android OAuth client；尚無可驗證回覆，不能擅自認定已設定或代碼 16 的唯一原因。

### 最新登入根因／28444 補記（2026-10-15）

- 使用者已確認同專案 Android OAuth client 的套件與 SHA-1 相符，不再重複要求建立相同 Android client。
- Google 21:28 紀錄顯示帳戶重新驗證流程完成；後續 GetTokenResponseHandler 明確回傳 `You must use a Web client as the server client ID.`，並拋出 `[28444] Developer console is not set up correctly.`。已遮蔽且只篩選相關錯誤的紀錄在 phase5-google-root-cause.log。
- 當前 google.webClientId 確實與使用者前次提供值相同，但該值不被 Google 接受為此要求需要的 Web client。不能僅憑 client ID 的通用結尾判斷類型。
- 已請使用者提供同專案「Web application／網頁應用程式」類型的 OAuth client ID，保留既有 Android client；沒有要求 secret。
- 修改 CalendarImportViewModel，對已觀察的 GetCredentialCustomException 28444 顯示精確繁體中文設定提示。沒有因誤判而更換 SDK、降級登入 API、自動重試或假造成功。
- phase5-oauth-diagnostic-build.log：assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 全成功；20 項單元測試無失敗，Lint 0 errors／29 warnings。
- 最新 APK 已 install -r 成功，phase5-oauth-configured-ui-test.log 的實際登入入口測試 1/1 通過，未觸發 Google 授權。既有資料／全套測試在此輪未重複執行，沿用先前結果，不宣稱本輪重跑。
- 當時 APK 37,321,599 bytes，SHA-256 D2B433A3D50C40EC1885F3CF56B27B1C1426350E1BA0661988D72AA85A83BFFE。
- 下一步等待正確 Web client ID 後更新 local.properties、重新建置安裝並驗證真實登入／Calendar 唯讀授權與匯入。Phase 5 真實驗收仍未通過。

### 新 Web client ID 已設定（2026-10-15 21:42 臺北）

- 收到使用者另行提供的 Web client ID（識別段 klgtl94e…），更新 local.properties，沒有新增 secret 或變更既有 Android client。
- phase5-web-client-build.log：assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 全成功；20 項單元測試通過、Lint 0 errors／29 warnings。
- App 與測試 APK install -r 成功；phase5-web-client-ui-test.log 的入口測試 1/1 通過。MainActivity 實際 COLD 啟動 Status ok，已導向 Google 匯入頁，沒有自動重試或代替使用者選帳號。
- 最新 APK 0.5.0，37,321,599 bytes，SHA-256 0B2C9373A0417CBA57BC36B9D64701383CD58FDB1DDACFFE4F0990F92ED75FED。
- 模擬器實際顯示 21:42 CST，Asia/Taipei 與自動校時保持正常。
- 已請使用者手動點登入、選取帳號，成功後明確授予 Calendar 唯讀權限。收到新結果前，不能宣稱 28444 已排除、登入成功或真實 Calendar API 已通過。
- 本輪只變更 client ID 設定與文件，未改動資料模型或登入 SDK；不重複執行已通過的整套資料測試。

### 新 Web client 真實登入／API 驗證與 403 處理

- 使用者完成新 Web client 的登入與唯讀同意後，實際畫面已進入授權後的行事曆選擇頁；首次 CalendarList 請求曾回傳 403，當時尚未讀取 error JSON，所以不能將其斷定為未啟用 API。
- GoogleCalendarApi 新增有界 error body 解析（最多約 64 KiB），只取 errors／details 中安全格式的 reason，不保存原始伺服器文字、權杖或個人事件。區分 SERVICE_DISABLED／accessNotConfigured、權限不足、限流、配額等繁體中文錯誤。
- CalendarImportViewModel 的 debug 診斷僅列 HTTP status／reason；API 失敗不再同時顯示「沒有可讀取的行事曆」，重新載入會先清除舊選取與預覽，避免確認過期預覽。
- 新增一項明確 fixture 測試，驗證 API 未啟用／授權範圍不足／403 限流分類及不合法 reason 不顯示；不是假成功或假 Google 資料。
- phase5-api-error-build.log：App／測試 APK、20 項單元測試與 Lint 全成功（0 errors／29 warnings）。phase5-api-error-device-test.log：9 項資料／錯誤解析＋入口測試，10/10 通過；最後修正空清單提示後 phase5-api-final-ui-test.log 入口 1/1 通過。沒有宣稱本輪重跑整套 33 項測試。
- 最新版本安裝成功、MainActivity COLD 啟動 Status ok。APK 37,322,779 bytes，SHA-256 71E492DF22237E5816E56D7EB4980056F78576D18D7663EF79DFA7225AFE5872。
- 使用者回覆「已成功」後，唯讀 UI 核對顯示已登入／授權後的操作、5 個行事曆勾選框與事件預覽入口，沒有 API 錯誤。此清單由正式 Google CalendarList API 載入，沒有 fixture 注入正式流程。
- **已驗證真實 Google 登入、Calendar 唯讀同意、CalendarList 清單 API 成功。** 先前 28444 已在新 Web client 流程排除。先前 403 的確切原因未取得，不能把修正 error parser 當成解決 Cloud 設定的原因。
- 已請使用者選擇包含事件的範圍、確認匯入至少一筆，再同範圍預覽驗證「已匯入，將跳過」。收到結果並核對前，Events API／真實匯入去重仍未列為通過。

### 真實 Events 400 與 RFC3339 修正

- 使用者回報預覽或匯入失敗；實際 NoteHubGoogle 診斷為 Events 讀取 HTTP 400／reason=badRequest（phase5-events-diagnostic.log），尚未進入 Room 匯入交易。
- 找出日期範圍產生方式使用 OffsetDateTime.toString()，午夜會省略秒數（例如 T00:00+08:00），不符合 Google Calendar timeMin／timeMax 要求的 RFC3339 完整時間。
- ImportRange 改為先依所選時區取當日午夜，再轉 Instant 的 ISO 字串（例如臺北 2026-10-15 → 2026-10-14T16:00:00Z），保留含起訖日／exclusive end 邏輯與 DST 日界線。
- 新增 ImportRangeTest 回歸測試：先執行舊程式，phase5-rfc3339-reproduction.log 實際 4 項中 1 項失敗；修改後 phase5-rfc3339-fixed-build.log 的 assembleDebug／assembleDebugAndroidTest／testDebugUnitTest／lintDebug 全成功。
- 最新單元測試 **21/21**，0 failures；既有 DST 23 小時日與日期限制測試仍通過。CalendarImportTest 的分頁 transport fixture 加入對實際送出 timeMin／timeMax 含秒 UTC 格式的斷言，避免測試 transport 接受正式 API 不接受的時間。
- 修正版已 install -r 成功，phase5-rfc3339-device-test.log 的匯入資料／請求／錯誤與入口測試 **10/10** 通過。App 已啟動，未建立任何未確認的真實事件副本。
- 嘗試接回既有單一帳號授權的畫面操作未完成，沒有藉此宣稱登入或預覽成功；已改請使用者從修正版手動登入／授權並重試相同範圍。
- 最新 APK **0.5.0**，**37,322,779 bytes**，SHA-256 **E842FD00B2AC10A52A395221DB7CE26925D3035C1EE971EEE95C688F1B1C24DE**；Lint 0 errors／29 warnings。
- 這是已修正且有回歸驗證的 App 請求格式缺陷，Google 是否已接受修正版並回傳真實事件仍待實際重試，不能以 fixture 測試替代該驗收。


## Phase 5 最終驗收與手動重匯需求（2026-10-15 22:07 臺北）

### 真實 Google 與新增行為

- 使用者確認真實預覽／匯入成功、同範圍重讀顯示跳過；唯讀 UI 核對也觀察到 3 筆真實事件，3 筆均已匯入、確認匯入選取數為 0，無新的 API 錯誤。RFC3339 修正版已排除先前觀察的 Events 400。
- 使用者新增需求已實作：仍存在的本機副本不可勾選覆蓋；已刪除副本預設不勾選，但允許手動勾選重新匯入。重匯建立新記事並於同一交易更新來源指向，保留相同來源唯一鍵，區分新增／重新匯入／跳過數量。
- 確認匯入時會再次檢查來源實際是否仍缺少記事，過期預覽不會覆蓋已被其他操作重建／編輯的副本。交易失敗時，來源指向與記事新增一併回滾。重新匯入不恢復先前已刪除的本機修改或提醒。
- 使用者已在真實事件上確認手動重新匯入。只讀資料核對最後為 **3 筆來源識別、1 筆仍存在的本機副本**，符合只重建所選項目，其餘已刪除副本未自動重建。

### 最終驗證

- `phase5-restore-build.log` 與 `phase5-final-acceptance.log`：assembleDebug／assembleDebugAndroidTest／testDebugUnitTest／lintDebug 全部成功。
- 單元測試 **22/22**；新增回歸涵蓋完整 RFC3339 秒數與日期界限，以及已刪除副本可選但預設未選。
- 完整 Android runner 回報 **OK (38 tests)**；其中 2 項真實資料冷啟動測試因需要明確 googlePersistenceRun 參數，在一般套件中依預期跳過，**36 項一般裝置測試實際執行通過**。包含真實 CameraX／提醒等既有回歸。
- 已刪除副本測試包含：預設不重建、明確選取重建、來源唯一鍵不增加、舊預覽不覆蓋新本機編輯、來源更新與記事寫入失敗的整批回滾。
- 另有 2 項 Compose 元件測試，以明確測試資料驗證已刪除項目預設未勾選、可勾選／取消；現存項目無法勾選覆蓋。不以元件測試冒充 Google API 成功。
- 新增 `scripts/Test-GoogleImportPersistence.ps1` 與 `CalendarImportPersistenceTest`，只讀真實 App 資料庫，比對來源與記事全欄位的 SHA-256，不輸出帳號、標題、描述或權杖，不新增／刪除使用者記事。
- 第一次只讀驗證未符合前置條件：實際來源為 3，但本機副本為 0，因此測試明確失敗，沒有宣稱持久化通過。當時來源識別仍在；沒有自動重建缺少的副本。
- 使用者手動重新匯入後再次執行，`phase5-google-persistence-run.log`：準備 **1/1**，am force-stop 後 MainActivity **LaunchState COLD / Status ok**，另一個獨立測試程序比對 **1/1 通過**。
- `phase5-google-persistence-counts.log`：重啟前後皆為來源 3／記事 1，內容摘要完全一致。來源中已刪除項目的狀態也保留。測試用快取只存摘要並於驗證後移除。
- Lint **0 errors / 29 warnings**。一般裝置測試的相機／通知／精準鬧鐘授權在 finally 還原；臺北時區與自動校時修正仍保留。
- 最新 APK **0.5.0 / versionCode 5**，**36,753,427 bytes**，SHA-256 **F65D8D6E216695943A7AB27CD280DC8C874ED75B431FF1F3E8F0B29865FF267B**，已安裝於 emulator-5554。

### 本輪檔案與待辦

- `domain/CalendarImport.kt`：修正 API 日期格式、缺少副本旗標、預設選取規則、重新匯入計數。
- `data/CalendarImportDao.kt`：來源與本機存在狀態的 LEFT JOIN、條件式重建／原子更新；資料表未變動，維持 Room v5，不需新 Migration。
- `calendar/GoogleCalendarApi.kt`、`ui/CalendarImportViewModel.kt`、`ui/CalendarImportScreen.kt`：有界 API reason 解析、避免失敗假空清單、手動重匯 UI 與確認文案。
- 新增／更新 `ImportRangeTest`、`ImportSelectionTest`、`CalendarImportTest`、`CalendarImportSelectionUiTest`、`CalendarImportPersistenceTest`；新增 `scripts/Test-GoogleImportPersistence.ps1`，更新 README、GOOGLE_SETUP、PROJECT_STATUS。
- Phase 5 的核心驗收已完成；下一階段 **Phase 6 設定與品質檢查**，再做核心流程整體檢查與最終 APK 整理。
- 實體裝置／廠牌限制、其他 Android API、真實超過 250 筆的分頁、真實重複事件例外／撤銷授權／配額情境仍待外部測試。分頁、重複事件與相關錯誤目前有明確 fixture 測試，未宣稱這些都已在真實帳號上測過。
