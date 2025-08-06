package com.developer_rahul.docunova.Fragments.Home

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.text.style.AlignmentSpan
import android.text.Layout
import android.graphics.Typeface

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.text.style.LeadingMarginSpan
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.developer_rahul.docunova.R
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.*

class TextExtractorActivity : AppCompatActivity() {

    // UI Components
    private lateinit var container: FrameLayout
    private lateinit var fileNameTextView: TextView
    private lateinit var extractedTextEditText: EditText
    private lateinit var copyButton: Button
    private lateinit var generatePdfButton: Button
    private lateinit var generateWordButton: Button
    private lateinit var formatButton: Button
    private lateinit var translateButton: Button
    private lateinit var saveButton: Button
    private lateinit var languageSpinner: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    // File data
    private var selectedFileUri: Uri? = null
    private lateinit var fileType: String
    private lateinit var originalFileName: String

    // ML Kit components
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val languages = listOf(
        "Afrikaans", "Arabic", "Belarusian", "Bengali", "Bulgarian", "Catalan",
        "Chinese", "Croatian", "Czech", "Danish", "Dutch", "English", "Estonian",
        "Finnish", "French", "German", "Greek", "Gujarati", "Hebrew", "Hindi",
        "Hungarian", "Icelandic", "Indonesian", "Italian", "Japanese", "Kannada",
        "Korean", "Latvian", "Lithuanian", "Macedonian", "Malay", "Malayalam",
        "Marathi", "Nepali", "Norwegian", "Persian", "Polish", "Portuguese",
        "Punjabi", "Romanian", "Russian", "Slovak", "Slovenian", "Spanish",
        "Swahili", "Swedish", "Tamil", "Telugu", "Thai", "Turkish", "Ukrainian",
        "Urdu", "Vietnamese", "Welsh"
    )

    private val languageCodes = mapOf(
        "Afrikaans" to "af",
        "Arabic" to "ar",
        "Belarusian" to "be",
        "Bengali" to "bn",
        "Bulgarian" to "bg",
        "Catalan" to "ca",
        "Chinese" to "zh",
        "Croatian" to "hr",
        "Czech" to "cs",
        "Danish" to "da",
        "Dutch" to "nl",
        "English" to "en",
        "Estonian" to "et",
        "Finnish" to "fi",
        "French" to "fr",
        "German" to "de",
        "Greek" to "el",
        "Gujarati" to "gu",
        "Hebrew" to "he",
        "Hindi" to "hi",
        "Hungarian" to "hu",
        "Icelandic" to "is",
        "Indonesian" to "id",
        "Italian" to "it",
        "Japanese" to "ja",
        "Kannada" to "kn",
        "Korean" to "ko",
        "Latvian" to "lv",
        "Lithuanian" to "lt",
        "Macedonian" to "mk",
        "Malay" to "ms",
        "Malayalam" to "ml",
        "Marathi" to "mr",
        "Nepali" to "ne",
        "Norwegian" to "no",
        "Persian" to "fa",
        "Polish" to "pl",
        "Portuguese" to "pt",
        "Punjabi" to "pa",
        "Romanian" to "ro",
        "Russian" to "ru",
        "Slovak" to "sk",
        "Slovenian" to "sl",
        "Spanish" to "es",
        "Swahili" to "sw",
        "Swedish" to "sv",
        "Tamil" to "ta",
        "Telugu" to "te",
        "Thai" to "th",
        "Turkish" to "tr",
        "Ukrainian" to "uk",
        "Urdu" to "ur",
        "Vietnamese" to "vi",
        "Welsh" to "cy"
    )


    // Translation
    private var translator: Translator? = null

    companion object {
        private const val TAG = "TextExtractorActivity"
        const val REQUEST_DOCUMENT = 101
        private const val MAX_IMAGE_DIMENSION = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_extraction)

