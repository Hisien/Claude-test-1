package com.scrollmate.app.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One row per local day, app and source. Kept because the OS only retains raw events for a few days. */
@Entity(tableName = "usage_summary", primaryKeys = ["date", "packageName", "source"])
data class UsageSummaryEntity(
    val date: String,
    val packageName: String,
    val source: String,
    val durationMs: Long,
    val updatedAtMs: Long,
)

/** Idempotency log of every reminder: the primary key is the idempotency key. */
@Entity(tableName = "notification_record")
data class NotificationRecordEntity(
    @PrimaryKey val key: String,
    val date: String,
    val ruleId: String,
    val threshold: Int?,
    val sentAtMs: Long,
    val countsTowardsLimit: Boolean,
    /** False for thresholds that were overtaken and stored silently. */
    val shown: Boolean,
    val deliveredAsNotification: Boolean,
    val messageKind: String?,
    val action: String?,
    val percent: Int?,
    val minutes: Int?,
    val packageName: String?,
    /** Voice the message was sent in (null for records from version 1). */
    val voice: String? = null,
    @ColumnInfo(defaultValue = "0") val demo: Boolean = false,
)

@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val plannedMs: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val status: String,
    val reflection: String?,
)

/** Short, optional notes the user writes after a coach question. Never analysed, only shown back and exportable. */
@Entity(tableName = "reflection_note")
data class ReflectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val createdAtMs: Long,
    val text: String,
)

@Dao
interface ReflectionDao {
    @Insert
    suspend fun insert(note: ReflectionEntity): Long

    @Query("SELECT * FROM reflection_note ORDER BY createdAtMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ReflectionEntity>>

    @Query("SELECT * FROM reflection_note ORDER BY createdAtMs")
    suspend fun all(): List<ReflectionEntity>

    @Query("DELETE FROM reflection_note")
    suspend fun clear()
}

@Dao
interface UsageSummaryDao {
    @Query("SELECT * FROM usage_summary WHERE date >= :fromDate AND source = :source")
    suspend fun since(fromDate: String, source: String): List<UsageSummaryEntity>

    @Upsert
    suspend fun upsert(items: List<UsageSummaryEntity>)

    @Query("DELETE FROM usage_summary WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("SELECT * FROM usage_summary ORDER BY date, packageName")
    suspend fun all(): List<UsageSummaryEntity>

    @Query("DELETE FROM usage_summary")
    suspend fun clear()
}

@Dao
interface NotificationRecordDao {
    /** Returns -1 when the key already exists: the insert itself is the idempotency check. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: NotificationRecordEntity): Long

    @Query("SELECT * FROM notification_record WHERE date >= :fromDate")
    suspend fun since(fromDate: String): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_record WHERE date = :date AND shown = 1 ORDER BY sentAtMs DESC")
    fun shownOn(date: String): Flow<List<NotificationRecordEntity>>

    /** Everything that was shown (notification or in-app), newest first – for the bell history. */
    @Query("SELECT * FROM notification_record WHERE shown = 1 ORDER BY sentAtMs DESC LIMIT :limit")
    fun recentShown(limit: Int): Flow<List<NotificationRecordEntity>>

    @Query("DELETE FROM notification_record WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("DELETE FROM notification_record")
    suspend fun clear()
}

@Dao
interface FocusSessionDao {
    @Upsert
    suspend fun upsert(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_session WHERE status = 'RUNNING' ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun running(): FocusSessionEntity?

    @Query("SELECT * FROM focus_session WHERE id = :id")
    suspend fun byId(id: String): FocusSessionEntity?

    @Query("SELECT * FROM focus_session ORDER BY startedAtMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_session ORDER BY startedAtMs")
    suspend fun all(): List<FocusSessionEntity>

    @Query("DELETE FROM focus_session")
    suspend fun clear()
}

@Database(
    entities = [
        UsageSummaryEntity::class,
        NotificationRecordEntity::class,
        FocusSessionEntity::class,
        ReflectionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ScrollMateDatabase : RoomDatabase() {
    abstract fun usageSummaryDao(): UsageSummaryDao
    abstract fun notificationRecordDao(): NotificationRecordDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun reflectionDao(): ReflectionDao

    companion object {
        fun create(context: Context): ScrollMateDatabase =
            Room.databaseBuilder(context, ScrollMateDatabase::class.java, "scrollmate.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: store voice and demo flag with each reminder for the notification history. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_record ADD COLUMN voice TEXT")
                db.execSQL("ALTER TABLE notification_record ADD COLUMN demo INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
