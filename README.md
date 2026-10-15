# NoteHub

原生 Android 本機記事與行事曆，Kotlin / Jetpack Compose / Material 3。Phase 1–4 已實作並通過對應模擬器驗證；Phase 5 Google Calendar 唯讀匯入已加入真實登入、授權與 API 程式，本機測試通過，真實 Google 登入、唯讀同意、事件匯入／去重、已刪除副本手動重匯及真正冷啟動驗證已通過。

## 可操作功能

- 事項新增、編輯、刪除確認、完成／恢復未完成。
- 標題、描述、自訂分類；依標題／描述／分類搜尋，依分類與完成狀態篩選。
- 月視圖：星期一起始的月曆、每日事項筆數、選定日期的事項。
- 週視圖：完整七天議程，點日期進入日視圖。
- 日視圖：全天區與 24 小時議程；跨日事項在每個相交日期顯示，延續事件在當日 00:00 區列出。
- 清單視圖：跨所有日期的事項清單。
- 無時間待辦在各視圖下方獨立列出，不受選定日期限制。
- 前後月／週／日、今天與跳至日期；開始／結束日期與時間可輸入或以選擇器選取。
- 跟隨系統／淺色／深色主題；繁體中文介面、錯誤與日期選擇器。
- 編輯草稿在 Activity 重建時保留；離開未儲存內容會要求確認。
- 每個事項可設定多個獨立提醒，支援編輯時間、停用、移除與通知點擊查看事項。
- CameraX 預覽與多張照片記事、照片說明、追加／移除照片，拍攝時間不隨儲存或編輯改變。

## 開發環境與建置

- `:app` 單一模組，套件 `com.nqutaoitfteikc.notehub`。
- Gradle 9.3.1 / AGP 9.1.1 / 內建 Kotlin 與 Compose compiler 2.2.10 / KSP 2.3.9。
- JDK 17 以上，本機驗證使用 Microsoft OpenJDK 21.0.12.1。
- compile SDK 36.1 / target API 36 / min API 28。
- 所有套件版本集中於 `gradle/libs.versions.toml`；Phase 2 沒有增加外部依賴；Phase 3 新增 WorkManager 2.10.5，Phase 4 新增 CameraX 1.5.3。WorkManager 僅用於恢復排程與照片檔案清理等可延後工作。
- `local.properties` 設定自己的 SDK 路徑，不納入版本控制。

