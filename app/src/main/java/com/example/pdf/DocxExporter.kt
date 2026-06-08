package com.example.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.BookPage
import com.example.data.BookProject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxExporter {

    // Generates a fully-compliant Word Processing OpenXML Document (.docx) purely offline
    fun exportBookToDocx(
        context: Context,
        project: BookProject,
        pages: List<BookPage>,
        share: Boolean = true
    ): File? {
        if (pages.isEmpty()) {
            Toast.makeText(context, "No hay páginas para exportar.", Toast.LENGTH_SHORT).show()
            return null
        }

        val fileName = "${project.title.replace("\\s+".toRegex(), "_")}_Borrador.docx"
        val outputFile = File(context.cacheDir, fileName)

        try {
            outputFile.createNewFile()
            val fos = FileOutputStream(outputFile)
            val zos = ZipOutputStream(fos)

            // 1. Write [Content_Types].xml
            val contentTypesXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
            """.trimIndent()
            writeZipEntry(zos, "[Content_Types].xml", contentTypesXml)

            // 2. Write _rels/.rels
            val globalRelsXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
            """.trimIndent()
            writeZipEntry(zos, "_rels/.rels", globalRelsXml)

            // 3. Construct word/document.xml with actual layout text contents
            val docXmlContent = buildDocumentXml(project, pages)
            writeZipEntry(zos, "word/document.xml", docXmlContent)

            zos.close()
            fos.close()

            if (share) {
                shareFile(context, outputFile)
            } else {
                Toast.makeText(context, "Borrador de Word guardado en caché: $fileName", Toast.LENGTH_LONG).show()
            }
            return outputFile

        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar archivo Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun buildDocumentXml(project: BookProject, pages: List<BookPage>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n")
        sb.append("  <w:body>\n")

        // Chapter head title
        sb.append("    <w:p>\n")
        sb.append("      <w:pPr>\n")
        sb.append("        <w:jc w:val=\"center\"/>\n")
        sb.append("      </w:pPr>\n")
        sb.append("      <w:r>\n")
        sb.append("        <w:rPr>\n")
        sb.append("          <w:b/>\n")
          // Half-points (36 = 18pt font)
        sb.append("          <w:sz w:val=\"36\"/>\n")
        sb.append("        </w:rPr>\n")
        sb.append("        <w:t>${escapeXml(project.title)}</w:t>\n")
        sb.append("      </w:r>\n")
        sb.append("    </w:p>\n")

        sb.append("    <w:p>\n")
        sb.append("      <w:pPr>\n")
        sb.append("        <w:jc w:val=\"center\"/>\n")
        sb.append("      </w:pPr>\n")
        sb.append("      <w:r>\n")
        sb.append("        <w:rPr>\n")
        sb.append("          <w:i/>\n")
        sb.append("          <w:sz w:val=\"24\"/>\n")
        sb.append("        </w:rPr>\n")
        sb.append("        <w:t>Borrador Editorial de Maqueta</w:t>\n")
        sb.append("      </w:r>\n")
        sb.append("    </w:p>\n")

        // Draw spacer paragraph
        sb.append("    <w:p/>\n")

        // Extract and order elements from each page
        for ((pageIndex, page) in pages.withIndex()) {
            if (pageIndex > 0) {
                // Word Page Break tag element
                sb.append("    <w:p>\n")
                sb.append("      <w:r>\n")
                sb.append("        <w:br w:type=\"page\"/>\n")
                sb.append("      </w:r>\n")
                sb.append("    </w:p>\n")
            }

            // Page label helper heading in draft
            val isLined = project.ruledLinesEnabled && page.pageNumber >= project.ruledLinesStartPage && page.pageNumber <= project.ruledLinesEndPage
            val isA5Limit = project.a5TextLimitEnabled && page.pageNumber >= project.a5TextLimitStartPage && page.pageNumber <= project.a5TextLimitEndPage
            
            val linesSuffix = when {
                isLined -> " [PLANTILLA DE ${project.ruledLinesCount} RENGLONES]"
                isA5Limit -> " [FORMATO A5 - ANCHO 11 CM / ${project.a5TextLimitLines} RENGLONES]"
                else -> ""
            }

            val pageLabelText = if (project.showPageNumbers && page.pageNumber >= project.pageNumbersStartAtPage) {
                val assignedNum = page.pageNumber - project.pageNumbersStartAtPage + project.pageNumbersStartFromValue
                "--- PÁGINA ${page.pageNumber} [NÚM. DE PÁGINA: $assignedNum]$linesSuffix ---"
            } else {
                "--- PÁGINA ${page.pageNumber} [SIN NÚMERA]$linesSuffix ---"
            }

            sb.append("    <w:p>\n")
            sb.append("      <w:pPr>\n")
            sb.append("        <w:pBdr>\n")
            sb.append("          <w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"A0A0A0\"/>\n")
            sb.append("        </w:pBdr>\n")
            sb.append("      </w:pPr>\n")
            sb.append("      <w:r>\n")
            sb.append("        <w:rPr>\n")
            sb.append("          <w:b/>\n")
            sb.append("          <w:color w:val=\"707070\"/>\n")
            sb.append("          <w:sz w:val=\"18\"/>\n")
            sb.append("        </w:rPr>\n")
            sb.append("        <w:t>$pageLabelText</w:t>\n")
            sb.append("      </w:r>\n")
            sb.append("    </w:p>\n")

            // Grab text layout elements sorted inside this page
            val txtElements = page.elements
                .filter { it.type == "text" }
                .sortedWith(compareBy({ it.yMm }, { it.xMm }))

            if (txtElements.isEmpty()) {
                sb.append("    <w:p>\n")
                sb.append("      <w:r>\n")
                sb.append("        <w:rPr>\n")
                sb.append("          <w:i/>\n")
                sb.append("          <w:color w:val=\"999999\"/>\n")
                sb.append("        </w:rPr>\n")
                sb.append("        <w:t>[Página de maqueta sin contenido de marcos de texto]</w:t>\n")
                sb.append("      </w:r>\n")
                sb.append("    </w:p>\n")
            } else {
                for (elem in txtElements) {
                    val lines = elem.textContent.split("\n")
                    for (line in lines) {
                        if (line.trim().isEmpty()) continue

                        val alignVal = when (elem.textAlignment) {
                            "CENTER" -> "center"
                            "RIGHT" -> "right"
                            "JUSTIFY" -> "both"
                            else -> "left"
                        }

                        sb.append("    <w:p>\n")
                        sb.append("      <w:pPr>\n")
                        sb.append("        <w:jc w:val=\"$alignVal\"/>\n")
                        sb.append("      </w:pPr>\n")
                        sb.append("      <w:r>\n")
                        sb.append("        <w:rPr>\n")
                        if (elem.isBold) sb.append("          <w:b/>\n")
                        if (elem.isItalic) sb.append("          <w:i/>\n")
                        
                        // Font selection details mapping
                        val runFont = when (elem.fontFamily) {
                            "Serif" -> "Times New Roman"
                            "Sans-Serif" -> "Arial"
                            "Monospace" -> "Courier New"
                            else -> "Georgia"
                        }
                        sb.append("          <w:rFonts w:ascii=\"$runFont\" w:hAnsi=\"$runFont\"/>\n")
                        
                        // Map color hex
                        val colorVal = elem.textColorHex.trim().removePrefix("#").takeLast(6)
                        sb.append("          <w:color w:val=\"$colorVal\"/>\n")
                        
                        // Font Size mapping (e.g. 12sp -> 24 halfpoints)
                        val sizeVal = (elem.fontSizeSp * 2).toInt()
                        sb.append("          <w:sz w:val=\"$sizeVal\"/>\n")
                        
                        sb.append("        </w:rPr>\n")
                        sb.append("        <w:t>${escapeXml(line)}</w:t>\n")
                        sb.append("      </w:r>\n")
                        sb.append("    </w:p>\n")
                    }
                }
            }

            // Blank spacer
            sb.append("    <w:p/>\n")
        }

        sb.append("  </w:body>\n")
        sb.append("</w:document>")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun shareFile(context: Context, file: File) {
        val fileUri: Uri = FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "Borrador de Libro Word (.docx)")
            putExtra(Intent.EXTRA_TEXT, "Borrador de libro exportado directamente desde el Diseñador Digital.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Compartir Borrador de Word"))
    }
}
