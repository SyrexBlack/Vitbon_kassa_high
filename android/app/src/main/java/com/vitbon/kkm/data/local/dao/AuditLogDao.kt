package com.vitbon.kkm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vitbon.kkm.data.local.entity.AuditLogEntry

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditLogEntry)

    @Query("SELECT * FROM audit_log ORDER BY timestamp ASC LIMIT :limit")
    suspend fun findPending(limit: Int): List<AuditLogEntry>

    @Query("DELETE FROM audit_log WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
