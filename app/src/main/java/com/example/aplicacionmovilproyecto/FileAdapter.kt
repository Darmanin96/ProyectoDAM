package com.example.aplicacionmovilproyecto

import android.view.*
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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
        val icon: TextView = view.findViewById(R.id.text_icon)
        val name: TextView = view.findViewById(R.id.text_name)

        init {
            view.setOnClickListener {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    val item = items[adapterPosition]
                    onDoubleClick(item)
                }
                lastClickTime = currentTime
            }

            view.setOnLongClickListener {
                showPopup(it, adapterPosition)
                true
            }
        }

        private fun showPopup(v: View, position: Int) {
            val item = items[position]
            val popup = PopupMenu(v.context, v)
            popup.menuInflater.inflate(R.menu.menu_item, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.rename -> {
                        onRename(item)
                        true
                    }
                    R.id.delete -> {
                        onDelete(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.text = if (item.isDirectory) "📁" else "📄"
        holder.name.text = item.name
    }

    override fun getItemCount(): Int = items.size
}
