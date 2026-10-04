package com.jonkryl.cutledger.core

import org.json.JSONObject

object DraftCodec {
    fun encode(d: Draft): String = JSONObject().put("schema", 1).put("name", d.name).put("stock", d.stock)
        .put("parts", d.parts).put("kerf", d.kerf).put("trim", d.trim).toString()
    fun decode(text: String?): Draft? = try {
        if (text == null || text.length > 45_000) null else JSONObject(text).let {
            require(it.getInt("schema") == 1)
            Draft(it.getString("name").take(60), it.getString("stock"), it.getString("parts"), it.getString("kerf"), it.getString("trim"))
                .also { d -> require(d.stock.length <= 20_000 && d.parts.length <= 20_000 && d.kerf.length <= 20 && d.trim.length <= 20) }
        }
    } catch (_: Exception) { null }
}
