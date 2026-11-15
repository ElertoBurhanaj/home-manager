@file:OptIn(ExperimentalMaterial3Api::class)

package com.elerto.homemanager

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Stato del trascinamento: quale capo si sta muovendo e dove si trova il dito (coordinate root). */
class DragState {
    var item by mutableStateOf<Item?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var containerOrigin: Offset = Offset.Zero
    val chipBounds = HashMap<String, Rect>()
    val cardBounds = HashMap<String, Rect>()
    var lastDragEnd: Long = 0L
    var lastReorder: Long = 0L
    var onDrop: (Item, String) -> Unit = { _, _ -> }
    var onReorder: (String, String) -> Unit = { _, _ -> }

    val hovered: String?
        get() = if (item == null) null else chipBounds.entries.firstOrNull { it.value.contains(pointer) }?.key

    /** Se il dito e' sopra un altro capo, sposta il capo trascinato nella sua posizione. */
    fun checkReorder() {
        val dragged = item ?: return
        if (hovered != null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastReorder < 250) return
        val target = cardBounds.entries
            .firstOrNull { it.key != dragged.id && it.value.contains(pointer) }
            ?.key ?: return
        lastReorder = now
        onReorder(dragged.id, target)
    }

    fun start(i: Item, p: Offset) {
        item = i
        pointer = p
    }

    fun finish() {
        val dragged = item
        val target = hovered
        item = null
        lastDragEnd = SystemClock.uptimeMillis()
        if (dragged != null && target != null && target != dragged.residenceId) {
            onDrop(dragged, target)
        }
    }

    fun cancel() {
        item = null
        lastDragEnd = SystemClock.uptimeMillis()
    }
}

private sealed interface ResidenceDialogState {
    data object Add : ResidenceDialogState
    data class Edit(val residence: Residence) : ResidenceDialogState
}

private fun capi(n: Int) = if (n == 1) "1 capo" else "$n capi"

