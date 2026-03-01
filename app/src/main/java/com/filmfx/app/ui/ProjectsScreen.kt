package com.filmfx.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import coil.compose.AsyncImage
import com.filmfx.app.data.Project

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onNewProject: (String) -> Unit,
    onOpenProject: (Long) -> Unit,
    viewModel: ProjectsViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val isLoadingData by viewModel.isLoading.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isImporting = true
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val copiedUri = com.filmfx.app.utils.ImageUtils.copyUriToInternalStorage(context, it)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isImporting = false
                    if (copiedUri != null) {
                        onNewProject(copiedUri.toString())
                    } else {
                        android.widget.Toast.makeText(context, "Failed to import image", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    var selectedProjects by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selectedProjects.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedProjects.size} Selected", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { selectedProjects = setOf() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            } else {
                TopAppBar(
                    title = { Text("FilmFX Projects", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            }
        },
        floatingActionButton = {
            if (projects.isNotEmpty() && selectedProjects.isEmpty()) {
                FloatingActionButton(
                    onClick = { launcher.launch("image/*") },
                    containerColor = Color.Cyan
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Project", tint = Color.Black)
                }
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (projects.isEmpty() && !isLoadingData) {
                // Empty State
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Time to use that spare film roll.",
                        color = Color.Gray,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Button(
                        onClick = { launcher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(56.dp)
                    ) {
                        Text("Import Image", color = Color.White, fontSize = 16.sp)
                    }
                }
            } else {
                // Grid View
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        val isSelected = selectedProjects.contains(project.id)
                        ProjectCard(
                            project = project,
                            isSelected = isSelected,
                            isSelectionMode = selectedProjects.isNotEmpty(),
                            onClick = {
                                if (selectedProjects.isNotEmpty()) {
                                    selectedProjects = if (isSelected) selectedProjects - project.id else selectedProjects + project.id
                                } else {
                                    onOpenProject(project.id)
                                }
                            },
                            onLongClick = {
                                if (selectedProjects.isEmpty()) {
                                    selectedProjects = selectedProjects + project.id
                                }
                            },
                            onDelete = { viewModel.deleteProject(project) }
                        )
                    }
                }
                
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete Projects?") },
                        text = { Text("Are you sure you want to delete ${selectedProjects.size} projects?") },
                        confirmButton = { 
                            Button(onClick = { 
                                viewModel.bulkDeleteProjects(projects.filter { it.id in selectedProjects })
                                selectedProjects = setOf()
                                showDeleteConfirm = false 
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete") } 
                        },
                        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
                    )
                }
            }
            
            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.Cyan)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(project: Project, isSelected: Boolean, isSelectionMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfig by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Square
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
        border = if (isSelected) BorderStroke(2.dp, Color.Cyan) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = project.previewUri ?: project.originalUri,
                contentDescription = "Project Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Optional: Gradient overlay for text reading
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 0f,
                            endY = 500f
                        )
                    )
            )

            // Delete Button (Top Right)
            if (!isSelectionMode) {
                IconButton(
                    onClick = { showDeleteConfig = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            // Project Name and Date (Bottom Info)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Created: ${com.filmfx.app.utils.DateUtils.formatDate(project.createdAt)}",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = "Modified: ${com.filmfx.app.utils.DateUtils.formatDate(project.lastModified)}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Delete Confirmation Overlay
            if (showDeleteConfig) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable { showDeleteConfig = false },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Delete Project?", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { showDeleteConfig = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                                Text("No")
                            }
                            Button(onClick = { 
                                showDeleteConfig = false
                                onDelete() 
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                Text("Yes")
                            }
                        }
                    }
                }
            }
        }
    }
}
