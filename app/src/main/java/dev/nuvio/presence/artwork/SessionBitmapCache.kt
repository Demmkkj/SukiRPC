package dev.nuvio.presence

import android.graphics.Bitmap
import android.media.MediaMetadata
import dev.nuvio.artwork.SessionArtwork
import java.lang.ref.WeakReference

/** Only the selected session can allocate a compressed bitmap. Keep one encoded frame in RAM. */
internal class SessionBitmapCache {
    private var identity: List<String>? = null
    private var bitmap: WeakReference<Bitmap>? = null
    private var generation = -1
    private var encoded: ByteArray? = null

    fun read(media: List<String>, metadata: MediaMetadata): ByteArray? {
        val image = SessionArtwork.bitmap(metadata)
        if (image == null) {
            clear()
            return null
        }
        if (identity == media && image === bitmap?.get() && generation == image.generationId) return encoded
        identity = media
        bitmap = WeakReference(image)
        generation = image.generationId
        encoded = SessionArtwork.encode(image)
        return encoded
    }

    fun clear() {
        identity = null
        bitmap = null
        generation = -1
        encoded = null
    }
}
