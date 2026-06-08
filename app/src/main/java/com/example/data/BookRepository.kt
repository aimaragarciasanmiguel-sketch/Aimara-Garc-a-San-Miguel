package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class BookRepository(private val bookDao: BookDao) {

    val allProjects: Flow<List<BookProject>> = bookDao.getAllProjectsFlow()

    fun getProjectFlow(id: Int): Flow<BookProject?> = bookDao.getProjectByIdFlow(id)

    suspend fun getProject(id: Int): BookProject? = bookDao.getProjectById(id)

    fun getPagesFlow(projectId: Int): Flow<List<BookPage>> = bookDao.getPagesForProjectFlow(projectId)

    suspend fun getPages(projectId: Int): List<BookPage> = bookDao.getPagesForProject(projectId)

    suspend fun insertProject(project: BookProject): Int {
        return bookDao.insertProject(project).toInt()
    }

    suspend fun updateProject(project: BookProject) {
        bookDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(projectId: Int) {
        val proj = bookDao.getProjectById(projectId)
        if (proj != null) {
            bookDao.deletePagesForProject(projectId)
            bookDao.deleteProject(proj)
        }
    }

    suspend fun insertPage(page: BookPage): Int {
        return bookDao.insertPage(page).toInt()
    }

    suspend fun updatePage(page: BookPage) {
        bookDao.updatePage(page)
    }

    suspend fun deletePage(page: BookPage) {
        bookDao.deletePage(page)
        // Re-number subsequent pages to avoid gaps
        val remainingPages = bookDao.getPagesForProject(page.projectId)
        var count = 1
        for (p in remainingPages) {
            if (p.pageNumber != count) {
                bookDao.updatePage(p.copy(pageNumber = count))
            }
            count++
        }
    }

    suspend fun createPageForProject(projectId: Int): BookPage {
        val currentPages = bookDao.getPagesForProject(projectId)
        val nextNumber = if (currentPages.isEmpty()) 1 else currentPages.maxOf { it.pageNumber } + 1
        val newPage = BookPage(
            projectId = projectId,
            pageNumber = nextNumber,
            elements = emptyList()
        )
        val id = bookDao.insertPage(newPage).toInt()
        return newPage.copy(id = id)
    }

    suspend fun populateSampleIfEmpty() {
        val existing = bookDao.getAllProjectsFlow().firstOrNull()
        if (existing.isNullOrEmpty()) {
            // Create a gorgeous Trade Novel Layout as a default tutorial
            val sampleProject = BookProject(
                title = "Estilo de Novela (Plantilla de Muestra)",
                pageWidthMm = 152.4f,  // 6x9 inches
                pageHeightMm = 228.6f,
                marginMmLeft = 20.0f,
                marginMmRight = 15.0f,
                marginMmTop = 22.0f,
                marginMmBottom = 22.0f,
                bleedMm = 3.175f
            )
            val projectId = bookDao.insertProject(sampleProject).toInt()

            // Page 1: Creative Cover
            val page1 = BookPage(
                projectId = projectId,
                pageNumber = 1,
                elements = listOf(
                    LayoutElement(
                        type = "shape",
                        xMm = 0f,
                        yMm = 0f,
                        widthMm = 152.4f,
                        heightMm = 228.6f,
                        zIndex = 0,
                        shapeType = "RECTANGLE",
                        shapeFillColorHex = "#F8FAFC", // soft offwhite paper texture
                        shapeStrokeColorHex = "#334155",
                        shapeStrokeWidthMm = 2.0f
                    ),
                    LayoutElement(
                        type = "shape",
                        xMm = 10f,
                        yMm = 10f,
                        widthMm = 132.4f,
                        heightMm = 208.6f,
                        zIndex = 1,
                        shapeType = "RECTANGLE",
                        shapeFillColorHex = "#0F172A", // Dark Slate Accent Cover Shape
                        shapeStrokeColorHex = "#64748B",
                        shapeStrokeWidthMm = 0.5f
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 35f,
                        widthMm = 112.4f,
                        heightMm = 45f,
                        zIndex = 2,
                        textContent = "EL ALQUIMISTA DE SUEÑOS",
                        fontSizeSp = 28f,
                        textColorHex = "#F1F5F9",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman",
                        isBold = true,
                        isItalic = false
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 80f,
                        widthMm = 112.4f,
                        heightMm = 15f,
                        zIndex = 3,
                        textContent = "Una novela de fantasía clásica sobre el tiempo",
                        fontSizeSp = 14f,
                        textColorHex = "#94A3B8",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman",
                        isBold = false,
                        isItalic = true
                    ),
                    LayoutElement(
                        type = "shape",
                        xMm = 56.2f,
                        yMm = 110f,
                        widthMm = 40f,
                        heightMm = 40f,
                        zIndex = 4,
                        shapeType = "ELLIPSE",
                        shapeFillColorHex = "#F59E0B", // Golden Sun Ornament
                        shapeStrokeColorHex = "#D97706",
                        shapeStrokeWidthMm = 1.0f
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 175f,
                        widthMm = 112.4f,
                        heightMm = 20f,
                        zIndex = 5,
                        textContent = "AUTOR ADRIÁN SILVA",
                        fontSizeSp = 16f,
                        textColorHex = "#E2E8F0",
                        textAlignment = "CENTER",
                        fontFamily = "Sans-Serif",
                        isBold = true
                    )
                )
            )

            // Page 2: Chapter 1 Page
            val page2 = BookPage(
                projectId = projectId,
                pageNumber = 2,
                elements = listOf(
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 30f,
                        widthMm = 112.4f,
                        heightMm = 15f,
                        zIndex = 1,
                        textContent = "CAPÍTULO I",
                        fontSizeSp = 18f,
                        textColorHex = "#0F172A",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman",
                        isBold = true
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 48f,
                        widthMm = 112.4f,
                        heightMm = 12f,
                        zIndex = 2,
                        textContent = "El Amanecer de Arena",
                        fontSizeSp = 14f,
                        textColorHex = "#475569",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman",
                        isBold = false,
                        isItalic = true
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 65f,
                        widthMm = 112.4f,
                        heightMm = 135f,
                        zIndex = 3,
                        textContent = "El sol se alzaba lentamente sobre las colinas doradas del desierto infinito. Adrián había pasado toda su vida buscando las runas ocultas en los antiguos manuscritos. Scribus fue su guía.\n\nCada página que componía con esmero revelaba un nuevo misterio del cosmos. Sus manos temblaban mientras sostenía el viejo papel, cuyos márgenes perfectos y sangrado de impresión reflejaban un arte antiguo casi olvidado. Este libro era el culmen de su viaje.",
                        fontSizeSp = 12f,
                        textColorHex = "#1E293B",
                        textAlignment = "JUSTIFY",
                        fontFamily = "Times New Roman",
                        isBold = false
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 210f,
                        widthMm = 112.4f,
                        heightMm = 8f,
                        zIndex = 4,
                        textContent = "— 1 —",
                        fontSizeSp = 10f,
                        textColorHex = "#64748B",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman"
                    )
                )
            )

            bookDao.insertPage(page1)
            bookDao.insertPage(page2)
        }
    }
}
