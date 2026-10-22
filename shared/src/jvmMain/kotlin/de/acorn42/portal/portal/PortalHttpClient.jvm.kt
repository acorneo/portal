package de.acorn42.portal.portal

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun portalHttpClient() = HttpClient(CIO) {
    install(WebSockets) { maxFrameSize = MAX_PACKET_BYTES.toLong(); pingIntervalMillis = 2_000 }
}
