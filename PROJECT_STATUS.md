# 專案進度

更新：2026-10-16（Asia/Taipei）

目前：0.9.0 已完成照片預覽裁切修正與指定螢幕適應驗證。已建置、簽章驗證並更新安裝；28 項單元測試、最終 5 項版面＋直向實拍、橫向真實點擊／鍵盤／儲存，以及既有匯入資料冷啟動核對通過。完整回歸的中途失敗與後續針對性重測如實記錄於 0.9.0 章節，未宣稱最終版整套裝置測試一次全過。

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

## Phase 6 範圍與進度（2026-10-16）

- 開始時 Git 已建立（HEAD b499b8c），工作目錄乾淨；保留現有專案，基線封存為 .phase6-baseline.zip。
- 已確認 JDK 21.0.12.1、既有 app 模組與 assembleDebug／testDebugUnitTest／lintDebug 任務；模擬器 emulator-5554 起初離線，裝置驗收待恢復連線。
- [x] 設定頁補齊相機權限／版本／時區／本機與 Google 資料說明。
- [x] 錯誤訊息可關閉、繁體中文語言宣告、照片目錄失敗不阻止容器初始化。
- [x] 各歷史 Room schema → v5 的升級資料保留與主題／權限／錯誤 UI 測試。
- [x] 建置、單元、Lint、裝置回歸與真實匯入資料只讀冷啟動核對。
- [x] 產出可安裝 APK、固定檔名與 SHA-256、更新驗收與外部限制。

## Phase 6 最終驗收（2026-10-16）

### 實作與修正

- 設定頁新增實際相機狀態、明確的使用者授權／系統設定入口，回到 App 時重新檢查權限；不在查看設定時自動要求相機。
- 設定頁使用 BuildConfig 顯示真實版本，並補上裝置時區、純本機保存、解除安裝資料刪除、Google 唯讀副本與手動重匯說明。
- 全域錯誤使用可關閉訊息卡；不修改原記事、也不掩飾驗證失敗。失敗驗證測試核對沒有產生新記事。
- 宣告繁體中文 localeConfig（API 33 以上生效），保留原本繁體中文介面與選擇器。Google／系統自有畫面不由 App 強制改語言。
- 修正 PhotoFiles 建構時立即建立目錄的問題：改於實際寫入回報 IOException；不存在的空目錄無須清理，無法讀取的已存在目錄仍明確回報。測試使用被一般檔案阻擋的路徑，不冒充真實磁碟耗盡。
- App 升版 0.6.0／versionCode 6；Room 維持 v5，資料表未變動，未新增無必要的 Migration，未使用 destructive fallback。

### 真實命令與結果

- `phase6-environment.log`：使用 JDK 21.0.12.1，確認既有 app 任務。裝置起初 offline，僅以 adb reconnect offline 恢復既有模擬器，沒有 wipe／重建裝置。
- `phase6-settings-build.log`：先驗證設定與儲存修正的 assembleDebug／testDebugUnitTest／lintDebug，全部成功。
- 第一輪 `phase6-acceptance.log`：新增設定測試在尚未組成的 LazyColumn 列直接 performScrollTo，造成 1 項測試定位失敗。修正為在清單上 performScrollToNode 後才斷言；未修改業務邏輯來繞過測試。
- 最終 `phase6-final-acceptance.log`：`scripts/Test-Android.ps1 -Serial emulator-5554 -GrantReminderTestPermissions -GrantCameraTestPermissions` 完成；其執行 assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 並安裝 App／測試 APK。
- **單元 22／22，0 failures。**
- Android runner **OK (44 tests)**；其中 2 項真實資料方法在一般套件中因未提供啟用參數而跳過，**42 項一般裝置測試實際執行通過**。測試環境重新核對為 **Android 17／API 37，emulator-5554**。
- 新增 6 項一般裝置測試：三種主題跨 Activity 重建／實際相機狀態與版本政策／錯誤關閉不產生記事（3），歷史 v1–v4 升至 v5（1），未知主題值 fallback（1），照片目錄阻擋不使建構失敗（1）。
- 全版本升級測試逐一依原始匯出 schema 建庫，Room 驗證最新結構，核對標題、描述、分類、完成狀態、時間／時區、提醒、照片與清理佇列，升級後仍可編輯。既有個別 Migration／CRUD／照片／提醒等測試也通過。
- 相機／通知／精準鬧鐘權限由測試腳本保存並於 finally 還原（本輪 camera=True、notification=True、exact=allow）。沒有為測試清除使用者資料。
- `scripts/Test-GoogleImportPersistence.ps1 -Serial emulator-5554` → `phase6-google-persistence-run.log`：兩個獨立測試程序 **1／1＋1／1 通過**，中間 am force-stop 後 MainActivity **COLD／Status ok**。
- `phase6-google-persistence-counts.log`：本輪既有真實資料為 **3 筆來源／3 筆本機副本**，重啟前後來源與記事全欄位摘要一致。此步只讀本機，不重新呼叫 Google 或重授權；Phase 5 的真實 API 成功紀錄仍獨立保留。
- **Lint 0 errors／29 warnings**。主要為既有版本更新提示、範本未使用資源、既有 KTX 建議及 localeConfig 僅 API 33 以上生效；未停用檢查或新增 baseline。Google ID 版本仍保留已驗證與 Kotlin 相容的 1.1.1。
- `git diff --check` 通過，Git 有 CRLF 自動換行提醒，非程式或空白檢查錯誤。未自動提交，變更可直接審閱。