@Composable
fun MainScreen(vm: MainViewModel) {
    val data by vm.data.collectAsState()
    val residences = data.residences
    val selectedId = data.selectedResidenceId?.takeIf { id -> residences.any { it.id == id } }
        ?: residences.firstOrNull()?.id
    val selected = residences.firstOrNull { it.id == selectedId }

    val counts = remember(data.items) { data.items.groupingBy { it.residenceId }.eachCount() }
    val items = data.items.filter { it.residenceId == selectedId }
    val categories = items.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    var filter by remember { mutableStateOf<String?>(null) }
    val activeFilter = filter?.takeIf { it in categories }
    val shown = if (activeFilter == null) items else items.filter { it.category.trim() == activeFilter }

    var dialog by remember { mutableStateOf<ResidenceDialogState?>(null) }
    val drag = remember { DragState() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val gridBounds = remember { arrayOf(Rect.Zero) }

    drag.onReorder = { itemId, targetId -> vm.reorderItem(itemId, targetId) }

    // Durante il trascinamento: scorre la griglia ai bordi e controlla il riordino.
    LaunchedEffect(drag.item?.id) {
        if (drag.item == null) return@LaunchedEffect
        while (isActive) {
            val p = drag.pointer
            val b = gridBounds[0]
            val edge = 140f
            val inColumn = p.x >= b.left && p.x <= b.right
            val dy = when {
                !inColumn -> 0f
                p.y >= b.top && p.y < b.top + edge -> -20f
                p.y <= b.bottom && p.y > b.bottom - edge -> 20f
                else -> 0f
            }
            if (dy != 0f) gridState.scrollBy(dy)
            drag.checkReorder()
            delay(16)
        }
    }

    drag.onDrop = { item, target ->
        vm.moveItem(item.id, target)
        val targetName = residences.firstOrNull { it.id == target }?.name.orEmpty()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("${item.name} spostato in $targetName")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guardaroba") },
                actions = {
                    if (selected != null) {
                        IconButton(onClick = { dialog = ResidenceDialogState.Edit(selected) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifica residenza")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (selectedId != null && drag.item == null) {
                FloatingActionButton(
                    onClick = { vm.screen = Screen.Editor(itemId = null, residenceId = selectedId) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi capo")
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .onGloballyPositioned { drag.containerOrigin = it.positionInRoot() },
        ) {
            Column(Modifier.fillMaxSize()) {
                ResidenceBar(
                    residences = residences,
                    counts = counts,
                    selectedId = selectedId,
                    drag = drag,
                    onSelect = { vm.selectResidence(it) },
                    onAdd = { dialog = ResidenceDialogState.Add },
                )

                if (selectedId == null) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Inizia aggiungendo una residenza",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Ad esempio \"Casa al mare\" e \"Casa in città\". Poi potrai inserire i capi e spostarli trascinandoli da una residenza all'altra.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                            Button(onClick = { dialog = ResidenceDialogState.Add }) {
                                Text("Aggiungi residenza")
                            }
                        }
                    }
                } else {
                    if (categories.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = activeFilter == null,
                                    onClick = { filter = null },
                                    label = { Text("Tutte") },
                                )
                            }
                            items(categories) { c ->
                                FilterChip(
                                    selected = activeFilter == c,
                                    onClick = { filter = c },
                                    label = { Text(c) },
                                )
                            }
                        }
                    }

                    if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Nessun capo in ${selected?.name.orEmpty()}.\nPremi + per aggiungerne uno.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    } else {
                        Text(
                            "Tieni premuto un capo: trascinalo su un altro capo per cambiare l'ordine, o su una residenza in alto per spostarlo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { gridBounds[0] = it.boundsInRoot() },
                        ) {
                            items(shown, key = { it.id }) { item ->
                                ItemCard(
                                    item = item,
                                    drag = drag,
                                    modifier = Modifier.animateItem(),
                                    onOpen = {
                                        if (SystemClock.uptimeMillis() - drag.lastDragEnd > 500) {
                                            vm.screen = Screen.Editor(item.id, item.residenceId)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Anteprima che segue il dito durante il trascinamento.
            val dragged = drag.item
            if (dragged != null) {
                val density = LocalDensity.current
                val half = with(density) { 56.dp.toPx() }
                val local = drag.pointer - drag.containerOrigin
                Box(
                    Modifier
                        .offset { IntOffset((local.x - half).roundToInt(), (local.y - half).roundToInt()) }
                        .size(112.dp)
                        .shadow(10.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    PhotoImage(dragged.photo, Modifier.fillMaxSize(), maxPx = 300)
                    Text(
                        dragged.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .padding(4.dp),
                    )
                }
            }
        }
    }

    dialog?.let { state ->
        val editing = (state as? ResidenceDialogState.Edit)?.residence
        ResidenceDialog(
            state = state,
            itemCount = if (editing != null) counts[editing.id] ?: 0 else 0,
            onDismiss = { dialog = null },
            onSave = { name ->
                if (editing != null) vm.renameResidence(editing.id, name) else vm.addResidence(name)
                dialog = null
            },
            onDelete = { id ->
                vm.deleteResidence(id)
                dialog = null
            },
        )
    }
}

@Composable
private fun ResidenceBar(
    residences: List<Residence>,
    counts: Map<String, Int>,
    selectedId: String?,
    drag: DragState,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(residences, key = { it.id }) { r ->
            val hovered = drag.hovered == r.id && drag.item?.residenceId != r.id
            val isSelected = r.id == selectedId
            DisposableEffect(r.id) { onDispose { drag.chipBounds.remove(r.id) } }

            Surface(
                onClick = { onSelect(r.id) },
                shape = RoundedCornerShape(16.dp),
                color = when {
                    hovered -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    if (hovered) 3.dp else 1.dp,
                    if (hovered || isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.onGloballyPositioned { drag.chipBounds[r.id] = it.boundsInRoot() },
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        r.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(capi(counts[r.id] ?: 0), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            FilledTonalIconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi residenza")
            }
        }
    }
}

@Composable
private fun ItemCard(item: Item, drag: DragState, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val isDragged = drag.item?.id == item.id
    DisposableEffect(item.id) { onDispose { drag.cardBounds.remove(item.id) } }

    Card(
        onClick = onOpen,
        modifier = modifier
            .onGloballyPositioned {
                origin = it.positionInRoot()
                drag.cardBounds[item.id] = it.boundsInRoot()
            }
            .alpha(if (isDragged) 0.3f else 1f)
            .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> drag.start(item, origin + offset) },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.pointer += amount
                        drag.checkReorder()
                    },
                    onDragEnd = { drag.finish() },
                    onDragCancel = { drag.cancel() },
                )
            },
    ) {
        Column {
            PhotoImage(item.photo, Modifier.fillMaxWidth().aspectRatio(1f), maxPx = 480)
            Column(Modifier.padding(10.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.category.isNotBlank()) {
                    Text(
                        item.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoImage(name: String?, modifier: Modifier, maxPx: Int = 480) {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, name) {
        value = if (name == null) null else withContext(Dispatchers.IO) { PhotoStore.thumb(ctx, name, maxPx) }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("👕", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun ResidenceDialog(
    state: ResidenceDialogState,
    itemCount: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val editing = (state as? ResidenceDialogState.Edit)?.residence
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare ${editing.name}?") },
            text = {
                Text(
                    if (itemCount > 0) "Verranno eliminati anche ${capi(itemCount)} presenti in questa residenza. L'operazione non si può annullare."
                    else "L'operazione non si può annullare."
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(editing.id) }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (editing != null) "Modifica residenza" else "Nuova residenza") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome") },
                        singleLine = true,
                    )
                    if (editing != null) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Elimina residenza", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            },
        )
    }
}
