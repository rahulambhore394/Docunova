package com.developer_rahul.docunova.Fragments.Home
import android.content.Context
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.TextAlignment
import java.io.File

object PdfGenerator {
    private lateinit var normalFont: com.itextpdf.kernel.font.PdfFont
    private lateinit var boldFont: com.itextpdf.kernel.font.PdfFont
    private lateinit var italicFont: com.itextpdf.kernel.font.PdfFont

    fun generatePdfFromText(context: Context, text: String, fileName: String): File {
        val folder = File(context.getExternalFilesDir(null), "GeneratedPDFs").apply {
            if (!exists()) mkdirs()
        }
        val pdfFile = File(folder, "$fileName.pdf")

        PdfWriter(pdfFile.outputStream()).use { writer ->
            PdfDocument(writer).use { pdfDocument ->
                Document(pdfDocument).apply {
                    // Initialize fonts
                    normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)
                    boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
                    italicFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE)

                    // Document styling
                    setMargins(50f, 50f, 50f, 50f)
                    setFontSize(12f)

                    // Process text while preserving label-value pairs
                    processTextWithLabelValueFormatting(text, this)

                    close()
                }
            }
        }
        return pdfFile
    }

    private fun processTextWithLabelValueFormatting(text: String, document: Document) {
        val lines = text.split("\n")
        var currentLabel: String? = null
        var currentValue = StringBuilder()

        lines.forEach { line ->
            when {
                // Handle label-value pairs (contains colon)
                line.contains(":") -> {
                    // Save previous pair if exists
                    if (currentLabel != null) {
                        addLabelValuePair(document, currentLabel!!, currentValue.toString())
                        currentValue.clear()
                    }

                    // Split into label and value
                    val parts = line.split(":", limit = 2)
                    currentLabel = parts[0].trim()
                    currentValue.append(parts[1].trim())
                }

                // Continuation of previous value (indented line)
                currentLabel != null && line.trim().isNotEmpty() -> {
                    currentValue.append(" ").append(line.trim())
                }

                // Empty line - finalize current pair
                line.isBlank() && currentLabel != null -> {
                    addLabelValuePair(document, currentLabel!!, currentValue.toString())
                    currentLabel = null
                    currentValue.clear()
                    document.add(Paragraph(" ")) // Add empty line
                }

                // Regular text line
                else -> {
                    // Finalize any pending pair
                    if (currentLabel != null) {
                        addLabelValuePair(document, currentLabel!!, currentValue.toString())
                        currentLabel = null
                        currentValue.clear()
                    }
                    document.add(Paragraph(line).setFont(normalFont).setMarginBottom(8f))
                }
            }
        }

        // Add final pending pair
        if (currentLabel != null) {
            addLabelValuePair(document, currentLabel!!, currentValue.toString())
        }
    }

    private fun addLabelValuePair(
        document: Document,
        label: String,
        value: String
    ) {
        val paragraph = Paragraph()
            .setMarginBottom(8f)
            .setFont(normalFont)

        // Add label in bold
        paragraph.add(Text("$label: ").apply {
            setFont(boldFont)
            setFontColor(ColorConstants.BLUE)
        })

        // Add value in normal font
        paragraph.add(Text(value).setFont(normalFont))

        document.add(paragraph)
    }

    // For more complex documents, add these additional formatting methods
    private fun processOtherFormattings(text: String, document: Document) {
        // Handle bullet points, headings etc. (from previous implementation)
        when {
            text.trim().startsWith("- ") || text.trim().startsWith("* ") -> {
                val bulletText = text.trim().substringAfter("- ").substringAfter("* ")
                document.add(
                    Paragraph()
                        .setMarginLeft(20f)
                        .add(Text("• ").setFont(boldFont))
                        .add(Text(bulletText).setFont(normalFont))
                )
            }
            text.trim().startsWith("---") && text.trim().endsWith("---") -> {
                val headingText = text.trim().removeSurrounding("---")
                document.add(
                    Paragraph(headingText)
                        .setFont(boldFont)
                        .setFontSize(14f)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(10f)
                )
            }
            // Add other formatting cases as needed
        }
    }
}