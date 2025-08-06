package com.developer_rahul.docunova.Adapters

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.RoomDB.RecentFile
import java.io.File

class FileAdapter(
    private var list: MutableList<RecentFile>,
    private val onListUpdated: () -> Unit // Callback to refresh list in fragment/activity
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbImage: ImageView = view.findViewById(R.id.imageThumbnail)
        val fileName: TextView = view.findViewById(R.id.textTitle)
        val fileDate: TextView = view.findViewById(R.id.textDate)
        val more: ImageView = view.findViewById(R.id.iconMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_box, parent, false)
        return FileViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = list[position]
        val context = holder.itemView.context
        holder.fileName.text = file.name
        holder.fileDate.text = file.date

        Glide.with(context)
            .load(Uri.parse(file.thumbnailUri))
            .into(holder.thumbImage)

        // Open PDF on click
        holder.itemView.setOnClickListener {
            val actualFile = File(file.filePath)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                actualFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
            }

            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No PDF viewer found!", Toast.LENGTH_SHORT).show()
            }
        }

        // Show more options
        holder.more.setOnClickListener {
            showOptionsPopup(context, it, file, position)
        }
    }

    private fun showOptionsPopup(context: Context, anchor: View, file: RecentFile, position: Int) {
        val popup = PopupMenu(context, anchor)
        popup.menuInflater.inflate(R.menu.menu_file_options, popup.menu)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_share -> {
                    shareFile(context, file)
                    true
                }
                R.id.action_rename -> {
                    renameFileDialog(context, file, position)
                    true
                }
                R.id.action_delete -> {
                    deleteFile(context, file, position)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun shareFile(context: Context, file: RecentFile) {
        val actualFile = File(file.filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            actualFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
    }

    private fun deleteFile(context: Context, file: RecentFile, position: Int) {
        AlertDialog.Builder(context)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete this file?")
            .setPositiveButton("Delete") { _, _ ->
                val actualFile = File(file.filePath)
                if (actualFile.exists()) actualFile.delete()
                list.removeAt(position)
                notifyItemRemoved(position)
                onListUpdated()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameFileDialog(context: Context, file: RecentFile, position: Int) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(file.name.replace(".pdf", ""))
        }

        AlertDialog.Builder(context)
            .setTitle("Rename File")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val oldFile = File(file.filePath)
                val newFile = File(oldFile.parent, "$newName.pdf")

                if (newFile.exists()) {
                    Toast.makeText(context, "File with this name already exists", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (oldFile.renameTo(newFile)) {
                    file.name = "$newName.pdf"
                    file.filePath = newFile.absolutePath
                    notifyItemChanged(position)
                    onListUpdated()
                } else {
                    Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun updateFiles(newList: List<RecentFile>) {
        list = newList.toMutableList()
        notifyDataSetChanged()
    }
}
