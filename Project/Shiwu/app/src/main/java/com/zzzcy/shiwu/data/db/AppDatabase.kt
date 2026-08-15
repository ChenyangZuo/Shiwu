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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

// ---------- 实体 ----------

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val category: String,
    val location: String?,
    val price: Double,
    val purchaseDate: Long,
    val photos: String,
    val note: String,
    val retired: Boolean,
    val retirementDate: Long?,
    val productionDate: Long?,
    val expiryDate: Long?,
    val shelfLifeDays: Int?,
    val deleted: Boolean,
    val deletedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val listId: Long,
    val rev: Long,
    val updatedBy: String,
    val syncedRev: Long
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
    val listId: Long,
    val rev: Long,
    val updatedBy: String,
    val syncedRev: Long,
    val deleted: Boolean,
    val deletedAt: Long?
)

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val rev: Long,
    val updatedBy: String,
    val syncedRev: Long,
    val deleted: Boolean,
    val deletedAt: Long?
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val icon: String
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val name: String
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
    id = id, name = name, category = category, location = location, price = price, purchaseDate = purchaseDate,
    photos = photosToJson(photos), note = note, retired = retired, retirementDate = retirementDate,
    productionDate = productionDate, expiryDate = expiryDate, shelfLifeDays = shelfLifeDays,
    deleted = deleted, deletedAt = deletedAt, createdAt = createdAt, updatedAt = updatedAt, listId = listId,
    rev = rev, updatedBy = updatedBy, syncedRev = syncedRev
)

fun ItemEntity.toModel() = Item(
    id = id, name = name, category = category, location = location, price = price, purchaseDate = purchaseDate,
    photos = photosFromJson(photos), note = note, retired = retired, retirementDate = retirementDate,
    productionDate = productionDate, expiryDate = expiryDate, shelfLifeDays = shelfLifeDays,
    deleted = deleted, deletedAt = deletedAt, createdAt = createdAt, updatedAt = updatedAt, listId = listId,
    rev = rev, updatedBy = updatedBy, syncedRev = syncedRev
)

fun Wish.toEntity() = WishEntity(
    id = id, name = name, category = category, price = price, priority = priority,
    savedAmount = savedAmount, note = note, purchased = purchased, purchasedItemId = purchasedItemId,
    createdAt = createdAt, updatedAt = updatedAt, listId = listId,
    rev = rev, updatedBy = updatedBy, syncedRev = syncedRev, deleted = deleted, deletedAt = deletedAt
)

fun WishEntity.toModel() = Wish(
    id = id, name = name, category = category, price = price, priority = priority,
    savedAmount = savedAmount, note = note, purchased = purchased, purchasedItemId = purchasedItemId,
    createdAt = createdAt, updatedAt = updatedAt, listId = listId,
    rev = rev, updatedBy = updatedBy, syncedRev = syncedRev, deleted = deleted, deletedAt = deletedAt
)

fun InventoryList.toEntity() = ListEntity(
    id = id, name = name, createdAt = createdAt, updatedAt = updatedAt,
    rev = rev, updatedBy = updatedBy, syncedRev = syncedRev, deleted = deleted, deletedAt = deletedAt
)

fun ListEntity.toModel() = InventoryList(
    id = id, name = name, createdAt = createdAt, updatedAt = updatedAt,
    rev = rev, updatedBy = updatedBy, syncedRev = syncedRev, deleted = deleted, deletedAt = deletedAt
)

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

    @Query("UPDATE items SET location = :newName, updatedAt = :now WHERE location = :oldName")
    suspend fun updateLocation(oldName: String, newName: String, now: Long)

    @Query("UPDATE items SET location = NULL, updatedAt = :now WHERE location = :name")
    suspend fun clearLocation(name: String, now: Long)
}

@Dao
interface WishDao {
    @Query("SELECT * FROM wishes")
    fun observeAll(): Flow<List<WishEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wish: WishEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(wishes: List<WishEntity>)

    @Query("UPDATE wishes SET deleted = 1, deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE wishes SET deleted = 1, deletedAt = :now, updatedAt = :now WHERE listId = :listId")
    suspend fun softDeleteByList(listId: Long, now: Long)

    @Query("DELETE FROM wishes WHERE id = :id")
    suspend fun purge(id: Long)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lists: List<ListEntity>)

    @Query("UPDATE lists SET deleted = 1, deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun purge(id: Long)
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

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT COUNT(*) FROM locations")
    suspend fun count(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(locations: List<LocationEntity>)

    @Query("DELETE FROM locations WHERE name = :name")
    suspend fun delete(name: String)

    @Query("UPDATE locations SET name = :newName WHERE name = :oldName")
    suspend fun rename(oldName: String, newName: String)
}

// ---------- 数据库 ----------

@Database(
    entities = [ItemEntity::class, WishEntity::class, ListEntity::class, CategoryEntity::class, LocationEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun wishDao(): WishDao
    abstract fun listDao(): ListDao
    abstract fun categoryDao(): CategoryDao
    abstract fun locationDao(): LocationDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shiwu.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /** v1 → v2：items 表新增生产日期 / 到期日期 / 保质期天数（旧数据为空，视为无保质期信息） */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN productionDate INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN expiryDate INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN shelfLifeDays INTEGER")
            }
        }

        /** v2 → v3：items 表新增存放地点列；新增 locations 表（预置 家/车/公司） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN location TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS locations (name TEXT NOT NULL PRIMARY KEY)")
            }
        }

        /** v3 → v4：items / wishes / lists 增加同步版本字段；wishes / lists 增加软删墓碑 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN rev INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE items ADD COLUMN updatedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishes ADD COLUMN rev INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE wishes ADD COLUMN updatedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishes ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wishes ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE lists ADD COLUMN rev INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE lists ADD COLUMN updatedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE lists ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE lists ADD COLUMN deletedAt INTEGER")
            }
        }

        /** v4 → v5：items / wishes / lists 增加 syncedRev（记录上次同步到的版本，用于区分「落后」与「本地修改」） */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN syncedRev INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wishes ADD COLUMN syncedRev INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE lists ADD COLUMN syncedRev INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
