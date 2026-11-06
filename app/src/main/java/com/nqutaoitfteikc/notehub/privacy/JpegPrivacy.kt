package com.nqutaoitfteikc.notehub.privacy

import java.io.ByteArrayOutputStream

/** Lossless JPEG structural parser. Drop ALL APP/COM segments and bytes following EOI,
 * including EXIF/GPS, XMP, IPTC, thumbnails, maker notes, ICC and appended payloads.
 * Entropy-coded pixels are preserved, including progressive scans and byte stuffing. */
object JpegPrivacy {
    fun strip(input: ByteArray): ByteArray {
        require(input.size in 4..(25*1024*1024) && input[0]==0xff.toByte() && input[1]==0xd8.toByte()) { "照片格式不正確" }
        val out=ByteArrayOutputStream(input.size); out.write(input,0,2)
        var pos=2; var scan=false
        while(pos<input.size) {
            if(scan) {
                val begin=pos
                while(pos<input.size) {
                    if(input[pos]!=0xff.toByte()) { pos++; continue }
                    require(pos+1<input.size) { "照片掃描資料不完整" }
                    val code=input[pos+1].toInt() and 255
                    if(code==0 || code in 0xd0..0xd7) { pos+=2; continue }
                    if(code==255) { pos++; continue }
                    break
                }
                out.write(input,begin,pos-begin); scan=false
            }
            require(pos+1<input.size && input[pos]==0xff.toByte()) { "照片區段不完整" }
            val begin=pos
            while(pos<input.size && input[pos]==0xff.toByte()) pos++
            require(pos<input.size) { "照片標記不完整" }
            val code=input[pos++].toInt() and 255
            if(code==0xd9) { out.write(255); out.write(code); return out.toByteArray() }
            require(code!=0 && code!=0xd8 && code !in 0xd0..0xd7) { "照片區段錯誤" }
            if(code==1) { out.write(input,begin,pos-begin); continue }
            require(pos+1<input.size) { "照片區段長度遺失" }
            val length=((input[pos].toInt() and 255) shl 8)+(input[pos+1].toInt() and 255)
            require(length>=2 && length<=input.size-pos) { "照片區段長度不正確" }
            pos+=length
            if(code !in 0xe0..0xef && code!=0xfe) out.write(input,begin,pos-begin)
            if(code==0xda) scan=true
        }
        throw IllegalArgumentException("照片缺少結束標記")
    }
}
