package ants.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.math.sqrt

class MathTest {
    @ParameterizedTest
    @MethodSource("calculateMovementCases")
    fun `calculateMovement produces expected result`(testCase: Triple<Direction, Distance, PositionDelta>) {
        testCase.also { (direction, distance, expected) ->
            assertEquals(expected.dx, calculateMovement(direction, distance).dx, 0.001f)
            assertEquals(expected.dy, calculateMovement(direction, distance).dy, 0.001f)
        }
    }

    @ParameterizedTest
    @MethodSource("directionToCases")
    fun `directionTo produces expected result`(testCase: Triple<WorldPosition, WorldPosition, Direction>) {
        testCase.also { (a, b, expected) ->
            assertEquals(expected.degrees, directionTo(a, b).degrees, 0.001f)
        }
    }

    private companion object {
        @JvmStatic
        fun calculateMovementCases(): Stream<Triple<Direction, Distance, PositionDelta>> = Stream.of(
            Triple(Direction(0f), Distance(0f), PositionDelta(0f, 0f)),
            Triple(Direction(90f), Distance(0f), PositionDelta(0f, 0f)),
            Triple(Direction(180f), Distance(0f), PositionDelta(0f, 0f)),
            Triple(Direction(270f), Distance(0f), PositionDelta(0f, 0f)),
            Triple(Direction(0f), Distance(1f), PositionDelta(0f, -1f)),
            Triple(Direction(90f), Distance(1f), PositionDelta(1f, 0f)),
            Triple(Direction(180f), Distance(1f), PositionDelta(0f, 1f)),
            Triple(Direction(270f), Distance(1f), PositionDelta(-1f, 0f)),
            Triple(Direction(45f), Distance(sqrt(2f)), PositionDelta(1f, -1f)),
            Triple(Direction(135f), Distance(sqrt(2f)), PositionDelta(1f, 1f)),
            Triple(Direction(225f), Distance(sqrt(2f)), PositionDelta(-1f, 1f)),
            Triple(Direction(315f), Distance(sqrt(2f)), PositionDelta(-1f, -1f)),
        )

        @JvmStatic
        fun directionToCases(): Stream<Triple<WorldPosition, WorldPosition, Direction>> = Stream.of(
            Triple(WorldPosition(0f, 0f), WorldPosition(0f, -42f), Direction(0f)),
            Triple(WorldPosition(0f, 0f), WorldPosition(12f, -12f), Direction(45f)),
            Triple(WorldPosition(0f, 0f), WorldPosition(39f, 0f), Direction(90f)),
            Triple(WorldPosition(0f, 0f), WorldPosition(12f, 12f), Direction(135f)),
            Triple(WorldPosition(0f, 0f), WorldPosition(0f, 3f), Direction(180f)),
            Triple(WorldPosition(0f, 0f), WorldPosition(-12f, 12f), Direction(225f)),
            Triple(WorldPosition(0f, 0f), WorldPosition(-123f, 0f), Direction(270f)),
            Triple(WorldPosition(0f, 0f), WorldPosition(-12f, -12f), Direction(315f)),
        )
    }
}
