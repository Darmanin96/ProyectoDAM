package com.example.aplicacionmovilproyecto

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare


class ExplorerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        setContentView(textView)

        val username = intent.getStringExtra("username") ?: ""
        val password = intent.getStringExtra("password") ?: ""
        val smbHost = intent.getStringExtra("smbHost") ?: "192.168.1.252"  // Obtener la IP del Intent, o usar la predeterminada

        val sharedFolder = username  // Usamos el nombre de usuario como nombre de la carpeta

        Thread {
            try {
                val client = SMBClient()
                val connection = client.connect(smbHost)
                val auth = AuthenticationContext(username, password.toCharArray(), "")
                val session = connection.authenticate(auth)
                val share = session.connectShare(sharedFolder) as DiskShare

                val archivos = share.list("")
                    .filter { (it.fileAttributes and 0x10L) == 0L }  // Solo archivos
                    .joinToString("\n") { it.fileName }

                runOnUiThread {
                    textView.text = "Archivos en '$sharedFolder':\n\n$archivos"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    textView.text = "Error: ${e.message}"
                }
            }
        }.start()
    }
}

