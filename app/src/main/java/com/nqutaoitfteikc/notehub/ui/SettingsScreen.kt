package com.nqutaoitfteikc.notehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nqutaoitfteikc.notehub.domain.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(theme: ThemeMode, setTheme: (ThemeMode)->Unit,
    fontScale: Float, setFontScale: (Float)->Unit, onImport: ()->Unit, onSync: ()->Unit,
    permissions: @Composable ()->Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val tabs=listOf("外觀","權限","Google","資料")
    Column(Modifier.fillMaxSize()) {
        Text("設定",Modifier.padding(horizontal=20.dp,vertical=12.dp),style=MaterialTheme.typography.headlineMedium)
        ScrollableTabRow(selectedTabIndex=page,edgePadding=8.dp) {
            tabs.forEachIndexed { index,title ->
                Tab(selected=page==index,onClick={page=index},text={Text(title)},modifier=Modifier.testTag("settings-tab-$index"))
            }
        }
        key(page) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("settings-content"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                when(page) {
                    0 -> {
                        item { Text("字型與主題",style=MaterialTheme.typography.titleLarge) }
                        item { FontSizeSetting(fontScale,setFontScale) }
                        items(ThemeMode.entries) { mode ->
                            Row(Modifier.fillMaxWidth().selectable(selected=mode==theme,role=Role.RadioButton,onClick={setTheme(mode)}).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                RadioButton(selected=mode==theme,onClick=null)
                                Text(when(mode) { ThemeMode.SYSTEM->"跟隨系統"; ThemeMode.LIGHT->"淺色"; ThemeMode.DARK->"深色" })
                            }
                        }
                    }
                    1 -> item { Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("裝置權限",style=MaterialTheme.typography.titleLarge)
                        permissions()
                        Text("拍照精確定位預設關閉，請在拍照頁主動開啟。地圖的「定位到我」僅在點擊後取得前景位置。")
                    } }
                    2 -> {
                        item { Text("Google 行事曆",style=MaterialTheme.typography.titleLarge) }
                        item { Button(onClick=onImport,modifier=Modifier.fillMaxWidth()) { Text("Google 行事曆匯入") } }
                        item { Button(onClick=onSync,modifier=Modifier.fillMaxWidth()) { Text("Google 雙向定期同步") } }
                        item { Text("手動匯入維持唯讀；雙向同步需要另行授權並啟用，只同步一般有日期事項。照片記事（包含文字、拍攝時間與位置）、原圖及提醒不會上傳。舊雲端副本需在 Google 手動管理。") }
                    }
                    3 -> {
                        item { StorageCapacity() }
                        item { Text("資料儲存與隱私",style=MaterialTheme.typography.titleLarge) }
                        item { Text("記事、照片與設定保存在此裝置，沒有自有後端或雲端資料庫；解除安裝會刪除 App 內資料。照片原圖與精確座標使用 Keystore 金鑰加密，記事文字與時間仍在 App 私有資料庫，目前沒有整體備份功能。") }
                        item { Text("匯出照片會清除位置、原始拍攝時間與其他非必要附加資訊；相簿若啟用雲端備份，匯出副本可能由相簿服務上傳。") }
                        item { Text("裝置時區：${java.time.ZoneId.systemDefault().id}。指定時間依目前時區顯示，全天事件保留原日期。") }
                        item { Text("NoteHub ${com.nqutaoitfteikc.notehub.BuildConfig.VERSION_NAME}（${com.nqutaoitfteikc.notehub.BuildConfig.VERSION_CODE}） · 本機記事與選用行事曆同步") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCapacity() {
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current
    fun capacity()=runCatching { android.os.StatFs(context.filesDir.path).let { it.availableBytes to it.totalBytes } }
    var storage by remember { mutableStateOf(capacity()) }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) storage=capacity() }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth().testTag("storage-capacity")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("裝置內部儲存空間",style=MaterialTheme.typography.titleMedium)
            storage.fold(onSuccess={ (free,total) ->
                Text("可用 ${android.text.format.Formatter.formatFileSize(context,free)}／共 ${android.text.format.Formatter.formatFileSize(context,total)}")
                if(free<256L*1024*1024 || total>0 && free.toDouble()/total<.05) Text("剩餘空間偏低，可能無法拍照、匯出或更新資料；請先釋出空間。",color=MaterialTheme.colorScheme.error)
            },onFailure={Text("目前無法讀取剩餘空間，請至系統儲存空間設定查看。")})
        }
    }
}