### APK

- `dist/NoteHub-0.6.0-debug.apk`，固定檔名的可安裝驗收版；另保留 Gradle 原輸出 app/build/outputs/apk/debug/app-debug.apk。
- **37,512,663 bytes**；SHA-256 **2B8243A904F16140DB2F0EA6684F218F94B08F7B6F56971DB2E34D5D2E7B640B**，另寫入 dist/SHA256SUMS.txt。
- `phase6-apk-signature.log`：apksigner verify 成功，實際 SHA-1 為既有 69:F1:3B:65:8A:7B:E6:1E:83:82:C7:20:39:A1:6A:0F:F7:2B:2F:6A，沒有新增／更換金鑰。
- `phase6-apk-metadata.log`：套件 com.nqutaoitfteikc.notehub、versionCode 6、versionName 0.6.0、minSdk 28、targetSdk 36；已 install -r 成功並啟動。
- 此為 debug 簽章驗收版，非已簽發布金鑰的商店版本。若正式發布，須自行提供發布簽章並在 Cloud 登記對應 Android OAuth 指紋；沒有編造發布金鑰或假設該設定已完成。

### 修改檔案與外部限制

- `app/build.gradle.kts`、AndroidManifest、res/xml/locales_config.xml：版本與繁體中文語言宣告。
- `ui/NoteHubApp.kt`、新增 `ui/CameraPermissionSettings.kt`：可關閉錯誤與設定頁補齊。
- `photos/PhotoFiles.kt`：延後目錄建立與缺少目錄清理規則。
- 新增 `SettingsQualityTest.kt`、`MigrationCoverageTest.kt`；更新 PersistenceTest、PhotoRepositoryTest。
- .gitignore、README、PROJECT_STATUS；新增 RELEASE_NOTES.md 與忽略版控的 dist APK／摘要。修改前基線封存 `.phase6-baseline.zip`；現有 Git 修改未被覆蓋。
- 尚未完成的外部驗證：實體裝置、其他 Android API、廠牌 Doze／省電、真實磁碟耗盡、真實大量分頁／部分重複事件變更與撤權／配額情境。未將單元或 fixture 結果宣稱為這些環境已驗證。
- 本輪裝置 UTC 自動校時正常、目前時區 GMT，App 依裝置時區顯示且設定頁明示；本輪未改動使用者裝置時區。使用者可在系統選擇臺北時區，記事儲存的瞬間與全天日期規則不變。
- Phase 1–6 的核心開發與所列環境驗收完成；後續工作是上述外部裝置覆蓋、正式簽章／發布與使用者另行指定的產品調整。


## 0.7.0 介面整理（2026-10-16）

### 範圍與修改

