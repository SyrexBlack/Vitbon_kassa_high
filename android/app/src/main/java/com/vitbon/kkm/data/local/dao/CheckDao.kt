package com.vitbon.kkm.data.local.dao

import androidx.room.*
import com.vitbon.kkm.data.local.entity.LocalCheck
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckDao {
    @Query("SELECT * FROM checks WHERE status = 'PENDING_SYNC' ORDER BY createdAt ASC")
    suspend fun findPendingSync(): List<LocalCheck>

    @Query("SELECT * FROM checks WHERE id = :id")
    suspend fun findById(id: String): LocalCheck?

    @Query("SELECT * FROM checks WHERE localUuid = :localUuid")
    suspend fun findByLocalUuid(localUuid: String): LocalCheck?

    @Query(
        """
        SELECT * FROM checks
        WHERE type = 'sale'
          AND (id = :checkIdentifier OR localUuid = :checkIdentifier OR fiscalSign = :checkIdentifier)
        ORDER BY createdAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun findLatestSaleByIdentifier(checkIdentifier: String): LocalCheck?

    @Query("SELECT * FROM checks WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    fun observeByShift(shiftId: String): Flow<List<LocalCheck>>

    @Query("SELECT * FROM checks WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    suspend fun findByShiftId(shiftId: String): List<LocalCheck>

    @Query("SELECT * FROM checks WHERE createdAt BETWEEN :fromTs AND :toTs ORDER BY createdAt DESC")
    suspend fun findByDateRange(fromTs: Long, toTs: Long): List<LocalCheck>

    @Query("SELECT COUNT(*) FROM checks WHERE status = 'PENDING_SYNC'")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(check: LocalCheck)

    @Query("UPDATE checks SET status = :status, fiscalSign = :fiscalSign, ofdResponse = :ofdResponse, syncedAt = :syncedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, fiscalSign: String?, ofdResponse: String?, syncedAt: Long?)

    @Query("UPDATE checks SET status = 'SYNCED', syncedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long)
}
