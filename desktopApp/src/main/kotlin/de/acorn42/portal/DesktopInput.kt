package de.acorn42.portal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import de.acorn42.portal.world.MoveInput

/** Desktop key bookkeeping: repeats are idempotent and aliases release independently. */
internal class DesktopInput {
    private val held = mutableSetOf<Key>()
    private val movementKeys = setOf(Key.W, Key.A, Key.S, Key.D, Key.DirectionUp, Key.DirectionLeft, Key.DirectionDown, Key.DirectionRight)

    fun handle(event: KeyEvent): Boolean = handle(event.key, event.type)

    internal fun handle(key: Key, type: KeyEventType): Boolean {
        if (key !in movementKeys) return false
        when (type) {
            KeyEventType.KeyDown -> held.add(key)
            KeyEventType.KeyUp -> held.remove(key)
            else -> return false
        }
        return true
    }

    fun clear() = held.clear()

    fun movement(): MoveInput {
        fun down(primary: Key, alias: Key) = if (primary in held || alias in held) 1.0 else 0.0
        return MoveInput(
            down(Key.D, Key.DirectionRight) - down(Key.A, Key.DirectionLeft),
            down(Key.S, Key.DirectionDown) - down(Key.W, Key.DirectionUp),
        )
    }
}
