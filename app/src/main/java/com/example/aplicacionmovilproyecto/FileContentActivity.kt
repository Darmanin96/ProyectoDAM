package com.example.aplicacionmovilproyecto

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FileContentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_content)

        val fileName = intent.getStringExtra("fileName") ?: "Archivo"
        val content = intent.getStringExtra("fileContent") ?: "No se pudo cargar el contenido."

        val fileNameTextView: TextView = findViewById(R.id.file_name)
        fileNameTextView.text = fileName

        val fileContentTextView: TextView = findViewById(R.id.file_content)
        title = fileName
        fileContentTextView.text = content
    }
}
