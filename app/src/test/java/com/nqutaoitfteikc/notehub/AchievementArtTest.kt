package com.nqutaoitfteikc.notehub

import com.nqutaoitfteikc.notehub.achievements.*
import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream

class AchievementArtTest {
    @Test fun vectorsAreValidUniqueAndContainNoExternalAssetsOrScripts() {
        val milestones=listOf(1L,5L,10L,30L,50L,100L,300L,500L,1000L,Long.MAX_VALUE)
        val titles=mutableSetOf<String>(); val motions=mutableSetOf<String>()
        var previousJewels=0
        milestones.forEach { m ->
            val svg=AchievementArt.svg(m)
            val parser=DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true }
            val doc=parser.newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))
            assertEquals("svg",doc.documentElement.localName)
            assertTrue(svg.contains("拾光成就")); assertFalse(svg.contains("ACHIEVEMENT")); assertFalse(svg.contains("獨立提醒"))
            assertTrue(titles.add(AchievementArt.design(m).title))
            assertFalse(svg.contains("<script")); assertFalse(svg.contains("href=")); assertFalse(svg.contains("<image"))
            assertTrue(svg.contains("repeatCount=\"indefinite\""))
            assertTrue(motions.add(Regex("dur=\"[^\"]+\"").find(svg)!!.value))
            assertTrue(AchievementArt.design(m).jewels>=previousJewels)
            previousJewels=AchievementArt.design(m).jewels
            assertFalse(AchievementArt.svg(m,false).contains("<animate"))
        }
    }
    @Test fun everyReachableMilestoneHasADifferentChineseNameWithoutNumericSuffix() {
        val policy=MilestonePolicy(listOf(1,3,5)); var next:Long?=1L; val names=mutableSetOf<String>()
        while(next!=null) {
            val title=AchievementArt.design(next).title.substringBefore(" ·")
            assertTrue("名稱不重複：$next $title",names.add(title)); assertFalse(title.contains("萬象之境"))
            next=policy.nextAfter(next)
        }
        assertTrue(names.size>50)
    }
    @Test fun scaleUsesExplicitChoicesWithoutOverflowOrExtraAwards() {
        val policy=MilestonePolicy(listOf(1,3,5))
        listOf(1L,5L,10L,30L,50L,100L,300L,500L,1000L,5_000_000_000_000_000_000L).forEach { assertTrue("$it",policy.contains(it)) }
        listOf(-1L,0L,2L,6L,20L,31L,60L,101L,Long.MAX_VALUE).forEach { assertFalse("$it",policy.contains(it)) }
    }
}
