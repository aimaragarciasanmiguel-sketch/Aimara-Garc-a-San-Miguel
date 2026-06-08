package com.example.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.api.GeminiClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = BookRepository(db.bookDao())

    // --- State Observables ---
    val projects: StateFlow<List<BookProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProjectId = MutableStateFlow<Int?>(null)
    val selectedProjectId: StateFlow<Int?> = _selectedProjectId.asStateFlow()

    // Active project state
    val activeProject: StateFlow<BookProject?> = _selectedProjectId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getProjectFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Pages list for the active project
    val activeProjectPages: StateFlow<List<BookPage>> = _selectedProjectId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getPagesFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPageIndex = MutableStateFlow(0)
    val selectedPageIndex: StateFlow<Int> = _selectedPageIndex.asStateFlow()

    // Compute active page from list and index
    val activePage: StateFlow<BookPage?> = combine(activeProjectPages, _selectedPageIndex) { pages, index ->
        if (pages.isEmpty()) null
        else pages.getOrNull(index.coerceIn(0, pages.size - 1)) ?: pages.getOrNull(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Currently selected layout element inside the active page
    private val _selectedElementId = MutableStateFlow<String?>(null)
    val selectedElementId: StateFlow<String?> = _selectedElementId.asStateFlow()

    val selectedElement: StateFlow<LayoutElement?> = combine(activePage, _selectedElementId) { page, elementId ->
        if (page == null || elementId == null) null
        else page.elements.find { it.id == elementId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- AI Generator States ---
    private val _aiGenerationLoading = MutableStateFlow(false)
    val aiGenerationLoading: StateFlow<Boolean> = _aiGenerationLoading.asStateFlow()

    private val _aiGenerationError = MutableStateFlow<String?>(null)
    val aiGenerationError: StateFlow<String?> = _aiGenerationError.asStateFlow()

    // --- Auto-Save States ---
    private val _lastAutoSavedTime = MutableStateFlow<String?>(null)
    val lastAutoSavedTime: StateFlow<String?> = _lastAutoSavedTime.asStateFlow()

    // --- Undo/Redo States ---
    data class HistoryState(
        val pageId: Int,
        val elements: List<LayoutElement>,
        val selectedElementId: String?
    )

    private val undoStack = mutableListOf<HistoryState>()
    private val redoStack = mutableListOf<HistoryState>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var activeHistoryTransactionElementId: String? = null

    private fun updateUndoRedoStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun recordStateBeforeAltering(page: BookPage) {
        undoStack.add(
            HistoryState(
                pageId = page.id,
                elements = page.elements,
                selectedElementId = _selectedElementId.value
            )
        )
        if (undoStack.size > 100) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        updateUndoRedoStates()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val page = activePage.value ?: return
        
        val previousState = undoStack.removeAt(undoStack.size - 1)
        val currentState = HistoryState(
            pageId = page.id,
            elements = page.elements,
            selectedElementId = _selectedElementId.value
        )
        redoStack.add(currentState)
        
        activeHistoryTransactionElementId = null
        updateUndoRedoStates()
        
        viewModelScope.launch {
            val pages = activeProjectPages.value
            val targetIndex = pages.indexOfFirst { it.id == previousState.pageId }
            if (targetIndex != -1) {
                _selectedPageIndex.value = targetIndex
            }
            
            val targetPage = pages.find { it.id == previousState.pageId }
            if (targetPage != null) {
                repository.updatePage(targetPage.copy(elements = previousState.elements))
                _selectedElementId.value = previousState.selectedElementId
            }
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val page = activePage.value ?: return
        
        val nextState = redoStack.removeAt(redoStack.size - 1)
        val currentState = HistoryState(
            pageId = page.id,
            elements = page.elements,
            selectedElementId = _selectedElementId.value
        )
        undoStack.add(currentState)
        
        activeHistoryTransactionElementId = null
        updateUndoRedoStates()
        
        viewModelScope.launch {
            val pages = activeProjectPages.value
            val targetIndex = pages.indexOfFirst { it.id == nextState.pageId }
            if (targetIndex != -1) {
                _selectedPageIndex.value = targetIndex
            }
            
            val targetPage = pages.find { it.id == nextState.pageId }
            if (targetPage != null) {
                repository.updatePage(targetPage.copy(elements = nextState.elements))
                _selectedElementId.value = nextState.selectedElementId
            }
        }
    }

    init {
        // Populate database with elegant sample Novel layout on first run
        viewModelScope.launch {
            repository.populateSampleIfEmpty()
        }

        // Load last stored auto-save timestamp from sharedPreferences if any
        try {
            val sharedPrefs = application.getSharedPreferences("ScribusLocalStorage", android.content.Context.MODE_PRIVATE)
            _lastAutoSavedTime.value = sharedPrefs.getString("last_saved_timestamp", null)
        } catch (e: Exception) {
            // ignore
        }

        startAutoSaveLoop()
    }

    private fun startAutoSaveLoop() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30000) // every 30 seconds
                performAutoSave()
            }
        }
    }

    fun performAutoSave() {
        val proj = activeProject.value ?: return
        val currentPages = activeProjectPages.value
        if (currentPages.isEmpty()) return

        try {
            val sharedPrefs = getApplication<Application>().getSharedPreferences("ScribusLocalStorage", android.content.Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()

            val moshi = com.squareup.moshi.Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()

            val projectAdapter = moshi.adapter(BookProject::class.java)
            val pagesType = com.squareup.moshi.Types.newParameterizedType(List::class.java, BookPage::class.java)
            val pagesAdapter = moshi.adapter<List<BookPage>>(pagesType)

            val projJson = projectAdapter.toJson(proj)
            val pagesJson = pagesAdapter.toJson(currentPages)

            editor.putInt("last_saved_project_id", proj.id)
            editor.putString("last_saved_project_json_${proj.id}", projJson)
            editor.putString("last_saved_pages_json_${proj.id}", pagesJson)
            
            // Also store general globally last saved state for easier general restore
            editor.putString("last_saved_project_json_global", projJson)
            editor.putString("last_saved_pages_json_global", pagesJson)

            val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val timeString = formatter.format(java.util.Date())
            editor.putString("last_saved_timestamp", timeString)
            editor.apply()

            _lastAutoSavedTime.value = timeString
            android.util.Log.d("EditorViewModel", "Auto-saved project ${proj.id} to localStorage (SharedPreferences) at $timeString")
        } catch (e: Exception) {
            android.util.Log.e("EditorViewModel", "Failed to auto-save to localStorage: ${e.message}", e)
        }
    }

    fun restoreLastAutoSaved(projectId: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val sharedPrefs = getApplication<Application>().getSharedPreferences("ScribusLocalStorage", android.content.Context.MODE_PRIVATE)
                val projJson = sharedPrefs.getString("last_saved_project_json_$projectId", null)
                val pagesJson = sharedPrefs.getString("last_saved_pages_json_$projectId", null)

                if (projJson != null && pagesJson != null) {
                    val moshi = com.squareup.moshi.Moshi.Builder()
                        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                        .build()

                    val projectAdapter = moshi.adapter(BookProject::class.java)
                    val pagesType = com.squareup.moshi.Types.newParameterizedType(List::class.java, BookPage::class.java)
                    val pagesAdapter = moshi.adapter<List<BookPage>>(pagesType)

                    val restoredProj = projectAdapter.fromJson(projJson)
                    val restoredPages = pagesAdapter.fromJson(pagesJson)

                    if (restoredProj != null && restoredPages != null) {
                        // 1. Update project in database
                        repository.updateProject(restoredProj)
                        
                        // 2. Clear current pages and insert/update restored pages
                        val currentDbPages = repository.getPages(projectId)
                        for (p in currentDbPages) {
                            repository.deletePage(p)
                        }
                        for (p in restoredPages) {
                            repository.insertPage(p.copy(id = 0)) // Insert as new or match DB IDs
                        }
                        
                        // Switch page index to 0 to be safe
                        selectPage(0)
                        onComplete()
                        android.util.Log.d("EditorViewModel", "Successfully restored project $projectId from auto-save backup.")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "Failed to restore last auto-saved session: ${e.message}", e)
            }
        }
    }

    // --- Action Methods ---

    fun selectProject(projectId: Int?) {
        _selectedProjectId.value = projectId
        _selectedPageIndex.value = 0
        _selectedElementId.value = null
        undoStack.clear()
        redoStack.clear()
        activeHistoryTransactionElementId = null
        updateUndoRedoStates()
    }

    fun selectPage(index: Int) {
        _selectedPageIndex.value = index
        _selectedElementId.value = null // clear selection when switching page
        activeHistoryTransactionElementId = null
    }

    fun selectElement(elementId: String?) {
        _selectedElementId.value = elementId
        activeHistoryTransactionElementId = null
    }

    // --- Project Operations ---

    fun createNewProject(
        title: String,
        width: Float,
        height: Float,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        bleed: Float
    ) {
        viewModelScope.launch {
            val newProj = BookProject(
                title = title,
                pageWidthMm = width,
                pageHeightMm = height,
                marginMmLeft = left,
                marginMmRight = right,
                marginMmTop = top,
                marginMmBottom = bottom,
                bleedMm = bleed
            )
            val newId = repository.insertProject(newProj)
            
            // Create Page 1 automatically for the project
            repository.createPageForProject(newId)
            
            // Switch selection
            selectProject(newId)
        }
    }

    fun deleteProject(projectId: Int) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (_selectedProjectId.value == projectId) {
                selectProject(null)
            }
        }
    }

    fun updateProjectSettings(project: BookProject) {
        viewModelScope.launch {
            repository.updateProject(project)
        }
    }

    // --- Page Operations ---

    fun addNewPage() {
        val projId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            val newPage = repository.createPageForProject(projId)
            val pages = repository.getPages(projId)
            val index = pages.indexOfFirst { it.id == newPage.id }
            if (index != -1) {
                selectPage(index)
            }
        }
    }

    fun removeCurrentPage() {
        val page = activePage.value ?: return
        val pages = activeProjectPages.value
        if (pages.size <= 1) return // Do not delete the last page
        viewModelScope.launch {
            repository.deletePage(page)
            val nextIndex = (_selectedPageIndex.value - 1).coerceAtLeast(0)
            selectPage(nextIndex)
        }
    }

    // --- Layout Element Operations ---

    fun addElementToActivePageWithPosition(type: String, xMm: Float, yMm: Float, widthMm: Float? = null, heightMm: Float? = null) {
        val page = activePage.value ?: return
        val project = activeProject.value ?: return

        activeHistoryTransactionElementId = null
        recordStateBeforeAltering(page)

        val newElement = when (type) {
            "text" -> LayoutElement(
                type = "text",
                xMm = xMm,
                yMm = yMm,
                widthMm = widthMm ?: (project.pageWidthMm - project.marginMmLeft - project.marginMmRight - 10f).coerceAtLeast(50f),
                heightMm = heightMm ?: 40f,
                textContent = "Agrega tu texto aquí...",
                fontSizeSp = 12f,
                fontFamily = "Times New Roman"
            )
            "image" -> LayoutElement(
                type = "image",
                xMm = xMm,
                yMm = yMm,
                widthMm = widthMm ?: 60f,
                heightMm = heightMm ?: 65f,
                isPlaceholder = true
            )
            "shape" -> LayoutElement(
                type = "shape",
                xMm = xMm,
                yMm = yMm,
                widthMm = widthMm ?: 45f,
                heightMm = heightMm ?: 45f,
                shapeType = "RECTANGLE",
                shapeFillColorHex = "#CBD5E1",
                shapeStrokeColorHex = "#475569"
            )
            else -> return
        }

        val updatedElements = page.elements + newElement
        viewModelScope.launch {
            repository.updatePage(page.copy(elements = updatedElements))
            _selectedElementId.value = newElement.id // Auto-select newly created item!
        }
    }

    fun addElementToActivePage(type: String) {
        val page = activePage.value ?: return
        val project = activeProject.value ?: return

        activeHistoryTransactionElementId = null
        recordStateBeforeAltering(page)

        // Make reasonable starting coordinates inside the margins!
        val startX = project.marginMmLeft + 5f
        val startY = project.marginMmTop + 5f

        val newElement = when (type) {
            "text" -> LayoutElement(
                type = "text",
                xMm = startX,
                yMm = startY,
                widthMm = (project.pageWidthMm - project.marginMmLeft - project.marginMmRight - 10f).coerceAtLeast(50f),
                heightMm = 40f,
                textContent = "Agrega tu texto aquí...",
                fontSizeSp = 12f,
                fontFamily = "Times New Roman"
            )
            "image" -> LayoutElement(
                type = "image",
                xMm = startX,
                yMm = startY,
                widthMm = 60f,
                heightMm = 65f,
                isPlaceholder = true
            )
            "shape" -> LayoutElement(
                type = "shape",
                xMm = startX,
                yMm = startY,
                widthMm = 45f,
                heightMm = 45f,
                shapeType = "RECTANGLE",
                shapeFillColorHex = "#CBD5E1",
                shapeStrokeColorHex = "#475569"
            )
            else -> return
        }

        val updatedElements = page.elements + newElement
        viewModelScope.launch {
            repository.updatePage(page.copy(elements = updatedElements))
            _selectedElementId.value = newElement.id // Auto-select newly created item!
        }
    }

    fun updateElementInActivePage(updatedElement: LayoutElement) {
        val page = activePage.value ?: return
        if (activeHistoryTransactionElementId != updatedElement.id) {
            recordStateBeforeAltering(page)
            activeHistoryTransactionElementId = updatedElement.id
        }
        val updatedElements = page.elements.map {
            if (it.id == updatedElement.id) updatedElement else it
        }
        viewModelScope.launch {
            repository.updatePage(page.copy(elements = updatedElements))
        }
    }

    fun updateElementPositionAndSize(elementId: String, x: Float, y: Float, w: Float, h: Float) {
        val page = activePage.value ?: return
        if (activeHistoryTransactionElementId != elementId) {
            recordStateBeforeAltering(page)
            activeHistoryTransactionElementId = elementId
        }
        val updatedElements = page.elements.map {
            if (it.id == elementId) {
                it.copy(
                    xMm = x,
                    yMm = y,
                    widthMm = w.coerceAtLeast(5f),
                    heightMm = h.coerceAtLeast(5f)
                )
            } else it
        }
        viewModelScope.launch {
            repository.updatePage(page.copy(elements = updatedElements))
        }
    }

    fun removeSelectedElement() {
        val page = activePage.value ?: return
        val elementId = _selectedElementId.value ?: return
        activeHistoryTransactionElementId = null
        recordStateBeforeAltering(page)
        val updatedElements = page.elements.filterNot { it.id == elementId }
        viewModelScope.launch {
            repository.updatePage(page.copy(elements = updatedElements))
            _selectedElementId.value = null
        }
    }

    fun changeElementZIndex(step: Int) {
        val page = activePage.value ?: return
        val elementId = _selectedElementId.value ?: return
        activeHistoryTransactionElementId = null
        recordStateBeforeAltering(page)
        val updatedElements = page.elements.map {
            if (it.id == elementId) {
                it.copy(zIndex = (it.zIndex + step).coerceIn(-10, 50))
            } else it
        }
        viewModelScope.launch {
            repository.updatePage(page.copy(elements = updatedElements))
        }
    }

    // --- AI Integration Action ---

    fun generateTextForActiveFrame(prompt: String) {
        val currentElement = selectedElement.value ?: return
        if (currentElement.type != "text") return
        val page = activePage.value ?: return

        _aiGenerationLoading.value = true
        _aiGenerationError.value = null

        viewModelScope.launch {
            try {
                val generatedText = GeminiClient.generateFillerText(prompt)
                if (generatedText.startsWith("Error")) {
                    _aiGenerationError.value = generatedText
                } else {
                    activeHistoryTransactionElementId = null
                    recordStateBeforeAltering(page)
                    // Update active text element text content!
                    val updated = currentElement.copy(
                        textContent = generatedText
                    )
                    updateElementInActivePage(updated)
                }
            } catch (e: Exception) {
                _aiGenerationError.value = "Excepción: ${e.localizedMessage}"
            } finally {
                _aiGenerationLoading.value = false
            }
        }
    }

    fun clearAiError() {
        _aiGenerationError.value = null
    }

    fun clearActiveProjectState() {
        selectProject(null)
    }
}
