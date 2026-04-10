package com.vitbon.kkm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vitbon.kkm.data.local.entity.LocalCheckItem

@Dao
interface CheckItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalCheckItem>)

    @Query("SELECT * FROM check_items WHERE checkId = :checkId")
    suspend fun findByCheckId(checkId: String): List<LocalCheckItem>
}
