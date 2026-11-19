package com.elerto.homemanager

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

val DEFAULT_CATEGORIES = listOf(
    "Elettronica", "Elettrodomestici", "Mobili", "Cucina", "Abbigliamento", "Scarpe",
    "Libri", "Attrezzi", "Giochi", "Sport", "Gioielli", "Documenti", "Decorazioni", "Altro",
)

val DEFAULT_ROOMS = listOf(
    "Soggiorno", "Cucina", "Camera da letto", "Bagno", "Studio", "Garage", "Cantina", "Ripostiglio", "Balcone",
)

val RESIDENCE_ICONS = listOf("🏠", "🏢", "🏡", "🏖️", "🏔️", "🚗", "📦", "🏕️")

const val FAV_FILTER = "__fav__"
const val DAY_MS = 86_400_000L

data class Residence(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "🏠",
)

data class Item(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val category: String = "",
    val photos: List<String> = emptyList(),
    val residenceId: String,
    val room: String = "",
    val quantity: Int = 1,
    val value: Double? = null,
    /** Fine garanzia, in millisecondi UTC (mezzanotte del giorno). */
    val warrantyUntil: Long? = null,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val photo: String? get() = photos.firstOrNull()
}

data class AppData(
    val residences: List<Residence> = emptyList(),
    val items: List<Item> = emptyList(),
    val selectedResidenceId: String? = null,
    val sort: String = "manual",
)

// ---- Utilita' di formattazione ----

fun categoryEmoji(category: String): String {
    val c = category.lowercase()
    return when {
        "elettrodom" in c -> "🔌"
        "elettron" in c || "informat" in c || "telefon" in c -> "💻"
        "mobil" in c || "arred" in c || "divan" in c -> "🛋️"
        "cucina" in c || "stoviglie" in c || "pentol" in c -> "🍳"
        "scarp" in c -> "👟"
        "magliett" in c || "camic" in c || "maglion" in c || "pantalon" in c || "gonn" in c ||
            "vestit" in c || "giacc" in c || "cappott" in c || "abbigl" in c || "intimo" in c -> "👕"
        "accessor" in c -> "👜"
        "libr" in c -> "📚"
        "attrez" in c || "bricol" in c || "utens" in c -> "🔧"
        "gioc" in c -> "🧸"
        "sport" in c -> "⚽"
        "gioiell" in c || "orolog" in c -> "💍"
        "document" in c -> "📄"
        "decor" in c || "quadr" in c -> "🖼️"
        else -> "📦"
    }
}

fun money(v: Double): String = NumberFormat.getCurrencyInstance(Locale.ITALY).format(v)

/** Numero con due decimali e virgola (per CSV). */
fun decimal2(v: Double): String = String.format(Locale.ITALY, "%.2f", v)

/** Numero "pulito" per i campi di testo: 12 oppure 12,5. */
fun plainNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')

fun formatDate(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALY))

/** Giorni rimanenti alla fine della garanzia (0 = scade oggi, negativo = scaduta). */
fun daysUntil(ms: Long): Long = Math.floorDiv(ms + DAY_MS - System.currentTimeMillis(), DAY_MS)

fun warrantyLabel(ms: Long): String {
    val d = daysUntil(ms)
    return when {
        d < 0 -> "Garanzia scaduta"
        d == 0L -> "Garanzia: scade oggi"
        d <= 60 -> "Garanzia: ancora $d giorni"
        else -> "In garanzia fino al ${formatDate(ms)}"
    }
}

fun sortItems(list: List<Item>, sort: String): List<Item> = when (sort) {
    "name" -> list.sortedBy { it.name.lowercase() }
    "recent" -> list.sortedByDescending { it.createdAt }
    "value" -> list.sortedByDescending { (it.value ?: 0.0) * it.quantity }
    "favorites" -> list.sortedByDescending { it.favorite }
    else -> list
}

// ---- Serializzazione ----

object Codec {

    fun encode(d: AppData): String = toJson(d).toString()

    fun decode(s: String): AppData = parse(JSONObject(s))

    private fun parse(o: JSONObject): AppData {
        val rs = o.optJSONArray("residences") ?: JSONArray()
        val residences = (0 until rs.length()).map { i ->
            val r = rs.getJSONObject(i)
            Residence(
                id = r.getString("id"),
                name = r.getString("name"),
                icon = r.optString("icon", "🏠").ifEmpty { "🏠" },
            )
        }
        val its = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until its.length()).map { i ->
            val r = its.getJSONObject(i)
            // Compatibilita': le vecchie versioni salvavano una sola foto nel campo "photo".
            val photos = ArrayList<String>()
            val arr = r.optJSONArray("photos")
            if (arr != null) {
                for (k in 0 until arr.length()) photos.add(arr.getString(k))
            } else if (!r.isNull("photo")) {
                photos.add(r.getString("photo"))
            }
            Item(
                id = r.getString("id"),
                name = r.getString("name"),
                description = r.optString("description", ""),
                category = r.optString("category", ""),
                photos = photos,
                residenceId = r.getString("residenceId"),
                room = r.optString("room", ""),
                quantity = r.optInt("quantity", 1).coerceAtLeast(1),
                value = if (r.isNull("value")) null else r.getDouble("value"),
                warrantyUntil = if (r.isNull("warranty")) null else r.getLong("warranty"),
                favorite = r.optBoolean("favorite", false),
                createdAt = r.optLong("createdAt", 0L),
            )
        }
        val selected = if (o.isNull("selected")) null else o.getString("selected")
        return AppData(residences, items, selected, o.optString("sort", "manual"))
    }

    private fun toJson(d: AppData): JSONObject {
        val rs = JSONArray()
        d.residences.forEach {
            rs.put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon))
        }
        val its = JSONArray()
        d.items.forEach {
            val photos = JSONArray()
            it.photos.forEach { p -> photos.put(p) }
            its.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("description", it.description)
                    .put("category", it.category)
                    .put("photos", photos)
                    .put("residenceId", it.residenceId)
                    .put("room", it.room)
                    .put("quantity", it.quantity)
                    .put("value", it.value ?: JSONObject.NULL)
                    .put("warranty", it.warrantyUntil ?: JSONObject.NULL)
                    .put("favorite", it.favorite)
                    .put("createdAt", it.createdAt)
            )
        }
        return JSONObject()
            .put("residences", rs)
            .put("items", its)
            .put("selected", d.selectedResidenceId ?: JSONObject.NULL)
            .put("sort", d.sort)
    }
}

/** Salva e legge i dati in un unico file JSON nella memoria privata dell'app. */
class Store(private val file: File) {

    fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            Codec.decode(file.readText())
        } catch (e: Exception) {
            // File danneggiato: lo metto da parte invece di perderlo.
            file.renameTo(File(file.parentFile, file.name + ".corrupt"))
            AppData()
        }
    }

    fun save(data: AppData) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Codec.encode(data))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
