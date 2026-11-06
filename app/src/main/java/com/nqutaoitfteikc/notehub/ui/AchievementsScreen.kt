package com.nqutaoitfteikc.notehub.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nqutaoitfteikc.notehub.achievements.AchievementArt
import com.nqutaoitfteikc.notehub.domain.Achievement
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable
fun AchievementsScreen(earned: List<Achievement>, total: Long, onOpen: (Achievement)->Unit, onTest: (Long,Boolean)->Unit = {_,_->}) {
    LazyColumn(Modifier.fillMaxSize().testTag("achievements-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("成就",style=MaterialTheme.typography.headlineLarge) }
        item {
            val next=remember(total) { com.nqutaoitfteikc.notehub.achievements.MilestonePolicy(listOf(1,3,5)).nextAfter(total) }
            Card(Modifier.fillMaxWidth().testTag("achievement-total"),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("累積紀錄次數",style=MaterialTheme.typography.titleMedium)
                    Text(java.text.NumberFormat.getIntegerInstance(java.util.Locale.TAIWAN).format(total),Modifier.horizontalScroll(rememberScrollState()),style=MaterialTheme.typography.displayLarge,maxLines=1)
                    Text("已累積 $total 次生活紀錄")
                    if(next!=null) {
                        LinearProgressIndicator(progress={ (total.toDouble()/next).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                        Text("下一成就：$next 次，還差 ${next-total} 次")
                    } else Text("已達目前所有成就門檻")
                }
            }
        }
        if(com.nqutaoitfteikc.notehub.BuildConfig.DEBUG) item { AchievementTestMenu(onTest) }
        if(earned.isEmpty()) item { Text("尚未有成就。建立第一篇照片記事或行事曆事件，收藏您的第一道光。") }
        items(earned,key={it.milestone}) { row ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(row) }.testTag("achievement-${row.milestone}")) {
                Row(Modifier.padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AchievementEmblem(row.milestone,Modifier.size(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(AchievementArt.design(row.milestone).title,style=MaterialTheme.typography.titleLarge)
                        Text("${row.milestone} 次紀錄 · ${DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(row.unlockedAt))}",style=MaterialTheme.typography.bodyMedium)
                    }
                    Text("›",style=MaterialTheme.typography.titleLarge)
                }
            }
        }

    }
}

@Composable
internal fun AchievementEmblem(milestone: Long, modifier: Modifier=Modifier) {
    val d=remember(milestone) { AchievementArt.design(milestone) }
    val color=Color(d.accent.toColorInt())
    Canvas(modifier.semantics { contentDescription="${d.title} 成就徽章" }) {
        val center=Offset(size.width/2,size.height/2); val r=size.minDimension*.25f
        repeat(d.rings) { drawCircle(color.copy(alpha=.8f-it*.04f),r+it*2.5f,center,style=Stroke(1.5f)) }
        repeat(d.jewels) { i -> val a=2*PI*i/d.jewels
            drawCircle(color,2f,Offset(center.x+cos(a).toFloat()*r*1.55f,center.y+sin(a).toFloat()*r*1.55f)) }
        val path=androidx.compose.ui.graphics.Path()
        repeat(10) { i -> val a=-PI/2+i*PI/5; val radius=if(i%2==0) r*.85f else r*.4f
            val x=center.x+cos(a).toFloat()*radius; val y=center.y+sin(a).toFloat()*radius
            if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }
        path.close(); drawPath(path,color)
    }
}

@Composable
fun AchievementDialog(earned: Achievement, newlyUnlocked: Boolean, onDismiss: ()->Unit, onShare: (Boolean)->Unit) {
    val reduceMotion=android.provider.Settings.Global.getFloat(LocalContext.current.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    var animation by remember(earned.milestone) { mutableStateOf(!reduceMotion) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(if(newlyUnlocked) Modifier.widthIn(max=560.dp).fillMaxWidth(.94f).fillMaxHeight(.94f) else Modifier.fillMaxSize().safeDrawingPadding(),shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(newlyUnlocked) "成就達成！" else "成就典藏",style=MaterialTheme.typography.headlineMedium)
                Text(AchievementArt.design(earned.milestone).title,style=MaterialTheme.typography.titleLarge)
                AchievementSvg(earned.milestone,animation,Modifier.fillMaxWidth().aspectRatio(960f/1120f).clipToBounds())
                Text("累積 ${earned.milestone} 次生活紀錄的成就。")
                OutlinedButton(onClick={animation=!animation},modifier=Modifier.fillMaxWidth()) { Text(if(animation) "暫停裝飾動畫" else "播放裝飾動畫") }

                OutlinedButton(onClick={onShare(false)},modifier=Modifier.fillMaxWidth().testTag("share-achievement-png")) { Text("分享成就圖片…") }
                TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth().testTag("close-achievement")) { Text(if(newlyUnlocked) "稍後再分享" else "關閉") }
            }
        }
    }
}

