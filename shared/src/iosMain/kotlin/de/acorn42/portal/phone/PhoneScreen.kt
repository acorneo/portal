package de.acorn42.portal.phone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.acorn42.portal.world.Vec2

private val Ink = Color(0xFF080F1B)
private val Mint = Color(0xFF6FFFE2)
private val Muted = Color(0xFF9AAEC4)

@Composable
fun PhoneScreen() {
    val host = rememberPhoneHost()
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, primary = Mint)) {
        Column(Modifier.fillMaxSize().background(Ink).safeContentPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("P O R T A L", color = Mint, fontSize = 24.sp, fontWeight = FontWeight.Light)
                    Text("01 / THE POCKET ROOM", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Text("OFFLINE", color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(12.dp))
            RoomCanvas(host.state, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(12.dp))
            Text(if (host.state.completed) "EXIT REACHED" else if (!host.active) "PAUSED" else if (host.ready) "ROLL TO THE VIOLET EXIT" else "SET YOUR NEUTRAL", color = Mint, fontSize = 12.sp)
            Text(host.message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = host::reset, enabled = host.active) { Text("Reset room") }
                if (!host.touchMode) OutlinedButton(onClick = host::calibrate, enabled = host.active && host.hasSample && !host.state.completed) { Text("Calibrate") }
            }
            if (host.touchMode) {
                Text("SIMULATOR / TOUCH INPUT", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                TouchPad(host, Modifier.fillMaxWidth().height(100.dp).padding(top = 6.dp))
            } else {
                TextButton(onClick = host::useTouch, enabled = host.active && !host.state.completed) { Text("Use simulator / touch fallback") }
                Text("Portrait · hold steady and calibrate. Tilt more to accelerate more.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TouchPad(host: PhoneHost, modifier: Modifier) {
    var pointer by remember(host.active, host.ready) { mutableStateOf(Offset.Zero) }
    Canvas(modifier.background(Color(0xFF142638)).semantics { contentDescription = "Simulator tilt pad. Drag from center; release to coast." }
        .pointerInput(host.active, host.ready) {
            fun update(position: Offset) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) / 2f
                val delta = position - center
                pointer = delta
                host.touch(Vec2(delta.x / radius * 0.75, delta.y / radius * 0.75))
            }
            detectDragGestures(
                onDragStart = ::update,
                onDragEnd = { pointer = Offset.Zero; host.touch(Vec2.Zero) },
                onDragCancel = { pointer = Offset.Zero; host.touch(Vec2.Zero) },
                onDrag = { change, _ -> change.consume(); update(change.position) },
            )
        }) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Muted.copy(alpha = 0.4f), size.height * 0.35f, center, style = Stroke(1.dp.toPx()))
        drawLine(Muted, center - Offset(12f, 0f), center + Offset(12f, 0f))
        drawLine(Muted, center - Offset(0f, 12f), center + Offset(0f, 12f))
        val radius = size.height * 0.35f
        val knob = pointer * (radius / pointer.getDistance().coerceAtLeast(radius))
        drawCircle(Mint, 7.dp.toPx(), center + knob)
    }
}

@Composable
private fun RoomCanvas(state: PhoneState, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Handmade room with two alternating wall gaps, glowing sphere and violet exit" }) {
        val room = PhoneRoom.maze
        val scale = minOf(size.width / room.width, size.height / room.height)
        val origin = Offset((size.width - room.width * scale) / 2, (size.height - room.height * scale) / 2)
        translate(origin.x, origin.y) {
            drawRect(Color(0xFF101E2E), size = Size(room.width * scale, room.height * scale))
            for (x in 1 until room.width) drawLine(Color(0xFF192B3D), Offset(x * scale, 0f), Offset(x * scale, room.height * scale))
            for (y in 1 until room.height) drawLine(Color(0xFF192B3D), Offset(0f, y * scale), Offset(room.width * scale, y * scale))
            for (wall in room.walls) {
                val offset = Offset(wall.left.toFloat() * scale, wall.top.toFloat() * scale)
                val extent = Size((wall.right - wall.left).toFloat() * scale, (wall.bottom - wall.top).toFloat() * scale)
                drawRect(Color(0xFF304A62), offset, extent)
                drawLine(Color(0xFF567792), offset, offset + Offset(extent.width, 0f), 1f)
            }
            val exit = Offset(PhoneRoom.exit.x.toFloat() * scale, PhoneRoom.exit.y.toFloat() * scale)
            val violet = if (state.completed) Mint else Color(0xFFB89AFF)
            drawCircle(violet.copy(alpha = 0.15f), scale * 0.9f, exit)
            drawCircle(violet, PhoneRoom.exitRadius.toFloat() * scale, exit, style = Stroke(2.dp.toPx()))
            drawCircle(violet.copy(alpha = 0.4f), scale * 0.35f, exit, style = Stroke(1.dp.toPx()))
            val center = Offset(state.sphere.position.x.toFloat() * scale, state.sphere.position.y.toFloat() * scale)
            val radius = state.sphere.radius.toFloat() * scale
            drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha = 0.35f), Color.Transparent), center, radius * 3), radius * 3, center)
            drawCircle(Mint, radius, center)
            drawCircle(Color.White, radius * 0.35f, center - Offset(radius * 0.2f, radius * 0.2f))
        }
    }
}
