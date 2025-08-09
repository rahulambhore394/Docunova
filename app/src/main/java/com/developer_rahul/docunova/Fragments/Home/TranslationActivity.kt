package com.developer_rahul.docunova.Fragments.Home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.developer_rahul.docunova.R
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class TranslationActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var tabOriginal: TextView
    private lateinit var tabTranslated: TextView
    private lateinit var spinnerLanguages: Spinner
    private lateinit var textViewTranslatedContent: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var textRecognizer: com.google.mlkit.vision.text.TextRecognizer

    private var extractedText = ""
    private var selectedFileUri: Uri? = null
    private var fileType: String = "unknown"
    private var translator: Translator? = null
    private var currentPhotoPath: String? = null

    private val languages = listOf("Select Language", "Hindi", "Marathi", "Spanish", "French", "German", "Tamil", "Gujarati", "Kannada", "Bengali", "Punjabi")
    private val languageCodes = mapOf(
        "Hindi" to "hi",
        "Marathi" to "mr",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Tamil" to "ta",
        "Gujarati" to "gu",
        "Kannada" to "kn",
        "Bengali" to "bn",
        "Punjabi" to "pa"
    )

    companion object {
        private const val TAG = "TranslationActivity"
        const val REQUEST_DOCUMENT = 101
        const val REQUEST_CAMERA_PERMISSION = 201
        private const val MAX_IMAGE_DIMENSION = 1000
    }

    // Camera permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera result launcher
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            selectedFileUri = Uri.fromFile(File(currentPhotoPath))
            startTextExtraction(selectedFileUri!!, "image/jpeg")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translation)

        // Initialize ML Kit text recognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Initialize UI components
        container = findViewById(R.id.container)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        // Set progress bar color
        progressBar.indeterminateDrawable.setColorFilter(
            ContextCompat.getColor(this, R.color.blue),
            PorterDuff.Mode.SRC_IN
        )

        // Check if we have text passed from previous activity
        if (intent.hasExtra("EXTRACTED_TEXT")) {
            extractedText = intent.getStringExtra("EXTRACTED_TEXT") ?: ""
            showTranslationContent()
        } else if (intent.hasExtra("FILE_URI")) {
            // Handle file URI directly
            val uriString = intent.getStringExtra("FILE_URI")
            selectedFileUri = Uri.parse(uriString)
            val mimeType = contentResolver.getType(selectedFileUri!!)
            startTextExtraction(selectedFileUri!!, mimeType)
        } else {
            showUploadLayout()
        }
    }

    private fun showUploadLayout() {
        val uploadView = layoutInflater.inflate(R.layout.layout_upload_files_for_tranlate, null)
        container.removeAllViews()
        container.addView(uploadView)

        // Get reference to the EditText for manual text input
        val editTextManualInput = uploadView.findViewById<EditText>(R.id.etInputText)

        uploadView.findViewById<Button>(R.id.btnGallery).setOnClickListener {
            openDocumentPicker()
        }

        uploadView.findViewById<Button>(R.id.btnCamera).setOnClickListener {
            checkCameraPermission()
        }

        uploadView.findViewById<Button>(R.id.btnProceed).setOnClickListener {
            // Process manually entered text when Proceed button is clicked
            val manualText = editTextManualInput.text.toString().trim()
            if (manualText.isNotEmpty()) {
                extractedText = manualText
                showTranslationContent()
            } else {
                Toast.makeText(this, "Please enter text to translate", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTranslationContent() {
        val contentView = layoutInflater.inflate(R.layout.layout_translation, null)
        container.removeAllViews()
        container.addView(contentView)

        // Initialize UI elements from the contentView
        tabOriginal = contentView.findViewById(R.id.tabOriginal)
        tabTranslated = contentView.findViewById(R.id.tabTranslated)
        spinnerLanguages = contentView.findViewById(R.id.spinnerLanguage)
        textViewTranslatedContent = contentView.findViewById(R.id.editTextTranslatedText)

        // Update the text view
        textViewTranslatedContent.setText(extractedText)

        setupTabs()
        setupSpinner()
    }

    private fun startTextExtraction(uri: Uri, mimeType: String?) {
        showProgress(true, "Extracting text...")

        fileType = when {
            mimeType?.startsWith("image/") == true -> "image"
            mimeType == "application/pdf" -> "pdf"
            else -> "unknown"
        }

        lifecycleScope.launch {
            try {
                when (fileType) {
                    "image" -> extractTextFromImage(uri)
                    else -> {
                        showError("Unsupported file type")
                        showUploadLayout()
                    }
                }
                withContext(Dispatchers.Main) {
                    showTranslationContent()
                }
            } catch (e: Exception) {
                showError("Error extracting text: ${e.message}")
                showUploadLayout()
            } finally {
                showProgress(false)
            }
        }
    }

    private suspend fun extractTextFromImage(uri: Uri) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            // First get image dimensions
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            options.inJustDecodeBounds = false

            // Decode bitmap with proper sampling
            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (bitmap == null) {
                showError("Failed to decode image")
                return
            }

            // Rotate bitmap if needed
            val rotatedBitmap = rotateBitmapIfRequired(bitmap, uri)
            val image = InputImage.fromBitmap(rotatedBitmap, 0)
            val result = textRecognizer.process(image).await()

            extractedText = result.text ?: ""
        } catch (e: Exception) {
            throw IOException("Failed to process image: ${e.message}")
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating bitmap", e)
            bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun setupTabs() {
        val selectedBgOriginal = R.drawable.tab_left_selected
        val selectedBgTranslated = R.drawable.tab_right_selected
        val unselectedBgOriginal = R.drawable.tab_left_unselected
        val unselectedBgTranslated = R.drawable.tab_right_unselected

        fun setActiveTab(selectedTab: TextView, otherTab: TextView, selectedBg: Int, otherBg: Int) {
            selectedTab.setBackgroundResource(selectedBg)
            selectedTab.setTypeface(null, Typeface.BOLD)
            selectedTab.setTextColor(Color.BLACK)

            otherTab.setBackgroundResource(otherBg)
            otherTab.setTypeface(null, Typeface.NORMAL)
            otherTab.setTextColor(Color.BLACK)
        }

        // Default tab selection
        setActiveTab(tabOriginal, tabTranslated, selectedBgOriginal, unselectedBgTranslated)
        spinnerLanguages.visibility = View.GONE

        tabOriginal.setOnClickListener {
            setActiveTab(tabOriginal, tabTranslated, selectedBgOriginal, unselectedBgTranslated)
            spinnerLanguages.visibility = View.GONE
            textViewTranslatedContent.setText(extractedText)
        }

        tabTranslated.setOnClickListener {
            setActiveTab(tabTranslated, tabOriginal, selectedBgTranslated, unselectedBgOriginal)
            spinnerLanguages.visibility = View.VISIBLE
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguages.adapter = adapter

        spinnerLanguages.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedLanguage = languages[position]
                if (selectedLanguage != "Select Language") {
                    translateText(selectedLanguage)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun translateText(selectedLanguage: String) {
        val targetLanguageCode = languageCodes[selectedLanguage] ?: return

        if (extractedText.isBlank()) {
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true, "Translating to $selectedLanguage...")

        // Close previous translator if exists
        translator?.close()

        // Create translator
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLanguageCode)
            .build()

        translator = Translation.getClient(options)

        // Download models if needed
        val conditions = DownloadConditions.Builder()
            .build()

        translator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                // Perform translation
                translator?.translate(extractedText)
                    ?.addOnSuccessListener { translatedText ->
                        showProgress(false)
                        textViewTranslatedContent.setText(translatedText)
                        Toast.makeText(
                            this,
                            "Translated to $selectedLanguage",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    ?.addOnFailureListener { e ->
                        showProgress(false)
                        Toast.makeText(
                            this,
                            "Translation failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            ?.addOnFailureListener { e ->
                showProgress(false)
                Toast.makeText(
                    this,
                    "Model download failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
        }
        startActivityForResult(intent, REQUEST_DOCUMENT)
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e(TAG, "Error creating image file", ex)
                    null
                }

                photoFile?.also {
                    val photoURI = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider", // Must match manifest exactly
                        it
                    )
                    currentPhotoPath = it.absolutePath
                    takePictureLauncher.launch(photoURI)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DOCUMENT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val mimeType = contentResolver.getType(uri)
                selectedFileUri = uri
                startTextExtraction(uri, mimeType)
            }
        }
    }

    private fun updateProgress(currentPage: Int, totalPages: Int) {
        runOnUiThread {
            progressText.text = "Processing page $currentPage of $totalPages"
        }
    }

    private fun showProgress(show: Boolean, message: String = "") {
        runOnUiThread {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            progressText.visibility = if (show) View.VISIBLE else View.GONE
            progressText.text = message
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            Log.e(TAG, message)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
        textRecognizer.close()
    }
}