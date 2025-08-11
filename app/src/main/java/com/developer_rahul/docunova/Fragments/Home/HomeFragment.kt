package com.developer_rahul.docunova.Fragments.Home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.developer_rahul.docunova.ProfileActivity
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.RoomDB.AppDatabase
import com.developer_rahul.docunova.RoomDB.RecentFile
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private val SUPABASE_URL = "https://biudcywgygbacfxfpuva.supabase.co"
    private val SUPABASE_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJpdWRjeXdneWdiYWNmeGZwdXZhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM1Mzg1NDQsImV4cCI6MjA2OTExNDU0NH0.enJZKTQjKtOyB6VU5pYo_vf4p7ZLv2ayYuyqBWvLqbA"
    private val SUPABASE_STORAGE_URL = "$SUPABASE_URL/storage/v1"
    private val BUCKET_NAME = "user-documents"

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

    // UI elements
    private lateinit var tvUsername: TextView
    private lateinit var profileImage: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())
        initializeViews(view)
        setupScanner()
        setupRecyclerView()
        fetchUserDetails()
        fetchUserDocumentsFromSupabase()
    }

    private fun initializeViews(view: View) {
        tvUsername = view.findViewById(R.id.tv_Username)
        profileImage = view.findViewById(R.id.profileImage_home)

        view.findViewById<ImageView>(R.id.iv_scan).setOnClickListener {
            Log.d(TAG, "Scan button clicked")
            launchDocumentScanner()
        }
        profileImage.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        setupOptionButtons(view)
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
                Log.d(TAG, "Scan result received")
                handleScanResult(result.data)
            } else {
                Log.d(TAG, "Scan cancelled or failed")
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
                    Log.d(TAG, "Valid scan result received")
                    promptFileNameAndSave(pdfResult.uri, thumbnailUri)
                } else {
                    Log.d(TAG, "Scan result is empty")
                    Toast.makeText(context, "Scan result is empty", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling scan result", e)
            Toast.makeText(context, "Error processing scan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptFileNameAndSave(pdfUri: Uri, thumbnailUri: Uri) {
        Log.d(TAG, "Prompting for file name")

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filename_prompt, null).apply {
            findViewById<EditText>(R.id.filename_input).apply {
                setText("Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}")
                setSelection(text.length)
            }
            startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.dialog_fade_in))
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { d, _ ->
                Log.d(TAG, "Save cancelled")
                d.dismiss()
            }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val fileName = dialogView.findViewById<EditText>(R.id.filename_input).text.toString().trim()
                when {
                    fileName.isEmpty() -> {
                        dialogView.findViewById<EditText>(R.id.filename_input).error = "File name cannot be empty"
                        Log.d(TAG, "Empty file name entered")
                    }
                    else -> {
                        Log.d(TAG, "Attempting to save file: $fileName")
                        checkAndUploadFile(pdfUri, fileName, thumbnailUri, dialog)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun checkAndUploadFile(pdfUri: Uri, fileName: String, thumbnailUri: Uri, dialog: AlertDialog) {
        Log.d(TAG, "Checking if file exists: $fileName")

        getUserIdAndToken()?.let { (userId, token) ->
            val url = "$SUPABASE_STORAGE_URL/object/info/$BUCKET_NAME/$userId/$fileName.pdf"

            val existsRequest = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                { response ->
                    Log.d(TAG, "File exists check response: $response")
                    activity?.runOnUiThread {
                        dialog.findViewById<EditText>(R.id.filename_input)?.error =
                            "A file with this name already exists"
                    }
                },
                { error ->
                    Log.d(TAG, "File exists check error: ${error.networkResponse?.statusCode}")
                    if (error.networkResponse?.statusCode == 404) {
                        // File doesn't exist, proceed with upload
                        activity?.runOnUiThread {
                            dialog.dismiss()
                            uploadToSupabaseStorage(pdfUri, fileName, thumbnailUri)
                        }
                    } else {
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Error checking file existence", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                override fun getHeaders() = mapOf(
                    "apikey" to SUPABASE_API_KEY,
                    "Authorization" to "Bearer $token"
                ).toMutableMap()
            }

            Volley.newRequestQueue(requireContext()).add(existsRequest)
        } ?: run {
            Toast.makeText(context, "Not authenticated", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun uploadToSupabaseStorage(pdfUri: Uri, fileName: String, thumbnailUri: Uri) {
        Log.d(TAG, "Starting upload for file: $fileName")

        getUserIdAndToken()?.let { (userId, token) ->
            try {
                val inputStream = requireContext().contentResolver.openInputStream(pdfUri)
                    ?: throw Exception("Failed to open PDF stream")

                val pdfBytes = inputStream.use { it.readBytes() }
                Log.d(TAG, "PDF size: ${pdfBytes.size} bytes")

                val uploadRequest = object : StringRequest(
                    Request.Method.POST,
                    "$SUPABASE_STORAGE_URL/object/$BUCKET_NAME/$userId/$fileName.pdf",
                    { response ->
                        Log.d(TAG, "Upload successful: $response")
                        saveFileToDatabase(fileName, thumbnailUri)
                    },
                    { error ->
                        Log.e(TAG, "Upload failed: ${error.message}")
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Failed to upload document", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    override fun getHeaders(): MutableMap<String, String> {
                        return hashMapOf(
                            "apikey" to SUPABASE_API_KEY,
                            "Authorization" to "Bearer $token",
                            "Content-Type" to "application/pdf"
                        )
                    }

                    override fun getBody(): ByteArray {
                        return pdfBytes
                    }
                }

                Volley.newRequestQueue(requireContext()).add(uploadRequest)
                Log.d(TAG, "Upload request queued")

            } catch (e: Exception) {
                Log.e(TAG, "Error preparing upload: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error preparing document for upload", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Toast.makeText(context, "Not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFileToDatabase(fileName: String, thumbnailUri: Uri) {
        Log.d(TAG, "Saving file to database: $fileName")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val recentFile = RecentFile(
                    name = fileName,
                    filePath = "", // We're not storing locally
                    thumbnailUri = thumbnailUri.toString(),
                    date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                    isSynced = true
                )

                db.recentFileDao().insert(recentFile)
                Log.d(TAG, "File saved to database")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Document saved successfully", Toast.LENGTH_SHORT).show()
                    fetchUserDocumentsFromSupabase()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Database save error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save document record", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up recycler view")

        recyclerView = requireView().findViewById<RecyclerView>(R.id.recentFilesRecycler).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
        }

        db.recentFileDao().getAllFiles().observe(viewLifecycleOwner) { files ->
            Log.d(TAG, "Updating adapter with ${files.size} files")
            adapter.updateFiles(files ?: emptyList())
        }

        fetchUserDocumentsFromSupabase()
    }

    private fun fetchUserDocumentsFromSupabase() {
        Log.d(TAG, "Fetching user documents from Supabase")

        getUserIdAndToken()?.let { (userId, token) ->
            val url = "$SUPABASE_STORAGE_URL/object/list/$BUCKET_NAME?prefix=$userId/"

            val request = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                { response ->
                    Log.d(TAG, "Documents fetch successful")
                    handleDocumentsResponse(response)
                },
                { error ->
                    Log.e(TAG, "Documents fetch failed: ${error.message}")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Failed to load documents", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                override fun getHeaders() = mapOf(
                    "apikey" to SUPABASE_API_KEY,
                    "Authorization" to "Bearer $token"
                ).toMutableMap()
            }

            Volley.newRequestQueue(requireContext()).add(request)
        } ?: run {
            Log.d(TAG, "Not authenticated - skipping documents fetch")
        }
    }

    private fun handleDocumentsResponse(response: JSONObject) {
        try {
            val documents = mutableListOf<RecentFile>()
            val items = response.getJSONArray("data")
            Log.d(TAG, "Found ${items.length()} documents")

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
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing documents", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Error processing documents", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDocumentFromSupabase(file: RecentFile) {
        Log.d(TAG, "Attempting to open document: ${file.name}")

        getUserIdAndToken()?.let { (userId, _) ->
            try {
                val url = "$SUPABASE_STORAGE_URL/object/$BUCKET_NAME/$userId/${file.name}"
                Log.d(TAG, "Document URL: $url")

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                if (intent.resolveActivity(requireContext().packageManager) == null) {
                    intent.setDataAndType(Uri.parse(url), "application/pdf")
                }

                startActivity(intent)
                Log.d(TAG, "Document opened successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error opening document", e)
                Toast.makeText(context, "No app available to open PDF", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(context, "Not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadDocument(file: RecentFile) {
        Log.d(TAG, "Downloading document: ${file.name}")

        getUserIdAndToken()?.let { (userId, token) ->
            val url = "$SUPABASE_STORAGE_URL/object/$BUCKET_NAME/$userId/${file.name}"

            val request = object : StringRequest(
                Request.Method.GET, url,
                { response ->
                    Log.d(TAG, "Download successful")
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.recentFileDao().updateFilePath(file.id, "remote:$url")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Document ready to view", Toast.LENGTH_SHORT).show()
                            openDocumentFromSupabase(file)
                        }
                    }
                },
                { error ->
                    Log.e(TAG, "Download failed: ${error.message}")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                override fun getHeaders() = mapOf(
                    "apikey" to SUPABASE_API_KEY,
                    "Authorization" to "Bearer $token"
                ).toMutableMap()
            }

            Volley.newRequestQueue(requireContext()).add(request)
        } ?: run {
            Toast.makeText(context, "Not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchDocumentScanner() {
        Log.d(TAG, "Launching document scanner")

        scanner?.getStartScanIntent(requireActivity())
            ?.addOnSuccessListener { intentSender ->
                Log.d(TAG, "Scanner launched successfully")
                scannerLauncher?.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            ?.addOnFailureListener {
                Log.e(TAG, "Scanner launch failed", it)
                Toast.makeText(context, "Failed to start scanner", Toast.LENGTH_SHORT).show()
            }
    }

    private fun animateSelection(view: View) {
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(150)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun fetchUserDetails() {
        Log.d(TAG, "Fetching user details")

        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        when (prefs.getString("login_method", null)) {
            "google" -> {
                Log.d(TAG, "User logged in via Google")
                displayGoogleUserInfo(prefs)
            }
            "email" -> {
                Log.d(TAG, "User logged in via email")
                fetchSupabaseUserInfo(prefs)
            }
            else -> {
                Log.d(TAG, "User not logged in")
                showGuestUser()
            }
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
            Log.d(TAG, "Fetching user info from Supabase")

            val request = object : JsonObjectRequest(
                Request.Method.GET,
                "$SUPABASE_URL/auth/v1/user",
                null,
                { response ->
                    Log.d(TAG, "User info fetch successful")
                    handleUserResponse(response)
                },
                { error ->
                    Log.e(TAG, "User info fetch failed", error)
                    handleUserError(error)
                }
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
        try {
            val email = response.optString("email", "No Email")
            val metadata = response.optJSONObject("user_metadata")

            tvUsername.text = email

            metadata?.optString("avatar_url")?.takeIf { it.isNotEmpty() }?.let { url ->
                Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.ic_profile)
                    .into(profileImage)
            } ?: profileImage.setImageResource(R.drawable.ic_profile)

            Log.d(TAG, "User info displayed: $email")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user info", e)
            Toast.makeText(context, "Error reading profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleUserError(error: com.android.volley.VolleyError) {
        if (error.networkResponse?.statusCode == 401) {
            Log.d(TAG, "Session expired")
            showSessionExpired()
        } else {
            Log.e(TAG, "User info fetch error", error)
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

        return if (userId != null && token != null) {
            Log.d(TAG, "User authenticated: $userId")
            userId to token
        } else {
            Log.d(TAG, "User not authenticated")
            null
        }
    }
}