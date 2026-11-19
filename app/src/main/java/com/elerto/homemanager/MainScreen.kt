@file:OptIn(ExperimentalMaterial3Api::class)

package com.elerto.homemanager

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Stato del trascinamento: quale oggetto si sta muovendo e dove si trova il dito (coordinate root). */
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

    /** Se il dito e' sopra un altro oggetto, sposta quello trascinato nella sua posizione. */
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

private fun oggetti(n: Int) = if (n == 1) "1 oggetto" else "$n oggetti"

private fun matches(item: Item, query: String): Boolean {
    val q = query.lowercase()
    return item.name.lowercase().contains(q) ||
        item.description.lowercase().contains(q) ||
        item.category.lowercase().contains(q) ||
        item.room.lowercase().contains(q)
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val data by vm.data.collectAsState()
    val residences = data.residences
    val selectedId = data.selectedResidenceId?.takeIf { id -> residences.any { it.id == id } }
        ?: residences.firstOrNull()?.id
    val selected = residences.firstOrNull { it.id == selectedId }
    val residenceNames = remember(residences) { residences.associate { it.id to it.name } }

    val query = vm.query.trim()
    val searchActive = vm.searching && query.isNotEmpty()

    val counts = remember(data.items) {
        data.items.groupBy { it.residenceId }.mapValues { e -> e.value.sumOf { it.quantity } }
    }
    val residenceItems = data.items.filter { it.residenceId == selectedId }
    val categories = residenceItems.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    val hasFavorites = residenceItems.any { it.favorite }
    val activeFilter = vm.filter?.takeIf { (it == FAV_FILTER && hasFavorites) || it in categories }

    val filtered = when {
        searchActive -> data.items.filter { matches(it, query) }
        activeFilter == FAV_FILTER -> residenceItems.filter { it.favorite }
        activeFilter != null -> residenceItems.filter { it.category.trim() == activeFilter }
        else -> residenceItems
    }
    val shown = sortItems(filtered, data.sort)
    val canReorder = data.sort == "manual" && !searchActive

    val totalQty = residenceItems.sumOf { it.quantity }
    val totalValue = residenceItems.sumOf { (it.value ?: 0.0) * it.quantity }
    val expiring = residenceItems.count { i ->
        val w = i.warrantyUntil
        w != null && daysUntil(w) in 0L..60L
    }

    var dialog by remember { mutableStateOf<ResidenceDialogState?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val drag = remember { DragState() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val gridBounds = remember { arrayOf(Rect.Zero) }
    val searchFocus = remember { FocusRequester() }

    drag.onReorder = { itemId, targetId -> if (canReorder) vm.reorderItem(itemId, targetId) }

    LaunchedEffect(Unit) {
        for (m in vm.messages) snackbar.showSnackbar(m)
    }

    LaunchedEffect(vm.searching) {
        if (vm.searching) {
            try {
                searchFocus.requestFocus()
            } catch (e: Exception) {
                // Il campo non e' ancora pronto: l'utente puo' toccarlo.
            }
        }
    }

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
                title = { Text("Home Inventory", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        vm.searching = !vm.searching
                        if (!vm.searching) vm.query = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Cerca")
                    }
                    IconButton(onClick = { vm.screen = Screen.Overview }) {
                        Icon(Icons.Default.Info, contentDescription = "Riepilogo e backup")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Altro")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text(
                                "Ordina per",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            SortMenuItem("Ordine manuale", "manual", data.sort) { vm.setSort(it); menuOpen = false }
                            SortMenuItem("Nome A–Z", "name", data.sort) { vm.setSort(it); menuOpen = false }
                            SortMenuItem("Più recenti", "recent", data.sort) { vm.setSort(it); menuOpen = false }
                            SortMenuItem("Valore più alto", "value", data.sort) { vm.setSort(it); menuOpen = false }
                            SortMenuItem("Preferiti prima", "favorites", data.sort) { vm.setSort(it); menuOpen = false }
                            if (selected != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Modifica residenza") },
                                    onClick = {
                                        menuOpen = false
                                        dialog = ResidenceDialogState.Edit(selected)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (selectedId != null && drag.item == null) {
                ExtendedFloatingActionButton(
                    onClick = { vm.screen = Screen.Editor(itemId = null, residenceId = selectedId) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Aggiungi") },
                )
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
                    onSelect = {
                        vm.selectResidence(it)
                        vm.filter = null
                    },
                    onAdd = { dialog = ResidenceDialogState.Add },
                )

                if (vm.searching) {
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        placeholder = { Text("Cerca in tutte le residenze") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                vm.query = ""
                                vm.searching = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Chiudi ricerca")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .focusRequester(searchFocus),
                    )
                }

                if (selectedId == null) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🏠", style = MaterialTheme.typography.displayMedium)
                            Text(
                                "Benvenuto in Home Inventory",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                "Inizia aggiungendo una residenza, ad esempio \"Casa in città\" o \"Casa al mare\". Poi cataloga i tuoi oggetti con foto, valore e garanzia, e spostali da una casa all'altra trascinandoli.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                            Button(onClick = { dialog = ResidenceDialogState.Add }) {
                                Text("Aggiungi residenza")
                            }
                        }
                    }
                } else {
                    if (!searchActive && (categories.isNotEmpty() || hasFavorites)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = activeFilter == null,
                                    onClick = { vm.filter = null },
                                    label = { Text("Tutti") },
                                )
                            }
                            if (hasFavorites) {
                                item {
                                    FilterChip(
                                        selected = activeFilter == FAV_FILTER,
                                        onClick = { vm.filter = FAV_FILTER },
                                        label = { Text("Preferiti") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                    )
                                }
                            }
                            items(categories) { c ->
                                FilterChip(
                                    selected = activeFilter == c,
                                    onClick = { vm.filter = c },
                                    label = { Text("${categoryEmoji(c)} $c") },
                                )
                            }
                        }
                    }

                    if (residenceItems.isEmpty() && !searchActive) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(selected?.icon ?: "📦", style = MaterialTheme.typography.displayMedium)
                                Text(
                                    "${selected?.name.orEmpty()} è ancora vuota",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Text(
                                    "Premi \"Aggiungi\" per inserire il primo oggetto: puoi aggiungere più foto, quantità, valore e scadenza della garanzia.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    } else if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (searchActive) "Nessun risultato per \"$query\"" else "Nessun oggetto in questa selezione",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
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
                            if (!searchActive) {
                                item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                                    SummaryCard(qty = totalQty, value = totalValue, expiring = expiring)
                                }
                            }
                            items(shown, key = { it.id }) { item ->
                                ItemCard(
                                    item = item,
                                    residenceName = if (searchActive) residenceNames[item.residenceId] else null,
                                    drag = drag,
                                    modifier = Modifier.animateItem(),
                                    onFavorite = { vm.toggleFavorite(item.id) },
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
                    PhotoImage(dragged.photo, Modifier.fillMaxSize(), maxPx = 300, category = dragged.category)
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
            onSave = { name, icon ->
                if (editing != null) vm.renameResidence(editing.id, name, icon) else vm.addResidence(name, icon)
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
private fun SortMenuItem(label: String, value: String, current: String, onPick: (String) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onPick(value) },
        trailingIcon = {
            if (value == current) Icon(Icons.Default.Check, contentDescription = null)
        },
    )
}

@Composable
private fun SummaryCard(qty: Int, value: Double, expiring: Int) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Oggetti", qty.toString())
                Stat("Valore stimato", if (value > 0.0) money(value) else "—")
                Stat("Garanzie in scadenza", expiring.toString())
            }
            Text(
                "Tieni premuto un oggetto: trascinalo su un altro per riordinare, o su una residenza in alto per spostarlo.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp).alpha(0.75f),
            )
        }
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
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
                shape = RoundedCornerShape(18.dp),
                color = when {
                    hovered -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = BorderStroke(
                    if (hovered) 3.dp else 1.dp,
                    if (hovered || isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.onGloballyPositioned { drag.chipBounds[r.id] = it.boundsInRoot() },
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(r.icon, style = MaterialTheme.typography.titleLarge)
                    Column {
                        Text(
                            r.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(oggetti(counts[r.id] ?: 0), style = MaterialTheme.typography.bodySmall)
                    }
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
private fun ItemCard(
    item: Item,
    residenceName: String?,
    drag: DragState,
    modifier: Modifier = Modifier,
    onFavorite: () -> Unit,
    onOpen: () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val isDragged = drag.item?.id == item.id
    DisposableEffect(item.id) { onDispose { drag.cardBounds.remove(item.id) } }

    val warrantyDays = item.warrantyUntil?.let { daysUntil(it) }
    val subtitle = listOf(item.category.trim(), item.room.trim()).filter { it.isNotEmpty() }.joinToString(" · ")

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
            Box {
                PhotoImage(
                    item.photo,
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    maxPx = 480,
                    category = item.category,
                )

                // Preferito
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { onFavorite() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = if (item.favorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                        tint = if (item.favorite) Color(0xFFFFC107) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Quantita'
                if (item.quantity > 1) {
                    Text(
                        "×${item.quantity}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                // Garanzia scaduta o in scadenza
                if (warrantyDays != null && warrantyDays <= 60L) {
                    val expired = warrantyDays < 0L
                    Text(
                        if (expired) "Garanzia scaduta" else "Garanzia ${warrantyDays}g",
                        color = if (expired) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (residenceName != null) {
                    Text(
                        residenceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val v = item.value
                if (v != null) {
                    Text(
                        money(v * item.quantity),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoImage(name: String?, modifier: Modifier, maxPx: Int = 480, category: String = "") {
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
            modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(categoryEmoji(category), style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Composable
private fun ResidenceDialog(
    state: ResidenceDialogState,
    itemCount: Int,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val editing = (state as? ResidenceDialogState.Edit)?.residence
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var icon by remember { mutableStateOf(editing?.icon ?: RESIDENCE_ICONS.first()) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare ${editing.name}?") },
            text = {
                Text(
                    if (itemCount > 0) "Verranno eliminati anche ${oggetti(itemCount)} presenti in questa residenza. L'operazione non si può annullare."
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome") },
                        singleLine = true,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RESIDENCE_ICONS) { e ->
                            FilterChip(
                                selected = icon == e,
                                onClick = { icon = e },
                                label = { Text(e, style = MaterialTheme.typography.titleMedium) },
                            )
                        }
                    }
                    if (editing != null) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Elimina residenza", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave(name, icon) }, enabled = name.isNotBlank()) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            },
        )
    }
}
