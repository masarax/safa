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

@Entity(
    tableName = "domain_records",
    primaryKeys = ["entity", "localId"],
    indices = [Index(value = ["entity", "serverId"]), Index(value = ["entity", "updatedAt"])]
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
    suspend fun getAll(entity: String): List<RoomDomainRecord>

    @Query("SELECT * FROM domain_records WHERE entity = :entity AND localId = :localId LIMIT 1")
    suspend fun get(entity: String, localId: Int): RoomDomainRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: RoomDomainRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<RoomDomainRecord>)

    @Query("DELETE FROM domain_records WHERE entity = :entity AND localId = :localId")
    suspend fun delete(entity: String, localId: Int)
}

@Database(entities = [RoomDomainRecord::class], version = 1, exportSchema = false)
abstract class RoomLocalDatabase : androidx.room.RoomDatabase() {
    abstract fun domainRecords(): RoomDomainRecordDao

    companion object {
        @Volatile private var INSTANCE: RoomLocalDatabase? = null

        fun get(context: Context): RoomLocalDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, RoomLocalDatabase::class.java, "safa_room.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { INSTANCE = it }
        }
    }
}
