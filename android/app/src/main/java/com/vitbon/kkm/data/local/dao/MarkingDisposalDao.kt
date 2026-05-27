package com.vitbon.kkm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vitbon.kkm.data.local.entity.LocalMarkingDisposal

@Dao
interface MarkingDisposalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(disposal: LocalMarkingDisposal)

    @Query("SELECT * FROM marking_disposals WHERE code = :code ORDER BY disposedAt DESC LIMIT 1")
    suspend fun findByCode(code: String): LocalMarkingDisposal?
}