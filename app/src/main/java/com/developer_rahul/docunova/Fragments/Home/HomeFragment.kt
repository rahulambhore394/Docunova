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
import java.io.ByteArrayOutputStream
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
    private lateinit var adapter: RecentFileAdapter
    private lateinit var db: AppDatabase

    // UI elements
    private lateinit var tv_Username: TextView
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

        // Initialize UI
        tv_Username = view.findViewById(R.id.tv_Username)
        profileImage = view.findViewById(R.id.profileImage_home)

        initScanner()
        setupViews(view)
        setupRecyclerView(view)

        fetchUserDetails()
        fetchUserDocumentsFromSupabase()
    }

    private fun initScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        scanner = GmsDocumentScanning.getClient(options)
        scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.let { scanResult ->
                    val pdfResult = scanResult.pdf
                    val thumbnailUri = scanResult.pages?.firstOrNull()?.imageUri

                    if (pdfResult != null && thumbnailUri != null) {
                        promptFileNameAndSave(pdfResult.uri, thumbnailUri)
                    } else {
                        Toast.makeText(context, "Scan result is empty", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun promptFileNameAndSave(pdfUri: Uri, thumbnailUri: Uri) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val defaultName = "Scan_$timestamp"

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filename_prompt, null)
        val input = dialogView.findViewById<EditText>(R.id.filename_input)
        input.setText(defaultName)
        input.setSelection(defaultName.length)

        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.dialog_fade_in)
        dialogView.startAnimation(animation)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val enteredName = input.text.toString().trim()
                if (enteredName.isEmpty()) {
                    input.error = "File name cannot be empty"
                    return@setOnClickListener
                }

                // Check if file exists in Supabase
                checkFileExists(enteredName) { exists ->
                    if (exists) {
                        input.error = "A file with this name already exists"
                    } else {
                        uploadToSupabaseStorage(pdfUri, enteredName, thumbnailUri)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun checkFileExists(fileName: String, callback: (Boolean) -> Unit) {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null) ?: return
        val userId = prefs.getString("user_id", null) ?: return

        val url = "$SUPABASE_STORAGE_URL/object/info/$BUCKET_NAME/$userId/$fileName.pdf"
        val request = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                // File exists if we get a response
                callback(true)
            },
            { error ->
                // File doesn't exist if we get 404
                callback(error.networkResponse?.statusCode != 404)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "apikey" to SUPABASE_API_KEY,
                    "Authorization" to "Bearer $accessToken"
                )
            }
        }

        Volley.newRequestQueue(requireContext()).add(request)
    }

    private fun uploadToSupabaseStorage(pdfUri: Uri, fileName: String, thumbnailUri: Uri) {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null) ?: run {
            Toast.makeText(context, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = prefs.getString("user_id", null) ?: run {
            Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Read PDF content from URI
            val inputStream = requireContext().contentResolver.openInputStream(pdfUri)
            val byteArray = inputStream?.readBytes() ?: run {
                Toast.makeText(context, "Failed to read PDF", Toast.LENGTH_SHORT).show()
                return
            }

            val url = "$SUPABASE_STORAGE_URL/object/$BUCKET_NAME/$userId/$fileName.pdf"
            val request = object : StringRequest(
                Request.Method.POST, url,
                { response ->
                    Log.d(TAG, "File uploaded successfully: $fileName")

                    // Save record in local database without local file path
                    val recentFile = RecentFile(
                        name = fileName,
                        filePath = "", // No local path
                        thumbnailUri = thumbnailUri.toString(),
                        date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                        isSynced = true
                    )

                    lifecycleScope.launch(Dispatchers.IO) {
                        db.recentFileDao().insert(recentFile)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Document saved to cloud", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                { error ->
                    Log.e(TAG, "Error uploading file: ${error.message}")
                    Toast.makeText(context, "Failed to upload to cloud", Toast.LENGTH_SHORT).show()
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> {
                    return hashMapOf(
                        "apikey" to SUPABASE_API_KEY,
                        "Authorization" to "Bearer $accessToken",
                        "Content-Type" to "application/pdf"
                    )
                }

                override fun getBody(): ByteArray {
                    return byteArray
                }
            }

            Volley.newRequestQueue(requireContext()).add(request)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing file for upload", e)
            Toast.makeText(context, "Error preparing upload", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchUserDocumentsFromSupabase() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null) ?: return
        val userId = prefs.getString("user_id", null) ?: return

        val url = "$SUPABASE_STORAGE_URL/object/list/$BUCKET_NAME?prefix=$userId/"
        val request = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val documents = mutableListOf<RecentFile>()
                    val items = response.getJSONArray("data")

                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val name = item.getString("name").substringAfterLast('/')
                        val createdAt = item.getString("created_at")

                        documents.add(RecentFile(
                            name = name,
                            filePath = "", // No local path
                            thumbnailUri = "",
                            date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                .format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                    .parse(createdAt) ?: Date()),
                            isSynced = true
                        ))
                    }

                    lifecycleScope.launch(Dispatchers.Main) {
                        adapter.updateFiles(documents)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing documents list", e)
                }
            },
            { error ->
                Log.e(TAG, "Error fetching documents", error)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "apikey" to SUPABASE_API_KEY,
                    "Authorization" to "Bearer $accessToken"
                )
            }
        }

        Volley.newRequestQueue(requireContext()).add(request)
    }

    private fun setupViews(view: View) {
        val tvSelect = view.findViewById<TextView>(R.id.tv_select_upload)
        val tvOpenTranslate = view.findViewById<TextView>(R.id.tv_start_translate)
        val tvView = view.findViewById<TextView>(R.id.tv_view_files)
        val layoutCapture = view.findViewById<ImageView>(R.id.iv_scan)

        val options = listOf(tvSelect, tvOpenTranslate, tvView)
        options.forEach { tv ->
            tv.setOnClickListener {
                options.forEach { it.isSelected = false }
                tv.isSelected = true
                animateSelection(tv)

                when (tv) {
                    tvSelect -> startActivity(Intent(requireContext(), TypoProcessingActivity::class.java))
                    tvOpenTranslate -> startActivity(Intent(requireContext(), TranslationActivity::class.java))
                }
            }
        }

        layoutCapture.setOnClickListener {
            scanDocument()
        }

        profileImage.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.recentFilesRecycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = RecentFileAdapter(emptyList()) { file ->
            if (file.name.isNotEmpty()) {
                openDocumentFromSupabase(file)
            }
        }
        recyclerView.adapter = adapter

        db.recentFileDao().getAllFiles().observe(viewLifecycleOwner) { recentFiles ->
            adapter.updateFiles(recentFiles)
        }
    }

    private fun openDocumentFromSupabase(file: RecentFile) {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null) ?: return
        val userId = prefs.getString("user_id", null) ?: return

        val url = "$SUPABASE_STORAGE_URL/object/$BUCKET_NAME/$userId/${file.name}"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Try to open with browser if no PDF viewer handles the URL
        if (intent.resolveActivity(requireContext().packageManager) == null) {
            intent.setDataAndType(Uri.parse(url), "application/pdf")
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateSelection(view: View) {
        view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).duration = 100
            }
    }

    private fun scanDocument() {
        scanner?.getStartScanIntent(requireActivity())
            ?.addOnSuccessListener { intentSender ->
                scannerLauncher?.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            ?.addOnFailureListener {
                Toast.makeText(context, "Failed to start scan", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Scanner launch error", it)
            }
    }



    private fun fetchUserDetails() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)
        val accessToken = prefs.getString("access_token", null)

        Log.d(TAG, "Login method: $loginMethod")

        if (loginMethod.isNullOrEmpty()) {
            tv_Username.text = "Guest"
            profileImage.setImageResource(R.drawable.ic_profile)
            Toast.makeText(context, "Please log in first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (loginMethod == "google") {
            val fullName = prefs.getString("google_full_name", null)
            val avatarUrl = prefs.getString("google_avatar_url", null)

            tv_Username.text = fullName ?: "Google User"
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile)
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.ic_profile)
            }
            return
        }

        if (loginMethod == "email" && accessToken != null) {
            val url = "$SUPABASE_URL/auth/v1/user"
            val request = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                { response ->
                    try {
                        val email = response.optString("email", "No Email")
                        val metadata = response.optJSONObject("user_metadata")
                        val fullName = metadata?.optString("full_name") ?: email
                        val avatarUrl = metadata?.optString("avatar_url") ?: ""

                        tv_Username.text = email
                        if (avatarUrl.isNotEmpty()) {
                            Glide.with(this)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_profile)
                                .into(profileImage)
                        } else {
                            profileImage.setImageResource(R.drawable.ic_profile)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user details", e)
                        Toast.makeText(context, "Error reading profile data", Toast.LENGTH_SHORT).show()
                    }
                },
                { error ->
                    val code = error.networkResponse?.statusCode
                    Log.e(TAG, "Error fetching user: Code=$code", error)

                    if (code == 401) {
                        Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to load user profile", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> {
                    return hashMapOf(
                        "apikey" to SUPABASE_API_KEY,
                        "Authorization" to "Bearer $accessToken",
                        "Accept" to "application/json"
                    )
                }
            }

            Volley.newRequestQueue(requireContext()).add(request)
        }
    }
}