@file:OptIn(ExperimentalMaterial3Api::class)

package com.elerto.homemanager

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun OverviewScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by vm.data.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        for (m in vm.messages) snackbar.showSnackbar(m)
    }

    BackHandler { vm.screen = Screen.Main }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImport = uri
    }

    val items = data.items
    val totalQty = items.sumOf { it.quantity }
    val totalValue = items.sumOf { (it.value ?: 0.0) * it.quantity }
    val withPhoto = items.count { it.photos.isNotEmpty() }

    val byResidence = data.residences.map { r ->
        val list = items.filter { it.residenceId == r.id }
        Triple("${r.icon} ${r.name}", list.sumOf { it.quantity }, list.sumOf { (it.value ?: 0.0) * it.quantity })
    }
    val maxResidenceQty = (byResidence.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val byCategory = items
        .groupBy { it.category.trim().ifEmpty { "Senza categoria" } }
        .map { (cat, list) -> cat to list.sumOf { it.quantity } }
        .sortedByDescending { it.second }
        .take(8)
    val maxCategoryQty = (byCategory.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val residenceNames = data.residences.associate { it.id to it.name }
    val warranties = items
        .filter { it.warrantyUntil != null && daysUntil(it.warrantyUntil) <= 90L }
        .sortedBy { it.warrantyUntil }
        .take(10)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riepilogo e backup") },
                navigationIcon = {
                    IconButton(onClick = { vm.screen = Screen.Main }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("Panoramica") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        BigNumber(totalQty.toString())
                        Text("oggetti", style = MaterialTheme.typography.labelMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        BigNumber(if (totalValue > 0.0) money(totalValue) else "—")
                        Text("valore stimato", style = MaterialTheme.typography.labelMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        BigNumber(if (items.isEmpty()) "—" else "${withPhoto * 100 / items.size}%")
                        Text("con foto", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (byResidence.isNotEmpty()) {
                SectionCard("Per residenza") {
                    byResidence.forEach { (label, qty, value) ->
                        BarRow(
                            label = label,
                            detail = if (value > 0.0) "$qty · ${money(value)}" else "$qty",
                            fraction = qty.toFloat() / maxResidenceQty,
                        )
                    }
                }
            }

            if (byCategory.isNotEmpty()) {
                SectionCard("Per categoria") {
                    byCategory.forEach { (cat, qty) ->
                        BarRow(
                            label = "${categoryEmoji(cat)} $cat",
                            detail = qty.toString(),
                            fraction = qty.toFloat() / maxCategoryQty,
                        )
                    }
                }
            }

            if (warranties.isNotEmpty()) {
                SectionCard("Garanzie in scadenza") {
                    warranties.forEach { item ->
                        val w = item.warrantyUntil ?: return@forEach
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${warrantyLabel(w)} · ${residenceNames[item.residenceId].orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (daysUntil(w) < 0L) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionCard("Esporta e backup") {
                Text(
                    "Il backup contiene tutti gli oggetti e le foto in un unico file ZIP: salvalo su Drive o sul computer per non perdere nulla se cambi telefono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        exportLauncher.launch("HomeInventory-${LocalDate.now()}.zip")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Crea backup completo") }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ripristina da backup") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) { Csv.write(ctx, data) }
                                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(Intent.createChooser(send, "Esporta inventario (CSV)"))
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Esportazione non riuscita", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Esporta in CSV (Excel)") }
            }

            Text(
                "Home Inventory · i dati restano solo su questo telefono",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    val importUri = pendingImport
    if (importUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Ripristinare il backup?") },
            text = {
                Text("Tutti i dati attuali verranno sostituiti con quelli del backup. Ti consiglio di creare prima un backup dello stato attuale.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.importBackup(importUri)
                    pendingImport = null
                }) { Text("Ripristina") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun BigNumber(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BarRow(label: String, detail: String, fraction: Float) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}
