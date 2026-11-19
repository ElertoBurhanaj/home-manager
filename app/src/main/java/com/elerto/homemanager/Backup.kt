package com.elerto.homemanager

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup completo in un unico file ZIP: data.json + cartella photos/. */
object Backup {

    fun export(ctx: Context, data: AppData, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(Codec.encode(data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val names = data.items.flatMap { it.photos }.distinct()
            for (name in names) {
                val f = PhotoStore.fileOf(ctx, name)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry("photos/$name"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Legge un backup: copia le foto nella memoria dell'app e restituisce i dati, o null se non valido. */
    fun import(ctx: Context, input: InputStream): AppData? {
        var json: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (!entry.isDirectory) {
                    if (entryName == "data.json") {
                        json = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (entryName.startsWith("photos/")) {
                        // Solo il nome del file: evita percorsi con ".." nel backup.
                        val base = File(entryName).name
                        if (base.isNotEmpty()) {
                            FileOutputStream(PhotoStore.fileOf(ctx, base)).use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val text = json ?: return null
        return Codec.decode(text)
    }
}

/** Esportazione dell'inventario in CSV (apribile con Excel e Fogli Google). */
object Csv {

    fun write(ctx: Context, data: AppData): File {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "home-inventory.csv")
        val residenceNames = data.residences.associate { it.id to it.name }

        fun cell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

        val header = listOf(
            "Residenza", "Stanza", "Nome", "Categoria", "Quantità", "Valore unitario",
            "Valore totale", "Garanzia fino al", "Preferito", "Descrizione",
        )
        val sb = StringBuilder("﻿")
        sb.append(header.joinToString(";") { cell(it) }).append("\r\n")
        for (i in data.items) {
            val row = listOf(
                residenceNames[i.residenceId] ?: "",
                i.room,
                i.name,
                i.category,
                i.quantity.toString(),
                i.value?.let { decimal2(it) } ?: "",
                i.value?.let { decimal2(it * i.quantity) } ?: "",
                i.warrantyUntil?.let { formatDate(it) } ?: "",
                if (i.favorite) "Sì" else "",
                i.description,
            )
            sb.append(row.joinToString(";") { cell(it) }).append("\r\n")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }
}
