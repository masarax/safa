package com.safa.account.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

/**
 * Room is the authoritative local domain store. The legacy LocalFirstStore is
 * retained only for the durable encrypted sync outbox during the migration.
 */
@Entity(
    tableName = "domain_records",
    primaryKeys = ["entity", "localId"],
    indices = [
        Index(value = ["entity", "serverId"]),
        Index(value = ["entity", "updatedAt"]),
        Index(value = ["entity", "syncVersion"])
    ]
)
data class RoomDomainRecord(
    val entity: String,
    val localId: Int,
    val serverId: Int,
    val payload: String,
    val syncVersion: Int = 0,
    val syncStatus: Int = LocalFirstStore.PENDING,
    val mutationId: String? = null,
    val updatedAt: Long
)

@Dao
interface RoomDomainRecordDao {
    @Query("SELECT * FROM domain_records WHERE entity = :entity ORDER BY updatedAt DESC")
    fun observeAll(entity: String): Flow<List<RoomDomainRecord>>

    @Query("SELECT * FROM domain_records WHERE entity = :entity ORDER BY updatedAt DESC")
    suspend fun getAll(entity: String): List<RoomDomainRecord>

    @Query("SELECT COUNT(*) FROM domain_records WHERE entity = :entity")
    suspend fun count(entity: String): Int

    @Query("SELECT * FROM domain_records WHERE entity = :entity AND localId = :localId LIMIT 1")
    suspend fun get(entity: String, localId: Int): RoomDomainRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: RoomDomainRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<RoomDomainRecord>)

    @Query("DELETE FROM domain_records WHERE entity = :entity AND localId = :localId")
    suspend fun delete(entity: String, localId: Int)

    @Query("DELETE FROM domain_records WHERE entity = :entity")
    suspend fun deleteEntity(entity: String)
}

@Database(entities = [RoomDomainRecord::class], version = 2, exportSchema = false)
abstract class RoomLocalDatabase : androidx.room.RoomDatabase() {
    abstract fun domainRecords(): RoomDomainRecordDao

    companion object {
        @Volatile private var INSTANCE: RoomLocalDatabase? = null

        fun get(context: Context): RoomLocalDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                RoomLocalDatabase::class.java,
                "safa_room.db"
            )
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { INSTANCE = it }
        }
    }
}
