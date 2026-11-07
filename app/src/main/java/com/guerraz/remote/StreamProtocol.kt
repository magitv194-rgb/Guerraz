package com.guerraz.remote

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface

/** Shared constants and decoder setup for the local H.264 stream. */
internal object StreamProtocol {
    const val PORT = 47832
    const val MIME = "video/avc"
    const val KEY_FRAME_RATE = 20

    fun createDecoder(width: Int, height: Int, surface: Surface): MediaCodec {
        val format = MediaFormat.createVideoFormat(MIME, width, height)
        val decoder = MediaCodec.createDecoderByType(MIME)
        decoder.configure(format, surface, null, 0)
        decoder.start()
        return decoder
    }
}
