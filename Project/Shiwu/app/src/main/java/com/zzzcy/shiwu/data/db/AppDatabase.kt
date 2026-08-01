package com.zzzcy.shiwu.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

// ---------- 实体 ----------

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val category: String,
    val price: Double,
    val purchaseDate: Long,
    val photos: String,
    val note: String,
    val retired: Boolean,
    val retirementDate: Long?,
    val deleted: Boolean,
    val deletedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val listId: Long
)

@Entity(tableName = "wishes")
data class WishEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val category: String,
    val price: Double,
    val priority: String,
    val savedAmount: Double,
    val note: String,
    val purchased: Boolean,
    val purchasedItemId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val listId: Long
)

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val icon: String
)

// ---------- 领域模型 <-> 实体 映射 ----------

private fun photosToJson(photos: List<String>): String = JSONArray(photos).toString()

private fun photosFromJson(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

fun Item.toEntity() = ItemEntity(
    id = id, name = name, category = category, price = price, purchaseDate = purchaseDate,
    photos = photosToJson(photos), note = note, retired = retired, retirementDate = retirementDate,
    deleted = deleted, deletedAt = deletedAt, createdAt = createdAt, updatedAt = updatedAt, listId = listId
)

fun ItemEntity.toModel() = Item(
    id = id, name = name, category = category, price = price, purchaseDate = purchaseDate,
    photos = photosFromJson(photos), note = note, retired = retired, retirementDate = retirementDate,
    deleted = deleted, deletedAt = deletedAt, createdAt = createdAt, updatedAt = updatedAt, listId = listId
)

fun Wish.toEntity() = WishEntity(
    id = id, name = name, category = category, price = price, priority = priority,
    savedAmount = savedAmount, note = note, purchased = purchased, purchasedItemId = purchasedItemId,
    createdAt = createdAt, updatedAt = updatedAt, listId = listId
)

fun WishEntity.toModel() = Wish(
    id = id, name = name, category = category, price = price, priority = priority,
    savedAmount = savedAmount, note = note, purchased = purchased, purchasedItemId = purchasedItemId,
    createdAt = createdAt, updatedAt = updatedAt, listId = listId
)

fun InventoryList.toEntity() = ListEntity(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt)

fun ListEntity.toModel() = InventoryList(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt)

fun Category.toEntity() = CategoryEntity(name = name, icon = icon)

fun CategoryEntity.toModel() = Category(name = name, icon = icon)

// ---------- DAO ----------

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    fun observeAll(): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE items SET category = :newName, updatedAt = :now WHERE category = :oldName")
    suspend fun updateCategory(oldName: String, newName: String, now: Long)

    @Query("UPDATE items SET deleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE listId = :listId")
    suspend fun softDeleteByList(listId: Long, deletedAt: Long)
}

@Dao
interface WishDao {
    @Query("SELECT * FROM wishes")
    fun observeAll(): Flow<List<WishEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wish: WishEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(wishes: List<WishEntity>)

    @Query("DELETE FROM wishes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM wishes WHERE listId = :listId")
    suspend fun deleteByList(listId: Long)

    @Query("UPDATE wishes SET category = :newName, updatedAt = :now WHERE category = :oldName")
    suspend fun updateCategory(oldName: String, newName: String, now: Long)
}

@Dao
interface ListDao {
    @Query("SELECT * FROM lists")
    fun observeAll(): Flow<List<ListEntity>>

    @Query("SELECT COUNT(*) FROM lists")
    suspend fun count(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: ListEntity)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun delete(name: String)

    @Query("UPDATE categories SET name = :newName WHERE name = :oldName")
    suspend fun rename(oldName: String, newName: String)

    @Query("UPDATE categories SET icon = :icon WHERE name = :name")
    suspend fun updateIcon(name: String, icon: String)
}

// ---------- 数据库 ----------

@Database(
    entities = [ItemEntity::class, WishEntity::class, ListEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun wishDao(): WishDao
    abstract fun listDao(): ListDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shiwu.db").build()
    }
}
