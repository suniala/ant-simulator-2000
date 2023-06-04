package ants.common

import kotlin.random.asKotlinRandom

interface RNG {
    fun nextLong(): Pair<Long, RNG>
}

private data class KotlinRandomRNG(private val random: kotlin.random.Random) : RNG {
    override fun nextLong(): Pair<Long, RNG> {
        return Pair(random.nextLong(), this)
    }
}

fun pseudoRNG(seed: Long): RNG = KotlinRandomRNG(java.util.Random(seed).asKotlinRandom())

fun nextLong(rng: RNG): Pair<Long, RNG> = rng.nextLong()

fun nextNonNegativeLong(rng: RNG): Pair<Long, RNG> {
    val (c, nextRng) = rng.nextLong()
    return if (c < 0) {
        Pair(c + Long.MAX_VALUE + 1, nextRng)
    } else {
        Pair(c, nextRng)
    }
}

fun nextFloat(rng: RNG): Pair<Float, RNG> {
    val (long, nextRng) = nextNonNegativeLong(rng)
    return Pair(long.toFloat() / (Long.MAX_VALUE.toFloat() + 1), nextRng)
}

fun nextFloat(rng: RNG, min: Float, max: Float): Pair<Float, RNG> {
    val (float, nextRng) = nextFloat(rng)
    val normalized = float * (max - min) - (max - min) / 2
    return Pair(normalized, nextRng)
}

fun nextBooleanWithProbability(rng: RNG, probability: Float): Pair<Boolean, RNG> {
    require(probability >= 0)
    require(probability <= 1)
    return when (probability) {
        0f -> Pair(false, rng)
        1f -> Pair(true, rng)
        else -> nextFloat(rng).let { (float, nextRng) -> Pair(float <= probability, nextRng) }
    }
}