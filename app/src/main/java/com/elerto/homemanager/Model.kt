package com.elerto.homemanager

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

val DEFAULT_CATEGORIES = listOf(
    "Magliette", "Camicie", "Maglioni", "Pantaloni", "Gonne", "Vestiti",
    "Giacche", "Cappotti", "Scarpe", "Intimo", "Accessori", "Altro",
)

data class Residence(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
)

data class Item(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val category: String = "",
    val photo: String? = null,
    val residenceId: String,
)

data class AppData(
    val residences: List<Residence> = emptyList(),
    val items: List<Item> = emptyList(),
    val selectedResidenceId: String? = null,
)

/** Salva e legge i dati in un unico file JSON nella memoria privata dell'app. */
class Store(private val file: File) {

    fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            parse(JSONObject(file.readText()))
        } catch (e: Exception) {
            // File danneggiato: lo metto da parte invece di perderlo.
            file.renameTo(File(file.parentFile, file.name + ".corrupt"))
            AppData()
        }
    }

    fun save(data: AppData) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson(data).toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun parse(o: JSONObject): AppData {
        val rs = o.optJSONArray("residences") ?: JSONArray()
        val residences = (0 until rs.length()).map { i ->
            val r = rs.getJSONObject(i)
            Residence(id = r.getString("id"), name = r.getString("name"))
        }
        val its = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until its.length()).map { i ->
            val r = its.getJSONObject(i)
            Item(
                id = r.getString("id"),
                name = r.getString("name"),
                description = r.optString("description", ""),
                category = r.optString("category", ""),
                photo = if (r.isNull("photo")) null else r.getString("photo"),
                residenceId = r.getString("residenceId"),
            )
        }
        val selected = if (o.isNull("selected")) null else o.getString("selected")
        return AppData(residences, items, selected)
    }

    private fun toJson(d: AppData): JSONObject {
        val rs = JSONArray()
        d.residences.forEach {
            rs.put(JSONObject().put("id", it.id).put("name", it.name))
        }
        val its = JSONArray()
        d.items.forEach {
            its.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("description", it.description)
                    .put("category", it.category)
                    .put("photo", it.photo ?: JSONObject.NULL)
                    .put("residenceId", it.residenceId)
            )
        }
        return JSONObject()
            .put("residences", rs)
            .put("items", its)
            .put("selected", d.selectedResidenceId ?: JSONObject.NULL)
    }
}
