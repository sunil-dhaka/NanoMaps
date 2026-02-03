package com.example.nanomaps.data

import org.json.JSONObject

data class FantasyMap(
    val id: String,
    val name: String,
    val imagePath: String,
    val worldContext: String,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("imagePath", imagePath)
        put("worldContext", worldContext)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): FantasyMap = FantasyMap(
            id = json.getString("id"),
            name = json.getString("name"),
            imagePath = json.getString("imagePath"),
            worldContext = json.optString("worldContext", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

data class FantasyLocation(
    val xPercent: Float,
    val yPercent: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("xPercent", xPercent.toDouble())
        put("yPercent", yPercent.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): FantasyLocation = FantasyLocation(
            xPercent = json.getDouble("xPercent").toFloat(),
            yPercent = json.getDouble("yPercent").toFloat()
        )
    }
}
