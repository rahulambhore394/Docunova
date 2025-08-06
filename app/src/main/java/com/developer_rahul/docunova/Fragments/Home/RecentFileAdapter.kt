package com.developer_rahul.docunova.Adapters

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.RoomDB.RecentFile
class RecentFileAdapter(private var list: List<RecentFile>) :
    RecyclerView.Adapter<RecentFileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbImage: ImageView = view.findViewById(R.id.imageThumbnail2)
        val fileName: TextView = view.findViewById(R.id.fileTitle2)
        val fileDate: TextView = view.findViewById(R.id.fileMeta2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item_single, parent, false)
        return FileViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

//    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
//        val file = list[position]
//        holder.fileName.text = file.name
//        holder.fileDate.text = file.date
//        Glide.with(holder.itemView.context)
//            .load(Uri.parse(file.thumbnailUri))
//            .into(holder.thumbImage)
//    }
override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
    val file = list[position]
    holder.fileName.text = file.name
    holder.fileDate.text = file.date
    Glide.with(holder.itemView.context)
            .load(Uri.parse(file.thumbnailUri))
            .into(holder.thumbImage)

    holder.itemView.setOnClickListener {
        val context = holder.itemView.context
        val actualFile = java.io.File(file.filePath)

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
}


    fun updateFiles(newList: List<RecentFile>) {
        list = newList
        notifyDataSetChanged()
    }
}