```powershell
$env:JAVA_HOME='C:/Users/USER/.jdks/jdk-21.0.12.101-hotspot'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

可安裝檔案：`app/build/outputs/apk/debug/app-debug.apk`，版本 0.5.0。

## 模擬器測試

先啟動模擬器，使用 `adb devices` 確認序號。建議使用專用測試模擬器。

```powershell
.\scripts\Test-Android.ps1 -Serial emulator-5554 -GrantReminderTestPermissions -GrantCameraTestPermissions
# SDK 不在預設位置時，加上 -AndroidSdk '自己的 SDK 路徑'
```

腳本會建置、執行電腦端測試、Lint，以 `adb install -r` 保留資料安裝，直接執行 AndroidJUnitRunner 並檢查結果。`-GrantReminderTestPermissions` 會先保存原有權限，暫時授予通知與精準鬧鐘權限測試，再於 finally 還原；不使用此參數且未授權時，需精準發送的裝置測試會跳過，不能算作提醒觸發驗證。測試只清理自己建立的記事與測試資料庫；主題測試會還原測試前的設定。不要在測試期間編輯 App。

仍可執行 Gradle `:app:connectedDebugAndroidTest`，但其測試安裝／清理流程可能移除受測 App，勿對重要資料裝置使用。Phase 2–3 採直接 instrumentation 流程避免解除安裝；但 instrumentation 仍會強制停止受測程序，可能取消既有鬧鐘。測試結束後請開啟 App，恢復自己的提醒排程。

真正跨程序重啟驗證使用相同 `persistenceRun` 參數，分兩次執行：

```powershell
adb -s emulator-5554 shell am instrument -w -e persistenceRun unique-run-id -e class 'com.nqutaoitfteikc.notehub.ProcessRestartTest#aPrepare' com.nqutaoitfteikc.notehub.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am force-stop com.nqutaoitfteikc.notehub
adb -s emulator-5554 shell am instrument -w -e persistenceRun unique-run-id -e class 'com.nqutaoitfteikc.notehub.ProcessRestartTest#bVerifyAndCleanUp' com.nqutaoitfteikc.notehub.test/androidx.test.runner.AndroidJUnitRunner
```

務必執行第二步測試以還原主題及清理該次驗收記事。實際結果與日誌見 `PROJECT_STATUS.md`。

## 架構與時間規則

`ui`（Compose、Navigation、ViewModel）→ `domain`（模型、Repository 介面、Use Cases、CalendarRules）→ `data`（Room、DataStore）。`NoteHubApplication` 提供應用程式生命週期依賴容器；畫面透過 ViewModel 執行業務操作。

- 無時間待辦：沒有開始／結束時間，建立時間只當作記事中繼資料。
- 全天事件：以 LocalDate epoch day 保存，內部結束日期不包含；編輯器讓使用者選擇「結束日期（含當日）」。不受時區影響。
- 有時間事件：以 epoch milliseconds 與建立時的 zoneId 保存，日曆按裝置目前時區顯示，編輯則保留事件時區。進入前景時刷新顯示時區。
- 所有區間使用 `[start, end)`，結束於午夜的事件不會誤算至隔日。
- 日光節約時間採實際當地日期邊界，不假設每日固定 24 小時；不存在的當地時間拒絕儲存，重複時刻採第一次出現的 offset 並於畫面告知。
- 編輯保留 id、createdAt 與完成狀態。標題、分類、描述分別限制 200、40、20000 字元。

## 資料庫與隱私

Room v4。`MIGRATION_1_2` 只新增欄位與索引，保留舊資料；v1 記事歸為無時間待辦。`app/schemas` 保存 v1–v4 結構，未使用 destructive migration。已以實際 SQLite v1 建庫、Room 升級與重開驗證。

純本機儲存；關閉 Android 備份與資料轉移；沒有網路權限、雲端資料庫或自有後端。解除安裝會刪除資料，目前不提供匯出或備份。

分類為事項上的自訂文字，可於編輯時改名／清空；篩選列由現有分類產生，未提供獨立分類管理頁。資料目前透過 Room Flow 載入後做篩選，尚未進行大量資料效能基準測試。

## 後續階段

下一階段 Phase 5 為真實授權的 Google Calendar 唯讀匯入，需外部 Google Cloud／OAuth 設定；Phase 6 為整體品質檢查。目前提醒與照片仍需實體裝置驗收。

## 相容性參考

- https://developer.android.com/build/releases/agp-9-1-0-release-notes
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/jetpack/androidx/releases/room
- https://github.com/google/ksp/releases


## 提醒使用方式與恢復規則

先儲存事項，再點卡片的「管理提醒」。每筆提醒可指定自己的日期與時間，可同時有多筆相同或不同時間的提醒；通知以各自 Reminder ID 識別。

- 通知權限不足或頻道被停用：保存資料，但不排程，顯示「通知未開啟」。設定頁與提醒頁可申請／開啟系統設定，回到 App 時重新檢查。
- Android 12+ 檢查 canScheduleExactAlarms。獲准時採 setExactAndAllowWhileIdle；否則採 setAndAllowWhileIdle 並明確標示「非精準，可能延遲」，不將 WorkManager 當作精準鬧鐘。
- 日期時間以絕對 epoch milliseconds 儲存，時區變更只影響顯示；排程於 BOOT_COMPLETED、TIME_SET、TIMEZONE_CHANGED、MY_PACKAGE_REPLACED、精準鬧鐘授權與 App 前景恢復。
- 廣播先嘗試即時恢復，WorkManager 僅作可延後的持久重試；到時通知仍由 AlarmManager 廣播負責。
- 提醒修改時保留 id、換發 revision，取消舊 PendingIntent 與通知；晚到的舊 revision 被拒絕。
- 刪除事項會由 Room 外鍵 CASCADE 移除提醒，並取消所有對應鬧鐘及通知。完成事項時暫停未送出提醒，恢復未完成只重排尚未送出且未過期者。
- 重啟或時間調整後，15 分鐘內錯過的提醒可補排，更早者標為過期，避免大量舊通知。已送出者不因恢復排程而重複提醒。
- 使用資料庫條件更新先領取發送權，避免重複通知；若程序恰好在領取成功與呼叫系統通知之間被終止，該筆可能不會顯示。資料庫與系統通知之間無法做原子交易，不能承諾任意斷電情境的 exactly-once。
- 系統強制停止、撤回權限、省電、Doze、待機頻率限制與廠牌背景管理仍可能延遲或阻擋提醒；強制停止後需手動開啟 App 恢復。未要求自動忽略電池最佳化。

### 額外裝置驗證

APK 與測試 APK 安裝後：

```powershell
# 依序測試通知被拒絕、非精準、精準，最後還原原有權限
.\scripts\Test-ReminderPermissions.ps1 -Serial emulator-5554
# 實際重開指定模擬器並驗證系統恢復，最後還原時區與權限
.\scripts\Test-ReminderRecovery.ps1 -Serial emulator-5554
# 獨立驗證真正的時區廣播與觸發時間不變
.\scripts\Test-ReminderTimezone.ps1 -Serial emulator-5554
```

重開機驗證必須先以 dumpsys alarm 觀察恢復的原始時間，才啟動 instrumentation 查資料；後者會強制停止 App，會干擾鬧鐘與廣播。測試腳本已據此設計。重開機腳本只允許 emulator-* 序號，不對實體裝置自動重開機。

此版已在 Android 17 / API 37 模擬器驗證實際通知與恢復。API 28–36、實體裝置、真實 Doze／廠牌省電、通知頻道手動關閉與實際系統時間跳動尚未完整驗證。


## 照片記事使用方式

從下方「照片」進入，點「拍照記事」，允許相機權限後可連續拍攝多張。輸入標題、描述與分類，按「儲存照片記事」。已儲存記事可預覽大圖、編輯照片說明、追加照片或移除單張；「編輯記事描述」可修改標題、描述與分類。

- 拍攝時間：優先由感光元件的 realtime timestamp，以拍攝開始時記錄的 wall／elapsed clock 換算。裝置無可用 realtime clock 時採 CameraX onCaptureStarted 的時間；不使用 onCaptureSuccess、檔案寫入、儲存記事或描述修改的時間。
- 每張照片的 capturedAt、方向與時間來源只在插入時設定；照片說明更新只更改 caption。
- 記事時間：首次儲存時取這組照片最早 capturedAt，同時作為記事 createdAt、photoCapturedAt 與行事曆時間。後續追加／移除照片、修改描述不改變記事原始時間；移除最後一張照片也會保留記事及原始時間。
- 原圖以 JPEG 保存於 App 私有 files/photos，無需外部儲存權限，不寫入系統相簿或雲端。讀圖按顯示尺寸降採樣並套用 CameraX 方向資訊。
- 先完成 `.part` 寫入與同步、再改名、最後寫入 Room。照片在記事儲存前已持久化為草稿；返回可從照片頁繼續。草稿文字直到儲存才寫入記事，請不要將未儲存文字視為持久資料。
- 刪除照片／草稿／記事，會在資料庫交易內寫入檔案清理佇列；WorkManager 可靠重試。清理失敗仍保留佇列與重試入口；恢復時也會移除未被資料庫參照的殘留檔案。
- Room v3→v4 新增照片、清理表及記事 photoCapturedAt，保留既有記事與提醒。
- 無相機或未授權時會提供繁體中文提示；相機僅隨畫面生命週期綁定。每次拍攝有逾時／錯誤處理，未得到有效拍攝時間或 JPEG 時不會冒充成功。

### 照片驗證

```powershell
# 相機與提醒權限先保存，測試期間授予，finally 還原
.\scripts\Test-Android.ps1 -Serial emulator-5554 -GrantReminderTestPermissions -GrantCameraTestPermissions
# 重新實拍，結束程序、冷啟動，再核對時間、方向、說明、原圖 SHA-256 及圖片顯示
.\scripts\Test-PhotoRestart.ps1 -Serial emulator-5554
```

需先安裝最新 App 與 Android 測試 APK（Test-Android.ps1 會建置安裝）。未授權相機時，實拍測試會跳過，不能視為拍照驗證成功。資料層使用明確的 JPEG 測試樣本，實拍測試則呼叫真正的 CameraX 與模擬器相機，不以測試樣本冒充拍攝結果。

目前已驗證 Android 17 / API 37 模擬器；實體裝置、其他 API、前鏡頭與不同裝置方向、相機被其他 App 佔用、系統撤權及實際磁碟耗盡仍待測試。時間記錄不宣稱硬體奈秒級曝光精度，裝置無 realtime timestamp 時受開始拍攝回呼延遲影響。

## Google Calendar 唯讀匯入（Phase 5）

「設定 → Google 行事曆匯入」依序登入 Google、授予唯讀權限、選取行事曆與日期範圍、讀取預覽、勾選並確認匯入。網路請求使用真正 Google Calendar API，沒有正式假資料或假成功畫面。

- Credential Manager 處理登入、Google Identity Services AuthorizationClient 處理行事曆唯讀授權，兩者明確分開；不使用 Firebase 或自有後端。
- CalendarList 與 Events 皆讀完所有分頁才提供預覽，失敗不匯入半批結果；重複事件以 singleEvents 展開，移動例外以原發生時間去重。
- Google 全天 end.date 保留為 exclusive end；指定時間保留實際時差與事件時區，跨日事件交由既有行事曆顯示。
- Room v5 以來源帳號／行事曆／事件唯一鍵防止重複；整批匯入原子交易，重匯不覆蓋本機修改；已刪除副本預設不勾選，可手動勾選重新匯入。這是一次複製，不是雙向或持續同步。
- Google 提醒不會自動匯入為本機鬧鐘；匯入後可自行設定多提醒。
- 權杖只存在匯入頁記憶體，不寫入 DataStore、Room 或日誌；登出保留本機記事。
- 新增相依版本集中管理：Credentials 1.6.0、Google ID 1.1.1、Play Services Auth 22.0.0。Google ID 1.2.1 實際不相容既有 Kotlin metadata，已回退相容穩定版。

使用者提供的 Web client ID 已設定於 local.properties，未提供或編造 client secret。本機 debug 的真實登入與 API 已驗證；其他工作機或正式簽章仍須另行設定 Android OAuth client／SHA-1、API 與授權使用者。完整設定、實際簽章、限制與逐步真實驗收請見 [GOOGLE_SETUP.md](GOOGLE_SETUP.md)。

本機 fixture 的分頁／去重測試不代表真實 Google 授權與匯入已通過；最新真實結果請見 [PROJECT_STATUS.md](PROJECT_STATUS.md)。



### 已刪除副本與真實資料驗證

本機副本仍存在時，預覽會停用勾選並顯示「已匯入，將跳過」；副本已刪除時，顯示「本機副本已刪除，可勾選重新匯入」，預設未勾選。手動勾選並確認才會建立新副本；不覆蓋仍存在的本機記事，不恢復舊本機修改或提醒。來源與新副本於同一交易更新，重讀仍可去重。

本機驗證：22 項單元、36 項一般 Android 測試通過；另行啟用的兩階段真實資料核對 1＋1 通過。真實 Google 預覽／匯入 3 筆並重讀跳過，手動重匯後只讀核對 3 個來源／1 個本機副本，真正冷啟動前後內容與時間摘要相同。沒有修改 Google 事件。

若已透過真實 Google UI 匯入且仍保留至少一筆副本，可執行以下只讀驗證（需先安裝目前 App 與測試 APK，會重新啟動 App）：

```powershell
.\scripts\Test-GoogleImportPersistence.ps1 -Serial emulator-5554
```

此測試不呼叫 Google、不建立測試事件、不刪除或修改現有記事；一般整套測試會跳過這兩個需明確參數的真實資料方法。大量真實分頁、實際重複事件例外、其他 Android 版本／實體裝置等未驗證範圍，請見 PROJECT_STATUS.md。
