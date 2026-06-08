package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookPage
import com.example.data.BookProject
import com.example.data.LayoutElement
import com.example.pdf.DocxExporter
import com.example.pdf.PdfExporter
import com.example.ui.state.EditorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val project by viewModel.activeProject.collectAsState()
    val pages by viewModel.activeProjectPages.collectAsState()
    val activePageIndex by viewModel.selectedPageIndex.collectAsState()
    val activePage by viewModel.activePage.collectAsState()
    val selectedElement by viewModel.selectedElement.collectAsState()

    val aiLoading by viewModel.aiGenerationLoading.collectAsState()
    val aiError by viewModel.aiGenerationError.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    var showAiAssistantDialog by remember { mutableStateOf(false) }
    var showPrintPreviewDialog by remember { mutableStateOf(false) }
    var showMarginSidebar by remember { mutableStateOf(false) }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val activeProject = project!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeProject.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${activeProject.pageWidthMm}x${activeProject.pageHeightMm} mm • Sangrado: ${activeProject.bleedMm} mm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            val lastAutoSaved by viewModel.lastAutoSavedTime.collectAsState()
                            if (lastAutoSaved != null) {
                                Text(
                                    text = "• Auto-guardado ($lastAutoSaved)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF16A34A),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("lbl_last_autosaved_time")
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_home")) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    // Undo Button
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        modifier = Modifier.testTag("btn_undo")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Deshacer",
                            tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        modifier = Modifier.testTag("btn_redo")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Rehacer",
                            tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    // Margin Sidebar Toggle Button
                    IconButton(
                        onClick = { showMarginSidebar = !showMarginSidebar },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("btn_toggle_margin_sidebar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Ajustar Márgenes",
                            tint = if (showMarginSidebar) MaterialTheme.colorScheme.primary else Color(0xFF1C1B1F)
                        )
                    }

                    // Print Preview button
                    Button(
                        onClick = { showPrintPreviewDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("btn_print_preview")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vista Previa", fontWeight = FontWeight.Bold)
                    }

                    // Export draft Word docx button
                    Button(
                        onClick = {
                            DocxExporter.exportBookToDocx(context, activeProject, pages, share = true)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("btn_export_docx")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Borrador Word (.docx)", fontWeight = FontWeight.Bold)
                    }

                    // Export professional PDF button
                    Button(
                        onClick = {
                            PdfExporter.exportBookToPdf(context, activeProject, pages, share = true)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_export_pdf")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar PDF", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xDDFDF8FD),
                    titleContentColor = Color(0xFF1C1B1F),
                    navigationIconContentColor = Color(0xFF1C1B1F)
                )
            )
        },
        bottomBar = {
            // Main Bottom Control Tray: quick add elements and page navigators styled with "Frosted Glass"
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xDDFDF8FD) // Translucent light lavender cream
                ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.65f),
                                Color.White.copy(alpha = 0.15f)
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding() // Ensure safe area gesture protection on narrow screens
                        .padding(bottom = 16.dp, top = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    // Pages Navigator list
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Páginas: ",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.widthIn(max = 240.dp)
                            ) {
                                itemsIndexed(pages) { index, page ->
                                    val isSelected = index == activePageIndex
                                    Button(
                                        onClick = { viewModel.selectPage(index) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("btn_page_${index + 1}")
                                    ) {
                                        Text("${index + 1}")
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { viewModel.addNewPage() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_add_page")
                            ) {
                                Icon(Icons.Default.NoteAdd, contentDescription = "Agregar página")
                            }

                            IconButton(
                                onClick = {
                                    if (pages.size > 1) {
                                        viewModel.removeCurrentPage()
                                    } else {
                                        Toast.makeText(context, "No puedes eliminar la única página.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_delete_page")
                            ) {
                                Icon(
                                    Icons.Default.ContentPasteGo,
                                    contentDescription = "Eliminar página actual",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Elements inserter tools
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Añadir:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        FilledTonalButton(
                            onClick = { viewModel.addElementToActivePage("text") },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("btn_add_text_frame")
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Texto", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { viewModel.addElementToActivePage("image") },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("btn_add_image_frame")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Imagen", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { viewModel.addElementToActivePage("shape") },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("btn_add_shape")
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Figura", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF3EDF7)) // Beautiful lavender workspace background!
        ) {
            // Sribus workspace container (Row supporting split sidebar margin adjustments)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (activePage != null) {
                        ScribusCanvas(
                            project = activeProject,
                            page = activePage!!,
                            selectedElementId = selectedElement?.id,
                            onElementSelected = { viewModel.selectElement(it) },
                            onElementMoved = { element, x, y, w, h ->
                                viewModel.updateElementPositionAndSize(element.id, x, y, w, h)
                            },
                            onProjectUpdated = { updatedProj ->
                                viewModel.updateProjectSettings(updatedProj)
                            },
                            onAddElementAt = { type, x, y ->
                                viewModel.addElementToActivePageWithPosition(type, x, y)
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay páginas activas.", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                // Smoothly animated modern margin adjusters sidebar
                AnimatedVisibility(
                    visible = showMarginSidebar,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    MarginSidebarPanel(
                        project = activeProject,
                        viewModel = viewModel,
                        onUpdateProject = { viewModel.updateProjectSettings(it) },
                        onClose = { showMarginSidebar = false }
                    )
                }
            }

            // Element settings and coordinates drawer list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .background(Color(0xDDFDF8FD)) // Frosted glass soft lavender cream container
                    .border(1.dp, Color.White.copy(alpha = 0.45f))
            ) {
                if (selectedElement != null) {
                    ElementPropertiesTray(
                        element = selectedElement!!,
                        project = activeProject,
                        onUpdate = { viewModel.updateElementInActivePage(it) },
                        onDelete = { viewModel.removeSelectedElement() },
                        onMoveLayer = { viewModel.changeElementZIndex(it) },
                        onTriggerAiHelper = { showAiAssistantDialog = true }
                    )
                } else {
                    EmptyTrayView(
                        project = activeProject,
                        onUpdateProject = { viewModel.updateProjectSettings(it) }
                    )
                }
            }
        }

        if (showAiAssistantDialog && selectedElement != null && selectedElement!!.type == "text") {
            GeminiAiTextAssistantDialog(
                onDismiss = { showAiAssistantDialog = false },
                onGenerate = { prompt ->
                    viewModel.generateTextForActiveFrame(prompt)
                    showAiAssistantDialog = false
                },
                isLoading = aiLoading,
                error = aiError,
                onClearError = { viewModel.clearAiError() }
            )
        }

        if (showPrintPreviewDialog) {
            PrintPreviewDialog(
                project = activeProject,
                pages = pages,
                initialPageIndex = activePageIndex,
                onDismiss = { showPrintPreviewDialog = false }
            )
        }
    }
}

// Visual Scribus Interactive Canvas
@Composable
fun ScribusCanvas(
    project: BookProject,
    page: BookPage,
    selectedElementId: String?,
    onElementSelected: (String?) -> Unit,
    onElementMoved: (LayoutElement, Float, Float, Float, Float) -> Unit,
    onProjectUpdated: (BookProject) -> Unit,
    onAddElementAt: (String, Float, Float) -> Unit
) {
    var gridSnapSizeMm by remember { mutableStateOf(5f) } // default 5mm snapping size. 0f to turn off.
    
    // Drag-and-drop template state
    var activeDraggingPaletteType by remember { mutableStateOf<String?>(null) }
    var dragOffsetInWorkspace by remember { mutableStateOf(Offset.Zero) }
    var isCurrentlyDraggingPalette by remember { mutableStateOf(false) }

    // Smart Guide snaps
    var dragActiveXMm by remember { mutableStateOf<Float?>(null) }
    var dragActiveYMm by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Clicking clear space deselects elements
                detectDragGestures(
                    onDragStart = { onElementSelected(null) },
                    onDrag = { _, _ -> },
                    onDragEnd = {}
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val maxW = maxWidth.value
        val maxH = maxHeight.value

        // Add 10% spacing padding around the page canvas for outer bleed visual guides!
        val scaleX = (maxW / (project.pageWidthMm + (project.bleedMm * 2))) * 0.92f
        val scaleY = (maxH / (project.pageHeightMm + (project.bleedMm * 2))) * 0.92f
        val mmToDp = scaleX.coerceAtMost(scaleY)

        val pageW = project.pageWidthMm * mmToDp
        val pageH = project.pageHeightMm * mmToDp
        val bleed = project.bleedMm * mmToDp

        // Floating Top Controller Panel (incorporates Drag-and-Drop + Grid Snapping Toggle)
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp, bottom = 12.dp)
                .zIndex(100f) // Keep it well on top of the list of frames!
                .testTag("canvas_floating_toolbox"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Drag Templates
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Arrastrar al lienzo:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Draggable Text Element template
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        activeDraggingPaletteType = "text"
                                        isCurrentlyDraggingPalette = true
                                        // Place the ghost box directly in the middle-top area of container at start
                                        val startX = (maxW / 2f) - 60f
                                        val startY = 40f
                                        dragOffsetInWorkspace = Offset(startX, startY)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetInWorkspace = dragOffsetInWorkspace + dragAmount
                                    },
                                    onDragEnd = {
                                        isCurrentlyDraggingPalette = false
                                        val dropType = activeDraggingPaletteType
                                        if (dropType != null) {
                                            val offsetPageX = (maxW - pageW) / 2f
                                            val offsetPageY = (maxH - pageH) / 2f
                                            
                                            // The ghost width is 120dp, height is 45dp, so center the drop reference
                                            val dropXInDp = dragOffsetInWorkspace.x + 60f
                                            val dropYInDp = dragOffsetInWorkspace.y + 22.5f
                                            
                                            var dropXMm = (dropXInDp - offsetPageX) / mmToDp
                                            var dropYMm = (dropYInDp - offsetPageY) / mmToDp
                                            
                                            dropXMm = dropXMm.coerceIn(0f, project.pageWidthMm - 50f)
                                            dropYMm = dropYMm.coerceIn(0f, project.pageHeightMm - 20f)
                                            
                                            if (gridSnapSizeMm > 0f) {
                                                dropXMm = kotlin.math.round(dropXMm / gridSnapSizeMm) * gridSnapSizeMm
                                                dropYMm = kotlin.math.round(dropYMm / gridSnapSizeMm) * gridSnapSizeMm
                                            }
                                            
                                            onAddElementAt(dropType, dropXMm, dropYMm)
                                        }
                                        activeDraggingPaletteType = null
                                    },
                                    onDragCancel = {
                                        isCurrentlyDraggingPalette = false
                                        activeDraggingPaletteType = null
                                    }
                                )
                            }
                            .clickable { 
                                // Direct touch tap fallback support for tablet/devices!
                                val startX = project.marginMmLeft + 5f
                                val startY = project.marginMmTop + 5f
                                onAddElementAt("text", startX, startY)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("drag_template_text")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Texto", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // Draggable Image Element template
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        activeDraggingPaletteType = "image"
                                        isCurrentlyDraggingPalette = true
                                        val startX = (maxW / 2f) + 15f
                                        val startY = 40f
                                        dragOffsetInWorkspace = Offset(startX, startY)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetInWorkspace = dragOffsetInWorkspace + dragAmount
                                    },
                                    onDragEnd = {
                                        isCurrentlyDraggingPalette = false
                                        val dropType = activeDraggingPaletteType
                                        if (dropType != null) {
                                            val offsetPageX = (maxW - pageW) / 2f
                                            val offsetPageY = (maxH - pageH) / 2f
                                            
                                            // Ghost width is 80dp, height is 80dp, center the drop reference
                                            val dropXInDp = dragOffsetInWorkspace.x + 40f
                                            val dropYInDp = dragOffsetInWorkspace.y + 40f
                                            
                                            var dropXMm = (dropXInDp - offsetPageX) / mmToDp
                                            var dropYMm = (dropYInDp - offsetPageY) / mmToDp
                                            
                                            dropXMm = dropXMm.coerceIn(0f, project.pageWidthMm - 40f)
                                            dropYMm = dropYMm.coerceIn(0f, project.pageHeightMm - 40f)
                                            
                                            if (gridSnapSizeMm > 0f) {
                                                dropXMm = kotlin.math.round(dropXMm / gridSnapSizeMm) * gridSnapSizeMm
                                                dropYMm = kotlin.math.round(dropYMm / gridSnapSizeMm) * gridSnapSizeMm
                                            }
                                            
                                            onAddElementAt(dropType, dropXMm, dropYMm)
                                        }
                                        activeDraggingPaletteType = null
                                    },
                                    onDragCancel = {
                                        isCurrentlyDraggingPalette = false
                                        activeDraggingPaletteType = null
                                    }
                                )
                            }
                            .clickable {
                                val startX = project.marginMmLeft + 5f
                                val startY = project.marginMmTop + 5f
                                onAddElementAt("image", startX, startY)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("drag_template_image")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Imagen", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                
                Divider(modifier = Modifier.height(20.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
                
                // Alignment grid controllers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (gridSnapSizeMm > 0f) Icons.Default.GridOn else Icons.Default.GridOff,
                        contentDescription = null,
                        tint = if (gridSnapSizeMm > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Ajuste Rejilla:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    
                    listOf(
                        0f to "OFF",
                        1f to "1mm",
                        2f to "2mm",
                        5f to "5mm",
                        10f to "10mm"
                    ).forEach { (size, label) ->
                        val isSelected = gridSnapSizeMm == size
                        FilterChip(
                            selected = isSelected,
                            onClick = { 
                                gridSnapSizeMm = size
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("chip_grid_$label")
                        )
                    }
                }
            }
        }

        // Ghost Drag-and-drop placeholder visualizer (overlay following hand cursor finger)
        if (isCurrentlyDraggingPalette && activeDraggingPaletteType != null) {
            Box(
                modifier = Modifier
                    .offset(x = dragOffsetInWorkspace.x.dp, y = dragOffsetInWorkspace.y.dp)
                    .size(
                        width = if (activeDraggingPaletteType == "text") 120.dp else 80.dp,
                        height = if (activeDraggingPaletteType == "text") 45.dp else 80.dp
                    )
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .zIndex(150f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (activeDraggingPaletteType == "text") Icons.Default.TextFields else Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Colocar",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Outer layout board (draws bleed boundary card)
        Box(
            modifier = Modifier
                .size(width = (pageW + bleed * 2).dp, height = (pageH + bleed * 2).dp)
                .background(Color(0xFFE2D9EB)) // elegant workspace companion color
                .border(0.5.dp, Color.Red.copy(alpha = 0.4f)), // red bleed boundary
            contentAlignment = Alignment.Center
        ) {
            // Wrapper box of exact page size that holds Rulers and Print Canvas together
            Box(
                modifier = Modifier.size(width = pageW.dp, height = pageH.dp),
                contentAlignment = Alignment.TopStart
            ) {
                // Rulers System (Horizontal, Vertical, Top-Left intersection block)
                if (project.rulersEnabled) {
                    val rulerThickness = 18.dp
                    val tickColor = Color.Black.copy(alpha = 0.25f)
                    val labelColor = Color.Black.copy(alpha = 0.6f)
                    val rulerBg = Color(0xFFF8FAFC) // crisp clean slate white
                    val borderStrokeColor = Color(0xFFCBD5E1) // clean gray border

                    // Corner intersection box: Top-Left
                    Box(
                        modifier = Modifier
                            .offset(x = (-18).dp, y = (-18).dp)
                            .size(rulerThickness)
                            .background(rulerBg)
                            .border(0.5.dp, borderStrokeColor)
                    ) {
                        Text(
                            text = "mm",
                            color = labelColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Horizontal Ruler (Top)
                    Canvas(
                        modifier = Modifier
                            .offset(x = 0.dp, y = (-18).dp)
                            .size(width = pageW.dp, height = rulerThickness)
                            .background(rulerBg)
                            .border(0.5.dp, borderStrokeColor)
                    ) {
                        val widthMm = project.pageWidthMm.toInt()
                        
                        // Thin bottom baseline
                        drawLine(
                            color = borderStrokeColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )

                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(165, 30, 41, 59)
                            textSize = 7.5.dp.toPx()
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }

                        for (m in 0..widthMm) {
                            val xOffset = m * mmToDp.dp.toPx()
                            if (xOffset > size.width) break

                            when {
                                m % 10 == 0 -> {
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(xOffset, size.height - 9.dp.toPx()),
                                        end = Offset(xOffset, size.height),
                                        strokeWidth = 1.2f.dp.toPx()
                                    )
                                    val cmVal = (m / 10).toString()
                                    drawContext.canvas.nativeCanvas.drawText(
                                        cmVal,
                                        xOffset,
                                        size.height - 10.dp.toPx(),
                                        textPaint
                                    )
                                }
                                m % 5 == 0 -> {
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(xOffset, size.height - 5.dp.toPx()),
                                        end = Offset(xOffset, size.height),
                                        strokeWidth = 1f.dp.toPx()
                                    )
                                }
                                else -> {
                                    if (mmToDp > 2f) {
                                        drawLine(
                                            color = tickColor.copy(alpha = 0.15f),
                                            start = Offset(xOffset, size.height - 3.dp.toPx()),
                                            end = Offset(xOffset, size.height),
                                            strokeWidth = 0.7f.dp.toPx()
                                        )
                                    }
                                }
                            }
                        }

                        // Blue alignment indicators for margins on horizontal ruler
                        val marginL = project.marginMmLeft * mmToDp.dp.toPx()
                        val marginR = (project.pageWidthMm - project.marginMmRight) * mmToDp.dp.toPx()

                        drawRect(
                            color = Color(0xFF0284C7).copy(alpha = 0.4f),
                            topLeft = Offset(marginL - 1.dp.toPx(), size.height - 14.dp.toPx()),
                            size = Size(2.dp.toPx(), 14.dp.toPx())
                        )
                        drawRect(
                            color = Color(0xFF0284C7).copy(alpha = 0.4f),
                            topLeft = Offset(marginR - 1.dp.toPx(), size.height - 14.dp.toPx()),
                            size = Size(2.dp.toPx(), 14.dp.toPx())
                        )
                    }

                    // Vertical Ruler (Left)
                    Canvas(
                        modifier = Modifier
                            .offset(x = (-18).dp, y = 0.dp)
                            .size(width = rulerThickness, height = pageH.dp)
                            .background(rulerBg)
                            .border(0.5.dp, borderStrokeColor)
                    ) {
                        val heightMm = project.pageHeightMm.toInt()

                        // Thin right baseline
                        drawLine(
                            color = borderStrokeColor,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )

                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(165, 30, 41, 59)
                            textSize = 7.5.dp.toPx()
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.RIGHT
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }

                        for (m in 0..heightMm) {
                            val yOffset = m * mmToDp.dp.toPx()
                            if (yOffset > size.height) break

                            when {
                                m % 10 == 0 -> {
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(size.width - 9.dp.toPx(), yOffset),
                                        end = Offset(size.width, yOffset),
                                        strokeWidth = 1.2f.dp.toPx()
                                    )
                                    val cmVal = (m / 10).toString()
                                    drawContext.canvas.nativeCanvas.drawText(
                                        cmVal,
                                        size.width - 10.dp.toPx(),
                                        yOffset + 3.dp.toPx(),
                                        textPaint
                                    )
                                }
                                m % 5 == 0 -> {
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(size.width - 5.dp.toPx(), yOffset),
                                        end = Offset(size.width, yOffset),
                                        strokeWidth = 1f.dp.toPx()
                                    )
                                }
                                else -> {
                                    if (mmToDp > 2f) {
                                        drawLine(
                                            color = tickColor.copy(alpha = 0.15f),
                                            start = Offset(size.width - 3.dp.toPx(), yOffset),
                                            end = Offset(size.width, yOffset),
                                            strokeWidth = 0.7f.dp.toPx()
                                        )
                                    }
                                }
                            }
                        }

                        // Blue alignment indicators for margins on vertical ruler
                        val marginT = project.marginMmTop * mmToDp.dp.toPx()
                        val marginB = (project.pageHeightMm - project.marginMmBottom) * mmToDp.dp.toPx()

                        drawRect(
                            color = Color(0xFF0284C7).copy(alpha = 0.4f),
                            topLeft = Offset(size.width - 14.dp.toPx(), marginT - 1.dp.toPx()),
                            size = Size(14.dp.toPx(), 2.dp.toPx())
                        )
                        drawRect(
                            color = Color(0xFF0284C7).copy(alpha = 0.4f),
                            topLeft = Offset(size.width - 14.dp.toPx(), marginB - 1.dp.toPx()),
                            size = Size(14.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }

                // Actual print paper canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(2.dp))
                        .background(Color.White)
                        .border(1.3.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                        .testTag("print_page_canvas")
                ) {
                    // Background Dot Grid Overlay conforming dynamically to selected snap size
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val spacingMm = if (gridSnapSizeMm > 0f) gridSnapSizeMm else 5f
                        val dotSpacing = spacingMm * mmToDp.dp.toPx()
                        val dotRadius = 0.9.dp.toPx()
                        var x = dotSpacing
                        while (x < size.width) {
                            var y = dotSpacing
                            while (y < size.height) {
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.05f),
                                    radius = dotRadius,
                                    center = Offset(x, y)
                                )
                                y += dotSpacing
                            }
                            x += dotSpacing
                        }
                    }

                    // 1. Draw Margins guidelines (Dashed blue lines matching standard Scribus/InDesign styles)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val marginL = project.marginMmLeft * mmToDp
                        val marginR = project.marginMmRight * mmToDp
                        val marginT = project.marginMmTop * mmToDp
                        val marginB = project.marginMmBottom * mmToDp

                        val strokeWidth = 1.3f.dp.toPx()
                        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        val marginColor = Color(0xFF0284C7)

                        // Left Margin
                        drawLine(
                            color = marginColor,
                            start = Offset(marginL, 0f),
                            end = Offset(marginL, size.height),
                            strokeWidth = strokeWidth,
                            pathEffect = pathEffect
                        )
                        // Right Margin
                        drawLine(
                            color = marginColor,
                            start = Offset(size.width - marginR, 0f),
                            end = Offset(size.width - marginR, size.height),
                            strokeWidth = strokeWidth,
                            pathEffect = pathEffect
                        )
                        // Top Margin
                        drawLine(
                            color = marginColor,
                            start = Offset(0f, marginT),
                            end = Offset(size.width, marginT),
                            strokeWidth = strokeWidth,
                            pathEffect = pathEffect
                        )
                        // Bottom Margin
                        drawLine(
                            color = marginColor,
                            start = Offset(0f, size.height - marginB),
                            end = Offset(size.width, size.height - marginB),
                            strokeWidth = strokeWidth,
                            pathEffect = pathEffect
                        )

                        // Render horizontal ruled lines template (e.g. 30 lines) inside the page range
                        if (project.ruledLinesEnabled && page.pageNumber >= project.ruledLinesStartPage && page.pageNumber <= project.ruledLinesEndPage) {
                            val spaceY = size.height - marginT - marginB
                            val lineSpacing = spaceY / (project.ruledLinesCount + 1)
                            for (i in 1..project.ruledLinesCount) {
                                val yLine = marginT + i * lineSpacing
                                drawLine(
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.35f), // stylish lavender rules guideline
                                    start = Offset(marginL, yLine),
                                    end = Offset(size.width - marginR, yLine),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }

                        // Render A5 guidelines if active
                        val isA5LimitActive = project.a5TextLimitEnabled && 
                                              page.pageNumber >= project.a5TextLimitStartPage && 
                                              page.pageNumber <= project.a5TextLimitEndPage
                        if (isA5LimitActive) {
                            val targetWidthMm = project.a5TextLimitWidthMm
                            val centeredOffsetMm = (project.pageWidthMm - targetWidthMm) / 2f
                            val limitL = centeredOffsetMm * mmToDp
                            val limitR = (project.pageWidthMm - centeredOffsetMm) * mmToDp

                            val limitStrokeWidth = 1.5f.dp.toPx()
                            val limitPathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 5f), 0f)
                            
                            // Left vertical restriction line
                            drawLine(
                                color = Color(0xFFD97706), // warm amber format guide
                                start = Offset(limitL, 0f),
                                end = Offset(limitL, size.height),
                                strokeWidth = limitStrokeWidth,
                                pathEffect = limitPathEffect
                            )
                            // Right vertical restriction line
                            drawLine(
                                color = Color(0xFFD97706), // warm amber format guide
                                start = Offset(limitR, 0f),
                                end = Offset(limitR, size.height),
                                strokeWidth = limitStrokeWidth,
                                pathEffect = limitPathEffect
                            )

                            // Shading
                            drawRect(
                                color = Color(0xFFD97706).copy(alpha = 0.04f),
                                topLeft = Offset(0f, 0f),
                                size = Size(limitL, size.height)
                            )
                            drawRect(
                                color = Color(0xFFD97706).copy(alpha = 0.04f),
                                topLeft = Offset(limitR, 0f),
                                size = Size(size.width - limitR, size.height)
                            )

                            if (!project.ruledLinesEnabled) {
                                val spaceY = size.height - marginT - marginB
                                val lineSpacing = spaceY / (project.a5TextLimitLines + 1)
                                for (i in 1..project.a5TextLimitLines) {
                                    val yLine = marginT + i * lineSpacing
                                    drawLine(
                                        color = Color(0xFFD97706).copy(alpha = 0.25f), // clear amber lines
                                        start = Offset(limitL, yLine),
                                        end = Offset(limitR, yLine),
                                        strokeWidth = 0.8f.dp.toPx()
                                    )
                                }
                            }
                        }

                        // Smart Snapping Alignment Guides (Visual feedback rendered when snap holds)
                        if (dragActiveXMm != null) {
                            val guideX = dragActiveXMm!! * mmToDp
                            drawLine(
                                color = Color(0xFF8B5CF6).copy(alpha = 0.7f), // elegant violet smart snaps
                                start = Offset(guideX, 0f),
                                end = Offset(guideX, size.height),
                                strokeWidth = 1.2f.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        }
                        if (dragActiveYMm != null) {
                            val guideY = dragActiveYMm!! * mmToDp
                            drawLine(
                                color = Color(0xFF8B5CF6).copy(alpha = 0.7f),
                                start = Offset(0f, guideY),
                                end = Offset(size.width, guideY),
                                strokeWidth = 1.2f.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        }
                    }

                    // 1b. Render decorative dynamic page number
                    if (project.showPageNumbers && page.pageNumber >= project.pageNumbersStartAtPage) {
                        val pageNumberValue = page.pageNumber - project.pageNumbersStartAtPage + project.pageNumbersStartFromValue
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = ((project.marginMmBottom / 2) * mmToDp).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pageNumberValue.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray,
                                fontFamily = FontFamily.Serif,
                                modifier = Modifier.testTag("canvas_page_number")
                            )
                        }
                    }

                    // 1c. Interactive Drag-and-Drop Margins Guidelines (Drag on-canvas lines to resize margins instantly!)
                    val strokeColor = Color(0xFF0284C7)
                    val marginL = project.marginMmLeft * mmToDp
                    val marginR = project.marginMmRight * mmToDp
                    val marginT = project.marginMmTop * mmToDp
                    val marginB = project.marginMmBottom * mmToDp

                    // LEFT Margin Visual Handler
                    Box(
                        modifier = Modifier
                            .offset(x = (marginL - 8).dp, y = 0.dp)
                            .fillMaxHeight()
                            .width(16.dp)
                            .pointerInput(project) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val deltaMm = dragAmount.x / mmToDp
                                        var targetVal = project.marginMmLeft + deltaMm
                                        targetVal = targetVal.coerceIn(5f, project.pageWidthMm / 2f - 10f)
                                        if (gridSnapSizeMm > 0f) {
                                            targetVal = kotlin.math.round(targetVal / gridSnapSizeMm) * gridSnapSizeMm
                                        }
                                        onProjectUpdated(project.copy(marginMmLeft = targetVal.coerceAtLeast(5f)))
                                    }
                                )
                            }
                            .testTag("guide_margin_left")
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(16.dp, 24.dp)
                                .background(strokeColor, RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Ajustar margen izquierdo",
                                tint = Color.White,
                                modifier = Modifier
                                    .rotate(90f)
                                    .size(12.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    // RIGHT Margin Visual Handler
                    Box(
                        modifier = Modifier
                            .offset(x = (pageW - marginR - 8).dp, y = 0.dp)
                            .fillMaxHeight()
                            .width(16.dp)
                            .pointerInput(project) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val deltaMm = dragAmount.x / mmToDp
                                        // Increasing pixel coordinate moves handle to right (reducing margin distance)
                                        var targetVal = project.marginMmRight - deltaMm
                                        targetVal = targetVal.coerceIn(5f, project.pageWidthMm / 2f - 10f)
                                        if (gridSnapSizeMm > 0f) {
                                            targetVal = kotlin.math.round(targetVal / gridSnapSizeMm) * gridSnapSizeMm
                                        }
                                        onProjectUpdated(project.copy(marginMmRight = targetVal.coerceAtLeast(5f)))
                                    }
                                )
                            }
                            .testTag("guide_margin_right")
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(16.dp, 24.dp)
                                .background(strokeColor, RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Ajustar margen derecho",
                                tint = Color.White,
                                modifier = Modifier
                                    .rotate(90f)
                                    .size(12.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    // TOP Margin Visual Handler
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = (marginT - 8).dp)
                            .fillMaxWidth()
                            .height(16.dp)
                            .pointerInput(project) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val deltaMm = dragAmount.y / mmToDp
                                        var targetVal = project.marginMmTop + deltaMm
                                        targetVal = targetVal.coerceIn(5f, project.pageHeightMm / 2f - 10f)
                                        if (gridSnapSizeMm > 0f) {
                                            targetVal = kotlin.math.round(targetVal / gridSnapSizeMm) * gridSnapSizeMm
                                        }
                                        onProjectUpdated(project.copy(marginMmTop = targetVal.coerceAtLeast(5f)))
                                    }
                                )
                            }
                            .testTag("guide_margin_top")
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp, 16.dp)
                                .background(strokeColor, RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Ajustar margen superior",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    // BOTTOM Margin Visual Handler
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = (pageH - marginB - 8).dp)
                            .fillMaxWidth()
                            .height(16.dp)
                            .pointerInput(project) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val deltaMm = dragAmount.y / mmToDp
                                        var targetVal = project.marginMmBottom - deltaMm
                                        targetVal = targetVal.coerceIn(5f, project.pageHeightMm / 2f - 10f)
                                        if (gridSnapSizeMm > 0f) {
                                            targetVal = kotlin.math.round(targetVal / gridSnapSizeMm) * gridSnapSizeMm
                                        }
                                        onProjectUpdated(project.copy(marginMmBottom = targetVal.coerceAtLeast(5f)))
                                    }
                                )
                            }
                            .testTag("guide_margin_bottom")
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp, 16.dp)
                                .background(strokeColor, RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Ajustar margen inferior",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    // 2. Render all layout elements ordered by zIndex
                    val sortedElements = page.elements.sortedWith(compareBy({ it.zIndex }, { it.id }))
                    sortedElements.forEach { element ->
                        val isSelected = element.id == selectedElementId
                        val elemX = element.xMm * mmToDp
                        val elemY = element.yMm * mmToDp
                        val elemW = element.widthMm * mmToDp
                        val elemH = element.heightMm * mmToDp

                        Box(
                            modifier = Modifier
                                .offset(x = elemX.dp, y = elemY.dp)
                                .size(width = elemW.dp, height = elemH.dp)
                                .let {
                                    if (isSelected) {
                                        it.border(1.8.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                                    } else it
                                }
                                .clickable { onElementSelected(element.id) }
                                .pointerInput(element) {
                                    detectDragGestures(
                                        onDragStart = { 
                                            onElementSelected(element.id)
                                            dragActiveXMm = element.xMm
                                            dragActiveYMm = element.yMm
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            
                                            val rawXMm = element.xMm + (dragAmount.x / mmToDp)
                                            val rawYMm = element.yMm + (dragAmount.y / mmToDp)
                                            
                                            var snappedXMm = rawXMm
                                            var snappedYMm = rawYMm
                                            
                                            // 1. Apply grid snap
                                            if (gridSnapSizeMm > 0f) {
                                                snappedXMm = kotlin.math.round(snappedXMm / gridSnapSizeMm) * gridSnapSizeMm
                                                snappedYMm = kotlin.math.round(snappedYMm / gridSnapSizeMm) * gridSnapSizeMm
                                            }
                                            
                                            // 2. Apply page margins snapping magnet
                                            val snapThresholdMm = 3.5f
                                            
                                            // Left Margin Snap
                                            if (kotlin.math.abs(snappedXMm - project.marginMmLeft) < snapThresholdMm) {
                                                snappedXMm = project.marginMmLeft
                                            }
                                            // Right Margin Snap
                                            val rightBoundMm = project.pageWidthMm - project.marginMmRight
                                            if (kotlin.math.abs((snappedXMm + element.widthMm) - rightBoundMm) < snapThresholdMm) {
                                                snappedXMm = rightBoundMm - element.widthMm
                                            }
                                            
                                            // Top Margin Snap
                                            if (kotlin.math.abs(snappedYMm - project.marginMmTop) < snapThresholdMm) {
                                                snappedYMm = project.marginMmTop
                                            }
                                            // Bottom Margin Snap
                                            val bottomBoundMm = project.pageHeightMm - project.marginMmBottom
                                            if (kotlin.math.abs((snappedYMm + element.heightMm) - bottomBoundMm) < snapThresholdMm) {
                                                snappedYMm = bottomBoundMm - element.heightMm
                                            }
                                            
                                            // Update smart snap indicators
                                            dragActiveXMm = snappedXMm
                                            dragActiveYMm = snappedYMm
                                            
                                            onElementMoved(
                                                element,
                                                snappedXMm.coerceIn(0f, project.pageWidthMm - element.widthMm),
                                                snappedYMm.coerceIn(0f, project.pageHeightMm - element.heightMm),
                                                element.widthMm,
                                                element.heightMm
                                            )
                                        },
                                        onDragEnd = {
                                            dragActiveXMm = null
                                            dragActiveYMm = null
                                        },
                                        onDragCancel = {
                                            dragActiveXMm = null
                                            dragActiveYMm = null
                                        }
                                    )
                                }
                                .testTag("canvas_element_${element.id}")
                        ) {
                            // Render element visually
                            when (element.type) {
                                "text" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (element.textBackgroundColorHex != "#00000000") Color(
                                                    android.graphics.Color.parseColor(element.textBackgroundColorHex)
                                                ) else Color.Transparent
                                            )
                                            .let {
                                                if (element.textBorderWidthMm > 0f && element.textBorderColorHex != "#00000000") {
                                                    it.border(
                                                        (element.textBorderWidthMm * mmToDp / 3).dp.coerceAtLeast(1.dp),
                                                        Color(android.graphics.Color.parseColor(element.textBorderColorHex))
                                                    )
                                                } else it
                                            }
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = element.textContent,
                                            fontSize = (element.fontSizeSp * (mmToDp / 3.5f)).coerceAtLeast(6f).sp,
                                            lineHeight = (element.fontSizeSp * element.lineHeightMultiplier * (mmToDp / 3.5f)).coerceAtLeast(6f).sp,
                                            color = Color(android.graphics.Color.parseColor(element.textColorHex)),
                                            fontWeight = if (element.isBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (element.isItalic) FontStyle.Italic else FontStyle.Normal,
                                            fontFamily = when (element.fontFamily) {
                                                "Times New Roman", "Times News Roman", "Serif" -> FontFamily.Serif
                                                "Sans-Serif" -> FontFamily.SansSerif
                                                "Monospace" -> FontFamily.Monospace
                                                else -> FontFamily.Serif
                                            },
                                            textAlign = when (element.textAlignment) {
                                                "CENTER" -> TextAlign.Center
                                                "RIGHT" -> TextAlign.Right
                                                "JUSTIFY" -> TextAlign.Justify
                                                else -> TextAlign.Left
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                "image" -> {
                                    if (element.imageUrl.isNotEmpty() && !element.isPlaceholder) {
                                        androidx.compose.foundation.Image(
                                            painter = coil.compose.rememberAsyncImagePainter(model = element.imageUrl),
                                            contentDescription = "Imagen de usuario",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(android.graphics.Color.parseColor(element.placeholderColorHex)))
                                                .border(0.5.dp, Color.DarkGray),
                                            contentScale = if (element.imageScaleType == "FIT") {
                                                androidx.compose.ui.layout.ContentScale.Fit
                                            } else {
                                                androidx.compose.ui.layout.ContentScale.Crop
                                            }
                                        )
                                    } else {
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(android.graphics.Color.parseColor(element.placeholderColorHex)))
                                                .border(0.5.dp, Color.DarkGray)
                                        ) {
                                            // Cross placeholder drawings
                                            drawLine(
                                                color = Color.Gray,
                                                start = Offset(0f, 0f),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 1f
                                            )
                                            drawLine(
                                                color = Color.Gray,
                                                start = Offset(size.width, 0f),
                                                end = Offset(0f, size.height),
                                                strokeWidth = 1f
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "M. Imagen",
                                                color = Color.DarkGray,
                                                fontSize = (element.widthMm * 0.12f * mmToDp).coerceAtLeast(6f).sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                }
                                "shape" -> {
                                    when (element.shapeType) {
                                        "RECTANGLE" -> {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Color(
                                                            android.graphics.Color.parseColor(
                                                                element.shapeFillColorHex
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        (element.shapeStrokeWidthMm * mmToDp / 3).dp.coerceAtLeast(
                                                            1.dp
                                                        ),
                                                        Color(
                                                            android.graphics.Color.parseColor(
                                                                element.shapeStrokeColorHex
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                        "ELLIPSE" -> {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawOval(
                                                    color = Color(
                                                        android.graphics.Color.parseColor(
                                                            element.shapeFillColorHex
                                                        )
                                                    ),
                                                    size = size
                                                )
                                                drawOval(
                                                    color = Color(
                                                        android.graphics.Color.parseColor(
                                                            element.shapeStrokeColorHex
                                                        )
                                                    ),
                                                    size = size,
                                                    style = Stroke(width = element.shapeStrokeWidthMm * mmToDp / 3)
                                                )
                                            }
                                        }
                                        "LINE" -> {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawLine(
                                                    color = Color(
                                                        android.graphics.Color.parseColor(
                                                            element.shapeStrokeColorHex
                                                        )
                                                    ),
                                                    start = Offset(0f, 0f),
                                                    end = Offset(size.width, size.height),
                                                    strokeWidth = element.shapeStrokeWidthMm * mmToDp / 3
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Resize anchor grab indicator on selection
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(15.dp)
                                        .offset(x = 4.dp, y = 4.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .border(1.2.dp, Color.White, CircleShape)
                                        .pointerInput(element) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                var newW = (element.widthMm + (dragAmount.x / mmToDp)).coerceAtLeast(5f)
                                                
                                                if (gridSnapSizeMm > 0f) {
                                                    newW = kotlin.math.round(newW / gridSnapSizeMm) * gridSnapSizeMm
                                                }
                                                
                                                val newH = if (element.isAspectLocked) {
                                                    val ratio = if (element.heightMm > 0f) element.widthMm / element.heightMm else 1f
                                                    newW / ratio
                                                } else {
                                                    var rawH = (element.heightMm + (dragAmount.y / mmToDp)).coerceAtLeast(5f)
                                                    if (gridSnapSizeMm > 0f) {
                                                        rawH = kotlin.math.round(rawH / gridSnapSizeMm) * gridSnapSizeMm
                                                    }
                                                    rawH
                                                }
                                                onElementMoved(
                                                    element,
                                                    element.xMm,
                                                    element.yMm,
                                                    newW,
                                                    newH
                                                )
                                            }
                                        }
                                        .testTag("resize_anchor_${element.id}")
                                )
                            }
                        }
                    }
                } // Ends print_page_canvas Box
            } // Ends wrapper Box
        } // Ends outer workspace Box
    } // Ends BoxWithConstraints
}

// Active selection styling controls
@Composable
fun ElementPropertiesTray(
    element: LayoutElement,
    project: BookProject,
    onUpdate: (LayoutElement) -> Unit,
    onDelete: () -> Unit,
    onMoveLayer: (Int) -> Unit,
    onTriggerAiHelper: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (element.type) {
                        "text" -> Icons.Default.TextFields
                        "image" -> Icons.Default.Image
                        else -> Icons.Default.Category
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = "Elemento: ${element.type.uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Layer order actions
                IconButton(onClick = { onMoveLayer(1) }) {
                    Icon(Icons.Default.Upload, contentDescription = "Traer al frente")
                }
                IconButton(onClick = { onMoveLayer(-1) }) {
                    Icon(Icons.Default.Download, contentDescription = "Enviar al fondo")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Precision Coordinates (Scribus-like numeric alignments)
        Text(text = "Coordenadas Prácticas (Milímetros)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("X (Cercanía Izq.)", style = MaterialTheme.typography.labelSmall)
                CoordinateAdjuster(value = element.xMm, onValueChange = { onUpdate(element.copy(xMm = it)) })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Y (Cercanía Sup.)", style = MaterialTheme.typography.labelSmall)
                CoordinateAdjuster(value = element.yMm, onValueChange = { onUpdate(element.copy(yMm = it)) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Largo (W)", style = MaterialTheme.typography.labelSmall)
                CoordinateAdjuster(value = element.widthMm, min = 5f, onValueChange = { onUpdate(element.copy(widthMm = it)) })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Ancho (H)", style = MaterialTheme.typography.labelSmall)
                CoordinateAdjuster(value = element.heightMm, min = 5f, onValueChange = { onUpdate(element.copy(heightMm = it)) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(12.dp))

        // Element details section
        when (element.type) {
            "text" -> {
                // Text frame configuration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Contenido de Texto", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    // Gemini AI Assist triggering button
                    Button(
                        onClick = onTriggerAiHelper,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("btn_trigger_ai")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sugerir con IA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = element.textContent,
                    onValueChange = { onUpdate(element.copy(textContent = it)) },
                    placeholder = { Text("Escribe algún texto...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("input_element_text")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Font Styling Row
                Text(text = "Tipografía y Formato", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Font selection
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fuente: ", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        listOf("Times New Roman", "Sans-Serif", "Monospace").forEach { family ->
                            val selected = when (family) {
                                "Times New Roman" -> element.fontFamily == "Times New Roman" || element.fontFamily == "Serif"
                                else -> element.fontFamily == family
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdate(element.copy(fontFamily = family)) },
                                label = {
                                    val displayName = when (family) {
                                        "Times New Roman" -> "Times New Roman"
                                        "Sans-Serif" -> "Sans-Serif"
                                        else -> family
                                    }
                                    Text(displayName, fontSize = 11.sp)
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tamaño (${element.fontSizeSp.toInt()} sp):", fontSize = 12.sp)
                        Slider(
                            value = element.fontSizeSp,
                            onValueChange = { onUpdate(element.copy(fontSizeSp = it)) },
                            valueRange = 8f..36f,
                            modifier = Modifier.width(130.dp)
                        )
                    }

                    // Toggles
                    Row {
                        IconToggleButton(
                            checked = element.isBold,
                            onCheckedChange = { onUpdate(element.copy(isBold = it)) }
                        ) {
                            Icon(Icons.Default.FormatBold, contentDescription = "Negrita")
                        }
                        IconToggleButton(
                            checked = element.isItalic,
                            onCheckedChange = { onUpdate(element.copy(isItalic = it)) }
                        ) {
                            Icon(Icons.Default.FormatItalic, contentDescription = "Cursiva")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Alignments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Alineación:", fontSize = 12.sp)
                    Row {
                        listOf("LEFT", "CENTER", "RIGHT", "JUSTIFY").forEach { align ->
                            val selected = element.textAlignment == align
                            IconButton(
                                onClick = { onUpdate(element.copy(textAlignment = align)) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = when (align) {
                                        "CENTER" -> Icons.Default.FormatAlignCenter
                                        "RIGHT" -> Icons.Default.FormatAlignRight
                                        "JUSTIFY" -> Icons.Default.FormatAlignJustify
                                        else -> Icons.Default.FormatAlignLeft
                                    },
                                    contentDescription = align
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Interlineado (Line Height):", fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${(element.lineHeightMultiplier * 10).toInt() / 10.0}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Slider(
                            value = element.lineHeightMultiplier,
                            onValueChange = { onUpdate(element.copy(lineHeightMultiplier = it)) },
                            valueRange = 0.8f..3.0f,
                            modifier = Modifier
                                .width(130.dp)
                                .testTag("slider_line_height")
                        )
                    }
                }

                if (project.a5TextLimitEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val targetW = project.a5TextLimitWidthMm
                            val targetX = ((project.pageWidthMm - targetW) / 2f).coerceAtLeast(0f)
                            onUpdate(element.copy(
                                xMm = targetX,
                                widthMm = targetW
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_a5_fit_text_11cm")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ajustar texto a 11 cm y Centrar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Text box border configs
                Text(text = "Relleno del Marco y Bordes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Color Fondo", fontSize = 11.sp)
                        ColorPickerGrid(
                            selectedColor = element.textBackgroundColorHex,
                            onColorSelected = { onUpdate(element.copy(textBackgroundColorHex = it)) },
                            includeTransparent = true
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Color Borde", fontSize = 11.sp)
                        ColorPickerGrid(
                            selectedColor = element.textBorderColorHex,
                            onColorSelected = {
                                onUpdate(
                                    element.copy(
                                        textBorderColorHex = it,
                                        textBorderWidthMm = if (it == "#00000000") 0f else 1f
                                    )
                                )
                            },
                            includeTransparent = true
                        )
                    }
                }
            }
            "image" -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            // Uri permission caught safely
                        }
                        onUpdate(
                            element.copy(
                                imageUrl = uri.toString(),
                                isPlaceholder = false
                            )
                        )
                    }
                }

                // Header & Action Button
                Text(
                    text = "Insertar Contenido de Imagen",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (element.imageUrl.isNotEmpty() && !element.isPlaceholder) {
                    // Elevated card showing preview status
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Small thumbnail
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.LightGray)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(model = element.imageUrl),
                                    contentDescription = "Miniatura",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Imagen Vinculada",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "URI: ${element.imageUrl.takeLast(30)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { launcher.launch("image/*") },
                        modifier = Modifier.weight(1f).testTag("btn_select_gallery_image"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galería", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (element.imageUrl.isNotEmpty() && !element.isPlaceholder) {
                        Button(
                            onClick = {
                                onUpdate(
                                    element.copy(
                                        imageUrl = "",
                                        isPlaceholder = true
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("btn_remove_gallery_image"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quitar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Aspect Ratio Controls
                Text(
                    text = "Ajustar Proporción y Aspecto",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Toggle Aspect Ratio Lock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mantener Proporciones",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Reserva el aspect-ratio durante el redimensionado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = element.isAspectLocked,
                        onCheckedChange = { onUpdate(element.copy(isAspectLocked = it)) },
                        modifier = Modifier.testTag("switch_aspect_lock")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Escala de Ajuste: FIT vs CROP
                Text(
                    text = "Modo de Ajuste de Imagen",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CROP", "FIT").forEach { fitMode ->
                        val selected = element.imageScaleType == fitMode
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdate(element.copy(imageScaleType = fitMode)) },
                            label = { Text(if (fitMode == "CROP") "Recortar (Crop)" else "Ajustar (Fit)") },
                            modifier = Modifier.weight(1f).testTag("chip_fit_mode_$fitMode")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Image background if placeholder fallback/margins
                Text(
                    text = "Color de Fondo del Contenedor",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                ColorPickerGrid(
                    selectedColor = element.placeholderColorHex,
                    onColorSelected = { onUpdate(element.copy(placeholderColorHex = it)) }
                )
            }
            "shape" -> {
                // Shapes configurations
                Text(text = "Tipo de Figura", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("RECTANGLE", "ELLIPSE", "LINE").forEach { type ->
                        val selected = element.shapeType == type
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdate(element.copy(shapeType = type)) },
                            label = { Text(type) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Color de Relleno", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                ColorPickerGrid(
                    selectedColor = element.shapeFillColorHex,
                    onColorSelected = { onUpdate(element.copy(shapeFillColorHex = it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Contorno y Trazo (Stroke)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Grosor: ${element.shapeStrokeWidthMm.toInt()} mm", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Slider(
                        value = element.shapeStrokeWidthMm,
                        onValueChange = { onUpdate(element.copy(shapeStrokeWidthMm = it)) },
                        valueRange = 0.5f..8.0f,
                        modifier = Modifier.weight(2f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Color de Trazo", fontSize = 11.sp)
                ColorPickerGrid(
                    selectedColor = element.shapeStrokeColorHex,
                    onColorSelected = { onUpdate(element.copy(shapeStrokeColorHex = it)) }
                )
            }
        }
    }
}

// Default settings sheet when nothing is selected
@Composable
fun EmptyTrayView(
    project: BookProject,
    onUpdateProject: (BookProject) -> Unit
) {
    var title by remember(project.id) { mutableStateOf(project.title) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Parámetros de la Maqueta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ningún elemento seleccionado en la página actual. Puedes ajustar el tamaño físico global de la maqueta y los márgenes abajo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title editing
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                onUpdateProject(project.copy(title = it))
            },
            label = { Text("Renombrar Maqueta") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Tamaño de Página Físico (mm)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = project.pageWidthMm.toString(),
                onValueChange = {
                    val finalW = it.toFloatOrNull() ?: project.pageWidthMm
                    onUpdateProject(project.copy(pageWidthMm = finalW))
                },
                label = { Text("Ancho (mm)") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = project.pageHeightMm.toString(),
                onValueChange = {
                    val finalH = it.toFloatOrNull() ?: project.pageHeightMm
                    onUpdateProject(project.copy(pageHeightMm = finalH))
                },
                label = { Text("Alto (mm)") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Grosor del Sangrado Impresora (mm)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = project.bleedMm.toString(),
            onValueChange = {
                val finalB = it.toFloatOrNull() ?: project.bleedMm
                onUpdateProject(project.copy(bleedMm = finalB))
            },
            label = { Text("Sangrado (A sangre)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Márgenes de Encuadernación (mm)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = project.marginMmLeft.toString(),
                onValueChange = {
                    val finalVal = it.toFloatOrNull() ?: project.marginMmLeft
                    onUpdateProject(project.copy(marginMmLeft = finalVal))
                },
                label = { Text("Izq.") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = project.marginMmRight.toString(),
                onValueChange = {
                    val finalVal = it.toFloatOrNull() ?: project.marginMmRight
                    onUpdateProject(project.copy(marginMmRight = finalVal))
                },
                label = { Text("Der.") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = project.marginMmTop.toString(),
                onValueChange = {
                    val finalVal = it.toFloatOrNull() ?: project.marginMmTop
                    onUpdateProject(project.copy(marginMmTop = finalVal))
                },
                label = { Text("Sup.") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = project.marginMmBottom.toString(),
                onValueChange = {
                    val finalVal = it.toFloatOrNull() ?: project.marginMmBottom
                    onUpdateProject(project.copy(marginMmBottom = finalVal))
                },
                label = { Text("Inf.") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Helpers components for editing properties

@Composable
fun CoordinateAdjuster(
    value: Float,
    min: Float = -20f,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        IconButton(
            onClick = { onValueChange((value - 1.0f).coerceAtLeast(min)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Menos", modifier = Modifier.size(16.dp))
        }

        Text(
            text = String.format("%.1f", value),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .wrapContentWidth(Alignment.CenterHorizontally)
        )

        IconButton(
            onClick = { onValueChange(value + 1.0f) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Más", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ColorPickerGrid(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    includeTransparent: Boolean = false
) {
    val colors = remember {
        mutableListOf(
            "#1E293B", // Slate
            "#0F172A", // Dark Slate
            "#EF4444", // Red
            "#F59E0B", // Amber
            "#10B981", // Green
            "#3B82F6", // Blue
            "#6366F1", // Indigo
            "#8B5CF6", // Purple
            "#EC4899", // Pink
            "#FFFFFF", // White
            "#000000", // Black
            "#F1F5F9"  // Light Grey
        )
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        if (includeTransparent) {
            item {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            width = if (selectedColor == "#00000000") 2.5.dp else 1.dp,
                            color = if (selectedColor == "#00000000") MaterialTheme.colorScheme.primary else Color.LightGray,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected("#00000000") },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawLine(
                            color = Color.Red,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
        }

        items(colors) { hex ->
            val colorVal = Color(android.graphics.Color.parseColor(hex))
            val isSelected = selectedColor.equals(hex, ignoreCase = true)

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colorVal)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(hex) }
            )
        }
    }
}

@Composable
fun GeminiAiTextAssistantDialog(
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onClearError: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Asistente Editorial de IA (Gemini)", fontFamily = FontFamily.Serif)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Pide a Gemini que redacte textos de relleno inteligentes, capítulos introductorios, poesía o contenido temático ajustado para tu marco.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("¿Qué deseas escribir?") },
                    placeholder = { Text("Ej: Un poema melancólico sobre el mar de 3 estrofas, o Lorem Ipsum en español medieval...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isLoading
                )

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Gemini redactando texto...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aviso de Clave o Red", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(onClick = onClearError, modifier = Modifier.align(Alignment.End)) {
                                Text("Aceptar", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (prompt.isNotBlank()) onGenerate(prompt) },
                enabled = prompt.isNotBlank() && !isLoading,
                modifier = Modifier.testTag("btn_generate_ai_text")
            ) {
                Text("Generar e Insertar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPreviewDialog(
    project: BookProject,
    pages: List<BookPage>,
    initialPageIndex: Int,
    onDismiss: () -> Unit
) {
    var previewPageIndex by remember { mutableStateOf(initialPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
    var showBleedGuides by remember { mutableStateOf(true) }
    var showCropMarks by remember { mutableStateOf(true) }
    var showColorBars by remember { mutableStateOf(true) }
    var showPaperTexture by remember { mutableStateOf(false) }

    val activePage = pages.getOrNull(previewPageIndex) ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.92f),
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_print_preview")
            ) {
                Text("Cerrar", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Vista Previa de Impresión Profesional",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "Pliego de imprenta real con sangrado y marcas reguladoras",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth > 800.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                                .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PrinterSheetCanvas(
                                project = project,
                                page = activePage,
                                showBleed = showBleedGuides,
                                showCrops = showCropMarks,
                                showColors = showColorBars,
                                showTexture = showPaperTexture
                            )
                        }

                        Column(
                            modifier = Modifier
                                .width(300.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            PreviewControlPanel(
                                pages = pages,
                                currentIndex = previewPageIndex,
                                onIndexChange = { previewPageIndex = it },
                                showBleed = showBleedGuides,
                                onBleedChange = { showBleedGuides = it },
                                showCrops = showCropMarks,
                                onCropsChange = { showCropMarks = it },
                                showColors = showColorBars,
                                onColorsChange = { showColorBars = it },
                                showTexture = showPaperTexture,
                                onTextureChange = { showPaperTexture = it },
                                project = project
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxWidth()
                                .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PrinterSheetCanvas(
                                project = project,
                                page = activePage,
                                showBleed = showBleedGuides,
                                showCrops = showCropMarks,
                                showColors = showColorBars,
                                showTexture = showPaperTexture
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.8f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            PreviewControlPanel(
                                pages = pages,
                                currentIndex = previewPageIndex,
                                onIndexChange = { previewPageIndex = it },
                                showBleed = showBleedGuides,
                                onBleedChange = { showBleedGuides = it },
                                showCrops = showCropMarks,
                                onCropsChange = { showCropMarks = it },
                                showColors = showColorBars,
                                onColorsChange = { showColorBars = it },
                                showTexture = showPaperTexture,
                                onTextureChange = { showPaperTexture = it },
                                project = project
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun PrinterSheetCanvas(
    project: BookProject,
    page: BookPage,
    showBleed: Boolean,
    showCrops: Boolean,
    showColors: Boolean,
    showTexture: Boolean
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val maxW = maxWidth.value
        val maxH = maxHeight.value

        val trimMarginMm = 15f
        val bleedMm = project.bleedMm

        val totalWMm = project.pageWidthMm + (bleedMm * 2) + (trimMarginMm * 2)
        val totalHMm = project.pageHeightMm + (bleedMm * 2) + (trimMarginMm * 2)

        val scaleX = maxW / totalWMm
        val scaleY = maxH / totalHMm
        val mmToDp = scaleX.coerceAtMost(scaleY) * 0.95f

        val sheetW = totalWMm * mmToDp
        val sheetH = totalHMm * mmToDp

        val pageW = project.pageWidthMm * mmToDp
        val pageH = project.pageHeightMm * mmToDp
        val bleed = bleedMm * mmToDp
        val trimMargin = trimMarginMm * mmToDp

        Box(
            modifier = Modifier
                .size(sheetW.dp, sheetH.dp)
                .shadow(8.dp, RoundedCornerShape(2.dp))
                .background(
                    if (showTexture) Color(0xFFFAF6EE)
                    else Color.White
                )
                .border(0.5.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (showCrops) {
                    val lineColor = Color(0xFF0F172A)
                    val strokeW = 1.dp.toPx()

                    val tlX = (trimMarginMm + bleedMm) * mmToDp.dp.toPx()
                    val tlY = (trimMarginMm + bleedMm) * mmToDp.dp.toPx()

                    drawLine(
                        color = lineColor,
                        start = Offset(tlX - 10.dp.toPx(), tlY),
                        end = Offset(tlX - 2.dp.toPx(), tlY),
                        strokeWidth = strokeW
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(tlX, tlY - 10.dp.toPx()),
                        end = Offset(tlX, tlY - 2.dp.toPx()),
                        strokeWidth = strokeW
                    )

                    val trX = tlX + pageW.dp.toPx()
                    val trY = tlY
                    drawLine(
                        color = lineColor,
                        start = Offset(trX + 2.dp.toPx(), trY),
                        end = Offset(trX + 10.dp.toPx(), trY),
                        strokeWidth = strokeW
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(trX, trY - 10.dp.toPx()),
                        end = Offset(trX, trY - 2.dp.toPx()),
                        strokeWidth = strokeW
                    )

                    val blX = tlX
                    val blY = tlY + pageH.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(blX - 10.dp.toPx(), blY),
                        end = Offset(blX - 2.dp.toPx(), blY),
                        strokeWidth = strokeW
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(blX, blY + 2.dp.toPx()),
                        end = Offset(blX, blY + 10.dp.toPx()),
                        strokeWidth = strokeW
                    )

                    val brX = trX
                    val brY = blY
                    drawLine(
                        color = lineColor,
                        start = Offset(brX + 2.dp.toPx(), brY),
                        end = Offset(brX + 10.dp.toPx(), brY),
                        strokeWidth = strokeW
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(brX, brY + 2.dp.toPx()),
                        end = Offset(brX, brY + 10.dp.toPx()),
                        strokeWidth = strokeW
                    )
                }

                if (showCrops) {
                    val targetColor = Color.Black.copy(alpha = 0.4f)
                    val r = 5.dp.toPx()
                    
                    val marginCenters = listOf(
                        Offset(size.width / 2f, (trimMarginMm / 2f) * mmToDp.dp.toPx()),
                        Offset(size.width / 2f, size.height - (trimMarginMm / 2f) * mmToDp.dp.toPx()),
                        Offset((trimMarginMm / 2f) * mmToDp.dp.toPx(), size.height / 2f),
                        Offset(size.width - (trimMarginMm / 2f) * mmToDp.dp.toPx(), size.height / 2f)
                    )

                    marginCenters.forEach { center ->
                        drawCircle(color = targetColor, radius = r, center = center, style = Stroke(width = 0.8.dp.toPx()))
                        drawLine(color = targetColor, start = Offset(center.x - r * 1.8f, center.y), end = Offset(center.x + r * 1.8f, center.y), strokeWidth = 0.8.dp.toPx())
                        drawLine(color = targetColor, start = Offset(center.x, center.y - r * 1.8f), end = Offset(center.x, center.y + r * 1.8f), strokeWidth = 0.8.dp.toPx())
                    }
                }

                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(140, 15, 23, 42)
                    textSize = 6.sp.toPx()
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
                }
                val infoText = "PRE-PRENSA: ${project.title.uppercase()} | PLANCHA: PÁG. ${page.pageNumber} | RECORTE: ${project.pageWidthMm}x${project.pageHeightMm}mm | SANGRADO: ${project.bleedMm}mm | TINTA: PROCESO CMYK | FECHA: 2026-06-08"
                drawContext.canvas.nativeCanvas.drawText(
                    infoText,
                    size.width / 2f,
                    size.height - (trimMarginMm / 3.5f) * mmToDp.dp.toPx(),
                    textPaint
                )
            }

            if (showColors) {
                val swatchColors = listOf(
                    Color(0xFF00FFFF),
                    Color(0xFFFF00FF),
                    Color(0xFFFFFF00),
                    Color(0xFF000000),
                    Color(0xFFE2E8F0),
                    Color(0xFF94A3B8),
                    Color(0xFF475569)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = ((trimMarginMm / 3.5f) * mmToDp).dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    swatchColors.forEach { col ->
                        Box(
                            modifier = Modifier
                                .size(((trimMarginMm / 4f) * mmToDp).dp)
                                .background(col)
                                .border(0.5.dp, Color.Black.copy(alpha = 0.15f))
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = (trimMargin + bleed).dp, y = (trimMargin + bleed).dp)
                    .size(pageW.dp, pageH.dp)
                    .background(Color.White)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.3f))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dotSpacing = 12.dp.toPx()
                    val dotRadius = 0.8f.dp.toPx()
                    var x = dotSpacing
                    while (x < size.width) {
                        var y = dotSpacing
                        while (y < size.height) {
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.02f),
                                radius = dotRadius,
                                center = Offset(x, y)
                            )
                            y += dotSpacing
                        }
                        x += dotSpacing
                    }
                }

                if (project.showPageNumbers && page.pageNumber >= project.pageNumbersStartAtPage) {
                    val pageNumberValue = page.pageNumber - project.pageNumbersStartAtPage + project.pageNumbersStartFromValue
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = ((project.marginMmBottom / 2) * mmToDp).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pageNumberValue.toString(),
                            fontSize = (11f * (mmToDp / 3.5f)).coerceAtLeast(6f).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray,
                            fontFamily = FontFamily.Serif
                        )
                    }
                }

                val sortedElements = page.elements.sortedWith(compareBy({ it.zIndex }, { it.id }))
                sortedElements.forEach { element ->
                    val elemX = element.xMm * mmToDp
                    val elemY = element.yMm * mmToDp
                    val elemW = element.widthMm * mmToDp
                    val elemH = element.heightMm * mmToDp

                    Box(
                        modifier = Modifier
                            .offset(x = elemX.dp, y = elemY.dp)
                            .size(width = elemW.dp, height = elemH.dp)
                    ) {
                        when (element.type) {
                            "text" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (element.textBackgroundColorHex != "#00000000") Color(
                                                android.graphics.Color.parseColor(element.textBackgroundColorHex)
                                            ) else Color.Transparent
                                        )
                                        .let {
                                            if (element.textBorderWidthMm > 0f && element.textBorderColorHex != "#00000000") {
                                                it.border(
                                                    (element.textBorderWidthMm * mmToDp / 3).dp.coerceAtLeast(1.dp),
                                                    Color(android.graphics.Color.parseColor(element.textBorderColorHex))
                                                )
                                            } else it
                                        }
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = element.textContent,
                                        fontSize = (element.fontSizeSp * (mmToDp / 3.5f)).coerceAtLeast(6f).sp,
                                        lineHeight = (element.fontSizeSp * element.lineHeightMultiplier * (mmToDp / 3.5f)).coerceAtLeast(6f).sp,
                                        color = Color(android.graphics.Color.parseColor(element.textColorHex)),
                                        fontWeight = if (element.isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (element.isItalic) FontStyle.Italic else FontStyle.Normal,
                                        fontFamily = when (element.fontFamily) {
                                            "Times New Roman", "Times News Roman", "Serif" -> FontFamily.Serif
                                            "Sans-Serif" -> FontFamily.SansSerif
                                            "Monospace" -> FontFamily.Monospace
                                            else -> FontFamily.Serif
                                        },
                                        textAlign = when (element.textAlignment) {
                                            "CENTER" -> TextAlign.Center
                                            "RIGHT" -> TextAlign.Right
                                            "JUSTIFY" -> TextAlign.Justify
                                            else -> TextAlign.Left
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            "image" -> {
                                if (element.imageUrl.isNotEmpty() && !element.isPlaceholder) {
                                    androidx.compose.foundation.Image(
                                        painter = coil.compose.rememberAsyncImagePainter(model = element.imageUrl),
                                        contentDescription = "Imagen de usuario",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(android.graphics.Color.parseColor(element.placeholderColorHex)))
                                            .border(0.5.dp, Color.DarkGray),
                                        contentScale = if (element.imageScaleType == "FIT") {
                                            androidx.compose.ui.layout.ContentScale.Fit
                                        } else {
                                            androidx.compose.ui.layout.ContentScale.Crop
                                        }
                                    )
                                } else {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(android.graphics.Color.parseColor(element.placeholderColorHex)))
                                            .border(0.5.dp, Color.DarkGray)
                                    ) {
                                        drawLine(
                                            color = Color.Gray,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = 1f
                                        )
                                        drawLine(
                                            color = Color.Gray,
                                            start = Offset(size.width, 0f),
                                            end = Offset(0f, size.height),
                                            strokeWidth = 1f
                                        )
                                    }
                                }
                            }
                            "shape" -> {
                                when (element.shapeType) {
                                    "RECTANGLE" -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Color(android.graphics.Color.parseColor(element.shapeFillColorHex))
                                                )
                                                .border(
                                                    (element.shapeStrokeWidthMm * mmToDp / 3).dp.coerceAtLeast(1.dp),
                                                    Color(android.graphics.Color.parseColor(element.shapeStrokeColorHex))
                                                )
                                        )
                                    }
                                    "ELLIPSE" -> {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawOval(
                                                color = Color(android.graphics.Color.parseColor(element.shapeFillColorHex))
                                            )
                                            drawOval(
                                                color = Color(android.graphics.Color.parseColor(element.shapeStrokeColorHex)),
                                                style = Stroke(width = (element.shapeStrokeWidthMm * mmToDp / 3).dp.toPx().coerceAtLeast(1f))
                                            )
                                        }
                                    }
                                    "LINE" -> {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawLine(
                                                color = Color(android.graphics.Color.parseColor(element.shapeStrokeColorHex)),
                                                start = Offset(0f, size.height / 2f),
                                                end = Offset(size.width, size.height / 2f),
                                                strokeWidth = (element.shapeStrokeWidthMm * mmToDp / 3).dp.toPx().coerceAtLeast(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showBleed) {
                Box(
                    modifier = Modifier
                        .offset(x = trimMargin.dp, y = trimMargin.dp)
                        .size((pageW + bleed * 2).dp, (pageH + bleed * 2).dp)
                        .border(
                            width = 1.dp,
                            color = Color.Red.copy(alpha = 0.65f),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val txtPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.RED
                            textSize = 5.sp.toPx()
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }
                        drawContext.canvas.nativeCanvas.drawText("LÍMITE DE SANGRADO (${project.bleedMm} MM)", 10f, size.height - 10f, txtPaint)
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val trimLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(160, 15, 23, 42)
                        textSize = 5.sp.toPx()
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawText("LÍNEA DE CORTE (TRIM)", (trimMarginMm + bleedMm + 2f) * mmToDp.dp.toPx(), (trimMarginMm + bleedMm - 2f) * mmToDp.dp.toPx(), trimLabelPaint)
                }
            }
        }
    }
}

@Composable
fun PreviewControlPanel(
    pages: List<BookPage>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    showBleed: Boolean,
    onBleedChange: (Boolean) -> Unit,
    showCrops: Boolean,
    onCropsChange: (Boolean) -> Unit,
    showColors: Boolean,
    onColorsChange: (Boolean) -> Unit,
    showTexture: Boolean,
    onTextureChange: (Boolean) -> Unit,
    project: BookProject
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Navegación de Pliego",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentIndex > 0) onIndexChange(currentIndex - 1) },
                        enabled = currentIndex > 0,
                        modifier = Modifier.testTag("btn_prev_preview_page")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Anterior pliego")
                    }
                    Text(
                        text = "Pliego ${currentIndex + 1} de ${pages.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { if (currentIndex < pages.size - 1) onIndexChange(currentIndex + 1) },
                        enabled = currentIndex < pages.size - 1,
                        modifier = Modifier.testTag("btn_next_preview_page")
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Siguiente pliego")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Opciones de Impresión",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sangrado (Bleed)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Límites de recorte a ${project.bleedMm} mm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = showBleed,
                        onCheckedChange = onBleedChange,
                        modifier = Modifier.testTag("switch_show_bleed_preview")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Marcas de Corte (Crops)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Líneas de guillotina y targets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = showCrops,
                        onCheckedChange = onCropsChange,
                        modifier = Modifier.testTag("switch_show_crops_preview")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Barras CMYK", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Densidad y calibración cromática", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = showColors,
                        onCheckedChange = onColorsChange,
                        modifier = Modifier.testTag("switch_show_colors_preview")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Textura Mate Uncoated", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Simula grano y color del papel real", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = showTexture,
                        onCheckedChange = onTextureChange,
                        modifier = Modifier.testTag("switch_show_texture_preview")
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Checklist de Pre-Prensa",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Text("Sangrado soportado: ${project.bleedMm} mm", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Text("Caja de recorte alineada (Trim Box)", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Text("Texto seguro dentro de los márgenes", style = MaterialTheme.typography.bodySmall)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Text("Imágenes/Rasters en alta densidad", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}


@Composable
fun MarginSidebarPanel(
    project: BookProject,
    viewModel: EditorViewModel,
    onUpdateProject: (BookProject) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xEDFDF8FD) // Frosted glass light lavender cream
        ),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Márgenes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Modifica los márgenes del documento y observa la retícula en tiempo real.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Preset Margins Shortcuts
            Text(
                text = "Plantillas Rápidas",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    Triple("Estrecho", 10f, "10mm"),
                    Triple("Estándar", 20f, "20mm"),
                    Triple("Ancho", 30f, "30mm")
                ).forEach { (label, value, desc) ->
                    val isActive = project.marginMmLeft == value &&
                                   project.marginMmRight == value &&
                                   project.marginMmTop == value &&
                                   project.marginMmBottom == value
                    InputChip(
                        selected = isActive,
                        onClick = {
                            onUpdateProject(
                                project.copy(
                                    marginMmLeft = value,
                                    marginMmRight = value,
                                    marginMmTop = value,
                                    marginMmBottom = value
                                )
                            )
                        },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Margin Controls
            MarginAdjusterItem(
                label = "Margo Izquierdo",
                value = project.marginMmLeft,
                onValueChange = { onUpdateProject(project.copy(marginMmLeft = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            MarginAdjusterItem(
                label = "Margo Derecho",
                value = project.marginMmRight,
                onValueChange = { onUpdateProject(project.copy(marginMmRight = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            MarginAdjusterItem(
                label = "Margo Superior",
                value = project.marginMmTop,
                onValueChange = { onUpdateProject(project.copy(marginMmTop = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            MarginAdjusterItem(
                label = "Margo Inferior",
                value = project.marginMmBottom,
                onValueChange = { onUpdateProject(project.copy(marginMmBottom = it)) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(16.dp))

            // Rulers precision settings group
            Text(
                text = "Reglas de Precisión",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mostrar Reglas de Medida",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Muestra reglas en mm y cm en los bordes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = project.rulersEnabled,
                    onCheckedChange = { onUpdateProject(project.copy(rulersEnabled = it)) },
                    modifier = Modifier.testTag("switch_rulers_enabled")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(16.dp))

            // Page Numbering Settings Group
            Text(
                text = "Numeración de Páginas",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mostrar Números",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = project.showPageNumbers,
                    onCheckedChange = { onUpdateProject(project.copy(showPageNumbers = it)) },
                    modifier = Modifier.testTag("switch_show_page_numbers")
                )
            }

            if (project.showPageNumbers) {
                Spacer(modifier = Modifier.height(12.dp))

                // Start At Page Control
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Numerar desde la pág:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Página ${project.pageNumbersStartAtPage}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onUpdateProject(project.copy(pageNumbersStartAtPage = (project.pageNumbersStartAtPage - 1).coerceAtLeast(1))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Menos", modifier = Modifier.size(16.dp))
                        }

                        Slider(
                            value = project.pageNumbersStartAtPage.toFloat(),
                            onValueChange = { onUpdateProject(project.copy(pageNumbersStartAtPage = it.toInt().coerceAtLeast(1))) },
                            valueRange = 1f..10f,
                            steps = 9,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onUpdateProject(project.copy(pageNumbersStartAtPage = (project.pageNumbersStartAtPage + 1).coerceIn(1, 20))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Más", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Start From Value Control (e.g. Page 5 acts as number 1)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Valor inicial del número:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Número ${project.pageNumbersStartFromValue}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onUpdateProject(project.copy(pageNumbersStartFromValue = (project.pageNumbersStartFromValue - 1).coerceAtLeast(0))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Menos", modifier = Modifier.size(16.dp))
                        }

                        Slider(
                            value = project.pageNumbersStartFromValue.toFloat(),
                            onValueChange = { onUpdateProject(project.copy(pageNumbersStartFromValue = it.toInt().coerceAtLeast(0))) },
                            valueRange = 0f..10f,
                            steps = 10,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onUpdateProject(project.copy(pageNumbersStartFromValue = (project.pageNumbersStartFromValue + 1).coerceIn(0, 50))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Más", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(16.dp))

            // Lined notebook grid settings
            Text(
                text = "Páginas con Renglones/Líneas",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Líneas de Maqueta",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = project.ruledLinesEnabled,
                    onCheckedChange = { onUpdateProject(project.copy(ruledLinesEnabled = it)) },
                    modifier = Modifier.testTag("switch_ruled_lines_enabled")
                )
            }

            if (project.ruledLinesEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // Ruled Lines Count (Default 30)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Número de líneas:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${project.ruledLinesCount} renglones",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onUpdateProject(project.copy(ruledLinesCount = (project.ruledLinesCount - 1).coerceAtLeast(5))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Menos líneas", modifier = Modifier.size(16.dp))
                        }

                        Slider(
                            value = project.ruledLinesCount.toFloat(),
                            onValueChange = { onUpdateProject(project.copy(ruledLinesCount = it.toInt())) },
                            valueRange = 5f..100f,
                            steps = 94,
                            modifier = Modifier.weight(1f).testTag("slider_ruled_lines_count")
                        )

                        IconButton(
                            onClick = { onUpdateProject(project.copy(ruledLinesCount = (project.ruledLinesCount + 1).coerceAtMost(100))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Más líneas", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ruled Lines Start Page Range (Default 1)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Renglones desde pág:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Pág. ${project.ruledLinesStartPage}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onUpdateProject(project.copy(ruledLinesStartPage = (project.ruledLinesStartPage - 1).coerceAtLeast(1))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Pág inicio menor", modifier = Modifier.size(16.dp))
                        }

                        Slider(
                            value = project.ruledLinesStartPage.toFloat(),
                            onValueChange = { onUpdateProject(project.copy(ruledLinesStartPage = it.toInt())) },
                            valueRange = 1f..100f,
                            steps = 98,
                            modifier = Modifier.weight(1f).testTag("slider_ruled_start_page")
                        )

                        IconButton(
                            onClick = { onUpdateProject(project.copy(ruledLinesStartPage = (project.ruledLinesStartPage + 1).coerceAtMost(project.ruledLinesEndPage))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Pág inicio mayor", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ruled Lines End Page Range (Default 370 matching request)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Renglones hasta pág:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Pág. ${project.ruledLinesEndPage}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onUpdateProject(project.copy(ruledLinesEndPage = (project.ruledLinesEndPage - 1).coerceAtLeast(project.ruledLinesStartPage))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Pág fin menor", modifier = Modifier.size(16.dp))
                        }

                        Slider(
                            value = project.ruledLinesEndPage.toFloat(),
                            onValueChange = { onUpdateProject(project.copy(ruledLinesEndPage = it.toInt())) },
                            valueRange = 1f..500f,
                            steps = 498,
                            modifier = Modifier.weight(1f).testTag("slider_ruled_end_page")
                        )

                        IconButton(
                            onClick = { onUpdateProject(project.copy(ruledLinesEndPage = (project.ruledLinesEndPage + 1).coerceAtMost(500))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Pág fin mayor", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(16.dp))

            // A5 Text column layout limit with 30 lines
            Text(
                text = "A5: Límite de Texto y Líneas",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Restringir Texto a 11 cm",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Para formato A5 con 30 renglones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = project.a5TextLimitEnabled,
                    onCheckedChange = { isChecked ->
                        onUpdateProject(
                            project.copy(
                                a5TextLimitEnabled = isChecked,
                                // Automatically apply A5 format preset standard on enabling
                                pageWidthMm = if (isChecked) 148f else project.pageWidthMm,
                                pageHeightMm = if (isChecked) 210f else project.pageHeightMm
                            )
                        )
                    },
                    modifier = Modifier.testTag("switch_a5_text_limit_enabled")
                )
            }

            if (project.a5TextLimitEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // Text Width Display Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ancho de texto:", style = MaterialTheme.typography.labelSmall)
                    Text("11.0 cm (110 mm)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Filas de guía:", style = MaterialTheme.typography.labelSmall)
                    Text("30 renglones", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // A5 Start Page Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aplicar desde pág:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Pág. ${project.a5TextLimitStartPage}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onUpdateProject(project.copy(a5TextLimitStartPage = (project.a5TextLimitStartPage - 1).coerceAtLeast(1))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Pág inicio menor A5", modifier = Modifier.size(16.dp))
                        }

                        Slider(
                            value = project.a5TextLimitStartPage.toFloat(),
                            onValueChange = { onUpdateProject(project.copy(a5TextLimitStartPage = it.toInt())) },
                            valueRange = 1f..100f,
                            steps = 98,
                            modifier = Modifier.weight(1f).testTag("slider_a5_limit_start_page")
                        )

                        IconButton(
                            onClick = { onUpdateProject(project.copy(a5TextLimitStartPage = (project.a5TextLimitStartPage + 1).coerceAtMost(project.a5TextLimitEndPage))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Pág inicio mayor A5", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // A5 End Page Slider (e.g. 370 pages match)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aplicar hasta pág:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Pág. ${project.a5TextLimitEndPage}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onUpdateProject(project.copy(a5TextLimitEndPage = (project.a5TextLimitEndPage - 1).coerceAtLeast(project.a5TextLimitStartPage))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Pág fin menor A5", modifier = Modifier.size(16.dp))
                        }

                        Slider(
                            value = project.a5TextLimitEndPage.toFloat(),
                            onValueChange = { onUpdateProject(project.copy(a5TextLimitEndPage = it.toInt())) },
                            valueRange = 1f..500f,
                            steps = 498,
                            modifier = Modifier.weight(1f).testTag("slider_a5_limit_end_page")
                        )

                        IconButton(
                            onClick = { onUpdateProject(project.copy(a5TextLimitEndPage = (project.a5TextLimitEndPage + 1).coerceAtMost(500))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Pág fin mayor A5", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(16.dp))

            // LocalStorage Auto-Save Group
            Text(
                text = "Copia de Seguridad (localStorage)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Resguarda el borrador en la memoria local del dispositivo cada 30 segundos de manera independiente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            val lastSaved = viewModel.lastAutoSavedTime.collectAsState().value
            Text(
                text = if (lastSaved != null) "Última copia: $lastSaved" else "Sin copias guardadas aún",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (lastSaved != null) Color(0xFF16A34A) else MaterialTheme.colorScheme.outline
            )
            
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Manual backup trigger button
                Button(
                    onClick = {
                        viewModel.performAutoSave()
                    },
                    modifier = Modifier.weight(1f).testTag("btn_manual_auto_save"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("Auto-guardar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Restore backup button
                Button(
                    onClick = {
                        viewModel.restoreLastAutoSaved(project.id)
                    },
                    modifier = Modifier.weight(1f).testTag("btn_restore_auto_save"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    enabled = lastSaved != null,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("Restaurar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Technical specs preview
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Especificación de Maqueta",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Área de Impresión: ${(project.pageWidthMm - project.marginMmLeft - project.marginMmRight).toInt()}x${(project.pageHeightMm - project.marginMmTop - project.marginMmBottom).toInt()} mm",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Sangrado: ${project.bleedMm} mm",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun MarginAdjusterItem(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${value.toInt()} mm",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onValueChange((value - 1f).coerceAtLeast(0f)) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Reducir", modifier = Modifier.size(16.dp))
            }

            Slider(
                value = value,
                onValueChange = { onValueChange(it.coerceAtLeast(0f)) },
                valueRange = 0f..60f,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { onValueChange((value + 1f).coerceIn(0f, 60f)) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Incrementar", modifier = Modifier.size(16.dp))
            }
        }
    }
}

