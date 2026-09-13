package app.splouch.core.transport

/**
 * The platform's WebSocket, behind an interface so the loop in [SplouchSocket] runs
 * unchanged over OkHttp in the app and over a fake in tests.
 *
 * Callbacks may arrive on any thread; [SplouchSocket] marshals them onto its scope.
 */
interface WebSocketTransport {
    fun connect(url: String, listener: Listener): Handle

    interface Listener {
        fun onOpen()
        fun onMessage(text: String)
        /** The peer closed, or the close handshake finished. */
        fun onClosed()
        /** The connection failed to open, or broke. */
        fun onFailure(error: Throwable)
    }

    interface Handle {
        /** False when the frame could not be queued (socket not open). */
        fun send(text: String): Boolean

        /** Graceful close: send the close frame and wait for the peer. */
        fun close()

        /**
         * Tear the connection down immediately, no handshake. A socket the platform has
         * silently frozen (app.md C-05) never answers a close frame, so this is what a
         * dead-socket path must call.
         */
        fun abort()
    }
}
