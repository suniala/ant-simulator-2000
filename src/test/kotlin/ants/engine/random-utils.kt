package ants.engine

import ants.common.RNG

fun constantRng(constant: Long): RNG = object : RNG {
    override fun nextLong(): Pair<Long, RNG> {
        return Pair(constant, this)
    }
}