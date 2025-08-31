package com.developer_rahul.docunova.Fragments.Home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.R

data class DriveFileModel(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val modifiedTime: Long,
    val thumbnailLink: String? = null,
    val webContentLink: String? = null,
    val createdTime: Long? = null
)

class DriveFileAdapter(
    private val context: Context,
    private var files: List<DriveFileModel>,
    private val onDownloadClick: (DriveFileModel) -> Unit,
    private val onFileClick: (DriveFileModel) -> Unit
) : RecyclerView.Adapter<DriveFileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.fileTitle2)
        val fileThumbnail: ImageView = view.findViewById(R.id.imageThumbnail2)
        val downloadBtn: ImageButton = view.findViewById(R.id.downloadBtn)
        val fileMetaData: TextView = view.findViewById(R.id.fileMeta2) // Added metadata field
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item_single, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = files.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.name

        // Set metadata (size and date)
        holder.fileMetaData.text = "${formatFileSize(file.size)} • ${formatDate(file.modifiedTime)}"

        // Load thumbnail - fixed the null check
        if (!file.thumbnailLink.isNullOrEmpty()) {
            Glide.with(context)
                .load(file.thumbnailLink)
                .placeholder(R.drawable.app_icon)
                .error(R.drawable.app_icon) // Add error placeholder
                .into(holder.fileThumbnail)
        } else {
            holder.fileThumbnail.setImageResource(R.drawable.app_icon)
        }

        holder.downloadBtn.setOnClickListener {
            onDownloadClick(file)
        }

        holder.itemView.setOnClickListener {
            onFileClick(file)
        }
    }

    fun updateFiles(newFiles: List<DriveFileModel>) {
        files = newFiles
        notifyDataSetChanged()
    }

    private fun formatFileSize(size: Long): String {
        return android.text.format.Formatter.formatFileSize(context, size)
    }

    private fun formatDate(timestamp: Long): String {
        return try {
            val date = java.util.Date(timestamp)
            val format = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            "Unknown date"
        }
    }
}