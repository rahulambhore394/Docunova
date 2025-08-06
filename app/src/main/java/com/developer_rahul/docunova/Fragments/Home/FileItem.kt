package com.developer_rahul.docunova.Fragments.Home

data class FileItem(
    val title: String,
    val meta: String,      // e.g., "2024-01-15 • 2.4 MB"
    val fileType: String,  // e.g., "PDF"
    val thumbnailResId: Int // drawable resource ID
)
