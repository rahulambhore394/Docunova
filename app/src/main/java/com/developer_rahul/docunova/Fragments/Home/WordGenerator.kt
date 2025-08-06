package com.developer_rahul.docunova.Fragments.Home



import android.content.Context
import android.os.Environment
import org.apache.poi.xwpf.usermodel.*
import java.io.File
import java.io.FileOutputStream

object WordGenerator {

    fun generateDocxFile(context: Context, text: String, fileName: String): File {
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Docunova")
        if (!folder.exists()) folder.mkdirs()

        val wordFile = File(folder, "$fileName.docx")
        FileOutputStream(wordFile).use { out ->
            val document = XWPFDocument()

            // Add a paragraph with the extracted text
            val paragraph = document.createParagraph()
            val run = paragraph.createRun()
            run.setText(text)
            run.fontSize = 12

            document.write(out)
            document.close()
        }
        return wordFile
    }
}