- [x] 保留現有 Git 未提交修改與所有業務邏輯，未重建專案或清除本機資料。
- [x] `CalendarScreen.kt`：分類、狀態、顯示方式改為三個獨立下拉選單；長分類顯示省略但選單／無障礙描述保留全文，選單有最大高度並可捲動。
- [x] 行事曆搜尋、日期列、月曆與記事卡片使用圓角與明確層級；保留四種視圖、跨日、全天、待辦、編輯、完成、提醒、照片入口。
- [x] `Theme.kt`：統一藍色主色、淺灰／白色與深色配色；保留系統／淺色／深色設定，系統列圖示跟隨 App 實際主題。
- [x] `NoteHubApp.kt`／`NavigationSymbol.kt`：底部使用原生繪圖圖示與文字，保留既有導覽及狀態保存。
- [x] `CalendarDropdownTest.kt` 新增選單取消、篩選／視圖在 Activity 重建後保留的測試；既有 NavigationTest、ReminderUiTest 改以真正開啟選單操作。
- [x] PhotoCaptureUiTest 等待鍵盤關閉後捲動到描述；ReminderUiTest 等待儲存按鈕恢復可用；SettingsQualityTest 驗證三種主題對應的系統列圖示。
- [x] 版本 0.7.0／versionCode 7；Room schema 仍為 v5，沒有資料 Migration 或網路／授權邏輯變更。

### 實際命令與結果

