package com.example.aplicacionmovilproyecto

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu // Importar Menu
import android.view.MenuItem // Importar MenuItem
import android.webkit.MimeTypeMap
// import android.widget.Button // Ya no se usa directamente
import android.widget.EditText
// import android.widget.TextView // Ya no se usa textTitle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar // Importar Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes // Necesario para crear carpetas
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.*
import java.io.File as JavaFile
import java.io.IOException
import java.nio.charset.Charset
import java.util.Collections
import java.util.EnumSet // Necesario para crear carpetas

class ExplorerActivity : AppCompatActivity() {


    companion object {
        const val REQUEST_CODE_EDIT_FILE = 1
        private const val TAG = "ExplorerActivity"
    }

    private lateinit var recyclerView: RecyclerView
    // private lateinit var textTitle: TextView // <-- Eliminado
    private lateinit var fileAdapter: FileAdapter
    private val fileList = mutableListOf<FileItem>()

    private var connection: Connection? = null
    private var share: DiskShare? = null
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var smbHost: String
    private lateinit var sharedFolder: String
    private var currentPath: String = ""

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val externalOpenedFiles = Collections.synchronizedMap(mutableMapOf<String, Pair<String, Long>>())
    //endregion

    //region Lifecycle & Activity Results
    // ... (onCreate, onResume, onActivityResult sin cambios relevantes aquí) ...
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Establecer el NUEVO layout
        setContentView(R.layout.activity_explorer)
        Log.d(TAG, "onCreate")

        // --- Configurar Toolbar ---
        val toolbar: Toolbar = findViewById(R.id.toolbar_explorer)
        setSupportActionBar(toolbar)
        // La flecha "Atrás" (Up) se habilitará/deshabilitará en loadFiles

        // Inicializar vistas (ya no incluye textTitle ni btn_disconnect)
        initializeViews()
        retrieveIntentData()
        setupRecyclerView()
        setupBackButtonHandler() // El botón físico atrás sigue funcionando igual

