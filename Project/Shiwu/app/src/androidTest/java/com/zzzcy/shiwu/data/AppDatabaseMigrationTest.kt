package com.zzzcy.shiwu.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v3（main / 0.0.8）→ v5（WebDAV 同步）真实 schema 迁移回归。
 *
 * main 分支为 Room v3；同步功能引入 v4（rev/updatedBy/墓碑）与 v5（syncedRev）。
 * 这里手工创建 v3 表结构与旧数据，再用 AppDatabase.build() 打开触发 3→4→5，
 * 断言旧数据保留、新列默认值正确。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private val dbName = "shiwu.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        context.getDatabasePath(dbName).parentFile?.mkdirs()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate3To5_keepsDataAndAddsSyncColumns() {
        createV3Database()

        val db = AppDatabase.build(context)
        db.openHelper.writableDatabase // 触发 Room 迁移 3→4→5

        val raw = db.openHelper.readableDatabase
        raw.query("PRAGMA user_version").use { c ->
            c.moveToFirst()
            assertEquals(5, c.getInt(0))
        }
        assertColumn(raw, "items", "rev")
        assertColumn(raw, "items", "updatedBy")
        assertColumn(raw, "items", "syncedRev")
        assertColumn(raw, "wishes", "rev")
        assertColumn(raw, "wishes", "deleted")
        assertColumn(raw, "wishes", "deletedAt")
        assertColumn(raw, "wishes", "syncedRev")
        assertColumn(raw, "lists", "rev")
        assertColumn(raw, "lists", "deleted")
        assertColumn(raw, "lists", "syncedRev")

        assertEquals(2, count(raw, "lists"))
        assertEquals(3, count(raw, "items"))
        assertEquals(1, count(raw, "wishes"))
        assertEquals(1, count(raw, "categories"))
        assertEquals(1, count(raw, "locations"))

        raw.query(
            "SELECT name, listId, photos, deleted FROM items WHERE id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("耳机", c.getString(0))
            assertEquals(1L, c.getLong(1))
            assertTrue(c.getString(2).contains("photo_1.jpg"))
            assertEquals(0, c.getInt(3))
        }
        raw.query("SELECT rev, updatedBy, syncedRev FROM items WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("", c.getString(1))
            assertEquals(0L, c.getLong(2))
        }
        raw.query("SELECT purchasedItemId FROM wishes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
        }
        db.close()
    }

    private fun assertColumn(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, column: String) {
        var found = false
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIndex = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) {
                if (c.getString(nameIndex) == column) found = true
            }
        }
        assertTrue("$table.$column 缺失", found)
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    /** 创建 main 分支（Room v3）的 schema 与少量旧数据。 */
    private fun createV3Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `items` (
              `id` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL,
              `location` TEXT, `price` REAL NOT NULL, `purchaseDate` INTEGER NOT NULL,
              `photos` TEXT NOT NULL, `note` TEXT NOT NULL, `retired` INTEGER NOT NULL,
              `retirementDate` INTEGER, `productionDate` INTEGER, `expiryDate` INTEGER,
              `shelfLifeDays` INTEGER, `deleted` INTEGER NOT NULL, `deletedAt` INTEGER,
              `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
              `listId` INTEGER NOT NULL, PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wishes` (
              `id` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL,
              `price` REAL NOT NULL, `priority` TEXT NOT NULL, `savedAmount` REAL NOT NULL,
              `note` TEXT NOT NULL, `purchased` INTEGER NOT NULL, `purchasedItemId` INTEGER,
              `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
              `listId` INTEGER NOT NULL, PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lists` (
              `id` INTEGER NOT NULL, `name` TEXT NOT NULL,
              `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`name` TEXT NOT NULL, `icon` TEXT NOT NULL, PRIMARY KEY(`name`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `locations` (`name` TEXT NOT NULL, PRIMARY KEY(`name`))"
        )

        db.execSQL("INSERT INTO lists VALUES (1, '我的物品', 100, 100)")
        db.execSQL("INSERT INTO lists VALUES (2, 'ASS', 200, 200)")
        db.execSQL(
            "INSERT INTO items VALUES (1, '耳机', '数码', '', 199.0, 18000, '[\"/data/user/0/com.zzzcy.shiwu/files/photos/photo_1.jpg\"]', " +
                "'note', 0, NULL, NULL, NULL, NULL, 0, NULL, 1000, 1000, 1)"
        )
        db.execSQL(
            "INSERT INTO items VALUES (2, '键盘', '数码', NULL, 299.0, 18001, '[]', '', 0, NULL, NULL, NULL, NULL, 0, NULL, 1001, 1001, 1)"
        )
        db.execSQL(
            "INSERT INTO items VALUES (3, '台灯', '家居', '家', 99.0, 18002, '[]', '', 1, 19000, NULL, NULL, NULL, 0, NULL, 1002, 1002, 2)"
        )
        db.execSQL(
            "INSERT INTO wishes VALUES (1, '相机', '影像', 3999.0, 'HIGH', 100.0, '', 0, 1, 3000, 3000, 1)"
        )
        db.execSQL("INSERT INTO categories VALUES ('数码', 'devices')")
        db.execSQL("INSERT INTO locations VALUES ('家')")
        db.version = 3
        db.close()
    }
}
