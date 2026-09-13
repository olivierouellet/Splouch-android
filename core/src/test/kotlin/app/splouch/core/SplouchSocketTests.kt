package app.splouch.core

import app.splouch.core.support.FakeTransport
import app.splouch.core.transport.SocketEvent
import app.splouch.core.transport.SplouchSocket
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplouchSocketTests {
    private class Rig(scope: TestScope) {
        val transport = FakeTransport()
        val events = ArrayList<SocketEvent>()
        val socket = SplouchSocket("ws://x/ws/scoreboard", transport, scope.backgroundScope, timeSource = scope.testScheduler.timeSource)
        init {
            scope.backgroundScope.launch { socket.events.toList(events) }
            socket.start()
        }
        fun messages() = events.filterIsInstance<SocketEvent.Message>().map { it.event }
    }

    @Test fun `connect, flush the queue, report connected`() = runTest {
        val r = Rig(this)
        r.socket.send("join_meet")
        assertEquals(1, r.transport.connections.size)
        r.transport.last.serverOpen()
        runCurrent()
        assertEquals(listOf("join_meet"), r.transport.last.sentEvents())
        assertEquals<List<SocketEvent>>(listOf(SocketEvent.Connected), r.events)
        assertTrue(r.socket.isConnected)
    }

    @Test fun `messages are forwarded, pong is not, garbage is dropped`() = runTest {
        val r = Rig(this)
        r.transport.last.serverOpen()
        r.transport.last.serverSend("""{"event":"pong"}""")
        r.transport.last.serverSend("""{"event":"whatever_new","data":{}}""")
        r.transport.last.serverSend("nope")
        r.transport.last.serverSend("""{"event":"meet_live","data":{"live":true}}""")
        runCurrent()
        assertEquals(listOf("whatever_new", "meet_live"), r.messages())
    }

    @Test fun `heartbeat pings every 15s and no inbound frame for 35s means dead`() = runTest {
        val r = Rig(this)
        val c = r.transport.last
        c.serverOpen()
        runCurrent()
        advanceTimeBy(15_001)
        assertEquals(listOf("ping"), c.sentEvents())
        c.serverSend("""{"event":"pong"}""")
        advanceTimeBy(15_000)
        assertEquals(listOf("ping", "ping"), c.sentEvents())
        // silence from t=15: dead at the first check past 35s of silence (t=60)
        advanceTimeBy(15_000)
        assertFalse(c.aborted)
        advanceTimeBy(15_000)
        assertTrue(c.aborted)
        assertEquals(SocketEvent.Disconnected, r.events.last())
        assertFalse(r.socket.isConnected)
    }

    @Test fun `reconnect backs off 500ms to 5s and resets on open`() = runTest {
        val r = Rig(this)
        r.transport.last.fail()
        runCurrent()
        assertEquals(1, r.transport.connections.size)
        advanceTimeBy(500); runCurrent()
        assertEquals(2, r.transport.connections.size)
        r.transport.last.fail(); runCurrent()
        advanceTimeBy(999); runCurrent()
        assertEquals(2, r.transport.connections.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(3, r.transport.connections.size)
        r.transport.last.fail(); advanceTimeBy(2_000); runCurrent()
        assertEquals(4, r.transport.connections.size)
        r.transport.last.fail(); advanceTimeBy(4_000); runCurrent()
        assertEquals(5, r.transport.connections.size)
        r.transport.last.fail(); advanceTimeBy(5_000); runCurrent()
        assertEquals(6, r.transport.connections.size)
        r.transport.last.fail(); advanceTimeBy(5_000); runCurrent()
        assertEquals(7, r.transport.connections.size)
        // never reported as disconnected: it was never open
        assertTrue(r.events.isEmpty())
        r.transport.last.serverOpen(); runCurrent()
        r.transport.last.serverClose(); runCurrent()
        assertEquals<List<SocketEvent>>(listOf(SocketEvent.Connected, SocketEvent.Disconnected), r.events)
        advanceTimeBy(500); runCurrent()
        assertEquals(8, r.transport.connections.size)
    }

    @Test fun `wake probes an open socket and drops it when no frame answers within 4s`() = runTest {
        val r = Rig(this)
        val c = r.transport.last
        c.serverOpen(); runCurrent()
        r.socket.wake()
        assertEquals(listOf("ping"), c.sentEvents())
        advanceTimeBy(3_999); runCurrent()
        assertFalse(c.aborted)
        advanceTimeBy(1); runCurrent()
        assertTrue(c.aborted)
        assertEquals(SocketEvent.Disconnected, r.events.last())
        advanceTimeBy(500); runCurrent()
        assertEquals(2, r.transport.connections.size)
    }

    @Test fun `wake keeps a socket that answers, and reconnects a dead one at once`() = runTest {
        val r = Rig(this)
        val c = r.transport.last
        c.serverOpen(); runCurrent()
        r.socket.wake()
        advanceTimeBy(1_000)
        c.serverSend("""{"event":"pong"}"""); runCurrent()
        advanceTimeBy(5_000)
        assertFalse(c.aborted)
        assertTrue(r.socket.isConnected)
        // now the socket dies and backoff is at 5s after a few failures
        c.serverClose(); runCurrent()
        repeat(4) { advanceTimeBy(6_000); runCurrent(); r.transport.last.fail(); runCurrent() }
        val before = r.transport.connections.size
        r.socket.wake()
        assertEquals(before + 1, r.transport.connections.size)
    }

    @Test fun `close is permanent`() = runTest {
        val r = Rig(this)
        r.transport.last.serverOpen(); runCurrent()
        r.socket.close()
        assertTrue(r.transport.last.closed)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, r.transport.connections.size)
    }

    @Test fun `callbacks from a superseded connection are ignored`() = runTest {
        val r = Rig(this)
        val first = r.transport.last
        first.fail(); runCurrent()
        advanceTimeBy(500); runCurrent()
        val second = r.transport.last
        first.serverOpen(); runCurrent()     // stale
        assertFalse(r.socket.isConnected)
        second.serverOpen(); runCurrent()
        assertTrue(r.socket.isConnected)
    }
}