以原 JDK 21 與既有 Gradle Wrapper 執行：

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --console=plain
./scripts/Test-Android.ps1 -Serial emulator-5554 -GrantReminderTestPermissions -GrantCameraTestPermissions
./scripts/Test-GoogleImportPersistence.ps1 -Serial emulator-5554
# 已設 JAVA_HOME 後，以 SDK build-tools/36.1.0/apksigner.bat verify --verbose --print-certs 驗證 APK。
```

- 最終紀錄 `ui-refresh-final-theme-acceptance.log`：BUILD SUCCESSFUL；22 項單元測試通過；Lint 0 errors／29 warnings（既有版本、資源等建議）。
- Android 17／API 37 模擬器 runner `OK (45 tests)`：43 項一般測試實際執行通過；2 項需 opt-in 的真實匯入資料驗證方法在此套件跳過，另以下列腳本分開執行。
- 最終 `ui-refresh-google-persistence-final.log`：真實匯入來源與本機副本在兩個獨立測試程序中摘要一致，force-stop 後確認 COLD 啟動，各 1／1 通過。這是既有資料只讀核對，沒有假裝本次重新呼叫 Google API。
- 中途一輪有 2 項間歇性 UI 測試失敗（描述輸入時鍵盤移動使節點被移除、第一筆 Room 寫入後尚未恢復按鈕就按第二次新增）；已補上實際非同步條件的等待，再完整重跑通過，沒有略過這些測試。
- 目視檢查實際淺色與深色月曆，發現並修正 App 與系統主題不同時的狀態列對比。檢查後還原原深色設定，測試腳本還原相機、通知與精準鬧鐘權限。
- `dist/NoteHub-0.7.0-debug.apk`：37,575,715 bytes，簽章 v2 驗證通過，已更新安裝。
- SHA-256：`4DF361B9F6816DEEAD1F986165CD9B7B797D37DCBEB600A019B6C24EADBD54B7`。

### 待辦與限制

- [ ] 實體裝置、其他 Android API、各廠牌省電設定與不同螢幕／超大字體仍需額外驗證，未宣稱全部環境通過。
- 本次不需新增外部設定；既有 Google Cloud 與 OAuth 設定仍適用。Google 真實連線的前次驗證及 API 分頁等限制沿用 Phase 5／6 紀錄。


## 0.8.0 日行程、詳細資訊與搜尋（2026-10-16）

### 本輪範圍與驗收

- [x] 先檢查目前工作目錄、Git 修改、現有 CalendarScreen／CalendarRules／NoteHubApp／測試；未刪除或覆蓋既有 Phase 6／7 工作。修改前目前來源封存在已忽略的 `.phase8-baseline.zip`。
- [x] 月曆點日期進入日檢視，提供返回月曆按鈕與系統返回鍵；選定日期保留。
- [x] 所有行程清單卡片可開啟詳細頁；編輯、提醒管理移至詳細頁，保留完成切換與照片入口。通知開啟同一詳細頁；刪除後回到記事頁，避免留在已刪除事件。
- [x] 搜尋範圍為全部／選定日／選定月／選定年，以選定日期為基準，顯示實際範圍；搜尋結果不再被目前視圖裁掉。搜尋期間前後操作按所選日／月／年移動；跨日事件採交集搜尋。無日期待辦只納入全部範圍。
- [x] `TimelineRules.kt` 以半開區間裁切至當地日，保留分鐘位置與長度；重疊群組分欄，前一事件結束等於後一事件開始時可重用欄，連鎖／巢狀重疊仍不互相遮蔽。
- [x] `DayTimeline.kt` 半透明卡片覆蓋時間表，與整點線對齊；時間表可上下捲動，多欄可左右捲動，避免把重疊事件壓成不可讀文字。
- [x] 跨日指定時間事件逐日裁切，註明由前日延續／延續至次日；午夜結束不出現在次日。全天事件保持獨立區，完整跨日日期保留於清單與詳細頁。
- [x] 時間表按實際時間排列，支援 23／25 小時的 DST 日，重複小時額外顯示時差；短事件仍依真實長度呈現，可從下方完整行程清單操作。
- [x] 實際只讀檢查既有「台灣光復節」：目前本機資料為 GMT 2026/10/25 09:00–12:00，時間表呈現三小時卡片；點擊可開詳細頁，時間、編輯與 0 筆提醒入口一致。未把名稱含節日的指定時間事件擅自改成全天，也未修改原資料。

### 修改檔案

- 新增 `domain/TimelineRules.kt`、`ui/DayTimeline.kt`、`ui/NoteDetails.kt`。
- 更新 `ui/CalendarScreen.kt`、`ui/NoteHubApp.kt`、`app/build.gradle.kts`（0.8.0／versionCode 8）。
- 新增 `TimelineRulesTest.kt` 六項單元測試與 `DayTimelineUiTest.kt` 三項 UI 測試；更新 NavigationTest／ReminderUiTest 使用新的詳細頁入口。
- 更新 `.gitignore`、README.md、PROJECT_STATUS.md、RELEASE_NOTES.md，以及已忽略 dist 內安裝檔與資訊。
- Room 維持 schema v5，沒有新增依賴或變更 Google、照片、提醒排程業務邏輯。

### 執行命令與真實結果

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --console=plain
./scripts/Test-Android.ps1 -Serial emulator-5554 -GrantReminderTestPermissions -GrantCameraTestPermissions
# 最後調整後更新安裝兩個 APK，再執行相關測試：
adb -s emulator-5554 shell am instrument -w -e class com.nqutaoitfteikc.notehub.DayTimelineUiTest,com.nqutaoitfteikc.notehub.NavigationTest com.nqutaoitfteikc.notehub.test/androidx.test.runner.AndroidJUnitRunner
./scripts/Test-GoogleImportPersistence.ps1 -Serial emulator-5554
# 設定原 JDK JAVA_HOME 後，以 SDK apksigner verify --verbose --print-certs 驗證 APK。
```

- 最終建置 `calendar08-final-build.log`：BUILD SUCCESSFUL；28 項單元測試，0 failures；Lint 0 errors／29 warnings（既有版本／範本等建議）。初次 Kotlin 比較式解析及 Dp 運算編譯錯誤已修正並重跑，沒有忽略編譯錯誤。
- 完整 Android 17／API 37 模擬器 `calendar08-acceptance.log`：runner OK (48 tests)，46 項一般測試實際執行通過，2 項 opt-in 真實資料方法在此跳過。包括實際 CameraX、多提醒與既有記事流程。
- 最後卡片與整點線對齊、返回月曆調整後，`calendar08-final-ui.log`：DayTimelineUiTest 3 項＋NavigationTest 4 項，OK (7 tests)。沒有宣稱最後微調後再跑過整套 46 項。
- 新 UI 測試直接驗證重疊卡片的畫面邊界不遮蔽、起始高度不同、卡片／清單開啟回呼、返回月曆、跨日延續標示、日／月／年搜尋結果；資料是隔離的測試 fixture，不冒充 Google 回傳或寫入正式資料庫。
- `calendar08-google-persistence-final.log`：實際 force-stop 與 COLD 啟動，兩個獨立程序各 1／1 通過；來源帳本、記事內容與時間摘要一致。只讀既有真實資料，未重新呼叫 Google API。
- 真實光復節日檢視已目視檢查，截圖 `dist/calendar08-day.png`；本次沒有改動裝置 GMT 時區。完整測試腳本還原相機、通知與精準鬧鐘權限。
- APK `dist/NoteHub-0.8.0-debug.apk`：37,701,111 bytes；已安裝並確認 v2 簽章。
- SHA-256：`09DB7D578DB07DAFEACFF01FF1D222F6D9FD720B8B4C4EC06247ACB28460986B`。

