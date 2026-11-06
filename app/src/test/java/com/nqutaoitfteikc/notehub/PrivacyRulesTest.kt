package com.nqutaoitfteikc.notehub
import com.nqutaoitfteikc.notehub.privacy.JpegPrivacy
import org.junit.Assert.*
import org.junit.Test
class PrivacyRulesTest {
    @Test fun allAppSegmentsCommentsAndTrailingPayloadAreRemovedWithoutChangingScan() {
        val header=byteArrayOf(-1,-40)
        val metadata=(0xe0..0xef).flatMap { listOf(255,it,0,8,71,80,83,88,77,80) }.map { it.toByte() }.toByteArray()
        val comment=byteArrayOf(-1,-2,0,5,71,80,83)
        val scan=byteArrayOf(-1,-38,0,2,1,2,-1,0,3,-1,-48,4,-1,-39)
        val input=header+metadata+comment+scan+"secret-coordinate".toByteArray()
        assertArrayEquals(header+scan,JpegPrivacy.strip(input))
    }
    @Test fun malformedOrTruncatedJpegNeverFallsBackToUnfilteredBytes() {
        listOf(byteArrayOf(1,2,3),byteArrayOf(-1,-40,-1,-31,127,127),byteArrayOf(-1,-40,-1,-38,0,2,1,2)).forEach {
            try { JpegPrivacy.strip(it); fail("必須拒絕損壞檔案") } catch(_:IllegalArgumentException) { }
        }
    }
}
