package com.vitbon.kkm.data.local.dao

import androidx.room.*
import com.vitbon.kkm.data.local.entity.LocalCashier
import kotlinx.coroutines.flow.Flow

@Dao
interface CashierDao {
    @Query("SELECT * FROM cashiers")
    fun observeAll(): Flow<List<LocalCashier>>

    @Query("SELECT * FROM cashiers WHERE id = :id")
    suspend fun findById(id: String): LocalCashier?

    @Query("SELECT * FROM cashiers WHERE pinHash = :pinHash")
    suspend fun findByPinHash(pinHash: String): LocalCashier?

    @Query("SELECT COUNT(*) FROM cashiers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cashier: LocalCashier)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cashiers: List<LocalCashier>)
}
