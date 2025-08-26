package com.developer_rahul.docunova.Fragments.Home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.R

data class DriveFileModel(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: String,
    val thumbnailLink: String
)

class DriveFileAdapter(
    private val context: Context,
    private var files: List<DriveFileModel>,
    private val onDownloadClick: (DriveFileModel) -> Unit
) : RecyclerView.Adapter<DriveFileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.fileTitle2)
        val fileThumbnail: ImageView = view.findViewById(R.id.imageThumbnail2)
        val downloadBtn: ImageButton = view.findViewById(R.id.downloadBtn)
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

        if (file.thumbnailLink.isNotEmpty()) {
            Glide.with(context)
                .load(file.thumbnailLink)
                .placeholder(R.drawable.app_icon)
                .into(holder.fileThumbnail)
        } else {
            holder.fileThumbnail.setImageResource(R.drawable.app_icon)
        }

        holder.downloadBtn.setOnClickListener {
            onDownloadClick(file)
        }
    }

    fun updateFiles(newFiles: List<DriveFileModel>) {
        files = newFiles
        notifyDataSetChanged()
    }


}
