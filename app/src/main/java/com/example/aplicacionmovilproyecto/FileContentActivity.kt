package com.example.aplicacionmovilproyecto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar // Importante: usar androidx.appcompat.widget.Toolbar
import com.google.android.material.textfield.TextInputEditText

class FileContentActivity : AppCompatActivity() {

    private lateinit var fileContentEditText: TextInputEditText
    private var originalFileName: String = "Archivo" // Guardar el nombre original

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_content)

        // --- Configurar Toolbar ---
        val toolbar: Toolbar = findViewById(R.id.toolbar_file_content)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Muestra la flecha de "Atrás" (Up)

        // --- Obtener referencias y datos ---
        fileContentEditText = findViewById(R.id.file_content_edittext) // El EditText dentro del TextInputLayout

        originalFileName = intent.getStringExtra("fileName") ?: "Archivo"
        val content = intent.getStringExtra("fileContent") ?: "No se pudo cargar el contenido."

        // --- Establecer título y contenido inicial ---
        // Usamos supportActionBar?.title para el título de la Toolbar
        supportActionBar?.title = originalFileName.substringAfterLast('/') // Muestra solo el nombre, no la ruta completa
        // Si quieres la ruta completa: supportActionBar?.title = originalFileName

        fileContentEditText.setText(content)
        fileContentEditText.requestFocus() // Opcional: poner el foco en el editor
    }

    // --- Inflar el menú de opciones (donde está "Guardar") ---
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor_actions, menu)
        return true
    }

    // --- Manejar clicks en los items del menú y el botón "Up" ---
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Acción para el botón "Up" (flecha atrás en la toolbar)
                // Puedes preguntar si quiere guardar cambios no guardados aquí si lo deseas
                // onBackPressedDispatcher.onBackPressed() // Comportamiento estándar
                finish() // Simplemente cierra la actividad por ahora
                true
            }
            R.id.action_save -> {
                // Acción para el botón "Guardar"
                saveContentAndFinish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- Función para guardar el contenido y cerrar ---
    private fun saveContentAndFinish() {
        val editedContent = fileContentEditText.text.toString()

        // Creamos un nuevo Intent para el resultado
        val resultIntent = Intent()
        // Pasamos el nombre original del archivo y el contenido actualizado
        resultIntent.putExtra("fileName", originalFileName) // Usa el nombre original que recibiste
        resultIntent.putExtra("updatedContent", editedContent)

        setResult(Activity.RESULT_OK, resultIntent) // Establece el resultado como OK
        finish() // Cierra esta actividad
    }

    // --- (Opcional) Manejar el botón físico "Atrás" ---
    // Si quieres un comportamiento especial al pulsar atrás (ej. preguntar si guardar)
    // puedes descomentar y adaptar esto:
    /*
    override fun onBackPressed() {
        // Comprobar si hay cambios sin guardar
        val originalContent = intent.getStringExtra("fileContent") ?: ""
        val currentContent = fileContentEditText.text.toString()
        if (originalContent != currentContent) {
            // Mostrar diálogo de confirmación
            AlertDialog.Builder(this)
                .setTitle("Descartar Cambios")
                .setMessage("Hay cambios sin guardar. ¿Seguro que quieres salir?")
                .setPositiveButton("Salir sin guardar") { _, _ -> super.onBackPressed() } // Llama al comportamiento por defecto
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            super.onBackPressed() // Salir normalmente si no hay cambios
        }
    }
    */
}