### 待辦與驗證限制

- [ ] 實體裝置、其他 API、超大字體、小螢幕與大量同時重疊事件仍可進一步驗證，未宣稱所有環境皆通過。
- 本次沒有新增外部設定；Google OAuth／Calendar 唯讀設定沿用既有有效設定。


## 0.9.0 照片預覽裁切與螢幕適應（2026-10-16）

### 範圍與實作

- 使用者補圖確認問題位於照片預覽：底部儲存按鈕被螢幕下緣遮住。檢查 Git 修改、預覽、圖片縮放、編輯頁，以及尺寸／密度／字體；保留既有未提交修改及資料。
- `PhotoScreens.kt`：預覽改為 App 內完整頁面，沿用 Scaffold 實際安全視窗，避開原生全螢幕 Dialog 在此 Android 17 模擬器上的視窗偏移。關閉或系統返回鍵回到圖庫，保留原捲動位置。
- 新增 `PhotoPreviewContent.kt`：可用空間由父視窗決定，另外保留鍵盤空間；關閉與儲存獨立於內容捲動。正常直向儲存置底，短視窗改頂端操作列；少於 180 dp 的極短空間，改為關閉／說明輸入／儲存單列，不再垂直堆疊。
- 照片仍採 ContentScale.Fit 完整縮放，不裁切原圖；短視窗與鍵盤輸入優先提供說明欄，恢復空間後顯示照片。寬度至少 480 dp 的橫向視窗採照片／說明左右排列，內容寬度上限 1000 dp。
- `NoteEditor.kt`：橫向實測發現 IME 開啟時原本標題、內容與底部儲存列擁擠；短視窗把儲存移到頂端、縮減垂直留白、描述最小行數降為一行。驗證、儲存、刪除及草稿狀態沿用原邏輯。
- `CalendarScreen.kt`：標題取得受限寬度而可換行；小於 340 dp 或字體倍率超過 1.3 時三個篩選選單改直排。日期與照片草稿／圖庫多按鈕列改 FlowRow 換行。
- 預覽以固定元件樹的自訂 Layout 測量照片、說明與操作位置，鍵盤縮放時不搬移輸入框；刪除曾造成失焦／反覆重組的可移動內容與手動焦點恢復暫行方案。
- 新增 `PhotoPreviewLayoutTest.kt` 五項適應測試。固定小視窗 fixture 代表已扣除安全區的可用空間，避免再扣實體裝置的整個鍵盤高度；真實頁面的 IME 與儲存另外由 CameraX 實拍流程驗證。
- `PhotoCaptureUiTest.kt` 增加「尚未開鍵盤，儲存照片說明按鈕就必須可見」的回歸斷言，避免只驗證輸入後的精簡操作列而漏掉原問題。
- 版本 0.9.0／versionCode 9；Room v5、照片檔案／拍攝時間、提醒與 Google 匯入邏輯未變更，未新增依賴或外部設定。

### 執行與結果記錄

