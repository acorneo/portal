package de.acorn42.portal.nearby

import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import kotlin.test.*
import org.junit.Assume.assumeTrue

class NearbyBridgeTest {
    private val status = """{"phase":"awaitingApproval","message":"Approve","localName":"Mac","localID":"mac","peerName":"iPhone","peerID":"phone","fingerprint":"AA BB CC","requestID":"request-1","acknowledgements":2,"receivedPings":3,"pendingPing":false,"peers":[]}"""

    @Test fun statusAndDecisionPreserveExactRequestIdentity() {
        val decoded = NearbyBridgeCodec.decode(status)
        assertEquals("request-1", decoded.requestID)
        assertEquals(2, decoded.acknowledgements)
        val decision = NearbyBridgeCodec.command("accept", "quote\"\nrequest")
        assertEquals(1, decision.count { it == '\n' }, "Command values cannot inject another pipe command")
        assertTrue(decision.contains("\\n"))
        assertFails { NearbyBridgeCodec.command("shell") }
    }
    @Test fun malformedOrUnboundedNativeMessagesFailClosed() {
        assertFails { NearbyBridgeCodec.decode(status.replace("awaitingApproval", "unexpected")) }
        assertFails { NearbyBridgeCodec.decode(status.replace("\"acknowledgements\":2", "\"acknowledgements\":-1")) }
        assertFails { NearbyBridgeCodec.decode("{") }
        assertFails { NearbyBridgeCodec.line(StringReader("x".repeat(32769))) }
        assertFails { NearbyBridgeCodec.line(StringReader("incomplete")) }
        assertNull(NearbyBridgeCodec.line(StringReader("")))
        assertEquals(status, NearbyBridgeCodec.line(StringReader(status + "\n")))
    }
    @Test fun bundledNativeHelperStartsAndExitsWhenParentPipeCloses() {
        assumeTrue(System.getProperty("os.name").startsWith("Mac"))
        val helper = File(System.getProperty("portal.nearby.helper"))
        assertTrue(helper.canExecute(), "Packaged helper must retain executable permissions")
        val child = ProcessBuilder(helper.absolutePath).start()
        try {
            // Initial status requires no network discovery/permission prompt.
            val line = CompletableFuture.supplyAsync { child.inputStream.bufferedReader().readLine() }.get(3, TimeUnit.SECONDS)
            assertNotNull(line)
            val first = NearbyBridgeCodec.decode(line)
            assertEquals("stopped", first.phase)
            assertTrue(first.localID.isNotEmpty())
            child.outputStream.close()
            assertTrue(child.waitFor(3, TimeUnit.SECONDS), "Helper must not survive parent stdin EOF")
            assertEquals(0, child.exitValue())
        } finally { child.destroyForcibly() }
    }
}
