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
    private lateinit var editUsername: EditText
    private lateinit var editPassword: EditText
    private lateinit var errorMessage: TextView

    private val smbHost = "192.168.1.252"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnLogin = findViewById(R.id.btn_login)
        btnRegister = findViewById(R.id.btn_register)
        editUsername = findViewById(R.id.edit_email) // Asumiendo que mantuviste el ID en el layout
        editPassword = findViewById(R.id.edit_password)
        errorMessage = findViewById(R.id.error_message)

        btnLogin.setOnClickListener {
            val username = editUsername.text.toString() // Usando editUsername
            val password = editPassword.text.toString()

            if (username.isBlank() || password.isBlank()) {
                errorMessage.text = "Por favor ingresa el nombre de usuario y la contraseña."
                return@setOnClickListener
            }

            errorMessage.text = ""

            // Verificar contra servidor SMB
            verificarCredenciales(username, password, smbHost)
        }

        btnRegister.setOnClickListener {
            Toast.makeText(this, "Función de registro no implementada.", Toast.LENGTH_SHORT).show()
        }
    }

    // Función para verificar las credenciales
    private fun verificarCredenciales(username: String, password: String, smbHost: String) {
        Thread {
            try {
                // Modificar el nombre de la carpeta según el usuario
                val sharedFolder = obtenerCarpetaSegunUsuario(username)

                val client = SMBClient()
                val connection = client.connect(smbHost)
                val context = AuthenticationContext(username, password.toCharArray(), "")
                val session = connection.authenticate(context)
                val share = session.connectShare(sharedFolder) // Conectar a la carpeta del usuario

                runOnUiThread {
                    Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, ExplorerActivity::class.java)
                    intent.putExtra("username", username)
                    intent.putExtra("password", password)
                    intent.putExtra("smbHost", smbHost)  // Pasa la IP del servidor al intent
                    intent.putExtra("sharedFolder", sharedFolder) // Pasa la carpeta
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    errorMessage.text = "Error: ${e.message}" // Mostrar mensaje detallado
                }
            }

        }.start()
    }

    private fun obtenerCarpetaSegunUsuario(username: String): String {
        return when (username) {
            "user1" -> "User1" // Actualizado para coincidir con el nombre de usuario
            "usuario2" -> "User2"
            else -> "DefaultFolder"
        }
    }
}