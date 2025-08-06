package com.developer_rahul.docunova.Fragments.Home

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
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
import com.developer_rahul.docunova.Adapters.RecentFileAdapter
import com.developer_rahul.docunova.ProfileActivity
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.RoomDB.*
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.documentscanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private var scanner: GmsDocumentScanner? = null
    private var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecentFileAdapter
    private lateinit var db: AppDatabase

    // File picker launchers
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { launchTextExtraction(it, "image") }
    }

    private val documentPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { launchTextExtraction(it, "pdf") }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        initScanner()
        setupViews(view)
        setupRecyclerView(view)
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
        val tvStart = view.findViewById<TextView>(R.id.tv_start_translate)
        val tvView = view.findViewById<TextView>(R.id.tv_view_files)
        val profileImage = view.findViewById<ImageView>(R.id.profileImage)
        val layoutCapture = view.findViewById<ImageView>(R.id.iv_scan)

        val options = listOf(tvSelect, tvStart, tvView)
        options.forEach { tv ->
            tv.setOnClickListener {
                options.forEach { it.isSelected = false }
                tv.isSelected = true
                animateSelection(tv)

                // Handle upload button click
                if (tv == tvSelect) {
                    val i = Intent(this@HomeFragment.requireContext(), TextExtractorActivity::class.java)
                    startActivity(i)
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



//    private fun showFileTypeSelection() {
//        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_mode, null)
//
//        val dialog = AlertDialog.Builder(requireContext())
//            .setView(dialogView)
//            .setCancelable(true)
//            .create()
//
//        // Set transparent background and rounded corners
//        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
//        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
//
//        // Handle clicks using MaterialButtons
//        dialogView.findViewById<MaterialButton>(R.id.choose_gallery_button).setOnClickListener {
//            dialog.dismiss()
//            imagePicker.launch("image/*")
//        }
//
//        dialogView.findViewById<MaterialButton>(R.id.take_photo_button).setOnClickListener {
//            dialog.dismiss()
//            documentPicker.launch("application/pdf")
//        }
//
//        dialogView.findViewById<MaterialButton>(R.id.cancel_button).setOnClickListener {
//            dialog.dismiss()
//        }
//
//        dialog.show()
//    }


    @SuppressLint("Range")
    private fun launchTextExtraction(uri: Uri, fileType: String) {
        val fileName = try {
            context?.contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                } else {
                    uri.lastPathSegment?.substringAfterLast('/')
                }
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't get file name", e)
            "Document"
        }

        // Remove file extension if present
        val cleanFileName = fileName.substringBeforeLast('.').takeIf { it.isNotEmpty() } ?: fileName

        // Check if PDF is valid before launching
        if (fileType == "pdf") {
            verifyAndLaunchPdf(uri, cleanFileName)
        } else {
            startTextExtraction(uri, fileType, cleanFileName)
        }
    }

    private fun verifyAndLaunchPdf(uri: Uri, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val parcelFileDescriptor = context?.contentResolver?.openFileDescriptor(uri, "r")
                if (parcelFileDescriptor != null) {
                    val pdfRenderer = PdfRenderer(parcelFileDescriptor)
                    val isValid = pdfRenderer.pageCount > 0
                    pdfRenderer.close()
                    parcelFileDescriptor.close()

                    withContext(Dispatchers.Main) {
                        if (isValid) {
                            startTextExtraction(uri, "pdf", fileName)
                        } else {
                            Toast.makeText(
                                context,
                                "The PDF file is empty or invalid",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Failed to open PDF file",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error opening PDF: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startTextExtraction(uri: Uri, fileType: String, fileName: String) {
        Intent(requireContext(), TextExtractorActivity::class.java).apply {
            putExtra("fileUri", uri)
            putExtra("fileType", fileType)
            putExtra("fileName", fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(this)
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
}