沿用 JDK 21、Gradle Wrapper、API 37 模擬器及原 SDK：

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --console=plain
./scripts/Test-Android.ps1 -Serial emulator-5554 -GrantReminderTestPermissions -GrantCameraTestPermissions
# 針對 NavigationTest、CalendarDropdownTest、PhotoPreviewLayoutTest、PhotoCaptureUiTest 重跑。
# 暫以 adb shell wm size 1920x1080（density 480，即 640×360 dp）測試橫向；finally 還原原解析度。
```

測試 fixture 使用 320×480 dp、320×260 dp、320×140 dp、1.8 倍字體及目前可用視窗；僅為隔離 UI 測試，沒有冒充正式照片或 Google 資料。實拍測試則使用 CameraX 真實 JPEG、輸入記事與照片說明、更新 Room，核對拍攝時間保持不變。

中途驗證發現並處理：

1. 完整套件 `responsive09-acceptance.log` 的 51 個 runner 方法中，2 個 opt-in 方法跳過，48 個一般測試通過；CalendarDropdownTest 在結束活動時發生 PAUSED→DESTROYED 逾時。該測試後續重跑通過，未忽略例外，也未宣稱該次整套測試成功。
2. 假定可用小視窗的 fixture 一度重複扣實體 IME 高度，及在不會真正關閉畫面的儲存回呼後繼續點擊、遇上鍵盤動畫；已修正 fixture 的空間定義與操作順序，保留儲存時可見的斷言。
3. 橫向初次實拍後續記事編輯儲存失敗，定位為編輯頁空間不足，補上頂端儲存及較小留白後重跑通過。
4. 目視原照片時發現 Dialog 雖通過輸入後的儲存測試，但正常未開鍵盤時底部仍出界；原生視窗檢查確認偏移後，改為 App 內完整頁面，並新增未輸入時的可見性回歸斷言。沒有以通過部分測試冒充原問題已修好。


### 最終結果與交付

- `responsive09-final-build.log`：BUILD SUCCESSFUL；28 項 JVM 單元測試通過；Lint 0 errors／29 warnings（既有版本、範本等建議）。
- `responsive09-stable-portrait.log`：固定元件樹最終版，5 項 PhotoPreviewLayoutTest＋真實 CameraX PhotoCaptureUiTest，OK (6 tests)。包含尚未輸入時儲存按鈕可見、實際點入欄位、IME 顯示且欄位保有焦點後才輸入，並核對照片說明、記事描述及拍攝時間。
- `responsive09-stable-landscape.log`：640×360 dp 橫向，同一真實點擊／鍵盤／照片與記事儲存流程，OK (1 test)，無人為介入。後來僅重跑直向與版面驗證、核對文件與 APK，未修改該版原始碼。
- 原預覽裁切目視核對：開啟使用者原本 2026/10/16 07:03:13.098 拍攝的照片，沒有修改內容或說明。完整儲存按鈕已顯示於手勢列上方；文字 bounds 為 [545,2794][800,2855]。修正後截圖 `dist/responsive09-preview.png`。
- `responsive09-google-persistence-final.log`：force-stop 後 COLD 啟動，既有 Google 匯入來源／本機副本摘要一致，兩個獨立程序各 1／1 通過；只讀本機資料，沒有重新呼叫 Google API。
- 中途另曾揭露輸入框換位置失焦、過早請求焦點及反覆重組；最後改為同一元件樹只變更測量位置。舊版一次曾透過系統返回解除測試鍵盤等待，未把該次算作無介入驗收；以上 stable 兩份才是最終無介入結果。
- 更新後原照片、記事、提醒及匯入資料保留；沒有 uninstall、重建資料庫或刪除使用者資料。測試只清理自行建立的 UUID fixture。
- 已還原原始 1344×2992／density 480 螢幕與 1.0 系統字體；大字體測試僅使用測試內容的 1.8 fontScale。相機、通知及精準鬧鐘權限沿用／還原原設定。
- `dist/NoteHub-0.9.0-debug.apk`：37,736,203 bytes，APK v2 簽章驗證通過，沿用原 debug 簽章及套件。
- SHA-256：`B1A574BFACD2D6DE35FF9B9BCAB3203E59D0D3F93AAF4FD0A6CA4106301CE641`。

### 待辦與限制

- [ ] 實體手機、其他 Android API、超過已測字體倍率及更多極端分割視窗仍需額外驗證，未宣稱所有機型都已實測。
- 最終自訂版面後執行了相關版面、直／橫向實拍及資料保留核對；未重新執行整套最終 53 個 runner 方法。前面完整回歸與各次失敗／修正均保留在紀錄中。
- 本次不需新增 Google Cloud、OAuth 或其他外部設定；資料庫仍為 v5。
