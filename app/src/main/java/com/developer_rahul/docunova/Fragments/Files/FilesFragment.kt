package com.developer_rahul.docunova.Fragments.Files

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.developer_rahul.docunova.Adapters.FileAdapter
import com.developer_rahul.docunova.Adapters.RecentFileAdapter
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.RoomDB.AppDatabase
import com.developer_rahul.docunova.RoomDB.RecentFile
class FilesFragment : Fragment() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: FileAdapter
    private lateinit var recyclerView: RecyclerView
    private var fileList: MutableList<RecentFile> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_files, container, false)

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.recyclerFiles)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        // Initialize Room DB
        db = AppDatabase.getDatabase(requireContext())

        // Observe DB
        db.recentFileDao().getAllFiles().observe(viewLifecycleOwner) { files ->
            fileList.clear()
            fileList.addAll(files)

            adapter = FileAdapter(fileList) {
                // Callback to update list after rename/delete
                refreshFiles()
            }
            recyclerView.adapter = adapter
        }

        return view
    }

    private fun refreshFiles() {
        db.recentFileDao().getAllFiles().observe(viewLifecycleOwner) { files ->
            fileList.clear()
            fileList.addAll(files)
            adapter.notifyDataSetChanged()
        }
    }

}
