package com.elerto.homemanager

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface Screen {
    data object Main : Screen
    data object Overview : Screen
    data class Editor(val itemId: String?, val residenceId: String) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(File(app.filesDir, "data.json"))
    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    var screen by mutableStateOf<Screen>(Screen.Main)

    // Stato dell'interfaccia che deve sopravvivere al passaggio tra le schermate.
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var filter by mutableStateOf<String?>(null)

    /** Messaggi brevi da mostrare all'utente (esito di backup e ripristino). */
    val messages = Channel<String>(Channel.BUFFERED)

    // Coda di salvataggio: scrive sempre l'ultimo stato, in ordine, fuori dal thread principale.
    private val saveQueue = Channel<AppData>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (snapshot in saveQueue) store.save(snapshot)
        }
    }

    private fun update(block: (AppData) -> AppData) {
        val next = _data.updateAndGet { block(it) }
        saveQueue.trySend(next)
    }

    fun deletePhotoFiles(names: List<String>) {
        if (names.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            names.forEach { PhotoStore.delete(ctx, it) }
        }
    }

    // ---- Preferenze di visualizzazione ----

    fun setSort(sort: String) = update { it.copy(sort = sort) }

    // ---- Residenze ----

    fun selectResidence(id: String) = update { it.copy(selectedResidenceId = id) }

    fun addResidence(name: String, icon: String) {
        val residence = Residence(name = name.trim(), icon = icon)
        update {
            it.copy(
                residences = it.residences + residence,
                selectedResidenceId = residence.id,
            )
        }
    }

    fun renameResidence(id: String, name: String, icon: String) = update { d ->
        d.copy(
            residences = d.residences.map {
                if (it.id == id) it.copy(name = name.trim(), icon = icon) else it
            },
        )
    }

    fun deleteResidence(id: String) {
        val photos = _data.value.items.filter { it.residenceId == id }.flatMap { it.photos }
        update { d ->
            d.copy(
                residences = d.residences.filter { it.id != id },
                items = d.items.filter { it.residenceId != id },
                selectedResidenceId = if (d.selectedResidenceId == id) null else d.selectedResidenceId,
            )
        }
        deletePhotoFiles(photos)
    }

    // ---- Oggetti ----

    fun upsertItem(item: Item) = update { d ->
        if (d.items.any { it.id == item.id }) {
            d.copy(items = d.items.map { if (it.id == item.id) item else it })
        } else {
            d.copy(items = d.items + item)
        }
    }

    fun deleteItem(id: String) {
        val photos = _data.value.items.firstOrNull { it.id == id }?.photos.orEmpty()
        update { d -> d.copy(items = d.items.filter { it.id != id }) }
        deletePhotoFiles(photos)
    }

    fun toggleFavorite(id: String) = update { d ->
        d.copy(items = d.items.map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    }

    /** Crea una copia dell'oggetto (con copie indipendenti delle foto) subito dopo l'originale. */
    fun duplicateItem(id: String) {
        val src = _data.value.items.firstOrNull { it.id == id } ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val copies = src.photos.mapNotNull { PhotoStore.copy(ctx, it) }
            val dup = src.copy(
                id = UUID.randomUUID().toString(),
                name = src.name + " (copia)",
                photos = copies,
                createdAt = System.currentTimeMillis(),
            )
            update { d ->
                val list = d.items.toMutableList()
                val idx = list.indexOfFirst { it.id == id }
                list.add(if (idx < 0) list.size else idx + 1, dup)
                d.copy(items = list)
            }
        }
    }

    /** Sposta un oggetto nella posizione di un altro (riordino manuale). */
    fun reorderItem(itemId: String, targetId: String) = update { d ->
        val from = d.items.indexOfFirst { it.id == itemId }
        val to = d.items.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) {
            d
        } else {
            val list = d.items.toMutableList()
            val moved = list.removeAt(from)
            list.add(to, moved)
            d.copy(items = list)
        }
    }

    fun moveItem(itemId: String, residenceId: String) = update { d ->
        d.copy(items = d.items.map { if (it.id == itemId) it.copy(residenceId = residenceId) else it })
    }

    // ---- Backup ----

    fun exportBackup(uri: Uri) {
        val ctx = getApplication<Application>()
        val snapshot = _data.value
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val os = ctx.contentResolver.openOutputStream(uri)
                if (os == null) {
                    false
                } else {
                    os.use { Backup.export(ctx, snapshot, it) }
                    true
                }
            } catch (e: Exception) {
                false
            }
            messages.trySend(if (ok) "Backup salvato" else "Backup non riuscito")
        }
    }

    /** Ripristina un backup sostituendo tutti i dati attuali. */
    fun importBackup(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val restored = try {
                ctx.contentResolver.openInputStream(uri)?.use { Backup.import(ctx, it) }
            } catch (e: Exception) {
                null
            }
            if (restored == null) {
                messages.trySend("Backup non valido o illeggibile")
                return@launch
            }
            val oldPhotos = _data.value.items.flatMap { it.photos }.toSet()
            val newPhotos = restored.items.flatMap { it.photos }.toSet()
            update { restored }
            oldPhotos.filter { it !in newPhotos }.forEach { PhotoStore.delete(ctx, it) }
            messages.trySend("Backup ripristinato: ${restored.items.size} oggetti")
        }
    }
}
