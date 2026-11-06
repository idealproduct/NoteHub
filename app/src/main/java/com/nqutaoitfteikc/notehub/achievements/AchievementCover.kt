package com.nqutaoitfteikc.notehub.achievements

import android.graphics.*
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import kotlin.math.*

/** Gallery companion, using the same geometry and palette as the animated SVG. */
object AchievementCover {
    fun bitmap(milestone: Long): Bitmap {
        val d = AchievementArt.design(milestone)
        val bitmap = createBitmap(960, 1120)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val a = d.accent.toColorInt(); val b = d.accent2.toColorInt()
        p.shader = LinearGradient(0f, 0f, 960f, 1120f, Color.rgb(16,26,54), Color.rgb(41,33,68), Shader.TileMode.CLAMP)
        c.drawRoundRect(0f,0f,960f,1120f,56f,56f,p)
        p.shader = null; p.color = a; p.alpha = 100; p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        c.drawRoundRect(24f,24f,936f,1096f,40f,40f,p)
        p.alpha = 255; p.shader = LinearGradient(200f,200f,760f,700f,a,b,Shader.TileMode.CLAMP)
        repeat(d.rings) { c.drawCircle(480f,455f,174f+it*14,p) }
        p.style = Paint.Style.FILL
        repeat(d.jewels) { i ->
            val angle = 2*PI*i/d.jewels; val r = 285+(i%3)*15
            val x = 480+cos(angle).toFloat()*r; val y = 455+sin(angle).toFloat()*r; val s = 4f+(i%4)*2
            c.drawPath(Path().apply { moveTo(x,y-s); lineTo(x+s,y); lineTo(x,y+s); lineTo(x-s,y); close() },p)
        }
        if (d.tier >= 3) repeat(min(d.tier*3,36)) { i ->
            val angle = PI*(.15+i/(min(d.tier*3,36)-1.0)*.7)
            val x = 480+cos(angle).toFloat()*245; val y = 455+sin(angle).toFloat()*245
            p.alpha=153
            c.withRotation((angle*180/PI-40).toFloat(),x,y) { drawOval(x-8,y-20,x+8,y+20,p) }; p.alpha=255
        }
        val points = floatArrayOf(480f,304f,518f,404f,625f,410f,542f,478f,567f,585f,480f,526f,393f,585f,418f,478f,335f,410f,442f,404f)
        c.drawPath(Path().apply { moveTo(points[0],points[1]); for(i in 2 until points.size step 2) lineTo(points[i],points[i+1]); close() },p)
        p.shader=null; p.style=Paint.Style.STROKE; p.color=Color.WHITE; p.alpha=76
        listOf(480f to 304f,625f to 410f,567f to 585f,393f to 585f,335f to 410f).forEach { (x,y) -> c.drawLine(480f,460f,x,y,p) }
        p.alpha=255; p.style=Paint.Style.FILL; p.textAlign=Paint.Align.CENTER; p.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        fun text(text:String,y:Float,size:Float,color:Int,bold:Boolean=false) { p.textSize=size; p.color=color; p.isFakeBoldText=bold; c.drawText(text,480f,y,p) }
        text("拾光成就",112f,32f,a)
        text(d.title,840f,52f,Color.rgb(255,245,220),true)
        text("已新增 $milestone 次生活紀錄",915f,40f,a)
        text("讓每一次記錄，成為值得收藏的光。",1015f,30f,Color.rgb(196,201,221))
        return bitmap
    }
}
