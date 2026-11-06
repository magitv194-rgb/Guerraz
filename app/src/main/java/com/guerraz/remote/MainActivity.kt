package com.guerraz.remote

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private val projectionRequest = 4101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.APP_ROLE == "host") showHostScreen() else showClientScreen()
    }

    private fun baseLayout(title: String, description: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(0xFF101820.toInt())
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 26f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = description
                textSize = 15f
                setTextColor(0xFFCFD8DC.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 18, 0, 28)
            })
        }

    private fun actionButton(label: String, action: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun showHostScreen() {
        setContentView(baseLayout(
            "Guerraz · Host",
            "Este teléfono comparte su pantalla únicamente después de que aceptes el permiso del sistema. La sesión se puede detener desde la notificación."
        ).apply {
            addView(actionButton("Compartir pantalla") { requestScreenCapture() })
            addView(actionButton("Detener captura") {
                stopService(Intent(this@MainActivity, HostCaptureService::class.java))
            })
        })
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequest)
    }

    @Deprecated("Kept dependency-light for the initial project foundation.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == projectionRequest && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, HostCaptureService::class.java).apply {
                putExtra(HostCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(HostCaptureService.EXTRA_RESULT_DATA, data)
            }
            startForegroundService(serviceIntent)
            Toast.makeText(this, "Captura autorizada por Android", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClientScreen() {
        setContentView(baseLayout(
            "Guerraz · Client",
            "El cliente se conectará al Host por la red local. El emparejamiento autenticado y el transporte de vídeo se implementarán en la siguiente etapa."
        ).apply {
            addView(actionButton("Preparar conexión local") {
                Toast.makeText(this@MainActivity, "Módulo de conexión pendiente de implementar.", Toast.LENGTH_LONG).show()
            })
        })
    }
}
