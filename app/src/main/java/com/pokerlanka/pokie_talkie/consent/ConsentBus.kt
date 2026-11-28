package com.pokerlanka.pokie_talkie.consent

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Pending remote-access consent prompts that MainActivity can observe and resolve.
 */
object ConsentBus {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val pendingFolders = ConcurrentHashMap<String, CompletableDeferred<Uri?>>()

    fun request(requestId: String): CompletableDeferred<Boolean> =
        CompletableDeferred<Boolean>().also { pending[requestId] = it }

    fun respond(requestId: String, granted: Boolean) {
        pending.remove(requestId)?.complete(granted)
    }

    fun requestFolder(requestId: String): CompletableDeferred<Uri?> =
        CompletableDeferred<Uri?>().also { pendingFolders[requestId] = it }

    fun completeFolder(requestId: String, uri: Uri?) {
        pendingFolders.remove(requestId)?.complete(uri)
    }

    val events = MutableSharedFlow<ConsentRequest>(replay = 1, extraBufferCapacity = 8)

    fun clearReplay() {
        events.resetReplayCache()
    }
}

data class ConsentRequest(
    val requestId: String,
    val kind: Kind,
    val peerId: String,
    val peerName: String
) {
    enum class Kind {
        ALL,
        CAMERA,
        FILES,
        LOCATION,
        SCREEN
    }
}
