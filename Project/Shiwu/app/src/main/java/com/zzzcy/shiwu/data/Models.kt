package com.zzzcy.shiwu.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 格式化日期（yyyy-MM-dd） */
val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun Long.formatDate(): String = LocalDate.ofEpochDay(this).format(DateFmt)

fun LocalDate.formatDate(): String = format(DateFmt)

/** 物品 */
data class Item(
    val id: Long,
    val name: String,
    val category: String,
    val price: Double,
    val purchaseDate: Long,          // epochDay
    val photos: List<String> = emptyList(),
    val note: String = "",
    val retired: Boolean = false,
    val retirementDate: Long? = null,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 持有（服役）天数：退役后按购入→退役日计算 */
    val serviceDays: Long
        get() {
            val end = if (retired) retirementDate ?: LocalDate.now().toEpochDay() else LocalDate.now().toEpochDay()
            return (end - purchaseDate).coerceAtLeast(0)
        }

    /** 日均成本 */
    val dayCost: Double
        get() = if (price <= 0 || serviceDays == 0L) 0.0 else price / serviceDays
}

/** 心愿 */
data class Wish(
    val id: Long,
    val name: String,
    val category: String,
    val price: Double,
    val priority: String = "MEDIUM", // HIGH / MEDIUM / DREAM
    val savedAmount: Double = 0.0,
    val note: String = "",
    val purchased: Boolean = false,
    val purchasedItemId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ---------- JSON 序列化 ----------

fun Item.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("category", category)
    put("price", price)
    put("purchaseDate", purchaseDate)
    put("photos", JSONArray(photos))
    put("note", note)
    put("retired", retired)
    put("retirementDate", retirementDate ?: JSONObject.NULL)
    put("deleted", deleted)
    put("deletedAt", deletedAt ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

fun itemFromJson(o: JSONObject): Item {
    val photos = ArrayList<String>()
    val arr = o.optJSONArray("photos") ?: JSONArray()
    for (i in 0 until arr.length()) photos.add(arr.getString(i))
    val retirement = if (o.isNull("retirementDate")) null else o.optLong("retirementDate")
    val deletedAt = if (o.isNull("deletedAt")) null else o.optLong("deletedAt")
    return Item(
        id = o.getLong("id"),
        name = o.getString("name"),
        category = o.optString("category", "其他"),
        price = o.optDouble("price", 0.0),
        purchaseDate = o.optLong("purchaseDate", LocalDate.now().toEpochDay()),
        photos = photos,
        note = o.optString("note", ""),
        retired = o.optBoolean("retired", false),
        retirementDate = retirement,
        deleted = o.optBoolean("deleted", false),
        deletedAt = deletedAt,
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
    )
}

fun Wish.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("category", category)
    put("price", price)
    put("priority", priority)
    put("savedAmount", savedAmount)
    put("note", note)
    put("purchased", purchased)
    put("purchasedItemId", purchasedItemId ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

fun wishFromJson(o: JSONObject): Wish {
    val purchasedItemId = if (o.isNull("purchasedItemId")) null else o.optLong("purchasedItemId")
    return Wish(
        id = o.getLong("id"),
        name = o.getString("name"),
        category = o.optString("category", "其他"),
        price = o.optDouble("price", 0.0),
        priority = o.optString("priority", "MEDIUM"),
        savedAmount = o.optDouble("savedAmount", 0.0),
        note = o.optString("note", ""),
        purchased = o.optBoolean("purchased", false),
        purchasedItemId = purchasedItemId,
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
    )
}
