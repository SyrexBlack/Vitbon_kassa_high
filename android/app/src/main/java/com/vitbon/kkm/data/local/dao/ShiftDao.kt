package com.vitbon.kkm.data.local.dao

import androidx.room.*
import com.vitbon.kkm.data.local.entity.LocalShift
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE closedAt IS NULL ORDER BY openedAt DESC LIMIT 1")
    suspend fun findOpenShift(): LocalShift?

    @Query("SELECT * FROM shifts WHERE cashierId = :cashierId ORDER BY openedAt DESC")
    fun observeByCashier(cashierId: String): Flow<List<LocalShift>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shift: LocalShift)

    @Query("UPDATE shifts SET closedAt = :closedAt, totalCash = :totalCash, totalCard = :totalCard WHERE id = :shiftId")
    suspend fun closeShift(shiftId: String, closedAt: Long, totalCash: Long, totalCard: Long)
}
