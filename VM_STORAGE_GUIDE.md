## 新 25 GB VM 驗收完成（2026-11-01）

- 使用者完成 USB 偵錯授權後，已確認 AVD 為 Pixel_9_Pro_25GB（emulator-5556）。原 Pixel_9_Pro（emulator-5554）保留，沒有刪除、清除或搬移其資料。
- Android 實際 df：/data 總容量 24G、已用 1.6G、可用 23G、使用率 7%；25 GiB 虛擬磁碟扣除檔案系統空間後的正常顯示，已不只是設定數字。
- 已安裝 dist/NoteHub-0.16.0-debug.apk 與對應測試 APK，兩者均 Success。沒有修改 App 原始碼或重建 APK。
- 新 VM 無 Google 帳戶、同步預設關閉，因此直接執行限定 instrumentation：DatabaseStartupTest、StartupSettingsTest、SettingsTabsUiTest；OK (3 tests)，6.555 秒。包含正式資料庫重開、實際 App 啟動／所有資料讀取、四分頁／選擇保存、300% 大字型短螢幕操作。
- 再次 force-stop／am start -W：COLD、Status ok、941 ms、PID 10934。本次 PID logcat 未找到 FATAL EXCEPTION、SQLiteException、SQLiteFullException、ENOSPC 或原 PRAGMA 錯誤。
- 證據：dist/feature16-new25gb-device.log、feature16-new25gb-storage.log、feature16-new25gb-startup.log。先前等待授權與容量待驗證敘述屬歷史紀錄，以本節為準。
- App 已保持開啟。舊記事與照片仍只在舊 VM，沒有宣稱遷移成功；新 VM 的 Google 登入／授權須由使用者自行完成，本輪沒有測試其 Google 同步。

---

## 已另建 25 GB VM（2026-11-01 後續）

使用者已明確選擇「另建 25 GB VM，保留舊 VM」。已透過 IntelliJ Device Manager → Add Device 建立 **Pixel 9 Pro 25GB**（AVD id：Pixel_9_Pro_25GB），使用已安裝的 API 37.2／Google Play Store／x86_64 16 KB page size 系統映像，首次啟動前將 Internal storage 設為 25 GB。

新舊 AVD 皆保留。唯讀解析 qcow2 檔頭確認：

- 舊 Pixel_9_Pro：6,442,450,944 bytes（6 GiB）。
- 新 Pixel_9_Pro_25GB：26,843,545,600 bytes（25 GiB）。

證據：dist/vm16-created-proof.json。這是虛擬磁碟容量證據，尚不能取代 Android df 的實際檔案系統／可用容量驗收。

新 VM 已開機，IntelliJ Running Devices 有獨立分頁，ADB 為 emulator-5556；舊 VM 仍為 emulator-5554。新 VM 目前顯示「Allow USB debugging?」，需要使用者自行按 Allow。依 computer-use 技能的安全授權限制，未代按或繞過授權，因此 App 安裝、df 驗證與新 VM 啟動測試仍待授權完成。Google 帳號也未登入；既有記事／照片留在舊 VM，沒有宣稱已搬移。

---

# Pixel 9 Pro 內部容量調整（2026-11-01）

## 已完成的 IntelliJ 操作

1. Device Manager → Pixel 9 Pro → Stop，先停止 NoteHub 寫入並等待 VM 關閉。
2. 選單 Edit → Additional settings → Storage → Internal storage：25 GB → Finish。
3. 已唯讀核對 config.ini 的 disk.dataPartition.size=25G，hardware-qemu.ini 也為 25g。
4. 關機期間將整個 AVD 備份於專案忽略的 dist/vm16-before-resize；包含資料磁碟、加密金鑰磁碟與快照，只留在本機，不提交 Git。
5. 從 IntelliJ 選 Cold Boot 啟動，沒有 Wipe Data、刪除 VM 或解除安裝。

## 實際結果：設定 25 GB，舊磁碟仍為 6 GB

原 config.ini 曾是 50G，但 Android df 顯示 /data 總量 5.8G、已用 4.6G、可用 0.9G（83%）。改為 25G 並冷啟動後，實際 df 仍相同。

對離線備份執行 SDK qemu-img info（唯讀），確認 userdata-qemu.img.qcow2 的 virtual size 為 6.0G／6,442,450,944 bytes，corrupt=false。Android /data 為 ext4 且使用 /metadata/vold/metadata_encryption。這表示尚未完成實際容量擴大，不能用設定頁的數字宣稱成功。

沒有對原加密磁碟執行試驗性 resize2fs／格式化，也沒有刪除 qcow2 或快照。主機 C 槽原有約 460 GB 可用，主機不是本次空間瓶頸。

## 使用者已選擇另建 VM（後續驗收見本頁頂部）

- 另建 25 GB VM 並保留現有 Pixel 9 Pro：新 VM 可重新登入 Google，原記事、照片、Keystore 與帳戶仍完整留在舊 VM；不能直接搬 App 加密檔後宣稱可讀，跨 VM 金鑰需要另行設計遷移。
- 保留現有 VM，不重建：目前資料與 App 正常，IntelliJ 的預期容量仍設為 25 GB，但實際容量暫時只有 6 GB。

使用者已回覆「另建 25 GB VM，保留舊 VM」。新裝置已透過 IntelliJ 建立為 Pixel 9 Pro 25GB，正進行首次啟動容量驗收，沒有清除或替換原 VM。若未來決定清除／重建，須先明確確認資料遷移及既有加密原圖可復原；保留舊 VM 是必要保護。

## 與啟動錯誤的區別

本輪錯誤來自 NoteHub 0.15 的 PRAGMA secure_delete=ON 使用 execSQL；Android 37 SQLite 驅動對回傳資料列的指令拒絕此方式，導致資料庫打不開，連帶出現記事、照片、成就與提醒的讀取錯誤。0.16 已改 query 並驗證結果。

在尚未實際擴大、仍剩 0.9 GB 的同一 VM，10 項回歸、1 項大字型測試與三次冷啟動均通過，三次新 PID 的相關錯誤數皆為 0。沒有 SQLiteFullException、ENOSPC 等容量不足證據；偏低的剩餘空間仍應另外處理。

參考：[Android AVD 管理](https://developer.android.com/studio/run/managing-avds)、[Android Emulator 原始碼的既有分割區調整處理](https://android.googlesource.com/platform/external/qemu/+/emu-master-dev/android-qemu2-glue/main.cpp)。
