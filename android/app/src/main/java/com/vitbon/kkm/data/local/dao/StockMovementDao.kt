package com.vitbon.kkm.data.local.dao

import androidx.room.*
import com.vitbon.kkm.data.local.entity.StockMovement

@Dao
interface StockMovementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movement: StockMovement)

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp DESC")
    suspend fun findByProductId(productId: String): List<StockMovement>

    @Query("SELECT SUM(delta) FROM stock_movements WHERE productId = :productId")
    suspend fun sumByProductId(productId: String): Double?

    @Query("DELETE FROM stock_movements WHERE referenceId = :referenceId")
    suspend fun deleteByReferenceId(referenceId: String)
}
