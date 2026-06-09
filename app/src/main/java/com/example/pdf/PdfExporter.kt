package com.example.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.BookPage
import com.example.data.BookProject
import com.example.data.LayoutElement
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    private const val MM_TO_PT = 2.8346457f // 1 mm = 72 / 25.4 points

    // Generate a professional PDF of the book and share it using FileProvider
    fun exportBookToPdf(
        context: Context,
        project: BookProject,
        pages: List<BookPage>,
        share: Boolean = true
    ): File? {
        if (pages.isEmpty()) {
            Toast.makeText(context, "No hay páginas para exportar.", Toast.LENGTH_SHORT).show()
            return null
        }

        val pdfDocument = PdfDocument()

        val pageWidthPt = (project.pageWidthMm * MM_TO_PT).toInt()
        val pageHeightPt = (project.pageHeightMm * MM_TO_PT).toInt()

        // Page margin offsets (useful if rendering margins or centering, but elements are placed globally)
        val marginL = project.marginMmLeft * MM_TO_PT
        val marginR = project.marginMmRight * MM_TO_PT
        val marginT = project.marginMmTop * MM_TO_PT
        val marginB = project.marginMmBottom * MM_TO_PT

        for (page in pages) {
            // Create a page description
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, page.pageNumber).create()
            // Start a page
            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas = pdfPage.canvas

            // Fill page background color (Scribus base canvas is white/paper)
            canvas.drawColor(Color.WHITE)

            // Draw A5 formatted text length limits (11 cm width / 30 lines) on selected pages
            val isA5LimitActive = project.a5TextLimitEnabled && 
                                  page.pageNumber >= project.a5TextLimitStartPage && 
                                  page.pageNumber <= project.a5TextLimitEndPage

            // Draw horizontal ruled lines template (e.g. 30 lines) if enabled for the page range
            if (project.ruledLinesEnabled && page.pageNumber >= project.ruledLinesStartPage && page.pageNumber <= project.ruledLinesEndPage) {
                val linePaint = Paint().apply {
                    color = Color.parseColor("#E2E8F0") // elegant notebook line color
                    style = Paint.Style.STROKE
                    strokeWidth = 0.75f * MM_TO_PT
                }
                val spaceY = pageHeightPt - marginT - marginB
                val lineSpacing = spaceY / (project.ruledLinesCount + 1)
                for (i in 1..project.ruledLinesCount) {
                    val yLine = marginT + i * lineSpacing
                    canvas.drawLine(marginL, yLine, pageWidthPt - marginR, yLine, linePaint)
                }
            } else if (isA5LimitActive) {
                // If standard lines are not enabled, draw the exactly 30 guidelines representing the 11 cm column!
                val targetWidthMm = project.a5TextLimitWidthMm
                val centeredOffsetMm = (project.pageWidthMm - targetWidthMm) / 2f
                val limitL = centeredOffsetMm * MM_TO_PT
                val limitR = (project.pageWidthMm - centeredOffsetMm) * MM_TO_PT

                val linePaint = Paint().apply {
                    color = Color.parseColor("#F59E0B") // nice soft amber guide line
                    alpha = 60 // semi transparent
                    style = Paint.Style.STROKE
                    strokeWidth = 0.5f * MM_TO_PT
                }
                val spaceY = pageHeightPt - marginT - marginB
                val lineSpacing = spaceY / (project.a5TextLimitLines + 1)
                for (i in 1..project.a5TextLimitLines) {
                    val yLine = marginT + i * lineSpacing
                    canvas.drawLine(limitL, yLine, limitR, yLine, linePaint)
                }

                // Draw vertical boundaries showing 11 cm margins
                val borderPaint = Paint().apply {
                    color = Color.parseColor("#F59E0B")
                    alpha = 80
                    style = Paint.Style.STROKE
                    strokeWidth = 0.75f * MM_TO_PT
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                }
                canvas.drawLine(limitL, 0f, limitL, pageHeightPt.toFloat(), borderPaint)
                canvas.drawLine(limitR, 0f, limitR, pageHeightPt.toFloat(), borderPaint)
            }

            // OPTIONAL: Standard print bleed guide (if desired, but we print elements exactly as positioned)
            // Draw all elements on this page sorted by their zIndex
            val sortedElements = page.elements.sortedWith(compareBy({ it.zIndex }, { it.id }))

            for (element in sortedElements) {
                drawElementOnCanvas(canvas, element)
            }

            // Draw professional page numbering on the safe margin zone
            if (project.showPageNumbers && page.pageNumber >= project.pageNumbersStartAtPage) {
                val pageNumberValue = page.pageNumber - project.pageNumbersStartAtPage + project.pageNumbersStartFromValue
                val text = pageNumberValue.toString()
                
                val textPaint = TextPaint().apply {
                    color = Color.DKGRAY
                    textSize = 10f * 1.3333333f // scale factor matching sp to PDF point size
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }
                
                val textWidth = textPaint.measureText(text)
                val textX = (pageWidthPt - textWidth) / 2f
                // Center vertically inside bottom margin zone
                val textY = pageHeightPt - (marginB / 2f) + (textPaint.textSize / 2f)
                canvas.drawText(text, textX, textY, textPaint)
            }

            // Finish the page
            pdfDocument.finishPage(pdfPage)
        }

        // Save PDF to cache directory
        val fileName = "${project.title.replace("\\s+".toRegex(), "_")}_Layout.pdf"
        val outputFile = File(context.cacheDir, fileName)

        try {
            outputFile.createNewFile()
            val outputStream = FileOutputStream(outputFile)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            if (share) {
                shareFile(context, outputFile)
            } else {
                Toast.makeText(context, "PDF guardado con éxito en caché: $fileName", Toast.LENGTH_LONG).show()
            }
            return outputFile
        } catch (e: Exception) {
            pdfDocument.close()
            Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun drawElementOnCanvas(canvas: Canvas, element: LayoutElement) {
        val x = element.xMm * MM_TO_PT
        val y = element.yMm * MM_TO_PT
        val w = element.widthMm * MM_TO_PT
        val h = element.heightMm * MM_TO_PT

        if (w <= 0f || h <= 0f) return

        canvas.save()
        // Handle rotation if any around element center
        if (element.rotation != 0f) {
            canvas.rotate(element.rotation, x + w / 2, y + h / 2)
        }

        when (element.type) {
            "text" -> {
                // 1. Draw Text Frame background if specified
                if (element.textBackgroundColorHex != "#00000000") {
                    val fillPaint = Paint().apply {
                        color = parseHexColor(element.textBackgroundColorHex)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(x, y, x + w, y + h, fillPaint)
                }

                // 2. Draw Text Frame border/stroke if specified
                if (element.textBorderWidthMm > 0f && element.textBorderColorHex != "#00000000") {
                    val borderPaint = Paint().apply {
                        color = parseHexColor(element.textBorderColorHex)
                        style = Paint.Style.STROKE
                        strokeWidth = element.textBorderWidthMm * MM_TO_PT
                    }
                    canvas.drawRect(x, y, x + w, y + h, borderPaint)
                }

                // 3. Draw Text content inside bounding box
                val typefaceStyle = when {
                    element.isBold && element.isItalic -> Typeface.BOLD_ITALIC
                    element.isBold -> Typeface.BOLD
                    element.isItalic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                val typeface = when (element.fontFamily) {
                    "Times New Roman", "Times News Roman", "Serif" -> Typeface.create(Typeface.SERIF, typefaceStyle)
                    "Sans-Serif" -> Typeface.create(Typeface.SANS_SERIF, typefaceStyle)
                    "Monospace" -> Typeface.create(Typeface.MONOSPACE, typefaceStyle)
                    else -> Typeface.create(Typeface.SERIF, typefaceStyle)
                }

                val textPaint = TextPaint().apply {
                    color = parseHexColor(element.textColorHex)
                    textSize = element.fontSizeSp * 1.3333333f // scale factor from SP to PDF point density
                    this.typeface = typeface
                    isAntiAlias = true
                }

                val alignment = when (element.textAlignment) {
                    "CENTER" -> Layout.Alignment.ALIGN_CENTER
                    "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }

                // Layout padding
                val padding = 4f * MM_TO_PT
                val availableWidth = (w - (padding * 2)).coerceAtLeast(10f)

                val staticLayout = @Suppress("DEPRECATION") StaticLayout(
                    element.textContent,
                    textPaint,
                    availableWidth.toInt(),
                    alignment,
                    1.0f,
                    0.0f,
                    false
                )

                canvas.save()
                canvas.translate(x + padding, y + padding)
                // Clip rendering region inside frame height
                canvas.clipRect(0f, 0f, availableWidth, h - (padding * 2))
                staticLayout.draw(canvas)
                canvas.restore()
            }
            "image" -> {
                // Image frame rendering. Since it's mockup for layout, drawing the Scribus cross matches user's request perfectly!
                val borderPaint = Paint().apply {
                    color = parseHexColor(element.placeholderColorHex)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(x, y, x + w, y + h, borderPaint)

                // Draw solid outer border
                val outerBorderPaint = Paint().apply {
                    color = Color.DKGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }
                canvas.drawRect(x, y, x + w, y + h, outerBorderPaint)

                // Classic Scribus image placeholder diagonal lines
                val crossPaint = Paint().apply {
                    color = Color.GRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 0.5f * MM_TO_PT
                }
                canvas.drawLine(x, y, x + w, y + h, crossPaint)
                canvas.drawLine(x + w, y, x, y + h, crossPaint)

                // Caption text
                val textPaint = TextPaint().apply {
                    color = Color.DKGRAY
                    textSize = 9f * MM_TO_PT
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    isAntiAlias = true
                }
                val text = "MARCO DE IMAGEN"
                val textWidth = textPaint.measureText(text)
                canvas.drawText(text, x + (w - textWidth) / 2, y + (h / 2) + 4f * MM_TO_PT, textPaint)
            }
            "shape" -> {
                val fillPaint = Paint().apply {
                    color = parseHexColor(element.shapeFillColorHex)
                    style = Paint.Style.FILL
                }
                val strokePaint = Paint().apply {
                    color = parseHexColor(element.shapeStrokeColorHex)
                    style = Paint.Style.STROKE
                    strokeWidth = element.shapeStrokeWidthMm * MM_TO_PT
                }

                when (element.shapeType) {
                    "RECTANGLE" -> {
                        canvas.drawRect(x, y, x + w, y + h, fillPaint)
                        canvas.drawRect(x, y, x + w, y + h, strokePaint)
                    }
                    "ELLIPSE" -> {
                        canvas.drawOval(x, y, x + w, y + h, fillPaint)
                        canvas.drawOval(x, y, x + w, y + h, strokePaint)
                    }
                    "LINE" -> {
                        val linePaint = Paint().apply {
                            color = parseHexColor(element.shapeStrokeColorHex)
                            style = Paint.Style.STROKE
                            strokeWidth = element.shapeStrokeWidthMm * MM_TO_PT
                        }
                        canvas.drawLine(x, y, x + w, y + h, linePaint)
                    }
                }
            }
        }

        canvas.restore()
    }

    private fun parseHexColor(hexColor: String): Int {
        return try {
            Color.parseColor(hexColor)
        } catch (e: Exception) {
            Color.BLACK
        }
    }

    fun saveAndSharePdf(context: Context, base64Data: String, projectTitle: String): File? {
        val bytes = try {
            android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al decodificar PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            return null
        }

        val fileName = "${projectTitle.replace("\\s+".toRegex(), "_")}_Flow_Layout.pdf"
        val outputFile = File(context.cacheDir, fileName)

        return try {
            outputFile.createNewFile()
            val outputStream = FileOutputStream(outputFile)
            outputStream.write(bytes)
            outputStream.flush()
            outputStream.close()

            shareFile(context, outputFile)
            outputFile
        } catch (e: Exception) {
            Toast.makeText(context, "Error al guardar PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun shareFile(context: Context, file: File) {
        val fileUri: Uri = FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "Exportación de Maqueta de Libro")
            putExtra(Intent.EXTRA_TEXT, "Aquí está tu PDF profesional para impresión listo para usar.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Compartir PDF de Maqueta"))
    }
}
