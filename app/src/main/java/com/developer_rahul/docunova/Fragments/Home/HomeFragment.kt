package com.developer_rahul.docunova.Fragments.Home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.format.Formatter.formatFileSize
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.Adapters.RecentFileAdapter
import com.developer_rahul.docunova.DriveServiceHelper
import com.developer_rahul.docunova.DriveServiceHelper.downloadFile
import com.developer_rahul.docunova.ProfileActivity
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.RoomDB.AppDatabase
import com.developer_rahul.docunova.RoomDB.RecentFile
import com.developer_rahul.docunova.GoogleDriveClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private val SUPABASE_URL = "https://biudcywgygbacfxfpuva.supabase.co"
    private val SUPABASE_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJpdWRjeXdneWdiYWNmeGZwdXZhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM1Mzg1NDQsImV4cCI6MjA2OTExNDU0NH0.enJZKTQjKtOyB6VU5pYo_vf4p7ZLv2ayYuyqBWvLqbA"
    private val SUPABASE_STORAGE_URL = "$SUPABASE_URL/storage/v1"
    private val BUCKET_NAME = "user-documents"
    private lateinit var driveAdapter: DriveFileAdapter

    private lateinit var tvTotalFiles: TextView
    private lateinit var tvTotalSize: TextView



    private var scanner: GmsDocumentScanner? = null
    private var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    private lateinit var recyclerView: RecyclerView
    private val adapter by lazy {
        RecentFileAdapter(emptyList()) { file ->
            if (file.filePath.isNotEmpty()) {
                openDocumentFromSupabase(file)
            } else {
                downloadDocument(file)
            }
        }
    }
    private lateinit var db: AppDatabase

    private lateinit var tvUsername: TextView
    private lateinit var profileImage: ImageView

    // --- Google Drive sign-in for email-login users or missing permission
    private lateinit var googleDriveSignInLauncher: ActivityResultLauncher<Intent>
    private val driveGso by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }
    private val driveSignInClient by lazy { GoogleSignIn.getClient(requireContext(), driveGso) }
    private var pendingPdfUri: Uri? = null
    private var pendingDriveFileName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvTotalFiles = view.findViewById(R.id.tv_scanned_files_count)
        tvTotalSize = view.findViewById(R.id.tv_drive_size)

        db = AppDatabase.getDatabase(requireContext())
        initializeViews(view)
        setupScanner()
        setupRecyclerView()
        fetchUserDetails()

