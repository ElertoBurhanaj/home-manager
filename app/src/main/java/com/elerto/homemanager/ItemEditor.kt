@file:OptIn(ExperimentalMaterial3Api::class)

package com.elerto.homemanager

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ItemEditorScreen(vm: MainViewModel, screen: Screen.Editor) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by vm.data.collectAsState()

    val existing = remember(screen.itemId) { data.items.firstOrNull { it.id == screen.itemId } }
    val originalPhoto = existing?.photo

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "") }
    var photo by rememberSaveable { mutableStateOf(existing?.photo) }
    var residenceId by rememberSaveable { mutableStateOf(existing?.residenceId ?: screen.residenceId) }
    // Foto importate durante questa modifica: se si annulla vanno cancellate.
    var newPhotos by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun importPhoto(uri: Uri, tempFile: File? = null) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val result = PhotoStore.save(ctx, uri)
                tempFile?.delete()
                result
            }
            if (saved != null) {
                newPhotos = ArrayList(newPhotos + saved)
                photo = saved
            } else {
                Toast.makeText(ctx, "Impossibile leggere la foto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) importPhoto(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val path = cameraPath
        if (ok && path != null) {
            val file = File(path)
            importPhoto(Uri.fromFile(file), tempFile = file)
        }
    }

    fun launchCamera() {
        try {
            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
            cameraPath = file.absolutePath
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "Nessuna app fotocamera disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancel() {
        vm.deletePhotoFiles(newPhotos)
        vm.screen = Screen.Main
    }

    fun save() {
        val item = existing?.copy(
            name = name.trim(),
            description = description.trim(),
            category = category.trim(),
            photo = photo,
            residenceId = residenceId,
        ) ?: Item(
            name = name.trim(),
            description = description.trim(),
            category = category.trim(),
            photo = photo,
            residenceId = residenceId,
        )
        val toDelete = newPhotos.filter { it != photo } +
            listOfNotNull(originalPhoto.takeIf { it != null && it != photo })
        vm.upsertItem(item)
        vm.deletePhotoFiles(toDelete)
        vm.screen = Screen.Main
    }

    BackHandler(onBack = { cancel() })

    val suggestions = (DEFAULT_CATEGORIES + data.items.map { it.category.trim() })
        .filter { it.isNotEmpty() }
        .distinct()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nuovo capo" else "Modifica capo") },
                navigationIcon = {
                    IconButton(onClick = { cancel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina capo")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) {
                    PhotoImage(photo, Modifier.fillMaxSize(), maxPx = 1200)
                } else {
                    Text("Nessuna foto", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        pickLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Galleria") }
                OutlinedButton(
                    onClick = { launchCamera() },
                    modifier = Modifier.weight(1f),
                ) { Text("Fotocamera") }
                if (photo != null) {
                    TextButton(onClick = { photo = null }) { Text("Rimuovi") }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrizione") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoria") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s ->
                        FilterChip(
                            selected = category.trim().equals(s, ignoreCase = true),
                            onClick = { category = s },
                            label = { Text(s) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Residenza", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(data.residences, key = { it.id }) { r ->
                        FilterChip(
                            selected = residenceId == r.id,
                            onClick = { residenceId = r.id },
                            label = { Text(r.name) },
                        )
                    }
                }
            }

            Button(
                onClick = { save() },
                enabled = name.isNotBlank() && residenceId.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salva") }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare questo capo?") },
            text = { Text("\"${existing.name}\" verrà rimosso definitivamente.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePhotoFiles(newPhotos)
                    vm.deleteItem(existing.id)
                    vm.screen = Screen.Main
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            },
        )
    }
}
