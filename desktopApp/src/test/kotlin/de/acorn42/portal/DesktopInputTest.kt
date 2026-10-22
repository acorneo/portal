package de.acorn42.portal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import de.acorn42.portal.world.MoveInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopInputTest {
    @Test
    fun keyRepeatsAndAliasReleasesAreIndependent() {
        val input = DesktopInput()
        repeat(3) { input.handle(Key.W, KeyEventType.KeyDown) }
        input.handle(Key.DirectionUp, KeyEventType.KeyDown)
        input.handle(Key.W, KeyEventType.KeyUp)
        assertEquals(MoveInput(0.0, -1.0), input.movement())
        input.handle(Key.DirectionUp, KeyEventType.KeyUp)
        assertEquals(MoveInput(), input.movement())
    }

    @Test
    fun oppositeDirectionsCancelAndReleasingOneRestoresTheOther() {
        val input = DesktopInput()
        input.handle(Key.A, KeyEventType.KeyDown)
        input.handle(Key.DirectionRight, KeyEventType.KeyDown)
        assertEquals(MoveInput(), input.movement())
        input.handle(Key.A, KeyEventType.KeyUp)
        assertEquals(MoveInput(1.0), input.movement())
    }

    @Test
    fun focusLossClearsEveryHeldKeyWithoutNeedingReleaseEvents() {
        val input = DesktopInput()
        input.handle(Key.S, KeyEventType.KeyDown)
        input.handle(Key.D, KeyEventType.KeyDown)
        input.clear()
        assertEquals(MoveInput(), input.movement())
        input.handle(Key.S, KeyEventType.KeyUp)
        assertEquals(MoveInput(), input.movement())
        assertFalse(input.handle(Key.Spacebar, KeyEventType.KeyDown))
    }
}
