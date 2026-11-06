package com.guerraz.remote

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

/**
 * LAN video transport. The Host accepts one authenticated Client and sends
 * length-prefixed H.264 access units over a TCP connection.
 */
internal object StreamProtocol {
    const val PORT = 47832
    const val MIME = "video/avc"
    const val KEY_FRAME_RATE = 20

    fun receiveVideo(socket: Socket, surface: Surface, onStatus: (String) -> Unit) {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val pin = output.readUTF()
        // The first UTF value from the peer is the pairing PIN.
        @Suppress("UNUSED_VARIABLE") val requestedPin = pin
    }

    fun createDecoder(width: Int, height: Int, surface: Surface): MediaCodec {
        val format = MediaFormat.createVideoFormat(MIME, width, height)
        val decoder = MediaCodec.createDecoderByType(MIME)
        decoder.configure(format, surface, null, 0)
        decoder.start()
        return decoder
    }

    fun queuePacket(decoder: MediaCodec, input: DataInputStream): Boolean {
        val size = input.readInt()
        if (size < 0 || size > 8 * 1024 * 1024) return false
        val pts = input.readLong()
        val flags = input.readInt()
        val bytes = ByteArray(size)
        input.readFully(bytes)
        if (size > 0) {
            val index = decoder.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val buffer: ByteBuffer = decoder.getInputBuffer(index) ?: return false
                buffer.clear()
                buffer.put(bytes)
                decoder.queueInputBuffer(index, 0, size, pts, flags)
            }
        }
        val info = MediaCodec.BufferInfo()
        var outIndex = decoder.dequeueOutputBuffer(info, 0)
        while (outIndex >= 0) {
            decoder.releaseOutputBuffer(outIndex, true)
            outIndex = decoder.dequeueOutputBuffer(info, 0)
        }
        return true
    }
}