        container = findViewById(R.id.container)
        showUploadLayout()
    }

    private fun showUploadLayout() {
        val uploadView = layoutInflater.inflate(R.layout.extract_upload_layout, null)
        container.removeAllViews()
        container.addView(uploadView)

        uploadView.findViewById<LinearLayout>(R.id.uploadDocumentLayout).setOnClickListener {
            openDocumentPicker()
        }

        uploadView.findViewById<LinearLayout>(R.id.captureDocumentLayout).setOnClickListener {
            openCamera()
        }
    }

    private fun showConvertLayout(uri: Uri, mimeType: String?) {
        val convertView = layoutInflater.inflate(R.layout.convert_translate_layout, null)
        container.removeAllViews()
        container.addView(convertView)

        // Initialize UI components
        fileNameTextView = convertView.findViewById(R.id.editTextDocumentName)
        extractedTextEditText = convertView.findViewById(R.id.editTextExtractedText)
        languageSpinner = convertView.findViewById(R.id.spinnerLanguage)
        progressBar = convertView.findViewById(R.id.progressBar)
        progressText = convertView.findViewById(R.id.progressText)
        copyButton = convertView.findViewById(R.id.btnCopyText)
        generatePdfButton = convertView.findViewById(R.id.btnConvertToPDF)
        generateWordButton = convertView.findViewById(R.id.btnConvertToWord)
        formatButton = convertView.findViewById(R.id.btnFormatText)
        translateButton = convertView.findViewById(R.id.btnTranslate)
        saveButton = convertView.findViewById(R.id.btnSave)

        // Set file data
        selectedFileUri = uri
        fileType = when {
            mimeType?.startsWith("image/") == true -> "image"
            mimeType == "application/pdf" -> "pdf"
            else -> "unknown"
        }

        originalFileName = uri.lastPathSegment?.substringBeforeLast('.') ?: "Document"
        fileNameTextView.text = originalFileName

        // Setup language spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages
        )
        languageSpinner.adapter = adapter

        // Set up button listeners
        copyButton.setOnClickListener { copyTextToClipboard() }
        generatePdfButton.setOnClickListener { generatePdfFromExtractedText() }
        generateWordButton.setOnClickListener { generateWordFromExtractedText() }
        formatButton.setOnClickListener { formatExtractedText() }
        translateButton.setOnClickListener { translateText() }
        saveButton.setOnClickListener { saveEditedText() }

        // AUTO-START TEXT EXTRACTION
        startTextExtraction(uri)
    }

    private fun startTextExtraction(uri: Uri) {
        showProgress(true, "Extracting text...")

        lifecycleScope.launch {
            try {
                when (fileType) {
                    "image" -> extractTextFromImage(uri)
                    "pdf" -> extractTextFromPdf(uri)
                    else -> showError("Unsupported file type")
                }
            } catch (e: Exception) {
                showError("Error extracting text: ${e.message}")
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

            runOnUiThread {
                extractedTextEditText.setText(result.text)
            }
        } catch (e: Exception) {
            showError("Failed to process image: ${e.message}")
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
        try {
            val exif = androidx.exifinterface.media.ExifInterface(contentResolver.openInputStream(uri)!!)
            val orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )

            return when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating bitmap", e)
            return bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private suspend fun extractTextFromPdf(uri: Uri) {
        val stringBuilder = StringBuilder()
        val parcelFileDescriptor = try {
            contentResolver.openFileDescriptor(uri, "r")
        } catch (e: IOException) {
            showError("Failed to open PDF: ${e.message}")
            return
        } ?: run {
            showError("Failed to open PDF file")
            return
        }

        try {
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            val pageCount = pdfRenderer.pageCount
            Log.d(TAG, "Processing PDF with $pageCount pages")

            for (i in 0 until pageCount) {
                updateProgress(i + 1, pageCount)
                val page = pdfRenderer.openPage(i)

                try {
                    // Create a scaled bitmap to avoid memory issues
                    val scale = 0.5f // Reduce resolution for better performance
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val text = extractTextFromPdfPage(bitmap)
                    stringBuilder.append(text).append("\n\n")
                    bitmap.recycle()
                } finally {
                    page.close()
                }
            }

            pdfRenderer.close()

            runOnUiThread {
                extractedTextEditText.setText(stringBuilder.toString())
            }
        } catch (e: Exception) {
            showError("Error processing PDF: ${e.message}")
        } finally {
            try {
                parcelFileDescriptor.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing file descriptor", e)
            }
        }
    }

    private suspend fun extractTextFromPdfPage(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = textRecognizer.process(image).await()
            result.text ?: "No text found"
        } catch (e: Exception) {
            "Error extracting text: ${e.message}"
        }
    }

    private fun copyTextToClipboard() {
        val text = extractedTextEditText.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "No text to copy", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Extracted text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun generatePdfFromExtractedText() {
        val text = extractedTextEditText.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "No text to generate PDF", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true, "Generating PDF...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = "$originalFileName.pdf"
                val pdfFile = PdfGenerator.generatePdfFromText(
                    this@TextExtractorActivity,
                    text,
                    fileName
                )

                runOnUiThread {
                    showProgress(false)
                    Toast.makeText(
                        this@TextExtractorActivity,
                        "PDF generated: ${pdfFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    openFile(pdfFile, "application/pdf")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showProgress(false)
                    showError("Failed to generate PDF: ${e.message}")
                }
            }
        }
    }

    private fun generateWordFromExtractedText() {
        val text = extractedTextEditText.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "No text to generate Word document", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true, "Generating Word document...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = "$originalFileName.docx"
                val wordFile = WordGenerator.generateDocxFile(
                    this@TextExtractorActivity,
                    text,
                    fileName
                )

                runOnUiThread {
                    showProgress(false)
                    Toast.makeText(
                        this@TextExtractorActivity,
                        "Word document generated: ${wordFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    openFile(wordFile, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showProgress(false)
                    showError("Failed to generate Word document: ${e.message}")
                }
            }
        }
    }

    private fun formatExtractedText() {
        val rawText = extractedTextEditText.text.toString()
        if (rawText.isBlank()) {
            Toast.makeText(this, "No text to format", Toast.LENGTH_SHORT).show()
            return
        }

        // Step 1: Clean and normalize text
        val cleanedText = rawText
            .replace("\\s+".toRegex(), " ")          // Collapse multiple spaces
            .replace("\\s*\\n\\s*".toRegex(), "\n")  // Clean newlines
            .replace("\n{3,}".toRegex(), "\n\n")     // Reduce multiple newlines
            .trim()

        // Step 2: Split into paragraphs
        val paragraphs = cleanedText.split("\n\n").map { it.trim() }

        val formattedText = SpannableStringBuilder()
        var isFirstParagraph = true

        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) continue

            // Step 3: Determine paragraph type
            val isHeading = isHeading(paragraph)
            val isSubheading = isSubheading(paragraph)
            val isList = isList(paragraph)

            // Step 4: Apply appropriate formatting
            when {
                isHeading -> {
                    if (!isFirstParagraph) formattedText.append("\n\n")
                    formatHeading(formattedText, paragraph)
                }
                isSubheading -> {
                    if (!isFirstParagraph) formattedText.append("\n\n")
                    formatSubheading(formattedText, paragraph)
                }
                isList -> {
                    if (!isFirstParagraph) formattedText.append("\n")
                    formatList(formattedText, paragraph)
                }
                else -> {
                    if (!isFirstParagraph) formattedText.append("\n\n")
                    formatNormalParagraph(formattedText, paragraph)
                }
            }

            isFirstParagraph = false
        }

        // Step 5: Apply formatted text
        extractedTextEditText.setText(formattedText)
        Toast.makeText(this, "Document formatted", Toast.LENGTH_SHORT).show()
    }

    // Heuristic: Heading detection
    private fun isHeading(text: String): Boolean {
        // Short text (1-7 words) that ends without punctuation
        val wordCount = text.split("\\s+".toRegex()).size
        return wordCount in 1..7 && !text.endsWith(".") && !text.endsWith(":")
    }

    // Heuristic: Subheading detection
    private fun isSubheading(text: String): Boolean {
        // Medium length text (3-15 words) ending with colon
        val wordCount = text.split("\\s+".toRegex()).size
        return wordCount in 3..15 && text.endsWith(":")
    }

    // Heuristic: List detection
    private fun isList(text: String): Boolean {
        // Multiple list items or bullet points
        return text.contains("\n•") ||
                text.contains("\n-") ||
                text.contains("\n*") ||
                text.contains("\n1.") ||
                text.contains("\n2.")
    }

    // Formatting functions
    private fun formatHeading(builder: SpannableStringBuilder, text: String) {
        val start = builder.length
        builder.append(text)
        val end = builder.length

        // Apply styles
        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(RelativeSizeSpan(1.4f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun formatSubheading(builder: SpannableStringBuilder, text: String) {
        val start = builder.length
        builder.append(text)
        val end = builder.length

        // Apply styles
        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(RelativeSizeSpan(1.2f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun formatList(builder: SpannableStringBuilder, text: String) {
        // Split list items
        val items = text.split("\n")

        for (item in items) {
            if (item.isBlank()) continue

            val start = builder.length

            // Add bullet point
            if (!item.startsWith("•") && !item.startsWith("-") && !item.startsWith("*")) {
                builder.append("• ")
            }

            builder.append(item.trim())
            builder.append("\n")
            val end = builder.length

            // Apply indent
            builder.setSpan(LeadingMarginSpan.Standard(30, 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun formatNormalParagraph(builder: SpannableStringBuilder, text: String) {
        val start = builder.length
        builder.append(text)
        val end = builder.length

        // Apply paragraph indentation
        builder.setSpan(LeadingMarginSpan.Standard(30, 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Apply normal styling
        builder.setSpan(RelativeSizeSpan(1.0f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(StyleSpan(Typeface.NORMAL), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }


    private fun translateText() {
        val text = extractedTextEditText.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedLanguage = languageSpinner.selectedItem.toString()
        val targetLanguageCode = languageCodes[selectedLanguage] ?: "en"

        showProgress(true, "Detecting language...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Detect source language
                val sourceLanguage = detectLanguage(text)
                Log.d(TAG, "Detected language: $sourceLanguage")

                // 2. Check if translation is needed
                if (sourceLanguage == targetLanguageCode) {
                    runOnUiThread {
                        showProgress(false)
                        Toast.makeText(
                            this@TextExtractorActivity,
                            "Text is already in $selectedLanguage",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                // 3. Create translator
                runOnUiThread {
                    progressText.text = "Preparing translation to $selectedLanguage..."
                }

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLanguage)
                    .setTargetLanguage(targetLanguageCode)
                    .build()

                translator = Translation.getClient(options)

                // 4. Download models if needed
                val conditions = DownloadConditions.Builder()
                    .build() // Remove .requireWifi() to allow cellular downloads

                translator?.downloadModelIfNeeded(conditions)?.await()

                // 5. Perform translation
                runOnUiThread {
                    progressText.text = "Translating to $selectedLanguage..."
                }

                val result = translator?.translate(text)?.await()

                // 6. Show result
                result?.let {
                    runOnUiThread {
                        showProgress(false)
                        extractedTextEditText.setText(it)
                        Toast.makeText(
                            this@TextExtractorActivity,
                            "Text translated to $selectedLanguage",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showProgress(false)
                    showError("Translation failed: ${e.message}")
                }
            } finally {
                // Close the translator to free resources
                translator?.close()
            }
        }
    }

    private suspend fun detectLanguage(text: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Use ML Kit Language Identification
                val languageIdentifier = LanguageIdentification.getClient()
                val detectedLanguage = languageIdentifier.identifyLanguage(text).await()

                // Return detected language or default to English if unknown
                if (detectedLanguage != "und") { // "und" means undetermined
                    detectedLanguage
                } else {
                    "en" // Default to English
                }
            } catch (e: Exception) {
                Log.e(TAG, "Language detection failed", e)
                "en" // Default to English on error
            }
        }
    }

    private fun saveEditedText() {
        val text = extractedTextEditText.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "No text to save", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true, "Saving text...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = "$originalFileName.txt"
                val folder = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Docunova")
                if (!folder.exists()) folder.mkdirs()

                val textFile = File(folder, fileName)
                textFile.writeText(text)

                runOnUiThread {
                    showProgress(false)
                    Toast.makeText(
                        this@TextExtractorActivity,
                        "Text saved: ${textFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    openFile(textFile, "text/plain")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showProgress(false)
                    showError("Failed to save text: ${e.message}")
                }
            }
        }
    }

    private fun openFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        // Verify that there's an app to handle this intent
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
        }
        startActivityForResult(intent, REQUEST_DOCUMENT)
    }

    private fun openCamera() {
        Toast.makeText(this, "Camera feature coming soon!", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DOCUMENT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val mimeType = contentResolver.getType(uri)
                showConvertLayout(uri, mimeType)
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
        textRecognizer.close()
        translator?.close()
    }
}