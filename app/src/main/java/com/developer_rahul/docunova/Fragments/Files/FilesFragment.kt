package com.developer_rahul.docunova.Fragments.Files

import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.developer_rahul.docunova.DriveServiceHelper
import com.developer_rahul.docunova.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: FileAdapter
    private lateinit var emptyStateView: View
    private lateinit var toolbar: MaterialToolbar

    private var currentViewType = FileAdapter.VIEW_TYPE_LIST
    private var currentSortOption = SORT_NAME_ASC
    private var filesList = mutableListOf<DriveFileModel2>()

    companion object {
        private const val SORT_NAME_ASC = 0
        private const val SORT_NAME_DESC = 1
        private const val SORT_DATE_NEWEST = 2
        private const val SORT_DATE_OLDEST = 3
        private const val SORT_SIZE_LARGEST = 4
        private const val SORT_SIZE_SMALLEST = 5
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_files, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerViewFiles)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshFiles)
        emptyStateView = view.findViewById(R.id.emptyStateView)
        toolbar = view.findViewById(R.id.toolbar)

        // Setup RecyclerView FIRST (before calling updateViewType)
        setupRecyclerView()

        // Setup toolbar
        setupToolbar()

        // Setup swipe refresh
        setupSwipeRefresh()

        // Setup FAB click listener
        setupFAB()

        // Load files initially
        loadDriveFiles()
    }

    private fun setupToolbar() {
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_sort -> {
                    showSortMenu()
                    true
                }
                R.id.action_view -> {
                    toggleViewType()
                    true
                }
                else -> false
            }
        }

        // Set initial icon after adapter is initialized
        updateViewTypeIcons()
    }

    private fun setupFAB() {
        val fabAdd = requireView().findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener {
            Toast.makeText(requireContext(), "Add new document", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSortMenu() {
        // Find the sort menu item view properly
        val sortMenuItemView = requireView().findViewById<View>(R.id.action_sort) ?: return

        val popup = PopupMenu(requireContext(), sortMenuItemView)
        popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)

        when (currentSortOption) {
            SORT_NAME_ASC -> popup.menu.findItem(R.id.sort_name_asc).isChecked = true
            SORT_NAME_DESC -> popup.menu.findItem(R.id.sort_name_desc).isChecked = true
            SORT_DATE_NEWEST -> popup.menu.findItem(R.id.sort_date_newest).isChecked = true
            SORT_DATE_OLDEST -> popup.menu.findItem(R.id.sort_date_oldest).isChecked = true
            SORT_SIZE_LARGEST -> popup.menu.findItem(R.id.sort_size_largest).isChecked = true
            SORT_SIZE_SMALLEST -> popup.menu.findItem(R.id.sort_size_smallest).isChecked = true
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort_name_asc -> {
                    currentSortOption = SORT_NAME_ASC
                    sortFiles()
                    true
                }
                R.id.sort_name_desc -> {
                    currentSortOption = SORT_NAME_DESC
                    sortFiles()
                    true
                }
                R.id.sort_date_newest -> {
                    currentSortOption = SORT_DATE_NEWEST
                    sortFiles()
                    true
                }
                R.id.sort_date_oldest -> {
                    currentSortOption = SORT_DATE_OLDEST
                    sortFiles()
                    true
                }
                R.id.sort_size_largest -> {
                    currentSortOption = SORT_SIZE_LARGEST
                    sortFiles()
                    true
                }
                R.id.sort_size_smallest -> {
                    currentSortOption = SORT_SIZE_SMALLEST
                    sortFiles()
                    true
                }
                else -> false
            }
        }

        // Set force show icon to prevent multiple menu issues
        popup.setForceShowIcon(true)
        popup.show()
    }

    private fun toggleViewType() {
        currentViewType = if (currentViewType == FileAdapter.VIEW_TYPE_LIST) {
            FileAdapter.VIEW_TYPE_GRID
        } else {
            FileAdapter.VIEW_TYPE_LIST
        }
        updateViewType()
    }

    private fun updateViewType() {
        // Update layout manager
        if (currentViewType == FileAdapter.VIEW_TYPE_LIST) {
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
        } else {
            recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        }

        // Update toolbar icons
        updateViewTypeIcons()

        // Update adapter view type (only if adapter is initialized)
        if (::adapter.isInitialized) {
            adapter.setViewType(currentViewType)
        }
    }

    private fun updateViewTypeIcons() {
        val viewItem = toolbar.menu.findItem(R.id.action_view)
        if (viewItem != null) {
            if (currentViewType == FileAdapter.VIEW_TYPE_LIST) {
                try {
                    viewItem.setIcon(R.drawable.ic_grid_view)
                } catch (e: Resources.NotFoundException) {
                    Log.e("FilesFragment", "Grid view icon not found", e)
                }
                viewItem.title = "Grid View"
            } else {
                try {
                    viewItem.setIcon(R.drawable.ic_list_view)
                } catch (e: Resources.NotFoundException) {
                    Log.e("FilesFragment", "List view icon not found", e)
                }
                viewItem.title = "List View"
            }
        }
    }

    private fun setupRecyclerView() {
        // Create adapter first
        adapter = FileAdapter(
            requireContext(),
            emptyList(),
            currentViewType,
            onDownloadClick = { file ->
                downloadFileFromDrive(file.id, file.name)
            },
            onFileClick = { file ->
                openDriveFile(file)
            },
            onMoreClick = { file, anchorView ->
                showFileOptionsMenu(file, anchorView)
            }
        )

        // Set adapter
        recyclerView.adapter = adapter

        // Now set the layout manager
        if (currentViewType == FileAdapter.VIEW_TYPE_LIST) {
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
        } else {
            recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        }
    }

    private fun showFileOptionsMenu(file: DriveFileModel2, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_file_options, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> {
                    shareFile(file)
                    true
                }
                R.id.action_rename -> {
                    renameFile(file)
                    true
                }
                R.id.action_delete -> {
                    deleteFile(file)
                    true
                }
                else -> false
            }
        }

        // Set force show icon to prevent multiple menu issues
        popup.setForceShowIcon(true)
        popup.show()
    }

    private fun shareFile(file: DriveFileModel2) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, "https://drive.google.com/file/d/${file.id}/view")
        }
        startActivity(Intent.createChooser(shareIntent, "Share file"))
    }

    private fun renameFile(file: DriveFileModel2) {
        Toast.makeText(requireContext(), "Rename functionality coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun deleteFile(file: DriveFileModel2) {
        Toast.makeText(requireContext(), "Delete functionality coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadDriveFiles()
        }
    }

    private fun loadDriveFiles() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            swipeRefreshLayout.isRefreshing = true

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                    val files = DriveServiceHelper.listFilesFromAppFolder(drive)

                    // Convert to DriveFileModel2 - PROPERLY map all fields
                    val driveFiles = files.map { file ->
                        DriveFileModel2(
                            id = file.id,
                            name = file.name,
                            mimeType = file.mimeType,
                            size = file.size, // This should be Long in original DriveFileModel
                            modifiedTime = file.modifiedTime, // This is the upload date
                            thumbnailLink = file.thumbnailLink,
                            webContentLink = file.webContentLink,
                            createdTime = file.createdTime // If available in original
                        )
                    }

                    filesList.clear()
                    filesList.addAll(driveFiles)

                    // Sort on background thread but update UI on main thread
                    val sortedList = when (currentSortOption) {
                        SORT_NAME_ASC -> filesList.sortedBy { it.name.lowercase() }
                        SORT_NAME_DESC -> filesList.sortedByDescending { it.name.lowercase() }
                        SORT_DATE_NEWEST -> filesList.sortedByDescending { it.modifiedTime } // Use modifiedTime for sorting
                        SORT_DATE_OLDEST -> filesList.sortedBy { it.modifiedTime }
                        SORT_SIZE_LARGEST -> filesList.sortedByDescending { it.size }
                        SORT_SIZE_SMALLEST -> filesList.sortedBy { it.size }
                        else -> filesList
                    }

                    withContext(Dispatchers.Main) {
                        adapter.updateFiles(sortedList)
                        updateEmptyState(driveFiles.isEmpty())
                        swipeRefreshLayout.isRefreshing = false

                        // Debug: Check what data we're getting
                        if (driveFiles.isNotEmpty()) {
                            val firstFile = driveFiles.first()
                            Log.d("FilesFragment", "First file: ${firstFile.name}, size: ${firstFile.size}, modified: ${firstFile.modifiedTime}")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to load files: ${e.message}", Toast.LENGTH_SHORT).show()
                        swipeRefreshLayout.isRefreshing = false
                        updateEmptyState(true)
                        Log.e("FilesFragment", "Error loading files", e)
                    }
                }
            }
        } else {
            Toast.makeText(requireContext(), "Please sign in to Google Drive to access files", Toast.LENGTH_SHORT).show()
            updateEmptyState(true)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun sortFiles() {
        // Perform sorting on background thread but update UI on main thread
        lifecycleScope.launch(Dispatchers.Default) {
            val sortedList = when (currentSortOption) {
                SORT_NAME_ASC -> filesList.sortedBy { it.name.lowercase() }
                SORT_NAME_DESC -> filesList.sortedByDescending { it.name.lowercase() }
                SORT_DATE_NEWEST -> filesList.sortedByDescending { it.modifiedTime }
                SORT_DATE_OLDEST -> filesList.sortedBy { it.modifiedTime }
                SORT_SIZE_LARGEST -> filesList.sortedByDescending { it.size }
                SORT_SIZE_SMALLEST -> filesList.sortedBy { it.size }
                else -> filesList
            }

            withContext(Dispatchers.Main) {
                adapter.updateFiles(sortedList)
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            emptyStateView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun openDriveFile(file: DriveFileModel2) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())

        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            if (isGoogleDriveAppInstalled()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://drive.google.com/file/d/${file.id}/view")
                        setPackage("com.google.android.apps.docs")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    openFileInBrowser(file)
                }
            } else {
                openFileInBrowser(file)
            }
        } else {
            GoogleSignIn.getClient(requireContext(), GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .build())
                .silentSignIn()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        openDriveFile(file)
                    } else {
                        Toast.makeText(requireContext(), "Please sign in to access Drive", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun openFileInBrowser(file: DriveFileModel2) {
        val url = "https://drive.google.com/file/d/${file.id}/view"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isGoogleDriveAppInstalled(): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo("com.google.android.apps.docs", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun downloadFileFromDrive(fileId: String, fileName: String) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appFolder = File(downloadsDir, "Docunova")
                if (!appFolder.exists()) appFolder.mkdirs()

                val outputFile = File(appFolder, fileName)
                DriveServiceHelper.downloadFile(drive, fileId, outputFile)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Downloaded to ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadDriveFiles()
    }
}