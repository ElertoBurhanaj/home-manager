package com.elerto.homemanager

import android.app.Application
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
import kotlinx.coroutines.launch
import java.io.File

sealed interface Screen {
    data object Main : Screen
    data class Editor(val itemId: String?, val residenceId: String) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(File(app.filesDir, "data.json"))
    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    var screen by mutableStateOf<Screen>(Screen.Main)

    // Coda di salvataggio: scrive sempre l'ultimo stato, in ordine, fuori dal thread principale.
    private val saveQueue = Channel<AppData>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (snapshot in saveQueue) store.save(snapshot)
        }
    }

    private fun update(block: (AppData) -> AppData) {
        val next = block(_data.value)
        _data.value = next
        saveQueue.trySend(next)
    }

    fun deletePhotoFiles(names: List<String>) {
        if (names.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            names.forEach { PhotoStore.delete(ctx, it) }
        }
    }

    // ---- Residenze ----

    fun selectResidence(id: String) = update { it.copy(selectedResidenceId = id) }

    fun addResidence(name: String) {
        val residence = Residence(name = name.trim())
        update {
            it.copy(
                residences = it.residences + residence,
                selectedResidenceId = residence.id,
            )
        }
    }

    fun renameResidence(id: String, name: String) = update { d ->
        d.copy(residences = d.residences.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deleteResidence(id: String) {
        val photos = _data.value.items.filter { it.residenceId == id }.mapNotNull { it.photo }
        update { d ->
            d.copy(
                residences = d.residences.filter { it.id != id },
                items = d.items.filter { it.residenceId != id },
                selectedResidenceId = if (d.selectedResidenceId == id) null else d.selectedResidenceId,
            )
        }
        deletePhotoFiles(photos)
    }

    // ---- Capi ----

    fun upsertItem(item: Item) = update { d ->
        if (d.items.any { it.id == item.id }) {
            d.copy(items = d.items.map { if (it.id == item.id) item else it })
        } else {
            d.copy(items = d.items + item)
        }
    }

    fun deleteItem(id: String) {
        val photo = _data.value.items.firstOrNull { it.id == id }?.photo
        update { d -> d.copy(items = d.items.filter { it.id != id }) }
        if (photo != null) deletePhotoFiles(listOf(photo))
    }

    fun moveItem(itemId: String, residenceId: String) = update { d ->
        d.copy(items = d.items.map { if (it.id == itemId) it.copy(residenceId = residenceId) else it })
    }
}