/** Only app-generated SVG is loaded. No script, file/content access, navigation or network. */
@Composable
private fun AchievementSvg(milestone: Long, animated: Boolean, modifier: Modifier) {
    val svg=remember(milestone,animated) { AchievementArt.svg(milestone,animated) }
    var failed by remember(milestone) { mutableStateOf(false) }
    if(failed) {
        Column(modifier) {
            AchievementEmblem(milestone,Modifier.fillMaxWidth().weight(1f))
            Text("此裝置暫時無法播放 SVG 動畫；成就與圖片仍保留，可繼續分享。",style=MaterialTheme.typography.bodySmall)
        }
        return
    }
    key(svg) {
        AndroidView(modifier=modifier.semantics { contentDescription="${AchievementArt.design(milestone).title}，${if(animated) "動畫" else "靜態"}成就圖" },factory={ context ->
            try { WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled=false
                settings.allowFileAccess=false; settings.allowContentAccess=false
                settings.blockNetworkLoads=true
                settings.builtInZoomControls=false
                isVerticalScrollBarEnabled=false; isHorizontalScrollBarEnabled=false
                webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(view:WebView,request:android.webkit.WebResourceRequest)=true
                    override fun onPageFinished(view:WebView,url:String?) {
                        if(!failed) view.postVisualStateCallback(1,object:WebView.VisualStateCallback() { override fun onComplete(requestId:Long) { if(view.isAttachedToWindow) view.invalidate() } })
                    }
                    override fun onReceivedError(view:WebView,request:android.webkit.WebResourceRequest,error:android.webkit.WebResourceError) {
                        if(request.isForMainFrame) {
                            android.util.Log.e("NoteHubAchievements","SVG 預覽載入失敗：${error.errorCode}")
                            failed=true
                        }
                    }
                    override fun onRenderProcessGone(view:WebView,detail:android.webkit.RenderProcessGoneDetail):Boolean {
                        android.util.Log.e("NoteHubAchievements","SVG 繪製程序已結束，改用原生徽章")
                        failed=true
                        return true
                    }
                }
                loadDataWithBaseURL("https://notehub.invalid/","<html><head><meta name='viewport' content='width=device-width,initial-scale=1'/><style>html,body{margin:0;width:100%;height:100%;background:transparent;overflow:hidden}svg{width:100%;height:100%;position:absolute}</style></head><body>$svg</body></html>","text/html","UTF-8",null)
            } } catch(e:Exception) {
                android.util.Log.e("NoteHubAchievements","無法建立 SVG 預覽，改用原生徽章",e)
                failed=true
                android.widget.FrameLayout(context)
            }
        },onRelease={ view ->
            if(view is WebView) {
                try { view.stopLoading() } catch(e:RuntimeException) { android.util.Log.w("NoteHubAchievements","停止 SVG 預覽失敗",e) }
                try { view.destroy() } catch(e:RuntimeException) { android.util.Log.w("NoteHubAchievements","釋放 SVG 預覽失敗",e) }
            }
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementTestMenu(onTest: (Long,Boolean)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(1L) }
    val choices=remember { buildList { var n:Long?=1L; val policy=com.nqutaoitfteikc.notehub.achievements.MilestonePolicy(listOf(1,3,5)); while(n!=null) { add(n); n=policy.nextAfter(n) } } }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("成就測試（僅除錯版）",style=MaterialTheme.typography.titleSmall)
            Box {
                OutlinedButton(onClick={expanded=true}) { Text("選擇成就：$selected 次 ▾") }
                DropdownMenu(expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=280.dp)) {
                    choices.forEach { value -> DropdownMenuItem(text={Text("$value 次 · ${AchievementArt.design(value).title}")},onClick={selected=value;expanded=false}) }
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={onTest(selected,false)}) { Text("達成成就") }
                OutlinedButton(onClick={onTest(selected,true)}) { Text("重置該成就") }
            }
        }
    }
}
