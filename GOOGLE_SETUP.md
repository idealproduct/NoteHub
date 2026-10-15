# Google Calendar 唯讀匯入設定與真實驗收

## 本機已完成

- 使用者提供的 Web OAuth client ID 已寫入不納入版本控制的 `local.properties`，由 Gradle 注入 BuildConfig。未建立、要求或儲存 client secret。
- 套件名稱：`com.nqutaoitfteikc.notehub`。
- 此工作機 debug SHA-1（實際 signingReport）：`69:F1:3B:65:8A:7B:E6:1E:83:82:C7:20:39:A1:6A:0F:F7:2B:2F:6A`。
- 此工作機 debug SHA-256：`0E:19:46:E3:74:3C:06:B7:63:C5:A2:E6:BD:E4:BB:96:2C:16:48:A6:81:F9:F0:18:D2:22:DE:3B:66:E5:3B:36`。

## Google Cloud 尚須由專案擁有者確認

1. 在同一個 Google Cloud 專案啟用 **Google Calendar API**。本機無法由 client ID 判定是否已啟用。
2. Google Auth Platform 設定 Branding／Audience／Data Access；測試階段加入實際登入的測試使用者。對外發布時依 Google 要求完成應用程式與敏感範圍驗證、隱私政策等設定。
3. 建立 **Android OAuth client**，填入上面的實際套件名稱與 debug SHA-1。Web client 與 Android client 必須屬同一個 Cloud 專案。正式簽章／Play App Signing 的指紋需另行註冊，不能沿用 debug 指紋。
4. 確認提供的是 **Web application client ID**。Android client ID 不能取代 Web client ID。
5. 僅要求以下兩個唯讀範圍：
   - `https://www.googleapis.com/auth/calendar.calendarlist.readonly`
   - `https://www.googleapis.com/auth/calendar.events.readonly`
6. 測試裝置須有可用的 Google Play 服務、Google 帳號與網路；沒有 Google Play 服務的裝置無法執行此登入流程。

不需要 Firebase、google-services.json、API key、自有後端、refresh token 或伺服器 client secret。請勿將 secret 放進 APK。

## 其他工作機建置

在自己的 `local.properties` 加入 `google.webClientId=`，其值使用真實 Web client ID；也可透過 Gradle 的 `-Pgoogle.webClientId=...` 指定。未設定時仍可建置，本機功能可用，匯入頁會明確停用登入並顯示設定說明。

```powershell
$env:JAVA_HOME='C:/Users/USER/.jdks/jdk-21.0.12.101-hotspot'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:signingReport
```

## 真實連線驗收（需要使用者操作 Google 畫面）

