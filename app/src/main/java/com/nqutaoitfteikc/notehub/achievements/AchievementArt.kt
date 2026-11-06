package com.nqutaoitfteikc.notehub.achievements

import kotlin.math.*

/** Generated, self-contained vectors: no remote assets, scripts, personal event content or fonts. */
object AchievementArt {
    data class Design(val milestone: Long, val tier: Int, val title: String, val accent: String, val accent2: String) {
        val rings get() = min(2 + tier, 12)
        val jewels get() = min(8 + tier * 6, 96)
        val duration get() = 5.0 + tier * 0.37 + (milestone % 97) / 100.0
    }
    fun design(milestone: Long): Design {
        require(milestone > 0)
        val tier = when (milestone) { 1L -> 0; 5L -> 1; 10L -> 2; 30L -> 3; else -> 4 + max(0, floor(log10(milestone.toDouble())).toInt() - 1) * 3 + when { milestone.toString().startsWith("1") -> 0; milestone.toString().startsWith("3") -> 1; else -> 2 } }
        val early=mapOf(1L to "初心萌芽",5L to "微光拾穗",10L to "晨曦之星",30L to "月桂守望",50L to "流光編織",100L to "星河領航",300L to "璀璨殿堂",500L to "銀河典藏")
        val realms=listOf("千帆","萬里","十萬星","百萬光","千萬夢","億重山","十億河","百億海","千億穹","兆光","十兆雲","百兆虹","千兆月","京華","十京晨","百京夜")
        val exponent=max(3,milestone.toString().length-1)
        val realm=realms[(exponent-3).coerceIn(0,realms.lastIndex)]
        val suffix=when(milestone.toString().first()) { '1'->"啟航"; '3'->"織夢"; else->"加冕" }
        val title=early[milestone] ?: "$realm$suffix"
        val colors = listOf("#8ee8c3" to "#39aeb5", "#91c7ff" to "#6978ef", "#ffc978" to "#f18b8b", "#deb5ff" to "#8b89fc", "#ffdf8b" to "#e998b8", "#a2edff" to "#bdadff")
        val palette = colors[tier % colors.size]
        return Design(milestone, tier, "${title} · $milestone", palette.first, palette.second)
    }
    fun svg(milestone: Long, animated: Boolean = true): String {
        val d = design(milestone)
        val motion = if (!animated) "" else when (d.tier % 6) {
            0 -> "<animate attributeName=\"opacity\" values=\".5;1;.5\" dur=\"${d.duration}s\" repeatCount=\"indefinite\"/>"
            1 -> "<animateTransform attributeName=\"transform\" type=\"rotate\" values=\"-4 480 455;4 480 455;-4 480 455\" dur=\"${d.duration}s\" repeatCount=\"indefinite\"/>"
            2 -> "<animateTransform attributeName=\"transform\" type=\"translate\" values=\"0 0;0 -16;0 0\" dur=\"${d.duration}s\" repeatCount=\"indefinite\"/>"
            3 -> "<animate attributeName=\"stroke-dashoffset\" values=\"0;180\" dur=\"${d.duration}s\" repeatCount=\"indefinite\"/>"
            4 -> "<animateTransform attributeName=\"transform\" type=\"rotate\" from=\"0 480 455\" to=\"360 480 455\" dur=\"${d.duration * 6}s\" repeatCount=\"indefinite\"/>"
            else -> "<animate attributeName=\"stroke-width\" values=\"1;5;1\" dur=\"${d.duration}s\" repeatCount=\"indefinite\"/>"
        }
        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" width="960" height="1120" viewBox="0 0 960 1120" role="img" aria-labelledby="title desc"><title id="title">${d.title}</title><desc id="desc">累計新增 $milestone 次生活紀錄。NoteHub 成就；動畫為裝飾，不影響閱讀。</desc><defs><linearGradient id="bg" x2="1" y2="1"><stop stop-color="#101a36"/><stop offset="1" stop-color="#292144"/></linearGradient><linearGradient id="gem" x2="1" y2="1"><stop stop-color="${d.accent}"/><stop offset="1" stop-color="${d.accent2}"/></linearGradient><radialGradient id="halo"><stop stop-color="${d.accent}" stop-opacity=".28"/><stop offset="1" stop-color="${d.accent}" stop-opacity="0"/></radialGradient></defs><rect width="960" height="1120" rx="56" fill="url(#bg)"/><rect x="24" y="24" width="912" height="1072" rx="40" fill="none" stroke="${d.accent}" stroke-opacity=".4"/><circle cx="480" cy="455" r="400" fill="url(#halo)"/><text x="480" y="112" text-anchor="middle" fill="${d.accent}" font-family="sans-serif" font-size="32" letter-spacing="3">拾光成就</text>""")
            append("<g fill=\"none\" stroke=\"url(#gem)\" stroke-width=\"2\" stroke-dasharray=\"12 8\">")
            append(motion)
            repeat(d.rings) { i -> append("<circle cx=\"480\" cy=\"455\" r=\"${174 + i * 14}\" opacity=\"${1.0 - i * .055}\"/>") }
            append("</g><g fill=\"url(#gem)\">")
            repeat(d.jewels) { i ->
                val a = 2 * PI * i / d.jewels
                val r = 285 + (i % 3) * 15
                val x = 480 + cos(a) * r; val y = 455 + sin(a) * r
                val s = 4 + (i % 4) * 2
                append("<path d=\"M $x ${y - s} L ${x + s} $y L $x ${y + s} L ${x - s} $y Z\">")
                if (animated) append("<animate attributeName=\"opacity\" values=\".25;1;.25\" dur=\"${2.0 + (i % 7) * .21 + d.tier * .13}s\" begin=\"${i * .09}s\" repeatCount=\"indefinite\"/>")
                append("</path>")
            }
            append("</g>")
            if (d.tier >= 3) repeat(min(d.tier * 3, 36)) { i ->
                val a = PI * (.15 + i / (min(d.tier * 3, 36) - 1.0) * .7)
                val x = 480 + cos(a) * 245; val y = 455 + sin(a) * 245
                append("<ellipse cx=\"$x\" cy=\"$y\" rx=\"8\" ry=\"20\" transform=\"rotate(${a * 180 / PI - 40} $x $y)\" fill=\"${d.accent}\" opacity=\".6\"/>")
            }
            append("""<path d="M480 304 L518 404 L625 410 L542 478 L567 585 L480 526 L393 585 L418 478 L335 410 L442 404 Z" fill="url(#gem)" stroke="#fff" stroke-opacity=".7" stroke-width="3"/><path d="M480 304 L480 460 L625 410 M480 460 L567 585 M480 460 L393 585 M480 460 L335 410" fill="none" stroke="#fff" stroke-opacity=".3" stroke-width="2"/><text x="480" y="840" text-anchor="middle" fill="#fff5dc" font-family="sans-serif" font-size="${if(d.title.length>20) 36 else 52}" font-weight="bold">${d.title}</text><text x="480" y="915" text-anchor="middle" fill="${d.accent}" font-family="sans-serif" font-size="${if(milestone.toString().length>12) 28 else 40}">已新增 $milestone 次生活紀錄</text><text x="480" y="1015" text-anchor="middle" fill="#c4c9dd" font-family="sans-serif" font-size="30">讓每一次記錄，成為值得收藏的光。</text></svg>""")
        }
    }
}