        if (areConnectionParametersValid()) {
            // El título inicial lo pondrá connectToServer o loadFiles
            connectToServer()
        } else {
            showErrorAndFinish("Error: Faltan datos de conexión.")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Comprobando archivos externos...")
        checkExternalFileChanges()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_EDIT_FILE && resultCode == Activity.RESULT_OK) {
            val smbFilePath = data?.getStringExtra("fileName")
            val updatedContent = data?.getStringExtra("updatedContent")
            if (smbFilePath != null && updatedContent != null) {
                saveFileToServer(smbFilePath, updatedContent)
            } else {
                showToast("No se recibieron datos completos.")
            }
        }
    }


    // --- Inflar el menú de la Toolbar ---
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_explorer, menu) // Infla tu nuevo menú
        // Opcional: Deshabilitar botones de crear si no hay conexión
        val connected = isSmbConnected()
        menu.findItem(R.id.action_create_folder)?.isEnabled = connected
        menu.findItem(R.id.action_create_file)?.isEnabled = connected
        return true
    }

    // --- Manejar clicks en el menú y botón "Up" ---
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { // Click en la flecha "Atrás" (Up) de la Toolbar
                handleNavigationBack() // Llama a la misma lógica que el botón físico Atrás
                true
            }
            R.id.action_disconnect -> { // Click en el item "Desconectar"
                disconnectAndReturnToMain()
                true
            }
            // --- NUEVOS MANEJADORES ---
            R.id.action_create_folder -> {
                showCreateFolderDialog()
                true
            }
            R.id.action_create_file -> {
                showCreateFileDialog()
                true
            }
            // --- FIN NUEVOS MANEJADORES ---
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ... (onDestroy, initializeViews, retrieveIntentData, etc. sin cambios) ...
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        activityScope.cancel()
        externalOpenedFiles.clear()
        GlobalScope.launch(Dispatchers.IO) {
            closeSmbConnection()
        }
    }
    //endregion

    //region UI Initialization & Setup
    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_files)
        // textTitle = findViewById(R.id.text_title) // <-- Eliminado
        // val btnDisconnect: Button = findViewById(R.id.btn_disconnect) // <-- Eliminado
        // btnDisconnect.setOnClickListener { disconnectAndReturnToMain() } // <-- Eliminado
    }

    private fun retrieveIntentData() {
        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""
        smbHost = intent.getStringExtra("smbHost") ?: ""
        sharedFolder = intent.getStringExtra("sharedFolder") ?: ""
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        fileAdapter = FileAdapter(
            fileList,
            onRename = { item -> showRenameDialog(item) },
            onDelete = { item -> showDeleteConfirmationDialog(item) },
            onDoubleClick = { item -> openFileOrDirectory(item) }
        )
        recyclerView.adapter = fileAdapter
    }

    private fun setupBackButtonHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleNavigationBack()
        })
    }

    private fun areConnectionParametersValid(): Boolean {
        return username.isNotEmpty() && smbHost.isNotEmpty() && sharedFolder.isNotEmpty()
    }

    private fun showErrorAndFinish(message: String) {
        showToast(message, Toast.LENGTH_LONG)
        finish()
    }
    //endregion

    //region SMB Connection & Disconnection
    // ... (connectToServer, disconnectAndReturnToMain, closeSmbConnection, isSmbConnected sin cambios)...
    private fun connectToServer() {
        updateTitle("Conectando a '$sharedFolder'...") // Actualiza el título de la Toolbar

        activityScope.launch {
            try {
                val connectionSuccess = withContext(Dispatchers.IO) {
                    Log.d(TAG, "Iniciando conexión SMB en hilo IO")
                    val client = SMBClient()
                    connection = client.connect(smbHost)
                    val auth = AuthenticationContext(username, password.toCharArray(), "")
                    val session = connection?.authenticate(auth)
                    share = session?.connectShare(sharedFolder) as? DiskShare
                    Log.d(TAG, "Conexión SMB finalizada en hilo IO. Share es null: ${share == null}")
                    share != null
                }

                if (connectionSuccess) {
                    Log.i(TAG, "Conectado a $smbHost\\$sharedFolder")
                    invalidateOptionsMenu() // Habilitar botones del menú si la conexión es exitosa
                    loadFiles() // loadFiles actualizará el título y la flecha Up
                } else {
                    Log.e(TAG, "No se pudo conectar a '$sharedFolder'. Share es null.")
                    updateTitle("Error de conexión") // Actualiza título Toolbar
                    supportActionBar?.setDisplayHomeAsUpEnabled(false) // Asegura que no hay flecha
                    invalidateOptionsMenu() // Deshabilitar botones si falla
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error durante la conexión SMB", e)
                withContext(Dispatchers.Main) {
                    updateTitle("Error: ${e.message}") // Actualiza título Toolbar
                    supportActionBar?.setDisplayHomeAsUpEnabled(false) // Asegura que no hay flecha
                    showToast("Error de conexión: ${e.message ?: "Desconocido"}")
                    invalidateOptionsMenu() // Deshabilitar botones si falla
                }
            }
        }
    }


    private fun disconnectAndReturnToMain() {
        Log.d(TAG, "Iniciando desconexión...")
        activityScope.launch(Dispatchers.IO) {
            closeSmbConnection()
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private suspend fun closeSmbConnection() {
        try {
            if (connection?.isConnected == true) {
                Log.d(TAG, "Cerrando conexión SMB...")
                share?.close()
                connection?.close(true)
                Log.i(TAG, "Conexión SMB cerrada.")
            }
            share = null
            connection = null
        } catch (exception: Exception) {
            Log.e(TAG, "Error al cerrar conexión SMB", exception)
        }
        // Asegurarse de que los botones del menú se deshabiliten en la UI
        withContext(Dispatchers.Main) {
            invalidateOptionsMenu()
        }
    }

    private fun isSmbConnected(): Boolean {
        val connected = share != null && connection?.isConnected == true
        if (!connected) Log.w(TAG, "Comprobación SMB: No conectado.")
        return connected
    }
    //endregion

    //region File Listing & UI Updates
    // ... (loadFiles, updatePathTitle, updateTitle sin cambios)...
    private suspend fun loadFiles(path: String = "") {
        currentPath = path
        if (!isSmbConnected()) {
            updateTitle("Sin conexión")
            supportActionBar?.setDisplayHomeAsUpEnabled(false) // No hay flecha si no hay conexión
            invalidateOptionsMenu() // Asegurar que botones estén deshabilitados
            return
        }

        updateTitle("Cargando...") // Título temporal
        supportActionBar?.setDisplayHomeAsUpEnabled(currentPath.isNotEmpty()) // Flecha si no estamos en la raíz
        Log.d(TAG, "Iniciando carga de archivos desde: '$path'")

        try {
            val filesFromSmb = withContext(Dispatchers.IO) {
                Log.d(TAG, "Accediendo a share.list en hilo IO para '$path'")
                share?.list(path)?.mapNotNull { fileInfo ->
                    if (fileInfo.fileName == "." || fileInfo.fileName == "..") null
                    else FileItem(fileInfo.fileName, (fileInfo.fileAttributes and 0x10L) != 0L)
                }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?: emptyList()
            }

            Log.d(TAG, "Actualizando UI con ${filesFromSmb.size} items para '$path'.")
            fileList.clear()
            fileList.addAll(filesFromSmb)
            fileAdapter.notifyDataSetChanged()
            updatePathTitle(path) // Actualiza el título final de la Toolbar
            // La flecha (Up button) ya se actualizó antes del withContext

        } catch (exception: Exception) {
            Log.e(TAG, "Error cargando archivos desde '$path'", exception)
            updateTitle("Error al cargar") // Título de error en Toolbar
            supportActionBar?.setDisplayHomeAsUpEnabled(currentPath.isNotEmpty()) // Muestra flecha si podemos volver
            showToast("Error al cargar: ${exception.message ?: "Desconocido"}")
        }
    }


    // --- Modificado para usar supportActionBar ---
    private fun updatePathTitle(path: String) {
        val displayPath = if (path.isEmpty()) sharedFolder else "$sharedFolder\\${path.replace("/", "\\")}"
        // Establece el título en la Toolbar
        supportActionBar?.title = displayPath
    }

    // --- Modificado para usar supportActionBar ---
    private fun updateTitle(newTitle: String) {
        // Establece el título en la Toolbar
        supportActionBar?.title = newTitle
    }
    //endregion

    //region Navigation & File Opening
    // ... (openFileOrDirectory, handleNavigationBack, openFileByExtension, etc. sin cambios) ...
    private fun openFileOrDirectory(item: FileItem) {
        val smbFilePath = if (currentPath.isEmpty()) item.name else "$currentPath/${item.name}"
        if (item.isDirectory) {
            Log.d(TAG, "Navegando al directorio: $smbFilePath")
            activityScope.launch { loadFiles(smbFilePath) }
        } else {
            Log.d(TAG, "Intentando abrir archivo: $smbFilePath")
            openFileByExtension(item, smbFilePath)
        }
    }

    private fun handleNavigationBack() {
        if (currentPath.isNotEmpty()) {
            val parentPath = currentPath.substringBeforeLast('/', "")
            Log.d(TAG, "Navegando atrás a: '$parentPath'")
            activityScope.launch { loadFiles(parentPath) }
        } else {
            showDisconnectDialog() // Preguntar antes de desconectar al ir atrás desde la raíz
        }
    }
    // ... resto de funciones de apertura/guardado/etc sin cambios ...
    private fun openFileByExtension(item: FileItem, smbFilePath: String) {
        val fileExtension = item.name.substringAfterLast('.', "").lowercase()
        val externalExtensions = setOf(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "odt", "ods", "odp",
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
            "mp3", "wav", "ogg", "m4a", "flac",
            "mp4", "mkv", "avi", "mov", "wmv", "webm",
            "zip", "rar", "7z", "tar", "gz",
            "apk"
        )

        if (fileExtension in externalExtensions) {
            openFileWithExternalApp(item, smbFilePath, fileExtension)
        } else {
            openFileWithInternalEditor(smbFilePath)
        }
    }

    private fun openFileWithExternalApp(item: FileItem, smbFilePath: String, fileExtension: String) {
        Log.d(TAG, "Intentando abrir externamente: '$smbFilePath'")
        activityScope.launch(Dispatchers.IO) {
            var localTempFile: JavaFile? = null
            try {
                if (!isSmbConnected()) throw IOException("Sin conexión SMB.")

                val remoteFile = share!!.openFile(smbFilePath, setOf(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
                localTempFile = JavaFile.createTempFile(item.name + "_", ".$fileExtension", cacheDir).apply { deleteOnExit() }
                remoteFile.inputStream.use { input -> localTempFile.outputStream().use { output -> input.copyTo(output) } }
                remoteFile.close()
                val initialTimestamp = localTempFile.lastModified()
                Log.d(TAG, "Archivo descargado a temporal: ${localTempFile.absolutePath}")

                val tempFilePath = localTempFile.absolutePath
                externalOpenedFiles[tempFilePath] = Pair(smbFilePath, initialTimestamp)
                Log.d(TAG, "Registrado archivo externo: $tempFilePath (SMB: $smbFilePath, Time: $initialTimestamp)")

                val fileUri = FileProvider.getUriForFile(this@ExplorerActivity, "${packageName}.fileprovider", localTempFile)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension) ?: "*/*"

                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mimeType)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                withContext(Dispatchers.Main) {
                    try {
                        startActivity(openIntent)
                    } catch (e: ActivityNotFoundException) {
                        Log.w(TAG, "No se encontró aplicación para abrir .$fileExtension")
                        showToast("No hay aplicación instalada para abrir este tipo de archivo.")
                        externalOpenedFiles.remove(tempFilePath)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al lanzar intent externo para $smbFilePath", e)
                        showToast("Error al intentar abrir el archivo: ${e.message}")
                        externalOpenedFiles.remove(tempFilePath)
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Error procesando archivo para app externa '$smbFilePath'", exception)
                localTempFile?.absolutePath?.let { externalOpenedFiles.remove(it) }
                try { localTempFile?.delete() } catch (_: Exception) {}
                withContext(Dispatchers.Main) { showToast("Error abriendo archivo: ${exception.message}", Toast.LENGTH_LONG) }
            }
        }
    }

    private fun openFileWithInternalEditor(smbFilePath: String) {
        Log.d(TAG, "Intentando abrir internamente: '$smbFilePath'")
        activityScope.launch(Dispatchers.IO) {
            var fileContent: String? = null
            var errorMessage: String? = null
            try {
                if (!isSmbConnected()) throw IOException("Sin conexión SMB.")

                val remoteFile = share!!.openFile(smbFilePath, setOf(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
                remoteFile.use { file ->
                    fileContent = file.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                Log.d(TAG, "Contenido leído para editor interno (longitud: ${fileContent?.length})")

            } catch (exception: Exception) {
                Log.e(TAG, "Error leyendo '$smbFilePath' como texto", exception)
                errorMessage = "Error al leer: ${exception.message}"
            }

            withContext(Dispatchers.Main) {
                if (fileContent != null) {
                    val intent = Intent(this@ExplorerActivity, FileContentActivity::class.java).apply {
                        putExtra("fileName", smbFilePath)
                        putExtra("fileContent", fileContent)
                    }
                    startActivityForResult(intent, REQUEST_CODE_EDIT_FILE)
                } else {
                    showToast(errorMessage ?: "No se pudo leer el contenido.", Toast.LENGTH_LONG)
                }
            }
        }
    }
    //endregion

    //region Internal File Saving
    // ... (saveFileToServer sin cambios) ...
    private fun saveFileToServer(smbFilePath: String, content: String) {
        Log.d(TAG, "Guardando archivo en SMB: '$smbFilePath'")
        activityScope.launch(Dispatchers.IO) {
            var remoteFile: com.hierynomus.smbj.share.File? = null
            try {
                if (!isSmbConnected()) throw IOException("Sin conexión SMB para guardar.")

                remoteFile = share!!.openFile(
                    smbFilePath,
                    setOf(AccessMask.GENERIC_WRITE, AccessMask.DELETE), // GENERIC_WRITE debería ser suficiente, DELETE se usa para reemplazar
                    null, // FileAttributes
                    SMB2ShareAccess.ALL, // ShareAccess
                    SMB2CreateDisposition.FILE_SUPERSEDE, // CreateDisposition: Sobreescribir o crear
                    null // CreateOptions
                )

                remoteFile.outputStream.use { output ->
                    output.write(content.toByteArray(Charsets.UTF_8))
                    output.flush() // Asegura que los datos se escriban
                }
                Log.i(TAG, "Archivo guardado exitosamente: $smbFilePath")

                withContext(Dispatchers.Main) {
                    showToast("Archivo '${smbFilePath.substringAfterLast('/')}' guardado.")
                    // No es necesario recargar aquí si solo se guardó desde el editor interno,
                    // pero podría ser útil si hubiera otras formas de modificar.
                    // launch { loadFiles(currentPath) } // Recargar lista (opcional)
                }

            } catch (exception: Exception) {
                Log.e(TAG, "Error al guardar archivo '$smbFilePath'", exception)
                withContext(Dispatchers.Main) { showToast("Error guardando: ${exception.message}", Toast.LENGTH_LONG) }
            } finally {
                try { remoteFile?.close() } catch (ioe: IOException) { Log.e(TAG, "Error cerrando archivo SMB en finally", ioe) }
            }
        }
    }
    //endregion

    //region External File Changes Handling
    // ... (checkExternalFileChanges, showUploadConfirmationDialog, uploadTempFileToSmb sin cambios) ...
    private fun checkExternalFileChanges() {
        val pathsToCheck = externalOpenedFiles.keys.toList()
        if (pathsToCheck.isEmpty()) return
        Log.d(TAG, "Comprobando ${pathsToCheck.size} archivos externos.")

        activityScope.launch(Dispatchers.IO) {
            pathsToCheck.forEach { tempFilePath ->
                val fileInfo = externalOpenedFiles.remove(tempFilePath) ?: return@forEach
                val (smbPath, initialTimestamp) = fileInfo
                val tempFile = JavaFile(tempFilePath)

                if (!tempFile.exists()) {
                    Log.w(TAG, "Archivo temporal $tempFilePath no existe.")
                    return@forEach
                }

                val currentTimestamp = tempFile.lastModified()
                Log.d(TAG, "Check $tempFilePath: SMB=$smbPath, Initial=$initialTimestamp, Current=$currentTimestamp")

                if (currentTimestamp > initialTimestamp) {
                    Log.i(TAG, "Cambio detectado en $tempFilePath para $smbPath!")
                    withContext(Dispatchers.Main) {
                        showUploadConfirmationDialog(tempFilePath, smbPath)
                    }
                } else {
                    Log.d(TAG, "Sin cambios detectados para $tempFilePath, borrando temporal.")
                    try { tempFile.delete() } catch (e: Exception) { Log.e(TAG, "Error borrando temp $tempFilePath", e) }
                }
            }
        }
    }

    private fun showUploadConfirmationDialog(tempFilePath: String, smbPath: String) {
        val tempFile = JavaFile(tempFilePath)
        if (!tempFile.exists()) { Log.w(TAG, "Temp $tempFilePath no existe al mostrar diálogo."); return }
        val displayName = smbPath.substringAfterLast('/')

        AlertDialog.Builder(this)
            .setTitle("¿Subir Cambios?")
            .setMessage("Se detectaron posibles cambios en '${tempFile.name}'. ¿Quieres subir esta versión al servidor reemplazando '$displayName'?")
            .setPositiveButton("Sí, Subir") { _, _ ->
                Log.d(TAG, "Usuario confirma subir $tempFilePath a $smbPath")
                uploadTempFileToSmb(tempFilePath, smbPath)
            }
            .setNegativeButton("No, Descartar") { _, _ ->
                Log.d(TAG, "Usuario descarta cambios de $tempFilePath")
                try { tempFile.delete() } catch (e: Exception) { Log.e(TAG, "Error borrando $tempFilePath al descartar", e) }
            }
            .setNeutralButton("Cancelar") { dialog, _ ->
                // Si cancela, volvemos a añadir el archivo al mapa para comprobarlo más tarde
                Log.d(TAG, "Cancelado subir cambios para $tempFilePath. Re-registrando.")
                externalOpenedFiles[tempFilePath] = Pair(smbPath, tempFile.lastModified())
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun uploadTempFileToSmb(tempFilePath: String, smbPath: String) {
        val tempFile = JavaFile(tempFilePath)
        if (!tempFile.exists() || !tempFile.isFile) { showToast("Error: Archivo local temporal no existe.", Toast.LENGTH_LONG); return }
        Log.d(TAG, "Subiendo $tempFilePath a $smbPath")

        activityScope.launch(Dispatchers.IO) {
            if (!isSmbConnected()) { withContext(Dispatchers.Main){ showToast("Error: Sin conexión SMB.")}; return@launch }
            var success = false; var errorMsg: String? = null
            var remoteSmbFile: com.hierynomus.smbj.share.File? = null
            try {
                remoteSmbFile = share!!.openFile(smbPath, setOf(AccessMask.GENERIC_WRITE, AccessMask.DELETE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_SUPERSEDE, null)
                remoteSmbFile.outputStream.use { smbOut ->
                    tempFile.inputStream().use { localIn ->
                        val bytes = localIn.copyTo(smbOut)
                        smbOut.flush()
                        Log.i(TAG, "$bytes bytes subidos a $smbPath")
                    }
                }
                success = true
            } catch (e: Exception) { Log.e(TAG, "Error subiendo $smbPath", e); errorMsg = e.message }
            finally { try { remoteSmbFile?.close() } catch (ioe: IOException) { Log.e(TAG, "Error cerrando $smbPath en finally", ioe) } }

            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("'${smbPath.substringAfterLast('/')}' actualizado.", Toast.LENGTH_SHORT)
                    try { tempFile.delete() } catch (e: Exception) { Log.e(TAG, "Error borrando $tempFilePath post-subida", e) }
                } else {
                    showToast("Error subiendo: ${errorMsg ?: "desconocido"}", Toast.LENGTH_LONG)
                }
            }
        }
    }
    //endregion

    //region File Actions (Rename, Delete, Create)

    // ... (renameItem, deleteItem sin cambios) ...
    private fun renameItem(item: FileItem, newName: String) {
        val oldSmbPath = if (currentPath.isEmpty()) item.name else "$currentPath/${item.name}"
        Log.d(TAG, "Intentando renombrar '$oldSmbPath' a '$newName'")

        activityScope.launch(Dispatchers.IO) {
            var success = false
            var localErrorMessage: String? = null

            if (!isSmbConnected()) {
                localErrorMessage = "Sin conexión."
                Log.w(TAG, "Intento de renombrar sin conexión.")
            } else {
                try {
                    val newSmbPath = if (currentPath.isEmpty()) newName else "$currentPath/$newName"
                    if (item.isDirectory) {
                        share!!.openDirectory(oldSmbPath, setOf(AccessMask.DELETE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
                            .use { it.rename(newName, true) } // Cambiado a rename con replace=true por si acaso
                    } else {
                        share!!.openFile(oldSmbPath, setOf(AccessMask.DELETE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
                            .use { it.rename(newName, true) } // replaceIfExists=true
                    }
                    success = true
                    Log.i(TAG, "'$oldSmbPath' renombrado a '$newName'.")
                } catch (exception: Exception) {
                    Log.e(TAG, "Error renombrando '$oldSmbPath'", exception)
                    localErrorMessage = "Error al renombrar: ${exception.message}"
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("Renombrado exitosamente.")
                    launch { loadFiles(currentPath) } // Recargar lista
                } else {
                    showToast(localErrorMessage ?: "Error desconocido al renombrar.", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun deleteItem(item: FileItem) {
        val smbPath = if (currentPath.isEmpty()) item.name else "$currentPath/${item.name}"
        Log.d(TAG, "Intentando eliminar '$smbPath' (Directorio: ${item.isDirectory})")

        activityScope.launch(Dispatchers.IO) {
            var success = false
            var localErrorMessage: String? = null

            if (!isSmbConnected()) {
                localErrorMessage = "Sin conexión."
                Log.w(TAG, "Intento de eliminar sin conexión.")
            } else {
                try {
                    if (item.isDirectory) {
                        // Intenta eliminar directorio vacío primero
                        share!!.rmdir(smbPath, false) // recursive = false
                    } else {
                        share!!.rm(smbPath)
                    }
                    success = true
                    Log.i(TAG, "'$smbPath' eliminado exitosamente.")
                } catch (exception: Exception) {
                    Log.e(TAG, "Error eliminando '$smbPath'", exception)
                    localErrorMessage = "Error al eliminar: ${exception.message}"
                    // Mensaje específico si el directorio no está vacío (¡importante!)
                    if (item.isDirectory && exception.message?.contains("STATUS_DIRECTORY_NOT_EMPTY", ignoreCase = true) == true) {
                        localErrorMessage = "Error: El directorio no está vacío."
                    } else if (exception.message?.contains("STATUS_ACCESS_DENIED", ignoreCase = true) == true) {
                        localErrorMessage = "Error: Permiso denegado."
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("Elemento eliminado.")
                    launch { loadFiles(currentPath) } // Recargar lista
                } else {
                    showToast(localErrorMessage ?: "Error desconocido al eliminar.", Toast.LENGTH_LONG)
                }
            }
        }
    }

    // --- NUEVA FUNCIÓN: Crear Carpeta ---
    private fun createFolder(folderName: String) {
        val smbPath = if (currentPath.isEmpty()) folderName else "$currentPath/$folderName"
        Log.d(TAG, "Intentando crear carpeta: '$smbPath'")

        activityScope.launch(Dispatchers.IO) {
            var success = false
            var localErrorMessage: String? = null

            if (!isSmbConnected()) {
                localErrorMessage = "Sin conexión."
                Log.w(TAG, "Intento de crear carpeta sin conexión.")
            } else {
                try {
                    share?.mkdir(smbPath)
                    success = true
                    Log.i(TAG, "Carpeta creada exitosamente: '$smbPath'.")
                } catch (exception: Exception) {
                    Log.e(TAG, "Error creando carpeta '$smbPath'", exception)
                    localErrorMessage = "Error: ${exception.message}"
                    if (exception.message?.contains("STATUS_OBJECT_NAME_COLLISION", ignoreCase = true) == true) {
                        localErrorMessage = "Error: Ya existe una carpeta con ese nombre."
                    } else if (exception.message?.contains("STATUS_ACCESS_DENIED", ignoreCase = true) == true) {
                        localErrorMessage = "Error: Permiso denegado."
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("Carpeta '$folderName' creada.")
                    launch { loadFiles(currentPath) } // Recargar lista
                } else {
                    showToast(localErrorMessage ?: "Error desconocido al crear carpeta.", Toast.LENGTH_LONG)
                }
            }
        }
    }

    // --- NUEVA FUNCIÓN: Crear Archivo ---
    private fun createFile(fileName: String) {
        val smbPath = if (currentPath.isEmpty()) fileName else "$currentPath/$fileName"
        Log.d(TAG, "Intentando crear archivo: '$smbPath'")

        activityScope.launch(Dispatchers.IO) {
            var success = false
            var localErrorMessage: String? = null

            if (!isSmbConnected()) {
                localErrorMessage = "Sin conexión."
                Log.w(TAG, "Intento de crear archivo sin conexión.")
            } else {
                var fileHandle: com.hierynomus.smbj.share.File? = null
                try {
                    // Abrir (y crear) el archivo. FILE_CREATE falla si ya existe.
                    fileHandle = share?.openFile(
                        smbPath,
                        EnumSet.of(AccessMask.GENERIC_WRITE), // Acceso necesario para crear/escribir
                        null, // Atributos por defecto
                        SMB2ShareAccess.ALL, // Permitir otros accesos mientras está abierto (aunque lo cerramos rápido)
                        SMB2CreateDisposition.FILE_CREATE, // **Importante**: Crea si no existe, falla si existe
                        null // Opciones de creación por defecto
                    )
                    // Es crucial cerrar el handle para que el archivo se cree correctamente en el servidor
                    fileHandle?.close()
                    success = true
                    Log.i(TAG, "Archivo creado exitosamente: '$smbPath'.")
                } catch (exception: Exception) {
                    Log.e(TAG, "Error creando archivo '$smbPath'", exception)
                    localErrorMessage = "Error: ${exception.message}"
                    if (exception.message?.contains("STATUS_OBJECT_NAME_COLLISION", ignoreCase = true) == true) {
                        localErrorMessage = "Error: Ya existe un archivo con ese nombre."
                    } else if (exception.message?.contains("STATUS_ACCESS_DENIED", ignoreCase = true) == true) {
                        localErrorMessage = "Error: Permiso denegado."
                    }
                } finally {
                    // Asegurarse de cerrar el handle incluso si hubo error antes del close explícito
                    try { fileHandle?.close() } catch (e: IOException) { Log.w(TAG, "Error al cerrar handle en finally de createFile: ${e.message}") }
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("Archivo '$fileName' creado.")
                    launch { loadFiles(currentPath) } // Recargar lista
                } else {
                    showToast(localErrorMessage ?: "Error desconocido al crear archivo.", Toast.LENGTH_LONG)
                }
            }
        }
    }
    //endregion

    //region Dialogs

    // ... (showRenameDialog, showDeleteConfirmationDialog, showDisconnectDialog sin cambios) ...
    private fun showRenameDialog(item: FileItem) {
        val input = EditText(this).apply {
            setText(item.name)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Renombrar") // Podrías usar R.string.rename
            .setView(input)
            .setPositiveButton("Aceptar") { _, _ -> // Podrías usar R.string.accept
                val newName = input.text.toString().trim()
                if (newName.isBlank()) {
                    showToast("El nombre no puede estar vacío.")
                } else if (newName == item.name) {
                    // No hacer nada
                } else if (fileList.any { it.name.equals(newName, ignoreCase = true) }) { // Comprobar si ya existe (ignorando el item actual)
                    showToast("Ya existe un elemento con ese nombre.")
                } else if (newName.any { it in "\\/:*?\"<>|" }) { // Caracteres inválidos básicos
                    showToast("El nombre contiene caracteres inválidos.")
                }
                else {
                    renameItem(item, newName)
                }
            }
            .setNegativeButton("Cancelar", null) // Podrías usar R.string.cancel
            .show()
    }

    private fun showDeleteConfirmationDialog(item: FileItem) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar") // R.string.delete
            .setMessage("¿Seguro que quieres eliminar '${item.name}'?") // R.string.confirm_delete_message
            .setPositiveButton("Sí, eliminar") { _, _ -> deleteItem(item) } // R.string.yes_delete
            .setNegativeButton("Cancelar", null) // R.string.cancel
            .show()
    }

    private fun showDisconnectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Desconectar") // R.string.disconnect
            .setMessage("¿Desconectar y volver al inicio?") // R.string.confirm_disconnect_message
            .setPositiveButton("Sí") { _, _ -> disconnectAndReturnToMain() } // R.string.yes
            .setNegativeButton("No", null) // R.string.no
            .show()
    }

    // --- NUEVO DIÁLOGO: Crear Carpeta ---
    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            hint = "Nombre de la carpeta" // Podrías usar R.string.folder_name_hint
        }
        AlertDialog.Builder(this)
            .setTitle("Crear Carpeta") // R.string.create_folder
            .setView(input)
            .setPositiveButton("Crear") { _, _ -> // R.string.create
                val folderName = input.text.toString().trim()
                if (folderName.isBlank()) {
                    showToast("El nombre de la carpeta no puede estar vacío.")
                } else if (fileList.any { it.name.equals(folderName, ignoreCase = true) }) {
                    showToast("Ya existe un elemento con ese nombre.")
                } else if (folderName.any { it in "\\/:*?\"<>|" }) {
                    showToast("El nombre contiene caracteres inválidos.")
                } else {
                    createFolder(folderName)
                }
            }
            .setNegativeButton("Cancelar", null) // R.string.cancel
            .show()
    }

    // --- NUEVO DIÁLOGO: Crear Archivo ---
    private fun showCreateFileDialog() {
        val input = EditText(this).apply {
            hint = "Nombre del archivo (ej: mi_archivo.txt)" // R.string.file_name_hint
        }
        AlertDialog.Builder(this)
            .setTitle("Crear Archivo") // R.string.create_file
            .setView(input)
            .setPositiveButton("Crear") { _, _ -> // R.string.create
                val fileName = input.text.toString().trim()
                if (fileName.isBlank()) {
                    showToast("El nombre del archivo no puede estar vacío.")
                } else if (fileList.any { it.name.equals(fileName, ignoreCase = true) }) {
                    showToast("Ya existe un elemento con ese nombre.")
                } else if (fileName.any { it in "\\/:*?\"<>|" }) {
                    showToast("El nombre contiene caracteres inválidos.")
                } else {
                    createFile(fileName)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    //endregion

    //region Utilities
    // ... (showToast sin cambios) ...
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }
    //endregion

}