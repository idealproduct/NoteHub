> 0.15.0 更新：依使用者選擇，直接載入 OpenFreeMap 線上底圖，可切換離線位置圖；限制資源來源。拍照 GPS 改為每次主動啟用，原圖與精確座標使用 Keystore 金鑰加密。照片記事（文字與時間亦包含）不再同步 Google；匯出清理全部非必要附加資料。最新完整說明以 PRIVACY.md 為準，下方保留原設定歷史。

# 拍攝定位與照片地圖（0.13.0）

## 使用方式

1. 拍照記事頁提供「啟用拍攝精確定位」。首次使用時同時要求精確／約略前景位置權限，請在 Android 選擇精確位置。
2. 開啟 GPS，等待定位就緒後拍照。每張照片保存快門當下的定位快照：WGS84 座標、回報誤差、定位時間及是否為模擬位置。
3. 儲存記事後，底部「地圖」會標示有位置的照片。縮小時聚合為數字圓點，放大會分開；點群組可展開，點照片標記可查看並開啟對應照片記事。
4. 相同位置的多張照片持續聚合至最大縮放，點選可分批查看，每次載入 100 張；完整位置清單使用延遲載入。
5. 「顯示全部」調整視角包含照片位置。「定位到我」在您點擊後要求新 GPS，平滑移動並顯示藍色位置點；此動作不會新增記事或保存為照片歷史。
6. 短螢幕與大字型使用圖示操作：照片清單、全景、定位到我，均有繁體中文無障礙說明。

定位未授權、只允許約略位置、GPS 關閉、定位逾時或不夠精確時會提示原因。拍照仍可繼續，但不附上推測座標。舊照片沒有 GPS 資料時維持「未紀錄位置」，不會拿目前位置補進歷史照片。

## 定位規則與保存

- 使用 Android `LocationManager.GPS_PROVIDER`，不依賴 Google Play services 定位套件。
- 拍照頁可見且有精確定位權限時啟動監聽；離開、暫停或背景時停止，忽略遲到回呼。
- 照片只接受回報誤差不超過 50 公尺、距快門不超過 15 秒且時間順序有效的 GPS 修正。這是資料接納門檻，不是保證實際衛星定位誤差永遠小於該數值。
- 地圖定位為主動點擊後的一次定位，最長等待 20 秒；完成、失敗、離開或背景均停止。未取得可靠位置不會改用舊照片座標。
- 座標隨各張照片保存在 Room；延後儲存記事、改描述或照片說明，不會重新定位或改寫已保存位置。
- GPS 位置不新增至相簿匯出照片的 EXIF，也不隨 Google Calendar 同步。既有原始照片內容不會因本功能而重寫。
- Room schema 8，明確 7→8 Migration。舊列座標為空，位置狀態為 NOT_RECORDED；原有記事、照片、提醒、成就累計及同步資料保留。

## 地圖與效能

依使用者更正，採用 **MapLibre Native for Android 13.7.1 的官方 OpenGL ES 穩定版本**，不是 Google Maps SDK。原生向量地圖使用硬體加速，不要求新的 Google Cloud 專案、Maps API key 或 Google 授權。

底圖為 OpenFreeMap Liberty：`https://tiles.openfreemap.org/styles/liberty`。保留 MapLibre 資料來源按鈕及 OpenFreeMap／OpenMapTiles／OpenStreetMap 歸屬文字；使用時需要一般地圖圖磚網路連線。記事與照片仍在本機，地圖供應商會收到圖磚等一般網路請求；沒有將本機記事 GeoJSON 上傳至自有服務。

照片位置轉換在背景準備，只建立一個本機 GeoJSON source 與三個向量圖層。GeoJSON 僅包含位置及照片識別，不含記事標題、描述或照片檔案。source 採非同步更新，縮放與移動不重新查 Room、不重新建立每張照片的 Android Marker／View，而由原生聚合引擎處理。

聚合半徑 60，資料 source 最大層級 22、聚合至 20、地圖縮放上限 20；高縮放下相同座標仍能合併，避免大量重疊個別點。點群組使用原生 expansion zoom，清單採原生 leaves 分頁及 LazyColumn。定位到我另用一個暫時 source，不會混入照片 source。

為維持裝置相容性選用 OpenGL ES flavor，不新增 Vulkan 必備硬體限制；APK 包含 arm64-v8a、armeabi-v7a、x86、x86_64。實體機、不同驅動與 Android API 仍需各別驗收，不以單一模擬器保證所有手機效能。

## 成就顯示

成就頁新增醒目的「累積紀錄次數」、下個成就門檻、尚差次數及進度。維持 0.12.0 的獨立提醒計次與既有進度；編輯、刪除及重開不重複計算，不回推無法還原的舊歷史。長數字可橫向捲動，避免大字型裁切。

## 測試與外部驗收

- `PhotoLocationRulesTest`：新鮮度、誤差、精確／約略權限、無效座標、成就進度及 GeoJSON 不含私密文字。
- `PhotoLocationRepositoryTest`：多張不同位置、延後儲存、修改、重開資料庫、刪除更新地圖、匯出不新增 GPS EXIF。
- `PhotoMapNativeTest`：真正 MapLibre 引擎的三點縮放聚合／分離、萬點聚合、最大縮放同位置聚合及真實 OpenFreeMap 底圖。位置資料為明確測試樣本，並非使用者歷史。
- `PhotoMapUiTest`：原生點選、開啟對應記事、資料刪除、累計進度與合成 300% 字型／短視窗。
- `PhotoGpsDeviceTest`：專用模擬供應者測試前景 GPS、新鮮度、停止後不再接收，以及定位按鈕平移且不新增照片歷史。此測試不取代實體手機的衛星定位精度驗收。

一般隔離回歸使用 `scripts/Test-RegressionSafely.py --classes ...`，會暫停並還原 Google 同步。GPS 裝置證明使用 `scripts/Test-PhotoGpsSafely.py --adb <adb> --serial <serial>`，另保存／還原精確、約略定位及 mock-location 模式；供應者為測試專用名稱，不替換系統 GPS。還原失敗會保留 `dist/.location-test-permissions.json`，需先救援再重跑。

實體手機驗收：在室外選擇精確定位、等待 GPS 就緒，拍攝不同地點的多張照片並保存；重開後核對位置及時間；測試 GPS 關閉、拒絕／約略權限、弱訊號、地圖定位與縮放、斷網後的本機清單。真實結果以 PROJECT_STATUS.md 為準。

官方參考：[MapLibre Android 建置版本](https://maplibre.org/maplibre-native/docs/book/platforms/android/android-build-flavors.html)、[GeoJsonOptions 聚合](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.sources/-geo-json-options/index.html)、[OpenFreeMap](https://openfreemap.org/quick_start/)、[Android 前景位置權限](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)。
