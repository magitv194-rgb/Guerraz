package com.guerraz.remote

import android.app.Activity
import android.content.Intent
import android.media.MediaCodec
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.FrameLayout
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom

class MainActivity : ComponentActivity() {
    private val projectionRequest = 4101
    private var hostPin = ""
    private var clientSurface: Surface? = null
    private var clientSocket: Socket? = null
    private var decoder: MediaCodec? = null
    private lateinit var statusText: TextView
    private lateinit var addressInput: EditText
    private lateinit var pinInput: EditText
    private lateinit var videoSurface: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.APP_ROLE == "host") showHostScreen() else showClientScreen()
    }

    private fun baseLayout(title: String, description: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF101820.toInt())
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 25f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = description
                textSize = 14f
                setTextColor(0xFFCFD8DC.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 14, 0, 20)
            })
        }

    private fun actionButton(label: String, action: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun showHostScreen() {
        statusText = TextView(this).apply {
            text = "Sin sesión activa"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        }
        setContentView(baseLayout(
            "Guerraz · Host",
            "Conecta ambos teléfonos a la misma red Wi-Fi. El Client debe introducir la IP y el código que aparecen aquí. Android pedirá permiso para compartir la pantalla."
        ).apply {
            addView(statusText)
            addView(actionButton("Compartir pantalla y esperar conexión") { requestScreenCapture() })
            addView(actionButton("Detener sesión") {
                stopService(Intent(this@MainActivity, HostCaptureService::class.java))
                statusText.text = "Sesión detenida"
            })
        })
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequest)
    }

    @Deprecated("MediaProjection consent result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == projectionRequest && resultCode == Activity.RESULT_OK && data != null) {
            hostPin = (100000 + SecureRandom().nextInt(900000)).toString()
            val serviceIntent = Intent(this, HostCaptureService::class.java).apply {
                putExtra(HostCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(HostCaptureService.EXTRA_RESULT_DATA, data)
                putExtra(HostCaptureService.EXTRA_PAIRING_PIN, hostPin)
            }
            startForegroundService(serviceIntent)
            statusText.text = "IP local: ${localIpv4()}\nPuerto: ${StreamProtocol.PORT}\nCódigo de emparejamiento: $hostPin\nEsperando al Client…"
        }
    }

    private fun localIpv4(): String {
        return try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val value = wifi.connectionInfo.ipAddress
            if (value == 0) "Consulta la IP Wi-Fi en Ajustes" else
                listOf(value and 255, value shr 8 and 255, value shr 16 and 255, value shr 24 and 255).joinToString(".")
        } catch (_: Exception) { "Consulta la IP Wi-Fi en Ajustes" }
    }

    private fun showClientScreen() {
        addressInput = EditText(this).apply {
            hint = "IP del teléfono Host (ej. 192.168.1.20)"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFB0BEC5.toInt())
        }
        pinInput = EditText(this).apply {
            hint = "Código de 6 dígitos"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFB0BEC5.toInt())
        }
        statusText = TextView(this).apply {
            text = "Introduce la IP y el código mostrados en el Host."
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }
        videoSurface = SurfaceView(this)
        videoSurface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { clientSurface = holder.surface }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { clientSurface = holder.surface }
            override fun surfaceDestroyed(holder: SurfaceHolder) { clientSurface = null }
        })
        val root = baseLayout("Guerraz · Client", "Vista remota por Wi-Fi local. No uses una red pública.")
        root.addView(addressInput)
        root.addView(pinInput)
        root.addView(actionButton("Conectar y ver pantalla") { connectToHost() })
        root.addView(statusText)
        val videoContainer = FrameLayout(this)
        videoContainer.addView(videoSurface, FrameLayout.LayoutParams(-1, -1))
        val fullscreenButton = Button(this).apply {
            text = "Pantalla completa"
            setOnClickListener {
                val immersive = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
                if (immersive) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    text = "Pantalla completa"
                } else {
                    enterImmersiveMode()
                    text = "Salir de pantalla completa"
                }
            }
        }
        val buttonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        )
        buttonParams.setMargins(12, 12, 20, 20)
        videoContainer.addView(fullscreenButton, buttonParams)
        root.addView(videoContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun connectToHost() {
        val host = addressInput.text.toString().trim()
        val pin = pinInput.text.toString().trim()
        if (host.isBlank() || pin.length != 6) {
            Toast.makeText(this, "Introduce una IP y un código de 6 dígitos.", Toast.LENGTH_LONG).show()
            return
        }
        val surface = clientSurface
        if (surface == null || !surface.isValid) {
            statusText.text = "Esperando la superficie de vídeo…"
            return
        }
        statusText.text = "Conectando…"
        Thread({
            try {
                val socket = Socket(InetAddress.getByName(host), StreamProtocol.PORT)
                socket.tcpNoDelay = true
                clientSocket = socket
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                output.writeUTF(pin)
                output.flush()
                val response = input.readUTF()
                if (response != "OK") throw IllegalStateException("Código incorrecto o sesión no disponible.")
                val width = input.readInt()
                val height = input.readInt()
                val videoDecoder = StreamProtocol.createDecoder(width, height, surface)
                decoder = videoDecoder
                runOnUiThread { statusText.text = "Conectado · ${width}×${height}" }
                val info = MediaCodec.BufferInfo()
                while (!socket.isClosed) {
                    val size = input.readInt()
                    val pts = input.readLong()
                    val flags = input.readInt()
                    if (size < 0 || size > 8 * 1024 * 1024) break
                    val packet = ByteArray(size)
                    input.readFully(packet)
                    var inputIndex = videoDecoder.dequeueInputBuffer(20_000)
                    while (inputIndex < 0 && !socket.isClosed) inputIndex = videoDecoder.dequeueInputBuffer(20_000)
                    if (inputIndex >= 0) {
                        val buffer: ByteBuffer = videoDecoder.getInputBuffer(inputIndex) ?: break
                        buffer.clear()
                        if (size > 0) buffer.put(packet)
                        videoDecoder.queueInputBuffer(inputIndex, 0, size, pts, flags)
                    }
                    var outputIndex = videoDecoder.dequeueOutputBuffer(info, 0)
                    while (outputIndex >= 0) {
                        videoDecoder.releaseOutputBuffer(outputIndex, true)
                        outputIndex = videoDecoder.dequeueOutputBuffer(info, 0)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Conexión finalizada: ${e.message ?: "error de red"}" }
            } finally {
                try { decoder?.stop() } catch (_: Exception) {}
                decoder?.release()
                decoder = null
                try { clientSocket?.close() } catch (_: Exception) {}
                clientSocket = null
            }
        }, "Guerraz-client-stream").start()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onDestroy() {
        try { clientSocket?.close() } catch (_: Exception) {}
        try { decoder?.stop() } catch (_: Exception) {}
        decoder?.release()
        super.onDestroy()
    }
}
