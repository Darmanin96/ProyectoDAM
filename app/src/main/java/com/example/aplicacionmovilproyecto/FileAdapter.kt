package com.example.aplicacionmovilproyecto

import android.view.*
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.util.Log // Añadido para Log.e

// Tu clase FileItem
// data class FileItem(val name: String, val isDirectory: Boolean)

class FileAdapter(
    private val items: List<FileItem>,
    private val onRename: (FileItem) -> Unit,
    private val onDelete: (FileItem) -> Unit,
    private val onDoubleClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    companion object {
        private var lastClickTime: Long = 0
        private const val DOUBLE_CLICK_TIME_DELTA: Long = 300
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.text_icon)
        val name: TextView = view.findViewById(R.id.text_name)

        init {
            view.setOnClickListener {
                // *** CORRECCIÓN AQUÍ ***
                val position = adapterPosition // Usar adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                        val item = items[position]
                        onDoubleClick(item)
                    }
                    lastClickTime = currentTime
                }
            }

            view.setOnLongClickListener {
                // *** CORRECCIÓN AQUÍ ***
                val position = adapterPosition // Usar adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    showPopup(it, position) // Pasamos la posición obtenida aquí
                }
                true
            }
        }

        // El parámetro 'position' que recibe showPopup ya no es necesario si usamos adapterPosition dentro
        // pero lo mantenemos por ahora ya que lo usamos para obtener 'item' al principio.
        // Podríamos refactorizarlo para obtener 'item' usando adapterPosition directamente aquí también.
        private fun showPopup(v: View, position: Int) {
            // Obtenemos el item usando la posición pasada como argumento (que vino de adapterPosition)
            if (position == RecyclerView.NO_POSITION || position >= items.size) {
                Log.w("FileAdapter", "Intento de mostrar popup para posición inválida: $position")
                return // Salir si la posición no es válida
            }
            val item = items[position]
            val popup = PopupMenu(v.context, v, Gravity.END)
            try {
                popup.menuInflater.inflate(R.menu.menu_item, popup.menu)
                popup.setOnMenuItemClickListener { menuItem ->
                    // *** CORRECCIÓN AQUÍ ***
                    // Obtenemos la posición MÁS ACTUALIZADA posible en el momento del click del menú
                    val currentPositionOnClick = adapterPosition // Usar adapterPosition
                    if (currentPositionOnClick != RecyclerView.NO_POSITION) {
                        // Obtenemos el item de nuevo con la posición más reciente
                        val currentItemOnClick = items[currentPositionOnClick]
                        when (menuItem.itemId) {
                            R.id.rename -> {
                                onRename(currentItemOnClick)
                                true
                            }
                            R.id.delete -> {
                                onDelete(currentItemOnClick)
                                true
                            }
                            else -> false
                        }
                    } else {
                        Log.w("FileAdapter", "Popup menu item click con posición inválida.")
                        false // Posición inválida en el momento del click
                    }
                }
                popup.show()
            } catch (e: Exception) {
                Log.e("FileAdapter", "Error inflando o mostrando popup menu", e)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name

        val iconResId = when {
            item.isDirectory -> R.drawable.carpeta48
            else -> {
                val extension = item.name.substringAfterLast('.', "").lowercase()
                when (extension) {
                    "pdf" -> R.drawable.pdf50
                    "doc", "docx" -> R.drawable.word48
                    "xml" -> R.drawable.xml
                    else -> R.drawable.archivo50
                }
            }
        }
        holder.icon.setImageResource(iconResId)
    }

    override fun getItemCount(): Int = items.size
}