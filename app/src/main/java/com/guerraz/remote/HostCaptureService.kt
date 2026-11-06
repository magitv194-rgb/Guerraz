package com.guerraz.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Surface
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

class HostCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var server: ServerSocket? = null
    private var activeSocket: Socket? = null
    private val running = AtomicBoolean(false)
    private var pairingPin: String = ""

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        pairingPin = intent?.getStringExtra(EXTRA_PAIRING_PIN).orEmpty()
        if (resultData == null || pairingPin.length != 6) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)

        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(resultCode, resultData)
            projection?.registerCallback(projectionCallback, null)
            running.set(true)
            Thread({ startVideoServer() }, "Guerraz-video-server").start()
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startVideoServer() {
        try {
            val metrics = resources.displayMetrics
            val width = (metrics.widthPixels / 2 * 2).coerceAtLeast(2)
            val height = (metrics.heightPixels / 2 * 2).coerceAtLeast(2)
            val format = MediaFormat.createVideoFormat(StreamProtocol.MIME, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, StreamProtocol.KEY_FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            encoder = MediaCodec.createEncoderByType(StreamProtocol.MIME)
            encoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder!!.createInputSurface()
            encoder!!.start()
            virtualDisplay = projection?.createVirtualDisplay(
                "GuerrazHost", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, inputSurface, null, null
            )
            server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(StreamProtocol.PORT))
                soTimeout = 1000
            }
            while (running.get()) {
                try {
                    val socket = server!!.accept()
                    socket.tcpNoDelay = true
                    socket.soTimeout = 10_000
                    activeSocket = socket
                    handleClient(socket, width, height)
                } catch (_: java.net.SocketTimeoutException) {
                    // Check stop flag periodically.
                }
            }
        } catch (_: Exception) {
            // The persistent notification and Activity expose session state to the user.
        } finally {
            stopSelf()
        }
    }

    private fun handleClient(socket: Socket, width: Int, height: Int) {
        socket.use { peer ->
            val input = DataInputStream(peer.getInputStream())
            val output = DataOutputStream(peer.getOutputStream())
            val suppliedPin = input.readUTF()
            if (suppliedPin != pairingPin) {
                output.writeUTF("DENIED")
                output.flush()
                return
            }
            output.writeUTF("OK")
            output.writeInt(width)
            output.writeInt(height)
            output.flush()
            peer.soTimeout = 0

            // Read authenticated touch commands independently from the video writer.
            Thread({
                try {
                    while (running.get() && !peer.isClosed) {
                        RemoteInputAccessibilityService.handleRemoteCommand(input.readUTF())
                    }
                } catch (_: Exception) {
                    // Client disconnected or ended the control channel.
                }
            }, "Guerraz-input-channel").apply { isDaemon = true }.start()

            val codec = encoder ?: return
            val info = MediaCodec.BufferInfo()
            while (running.get() && !peer.isClosed) {
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)
                    val size = info.size
                    output.writeInt(size)
                    output.writeLong(info.presentationTimeUs)
                    output.writeInt(info.flags)
                    if (size > 0 && buffer != null) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + size)
                        val bytes = ByteArray(size)
                        buffer.get(bytes)
                        output.write(bytes)
                    }
                    output.flush()
                    codec.releaseOutputBuffer(index, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Send SPS/PPS from the negotiated encoder format before video frames.
                    val outputFormat = codec.outputFormat
                    listOf("csd-0", "csd-1").forEach { key ->
                        if (outputFormat.containsKey(key)) {
                            val config = outputFormat.getByteBuffer(key)?.duplicate()
                            if (config != null && config.remaining() > 0) {
                                val bytes = ByteArray(config.remaining())
                                config.get(bytes)
                                output.writeInt(bytes.size)
                                output.writeLong(0L)
                                output.writeInt(MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                                output.write(bytes)
                            }
                        }
                    }
                    output.flush()
                }
            }
        }
        activeSocket = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Guerraz · Captura", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("Guerraz · Host activo")
            .setContentText("Esperando conexión local en el puerto ${StreamProtocol.PORT}.")
            .setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build()
    }

    override fun onDestroy() {
        running.set(false)
        try { activeSocket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        virtualDisplay?.release()
        try { encoder?.stop() } catch (_: Exception) {}
        encoder?.release()
        inputSurface?.release()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        virtualDisplay = null
        encoder = null
        inputSurface = null
        projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_PAIRING_PIN = "pairing_pin"
        private const val CHANNEL_ID = "guerraz_capture"
        private const val NOTIFICATION_ID = 8001
    }
}

private object MediaCodecInfo {
    const val COLOR_FormatSurface = 0x7F000789
}
