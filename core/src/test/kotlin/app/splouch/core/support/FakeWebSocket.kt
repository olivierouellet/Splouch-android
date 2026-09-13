package app.splouch.core.support

import app.splouch.core.transport.WebSocketTransport

/** A scripted transport: the test opens, feeds, and kills each connection by hand. */
class FakeTransport : WebSocketTransport {
    val connections = ArrayList<FakeConnection>()
    val last: FakeConnection get() = connections.last()

    override fun connect(url: String, listener: WebSocketTransport.Listener): WebSocketTransport.Handle =
        FakeConnection(url, listener).also { connections += it }
}

class FakeConnection(val url: String, private val listener: WebSocketTransport.Listener) : WebSocketTransport.Handle {
    val sent = ArrayList<String>()
    var open = false
    var closed = false
    var aborted = false

    override fun send(text: String): Boolean {
        if (!open) return false
        sent += text
        return true
    }

    override fun close() { closed = true; open = false }
    override fun abort() { aborted = true; open = false }

    fun serverOpen() { open = true; listener.onOpen() }
    fun serverSend(text: String) = listener.onMessage(text)
    fun serverClose() { open = false; listener.onClosed() }
    fun fail() { open = false; listener.onFailure(RuntimeException("boom")) }

    fun sentEvents(): List<String> = sent.mapNotNull { app.splouch.core.wire.Frame.decode(it)?.event }
}