//        recyclerView.adapter = driveAdapter


        loadDriveFiles()
        driveAdapter = DriveFileAdapter(requireContext(), emptyList()) { file ->
            downloadFileFromDrive(file.id, file.name)
        }
        recyclerView.adapter = driveAdapter

        fetchUserDocumentsFromSupabase() // keep if you still list Supabase docs

        // Register Drive sign-in result handler
        googleDriveSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    // Now upload the pending file
                    val uri = pendingPdfUri
                    val name = pendingDriveFileName
                    if (uri != null && name != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val drive = DriveServiceHelper.buildService(requireContext(),
                                    account.toString()
                                )
                                val id = GoogleDriveClient.uploadUri(drive, requireContext(), uri, "$name.pdf")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "Uploaded to Drive (id: $id)", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "Drive upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                pendingPdfUri = null
                                pendingDriveFileName = null
                            }
                        }
                    }
                }
            } catch (e: ApiException) {
                Toast.makeText(requireContext(), "Google Sign-In cancelled/failed", Toast.LENGTH_SHORT).show()
                pendingPdfUri = null
                pendingDriveFileName = null
            }
        }
    }

    private fun initializeViews(view: View) {
        tvUsername = view.findViewById(R.id.tv_Username)
        profileImage = view.findViewById(R.id.profileImage_home)

        view.findViewById<ImageView>(R.id.iv_scan).setOnClickListener {
            launchDocumentScanner()
        }
        profileImage.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        setupOptionButtons(view)
    }

    private fun loadDriveFiles() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                    val files = DriveServiceHelper.listFilesFromAppFolder(drive)

                    // Calculate total files and size
                    var totalSize: Long = 0
                    for (file in files) {
                        totalSize += file.size ?: 0
                    }

                    val sizeFormatted = formatFileSize(totalSize)
                    withContext(Dispatchers.Main) {
                        driveAdapter.updateFiles(files)
                        tvTotalFiles.text = "${files.size}"
                        tvTotalSize.text = "$sizeFormatted"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to load Drive files", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var newSize = size.toDouble()
        var unitIndex = 0
        while (newSize >= 1024 && unitIndex < units.size - 1) {
            newSize /= 1024
            unitIndex++
        }
        return String.format("%.2f %s", newSize, units[unitIndex])
    }




    private fun downloadFileFromDrive(fileId: String, fileName: String) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)

                // Create app folder inside Downloads
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appFolder = File(downloadsDir, "Docunova")
                if (!appFolder.exists()) appFolder.mkdirs()

                val outputFile = File(appFolder, fileName)

                downloadFile(drive, fileId, outputFile)

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




    private fun setupOptionButtons(view: View) {
        val options = listOf(
            view.findViewById<TextView>(R.id.tv_select_upload),
            view.findViewById<TextView>(R.id.tv_start_translate),
            view.findViewById<TextView>(R.id.tv_view_files)
        )

        options.forEach { button ->
            button.setOnClickListener {
                options.forEach { it.isSelected = false }
                button.isSelected = true
                animateSelection(button)

                when (button.id) {
                    R.id.tv_select_upload -> {
                        Log.d(TAG, "Select upload clicked")
                        startActivity(Intent(requireContext(), TypoProcessingActivity::class.java))
                    }
                    R.id.tv_start_translate -> {
                        Log.d(TAG, "Start translate clicked")
                        startActivity(Intent(requireContext(), TranslationActivity::class.java))
                    }
                }
            }
        }
    }



    private fun setupScanner() {
        scanner = GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(20)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
        )

        scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleScanResult(result.data)
            }
        }
    }

    private fun handleScanResult(data: Intent?) {
        try {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(data)
            if (scanResult != null) {
                val pdfResult = scanResult.pdf
                val thumbnailUri = scanResult.pages?.firstOrNull()?.imageUri

                if (pdfResult != null && thumbnailUri != null) {
                    promptFileNameAndSave(pdfResult.uri, thumbnailUri)
                } else {
                    Toast.makeText(context, "Scan result is empty", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error processing scan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptFileNameAndSave(pdfUri: Uri, thumbnailUri: Uri) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filename_prompt, null).apply {
            findViewById<EditText>(R.id.filename_input).apply {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                setText("Scan_$ts")
                setSelection(text.length)
            }
            startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.dialog_fade_in))
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val input = dialogView.findViewById<EditText>(R.id.filename_input)
                val fileName = input.text.toString().trim()
                if (fileName.isEmpty()) {
                    input.error = "File name cannot be empty"
                    return@setOnClickListener
                }

                // === GOOGLE DRIVE UPLOAD ===
                ensureDriveAccountThenUpload(pdfUri, fileName)

                // === (OPTIONAL) SUPABASE UPLOAD ===
                // If you want to keep Supabase storage too, uncomment:
                // uploadToSupabaseStorage(pdfUri, fileName, thumbnailUri)

                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Ensures user has Google account with Drive scope, then uploads the file */
    private fun ensureDriveAccountThenUpload(pdfUri: Uri, fileName: String) {
        pendingPdfUri = pdfUri
        pendingDriveFileName = fileName

        val last = GoogleSignIn.getLastSignedInAccount(requireContext())
        val hasDrive = last != null && GoogleSignIn.hasPermissions(last, Scope(DriveScopes.DRIVE_FILE))
        if (hasDrive) {
            // ✅ Upload directly using last.email instead of account
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val drive = DriveServiceHelper.buildService(requireContext(), last!!.email!!)
                    val id = DriveServiceHelper.uploadFileToAppFolder(drive, requireContext(), pdfUri, "$fileName.pdf")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Uploaded to Drive", Toast.LENGTH_SHORT).show()
                        loadDriveFiles() // refresh list
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Drive upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    pendingPdfUri = null
                    pendingDriveFileName = null
                }
            }
        } else {
            // Ask user to pick/connect a Google account for Drive
            googleDriveSignInLauncher.launch(driveSignInClient.signInIntent)
        }
    }


    private fun setupRecyclerView() {
        recyclerView = requireView().findViewById<RecyclerView>(R.id.recentFilesRecycler).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
        }

        db.recentFileDao().getAllFiles().observe(viewLifecycleOwner) { files ->
            adapter.updateFiles(files ?: emptyList())
        }

        fetchUserDocumentsFromSupabase() // keep if listing Supabase docs
    }

    // ------------------ Supabase user/profile + list (unchanged) ------------------

    private fun fetchUserDetails() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        when (prefs.getString("login_method", null)) {
            "google" -> {
                displayGoogleUserInfo(prefs)
            }
            "email" -> {
                fetchSupabaseUserInfo(prefs)
            }
            else -> showGuestUser()
        }
    }

    private fun displayGoogleUserInfo(prefs: android.content.SharedPreferences) {
        val fullName = prefs.getString("google_full_name", null) ?: "Google User"
        val avatarUrl = prefs.getString("google_avatar_url", null)

        tvUsername.text = fullName

        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_profile)
                .into(profileImage)
        } else {
            profileImage.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun fetchSupabaseUserInfo(prefs: android.content.SharedPreferences) {
        prefs.getString("access_token", null)?.let { token ->
            val request = object : JsonObjectRequest(
                Request.Method.GET,
                "$SUPABASE_URL/auth/v1/user",
                null,
                { response -> handleUserResponse(response) },
                { error -> handleUserError(error) }
            ) {
                override fun getHeaders() = mapOf(
                    "apikey" to SUPABASE_API_KEY,
                    "Authorization" to "Bearer $token",
                    "Accept" to "application/json"
                ).toMutableMap()
            }

            Volley.newRequestQueue(requireContext()).add(request)
        } ?: showSessionExpired()
    }

    private fun handleUserResponse(response: JSONObject) {
        val email = response.optString("email", "No Email")
        val metadata = response.optJSONObject("user_metadata")

        tvUsername.text = email

        metadata?.optString("avatar_url")?.takeIf { it.isNotEmpty() }?.let { url ->
            Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_profile)
                .into(profileImage)
        } ?: profileImage.setImageResource(R.drawable.ic_profile)
    }

    private fun handleUserError(error: com.android.volley.VolleyError) {
        if (error.networkResponse?.statusCode == 401) {
            showSessionExpired()
        } else {
            Toast.makeText(context, "Profile load failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showGuestUser() {
        tvUsername.text = "Guest"
        profileImage.setImageResource(R.drawable.ic_profile)
        Toast.makeText(context, "Please log in", Toast.LENGTH_SHORT).show()
    }

    private fun showSessionExpired() {
        tvUsername.text = "Session Expired"
        profileImage.setImageResource(R.drawable.ic_profile)
        Toast.makeText(context, "Please log in again", Toast.LENGTH_SHORT).show()
    }

    private fun getUserIdAndToken(): Pair<String, String>? {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)
        val token = prefs.getString("access_token", null)
        return if (userId != null && token != null) userId to token else null
    }

    // ------------------ Supabase storage (optional; keep if you still use it) -----

    private fun fetchUserDocumentsFromSupabase() {
        getUserIdAndToken()?.let { (userId, token) ->
            val url = "$SUPABASE_STORAGE_URL/object/list/$BUCKET_NAME?prefix=$userId/"

            val request = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                { response -> handleDocumentsResponse(response) },
                { _ -> Toast.makeText(context, "Failed to load documents", Toast.LENGTH_SHORT).show() }
            ) {
                override fun getHeaders() = mapOf(
                    "apikey" to SUPABASE_API_KEY,
                    "Authorization" to "Bearer $token"
                ).toMutableMap()
            }

            Volley.newRequestQueue(requireContext()).add(request)
        }
    }

    private fun handleDocumentsResponse(response: JSONObject) {
        try {
            val documents = mutableListOf<RecentFile>()
            val items = response.getJSONArray("data")

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                documents.add(
                    RecentFile(
                        name = item.getString("name").substringAfterLast('/'),
                        filePath = "",
                        thumbnailUri = "",
                        date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            .format(
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                    .parse(item.getString("created_at")) ?: Date()
                            ),
                        isSynced = true
                    )
                )
            }

            lifecycleScope.launch(Dispatchers.Main) {
                adapter.updateFiles(documents)
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Error processing documents", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDocumentFromSupabase(file: RecentFile) {
        getUserIdAndToken()?.let { (userId, _) ->
            val url = "$SUPABASE_STORAGE_URL/object/$BUCKET_NAME/$userId/${file.name}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(requireContext().packageManager) == null) {
                intent.setDataAndType(Uri.parse(url), "application/pdf")
            }
            startActivity(intent)
        }
    }

    private fun downloadDocument(file: RecentFile) {
        getUserIdAndToken()?.let { (userId, token) ->
            val url = "$SUPABASE_STORAGE_URL/object/$BUCKET_NAME/$userId/${file.name}"

            val request = object : StringRequest(
                Request.Method.GET, url,
                { _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.recentFileDao().updateFilePath(file.id, "remote:$url")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Document ready to view", Toast.LENGTH_SHORT).show()
                            openDocumentFromSupabase(file)
                        }
                    }
                },
                { _ -> Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show() }
            ) {
                override fun getHeaders() = mapOf(
                    "apikey" to SUPABASE_API_KEY,
                    "Authorization" to "Bearer $token"
                ).toMutableMap()
            }

            Volley.newRequestQueue(requireContext()).add(request)
        }
    }

    private fun launchDocumentScanner() {
        scanner?.getStartScanIntent(requireActivity())
            ?.addOnSuccessListener { intentSender ->
                scannerLauncher?.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            ?.addOnFailureListener {
                Toast.makeText(context, "Failed to start scanner", Toast.LENGTH_SHORT).show()
            }
    }

    private fun animateSelection(view: View) {
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(150)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            .start()
    }
}
