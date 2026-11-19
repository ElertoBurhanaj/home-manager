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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "") }
    var room by rememberSaveable { mutableStateOf(existing?.room ?: "") }
    var quantity by rememberSaveable { mutableStateOf(existing?.quantity ?: 1) }
    var valueText by rememberSaveable { mutableStateOf(existing?.value?.let { plainNumber(it) } ?: "") }
    var warranty by rememberSaveable { mutableStateOf<Long?>(existing?.warrantyUntil) }
    var favorite by rememberSaveable { mutableStateOf(existing?.favorite ?: false) }
    var photos by rememberSaveable { mutableStateOf(ArrayList(existing?.photos ?: emptyList<String>())) }
    var residenceId by rememberSaveable { mutableStateOf(existing?.residenceId ?: screen.residenceId) }
    // Foto importate durante questa modifica: se si annulla vanno cancellate.
    var newPhotos by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var viewerPhoto by remember { mutableStateOf<String?>(null) }
    var tidying by remember { mutableStateOf(false) }

    fun importPhotos(uris: List<Uri>, tempFile: File? = null) {
        scope.launch {
            var failed = false
            for (uri in uris) {
                val saved = withContext(Dispatchers.IO) {
                    val result = PhotoStore.save(ctx, uri)
                    tempFile?.delete()
                    result
                }
                if (saved != null) {
                    newPhotos = ArrayList(newPhotos + saved)
                    photos = ArrayList(photos + saved)
                } else {
                    failed = true
                }
            }
            if (failed) Toast.makeText(ctx, "Impossibile leggere una foto", Toast.LENGTH_SHORT).show()
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) importPhotos(uris)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val path = cameraPath
        if (ok && path != null) {
            val file = File(path)
            importPhotos(listOf(Uri.fromFile(file)), tempFile = file)
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

    /** Isola l'oggetto su sfondo neutro e aggiunge il risultato come copertina; l'originale resta. */
    fun tidyPhoto(p: String) {
        if (tidying) return
        tidying = true
        Toast.makeText(ctx, "Sto ordinando la foto…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = withContext(Dispatchers.IO) { PhotoStore.tidy(ctx, p) }
            tidying = false
            if (result != null) {
                newPhotos = ArrayList(newPhotos + result)
                val list = ArrayList<String>(photos)
                val idx = list.indexOf(p)
                list.add(if (idx < 0) 0 else idx, result)
                photos = list
                Toast.makeText(ctx, "Foto ordinata aggiunta: l'originale è stato conservato", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    ctx,
                    "Non sono riuscito a isolare l'oggetto. Alla prima volta serve la connessione per scaricare il modello.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun cancel() {
        vm.deletePhotoFiles(newPhotos)
        vm.screen = Screen.Main
    }

    fun save() {
        val parsedValue = valueText.trim().replace(',', '.').toDoubleOrNull()
        val base = existing ?: Item(name = "", residenceId = residenceId)
        val item = base.copy(
            name = name.trim(),
            description = description.trim(),
            category = category.trim(),
            room = room.trim(),
            quantity = quantity.coerceAtLeast(1),
            value = parsedValue,
            warrantyUntil = warranty,
            favorite = favorite,
            photos = photos.toList(),
            residenceId = residenceId,
        )
        val originalPhotos = existing?.photos ?: emptyList()
        val toDelete = newPhotos.filter { it !in photos } + originalPhotos.filter { it !in photos }
        vm.upsertItem(item)
        vm.deletePhotoFiles(toDelete)
        vm.screen = Screen.Main
    }

    BackHandler(onBack = { cancel() })

    val categorySuggestions = (DEFAULT_CATEGORIES + data.items.map { it.category.trim() })
        .filter { it.isNotEmpty() }
        .distinct()

    val roomSuggestions = (
        data.items.filter { it.residenceId == residenceId }.map { it.room.trim() } + DEFAULT_ROOMS
        ).filter { it.isNotEmpty() }.distinct()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nuovo oggetto" else "Modifica oggetto") },
                navigationIcon = {
                    IconButton(onClick = { cancel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (existing != null) {
                        TextButton(onClick = {
                            vm.deletePhotoFiles(newPhotos)
                            vm.duplicateItem(existing.id)
                            vm.screen = Screen.Main
                        }) { Text("Duplica") }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina oggetto")
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
            // ---- Foto ----
            if (photos.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(categoryEmoji(category), style = MaterialTheme.typography.displayMedium)
                        Text(
                            "Nessuna foto",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(photos, key = { it }) { p ->
                        Box(
                            Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewerPhoto = p },
                        ) {
                            PhotoImage(p, Modifier.fillMaxSize(), maxPx = 600)
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { photos = ArrayList(photos.filter { it != p }) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Rimuovi foto",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                "✨ Ordina",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                    .clickable { tidyPhoto(p) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            if (p == photos.first()) {
                                Text(
                                    "Copertina",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            } else {
                                Text(
                                    "Imposta copertina",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            photos = ArrayList(listOf(p) + photos.filter { it != p })
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
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
            }

            if (tidying) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ---- Dati principali ----
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                singleLine = true,
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
                    items(categorySuggestions) { s ->
                        FilterChip(
                            selected = category.trim().equals(s, ignoreCase = true),
                            onClick = { category = s },
                            label = { Text(s) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Stanza o posizione") },
                    placeholder = { Text("Es. Cantina, scaffale 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(roomSuggestions) { s ->
                        FilterChip(
                            selected = room.trim().equals(s, ignoreCase = true),
                            onClick = { room = s },
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
                            label = { Text("${r.icon} ${r.name}") },
                        )
                    }
                }
            }

            // ---- Quantita' e valore ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = { if (quantity > 1) quantity -= 1 }) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            quantity.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Quantità", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalIconButton(onClick = { if (quantity < 9999) quantity += 1 }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { v -> valueText = v.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Valore") },
                    prefix = { Text("€ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            // ---- Garanzia ----
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Garanzia", style = MaterialTheme.typography.titleSmall)
                val w = warranty
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (w == null) "Imposta scadenza" else warrantyLabel(w))
                    }
                    if (w != null) {
                        TextButton(onClick = { warranty = null }) { Text("Rimuovi") }
                    }
                }
            }

            FilterChip(
                selected = favorite,
                onClick = { favorite = !favorite },
                label = { Text("Preferito") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = if (favorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Note e descrizione") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { save() },
                enabled = name.isNotBlank() && residenceId.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salva") }
        }
    }

    val viewing = viewerPhoto
    if (viewing != null) {
        FullScreenPhoto(name = viewing, onClose = { viewerPhoto = null })
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = warranty)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    warranty = pickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annulla") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare questo oggetto?") },
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
