package com.vitbon.kkm.data.local.dao

import androidx.room.*
import com.vitbon.kkm.data.local.entity.LocalProduct
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): LocalProduct?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: String): LocalProduct?

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR article LIKE '%' || :query || '%' LIMIT 20")
    suspend fun search(query: String): List<LocalProduct>

    @Query("SELECT * FROM products")
    fun observeAll(): Flow<List<LocalProduct>>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: LocalProduct)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<LocalProduct>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}