1. 安裝 debug APK，開啟「設定 → Google 行事曆匯入 → 使用 Google 帳號登入」，在 Google 畫面選擇實際測試帳號。
2. 點「授權讀取行事曆」。確認 Google 同意畫面只有上述唯讀存取；取消授權時不得載入或匯入資料。
3. 成功後核對 App 列出的行事曆與 Google 帳號相符；選取一個以上行事曆，選擇含起訖日的日期範圍（最多 367 天）。
4. 點「讀取事件預覽」。核對真實一般事件、跨日事件、全天多日事件、重複事件的不同發生次數、移動後的單次例外。全天結束日採 Google 的 exclusive end，畫面轉為含當日顯示。
5. 勾選並確認匯入。核對記事頁日期、描述、分類，關閉並重開 App 確認仍在。
6. 重複同範圍匯入，新增數量應為零；修改本機標題後重匯不得覆蓋；刪除一筆本機副本後重新預覽應預設不勾選；只有手動勾選並確認時才重新建立副本，之後重匯應再跳過。Google 本身不得有任何新增、修改或刪除。
7. 若要實際驗證分頁，使用超過 250 筆事件／行事曆的真實測試帳號；fixture 分頁測試不代表這一步通過。
8. 取消登入／授權、斷網、從 Google 帳戶撤銷 App 授權後重試；應出現繁體中文錯誤且無半批匯入。401 會清除本次存取狀態並要求重新授權。
9. 登出保留本機記事，只清除 App 記憶體中的存取權杖與帳號偏好；如需撤銷 Google 端同意，使用 [Google 帳戶第三方連線設定](https://myaccount.google.com/connections)。

## 資料與限制

### 選取帳號後返回／代碼 16

本次實測曾取得 `GetCredentialCancellationException: [16] Account reauth failed.`，不是已確認使用者主動取消。依 [官方疑難排解](https://developer.android.com/identity/sign-in/credential-manager-troubleshooting-guide)，取消類型也可能是技術限制／設定造成，不能僅依例外類型判斷。程式只在 debug 記錄遮蔽帳號／JWT 後的 SDK 錯誤，不自動無限重試。

本機模擬器曾關閉自動校時，系統 UTC 比主機快約 8 小時。已使用系統網路校時修正，並於 finally 還原原自動校時開關（0），時區 GMT 保留。後續使用者校時後仍登入失敗；重新檢查 UTC 與主機一致，故不能以時鐘誤差解釋全部失敗。自行測試時請先確認裝置日期時間正確，再確認 Android OAuth 的 package／SHA-1、Web client 所屬專案與測試帳號。不要反覆自動重試，也不需要刪除 Google 帳號或提供密碼給開發者。

### 本機資料

- 所有正式網路請求都是固定 Google HTTPS 端點的 GET，不跟隨重新導向，不會呼叫 Calendar 寫入／刪除 API。權杖只保留在匯入畫面的 ViewModel 記憶體中，離開頁面或程序結束後需重新登入／授權。
- 匯入是本機副本，不是持續同步。Google 後續變更不覆蓋本機編輯；Google 提醒不會自動建立本機鬧鐘。
- 唯一鍵是正規化登入 email＋行事曆 ID＋事件 ID；重複事件使用 series ID＋原始發生時間，移動單次例外仍能去重。Google 帳號改名為不同 email 時視為不同來源，可能再匯入一份。
- 來源識別表在本機刪除記事後仍保留，因此不會自動重建被刪除項目；使用者可在預覽中手動勾選重新匯入，來源記錄會於同一交易更新至新副本。登出不刪除此識別表；表中保留來源 email／ID，沒有權杖。
- 為保護資源，每次最多 100 頁／每個行事曆，合計最多 10,000 筆事件，單頁回應最多 8 MiB；超過限制須縮小範圍，不會提供不完整預覽當作成功。API 分頁並非跨請求的一致性快照，大量事件於讀取時被外部編輯，可能需要重新載入。
- 超過本機標題 200、描述 20,000、分類 40 字元時，預覽明確提示截短。零時長事件以 1 毫秒表示並提示。Google description 中的 HTML 以文字顯示，不執行 HTML。
- 沒有權杖／個人事件遙測、後端或雲端資料庫。資料仍受 Android App 私有目錄與原有禁止備份規則保護。

## 官方依據

- [Credential Manager 的 Google 登入](https://developer.android.com/identity/sign-in/credential-manager-siwg)
- [Google Identity Services 授權](https://developer.android.com/identity/authorization)
- [CalendarList 分頁](https://developers.google.com/workspace/calendar/api/v3/reference/calendarList/list)
- [Events 分頁、時間界限與 singleEvents](https://developers.google.com/workspace/calendar/api/v3/reference/events/list)
- [Google ID SDK 版本紀錄](https://developers.google.com/identity/android-credential-manager/releases)

googleid 1.2.1 實際建置回報 Kotlin metadata 2.4 與本專案 2.2 不相容，因此採官方穩定 1.1.1；未使用跳過版本檢查或隱藏錯誤。

### 2026-10-15 21:27（臺北）補驗

使用者回報畫面時間未修正。實際 UTC 與主機已一致，但先前保留 GMT，導致畫面少 8 小時。現已設定 `Asia/Taipei`、自動校時 `auto_time=1`、自動時區 `auto_time_zone=0`，保留修正以持續顯示臺北時間。實際裝置 21:27 CST、UTC 13:27 與主機一致。

校時後 13:24:52、13:25:35 UTC 的 SDK 紀錄仍是 `[16] Account reauth failed.`，表示校時並未解決登入。此代碼不足以單獨證明 Cloud 設定錯誤；下一步需要 Cloud 擁有者確認同專案 Android client 的 package／SHA-1、Web client 類型與帳號狀態。

已直接以 apksigner 驗證最新 APK 的 SHA-1 與本文相同，且 BuildConfig 的 Web client ID 與使用者提供值相符。最新錯誤提示版本已安裝，App 在前景，沒有再次自動發起 Google 登入。

### 2026-10-15：最新根因已進一步明確

使用者已確認 Android client 的 package／SHA-1 相符。讀取更完整且已遮蔽的 Google Play 服務錯誤後，發現流程有所進展：21:28 的 AccountReauth_flowRunner 已完成，但接著 Google 伺服器明確回覆：

> You must use a Web client as the server client ID.

隨後回傳 `GetCredentialCustomException: [28444] Developer console is not set up correctly.`，證據保存於 `phase5-google-root-cause.log`。因此目前不應繼續把它解釋為使用者取消、時間錯誤或已證實的 SDK 相容性問題；必須先改用 Google 接受的 Web client ID。

請保留原本 Android OAuth client。在同一 Cloud 專案的 Google Auth Platform → Clients 找到或建立類型為 **Web application／網頁應用程式** 的 OAuth client，將其 client ID 填入本機 google.webClientId。不要改貼 Android client ID，也不需提供 client secret。僅憑 `.apps.googleusercontent.com` 結尾無法判定 client 類型，兩種類型都有此結尾。

已請使用者提供正確 Web client ID，收到後才重新建置與驗證登入。此輪沒有變更登入 SDK 或改用舊版登入 API；錯誤提示已新增針對 28444 的設定說明。APK 建置、單元測試與 Lint 成功，安裝與入口測試 1/1 成功，並未宣稱真實登入／匯入通過。

### 新 Web client ID 已安裝

2026-10-15 21:42 臺北：已收到使用者另行提供的 Web client ID（klgtl94e…），寫入 local.properties 並成功重新建置、安裝。登入入口測試 1/1 通過，App 冷啟動成功，等待使用者手動完成真實登入與唯讀同意；先前 28444 是舊 client ID 的結果，不代表此新版已測試失敗或成功。其餘 Cloud 設定不做未經驗證的假設。

### 真實登入、唯讀同意、行事曆清單已成功

新 Web client 已通過使用者實際登入與唯讀同意。首次清單 API 曾是 403；新增有界錯誤原因解析後，可區分 API 啟用／範圍／配額等情況。使用者再完成流程並回報成功後，實際 App 已從真實 CalendarList API 載入 5 個行事曆，沒有新錯誤。因此不再把登入或清單讀取列為尚未通過。

先前 403 的原因尚無服務端 reason 可證明，不宣稱單靠 App 錯誤文案修正就解決 Cloud 設定。接著仍需驗證真實 Events 預覽、確認匯入及重匯去重。事件內容不需貼到對話。

### 真實 Events 預覽 400

清單成功後，真實 Events 預覽曾回傳 HTTP 400／badRequest。已找出並修正 App 的日期範圍字串省略秒數問題；現在 timeMin／timeMax 由本地午夜轉為含秒的 UTC RFC3339 字串，結束日仍加一天作 exclusive end。單元回歸先失敗再通過，並在分頁測試檢查送出的精確範圍。

修正版已安裝，21 項單元測試與 10 項匯入／入口裝置測試通過；仍需使用者在真實 Google 事件上重試預覽、確認匯入與同範圍去重。這個 App 請求格式問題不應要求使用者反覆重建 OAuth client。


### Phase 5 真實驗收完成

使用者已確認真實預覽與匯入成功，重讀會跳過；畫面亦核對到 3 筆事件均為已匯入。依使用者新增需求，已刪除副本可手動勾選重新匯入，預設不選。使用者使用新功能重新匯入後，以兩個獨立測試程序、實際 am force-stop／COLD 啟動，只讀比對來源與記事所有欄位摘要，兩階段均通過（來源 3／仍存在副本 1）。未選取的已刪除副本未自動重建。

真實登入、唯讀授權、CalendarList、Events、確認匯入、重讀去重與重新匯入已驗證。真實大量分頁、實際重複事件變更、撤銷同意／限流／實體裝置等仍未完整測試；不能將 fixture 的結果當作這些外部情境已通過。
