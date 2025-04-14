package com.example.aplicacionmovilproyecto

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.share.DiskShare

import kotlinx.coroutines.*

class ExplorerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var textTitle: TextView
    private lateinit var fileAdapter: FileAdapter
    private val fileList = mutableListOf<FileItem>()

    private var connection: Connection? = null
    private var share: DiskShare? = null
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var smbHost: String
    private lateinit var sharedFolder: String

    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explorer)

        recyclerView = findViewById(R.id.recycler_files)
        textTitle = findViewById(R.id.text_title)
        val btnDisconnect = findViewById<Button>(R.id.btn_disconnect)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fileAdapter = FileAdapter(
            fileList,
            onRename = { fileItem -> showRenameDialog(fileItem) },
            onDelete = { fileItem -> showDeleteConfirmationDialog(fileItem) },
            onDoubleClick = { fileItem ->
                openFile(fileItem)
            }
        )

        recyclerView.adapter = fileAdapter

        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""
        smbHost = intent.getStringExtra("smbHost") ?: ""
        sharedFolder = intent.getStringExtra("sharedFolder") ?: ""

        textTitle.text = "Conectando a '$sharedFolder'..."

        connectToServer()

        btnDisconnect.setOnClickListener{
            disconnectAndReturnToMain()
        }
    }

    private fun connectToServer() {
        activityScope.launch(Dispatchers.IO) {
            try {
                val client = SMBClient()
                connection = client.connect(smbHost)
                val auth = AuthenticationContext(username, password.toCharArray(), "")
                val session = connection?.authenticate(auth)
                share = session?.connectShare(sharedFolder) as? DiskShare

                if (share != null) {
                    val currentFiles = share!!.list("").map {
                        FileItem(it.fileName, (it.fileAttributes and 0x10L) != 0L)
                    }
                    withContext(Dispatchers.Main) {
                        fileList.clear()
                        fileList.addAll(currentFiles)
                        fileAdapter.notifyDataSetChanged()
                        textTitle.text = "Contenido de '$sharedFolder':"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        textTitle.text = "Error: No se pudo conectar a la carpeta compartida."
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    textTitle.text = "Error de conexión: ${e.message}"
                    Toast.makeText(this@ExplorerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun disconnectAndReturnToMain() {
        activityScope.launch(Dispatchers.IO) {
            try {
                connection?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Regresar al MainActivity
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@ExplorerActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()  // Finalizar la actividad actual
                }
            }
        }
    }

    private fun openFile(fileItem: FileItem) {
        activityScope.launch(Dispatchers.IO) {
            try {
                // Abre el archivo SMB
                val remoteFile = share?.openFile(
                    fileItem.name,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )

                // Lee el contenido del archivo
                val content = remoteFile?.inputStream?.bufferedReader()?.use { it.readText() }
                remoteFile?.close()

                withContext(Dispatchers.Main) {
                    if (content.isNullOrEmpty()) {
                        textTitle.text = "No se pudo cargar el contenido"
                    } else {
                        // Pasamos el nombre del archivo y el contenido a la actividad FileContentActivity
                        val intent = Intent(this@ExplorerActivity, FileContentActivity::class.java).apply {
                            putExtra("fileName", fileItem.name)
                            putExtra("fileContent", content)
                        }
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExplorerActivity, "Error al leer el archivo: ${e.message}", Toast.LENGTH_LONG).show()
                    textTitle.text = "No se pudo cargar el contenido"
                }
            }
        }
    }

    private fun showRenameDialog(fileItem: FileItem) {
        val input = EditText(this).apply {
            setText(fileItem.name)
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("Cambiar nombre")
            .setView(input)
            .setPositiveButton("Aceptar") { _, _ ->
                val newName = input.text.toString().trim()

                if (newName.isBlank()) {
                    Toast.makeText(this, "El nombre no puede estar vacío.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val index = fileList.indexOf(fileItem)

                activityScope.launch(Dispatchers.IO) {
                    try {
                        withContext(Dispatchers.Main) {
                            fileList[index] = fileItem.copy(name = newName)
                            fileAdapter.notifyItemChanged(index)
                            Toast.makeText(this@ExplorerActivity, "Renombrado exitosamente.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ExplorerActivity, "Error al renombrar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(fileItem: FileItem) {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar elemento?")
            .setMessage("¿Estás seguro de que quieres eliminar '${fileItem.name}'?")
            .setPositiveButton("Sí") { _, _ ->
                activityScope.launch(Dispatchers.IO) {
                    try {
                        if (fileItem.isDirectory) {
                            share?.rmdir(fileItem.name, false)
                        } else {
                            share?.rm(fileItem.name)
                        }
                        withContext(Dispatchers.Main) {
                            fileList.remove(fileItem)
                            fileAdapter.notifyDataSetChanged()
                            Toast.makeText(this@ExplorerActivity, "Elemento eliminado.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ExplorerActivity, "Error al eliminar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        activityScope.launch(Dispatchers.IO) {
            try {
                connection?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
