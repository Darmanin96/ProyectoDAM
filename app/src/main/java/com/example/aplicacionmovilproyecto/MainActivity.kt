package com.example.aplicacionmovilproyecto

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext

class MainActivity : AppCompatActivity() {

    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var errorMessage: TextView

    private val smbHost = "192.168.1.10"  // ← IP de tu servidor SMB (TrueNAS)
    private val sharedFolder = "Compartida"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // 👈 Importante: ¡esto va primero!

        btnLogin = findViewById(R.id.btn_login)
        btnRegister = findViewById(R.id.btn_register)
        editEmail = findViewById(R.id.edit_email)
        editPassword = findViewById(R.id.edit_password)
        errorMessage = findViewById(R.id.error_message)

        btnLogin.setOnClickListener {
            val email = editEmail.text.toString()
            val password = editPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                errorMessage.text = "Por favor ingresa todos los campos."
                return@setOnClickListener
            }

            errorMessage.text = ""

            // Verificar contra servidor SMB
            verificarCredenciales(email, password)
        }

        btnRegister.setOnClickListener {
            Toast.makeText(this, "Función de registro no implementada.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verificarCredenciales(username: String, password: String) {
        Thread {
            try {
                val client = SMBClient()
                val connection = client.connect(smbHost)
                val context = AuthenticationContext(username, password.toCharArray(), "")
                val session = connection.authenticate(context)
                val share = session.connectShare(sharedFolder)

                runOnUiThread {
                    Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, ExplorerActivity::class.java)
                    intent.putExtra("username", username)
                    intent.putExtra("password", password)
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    errorMessage.text = "Credenciales inválidas o error de conexión con el servidor."
                }
            }
        }.start()
    }
}
