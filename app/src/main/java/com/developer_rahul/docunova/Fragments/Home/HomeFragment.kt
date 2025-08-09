package com.developer_rahul.docunova.Fragments.Home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.Adapters.RecentFileAdapter
import com.developer_rahul.docunova.ProfileActivity
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.RoomDB.*
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.documentscanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private val SUPABASE_URL = "https://biudcywgygbacfxfpuva.supabase.co"
    private val SUPABASE_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJpdWRjeXdneWdiYWNmeGZwdXZhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM1Mzg1NDQsImV4cCI6MjA2OTExNDU0NH0.enJZKTQjKtOyB6VU5pYo_vf4p7ZLv2ayYuyqBWvLqbA"

    private var scanner: GmsDocumentScanner? = null
    private var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecentFileAdapter
    private lateinit var db: AppDatabase

    // UI elements for user info
    private lateinit var tv_Username: TextView
    private lateinit var profileImage: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        // Initialize UI references here for user info
        tv_Username = view.findViewById(R.id.tv_Username)
        profileImage = view.findViewById(R.id.profileImage_home)

        initScanner()
        setupViews(view)
        setupRecyclerView(view)

        fetchUserDetails()  // Fetch user profile from Supabase Auth
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

    private fun savePdfToDocunovaFolder(pdfUri: Uri, fileName: String, thumbnailUri: Uri) {
        val folder = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Docunova")
        if (!folder.exists()) folder.mkdirs()

        val file = File(folder, "$fileName.pdf")
        try {
            val inputStream = requireContext().contentResolver.openInputStream(pdfUri)
            val outputStream = FileOutputStream(file)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val recentFile = RecentFile(
                name = fileName,
                filePath = file.absolutePath,
                thumbnailUri = thumbnailUri.toString(),
                date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
            )

            lifecycleScope.launch(Dispatchers.IO) {
                db.recentFileDao().insert(recentFile)
                Log.d(TAG, "Saved file: ${file.absolutePath}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving PDF", e)
            Toast.makeText(context, "Failed to save scanned PDF", Toast.LENGTH_SHORT).show()
        }
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

                if (tv == tvSelect) {
                    startActivity(Intent(requireContext(), TypoProcessingActivity::class.java))
                } else if (tv == tvOpenTranslate) {
                    startActivity(Intent(requireContext(), TranslationActivity::class.java))
                }
            }
        }

        layoutCapture.setOnClickListener {
            scanDocument()
        }

        profileImage.setOnClickListener {
            it.animateClick()
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.recentFilesRecycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = RecentFileAdapter(emptyList())
        recyclerView.adapter = adapter

        db.recentFileDao().getAllFiles().observe(viewLifecycleOwner) { recentFiles ->
            adapter.updateFiles(recentFiles)
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

    private fun View.animateClick() {
        this.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            this.animate().scaleX(1f).scaleY(1f).duration = 100
        }
    }

    private fun getDocunovaFolder(): File {
        val folder = File(requireContext().getExternalFilesDir(null), "Docunova")
        if (!folder.exists()) folder.mkdirs()
        return folder
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

                val file = File(getDocunovaFolder(), "$enteredName.pdf")
                if (file.exists()) {
                    input.error = "A file with this name already exists"
                    return@setOnClickListener
                }

                savePdfToDocunovaFolder(pdfUri, enteredName, thumbnailUri)
                dialog.dismiss()
            }
        }

        dialog.show()
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
            // Load from SharedPreferences saved during Google login
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

            Log.d(TAG, "Loaded Google user: $fullName, $avatarUrl")
            return
        }

        if (loginMethod == "email") {
            if (accessToken.isNullOrEmpty()) {
                tv_Username.text = "Session Expired"
                profileImage.setImageResource(R.drawable.ic_profile)
                Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                return
            }

            val url = "$SUPABASE_URL/auth/v1/user"
            val request = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                { response ->
                    try {
                        Log.d(TAG, "User details response: $response")

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
                    val body = error.networkResponse?.data?.let { String(it) }
                    Log.e(TAG, "Error fetching user: Code=$code, Body=$body", error)

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
