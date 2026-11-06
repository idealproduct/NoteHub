package com.nqutaoitfteikc.notehub.achievements

/** Explicit, overflow-safe scale. The caller selects the sequence beyond the first four awards. */
class MilestonePolicy(private val scale: List<Long>) {
    init { require(scale.isNotEmpty() && scale.all { it in 1..9 } && scale.distinct().size==scale.size) }
    fun contains(total: Long): Boolean {
        if(total in listOf(1L,5L,10L,30L)) return true
        if(total<=30) return false
        var power=10L
        while(power<=total) {
            if(scale.any { it<=Long.MAX_VALUE/power && it*power==total }) return true
            if(power>Long.MAX_VALUE/10) break
            power*=10
        }
        return false
    }
    fun nextAfter(total: Long): Long? {
        require(total>=0)
        val candidates=mutableListOf(1L,5L,10L,30L)
        var power=10L
        while(true) {
            scale.filter { it<=Long.MAX_VALUE/power }.forEach { candidates.add(it*power) }
            if(power>Long.MAX_VALUE/10) break
            power*=10
        }
        return candidates.filter { it>total && contains(it) }.minOrNull()
    }

}
