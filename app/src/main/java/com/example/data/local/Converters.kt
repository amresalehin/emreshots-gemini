package com.example.data.local

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                if (!array.isNull(i)) {
                    val str = array.optString(i, "").trim()
                    if (str.isNotBlank() && !str.equals("null", ignoreCase = true)) {
                        list.add(str)
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }
}
