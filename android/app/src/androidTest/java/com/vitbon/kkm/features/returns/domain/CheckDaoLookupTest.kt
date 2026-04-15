package com.vitbon.kkm.features.returns.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitbon.kkm.data.local.VitbonDatabase
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import com.vitbon.kkm.core.fiscal.model.PaymentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckDaoLookupTest {

    private lateinit var db: VitbonDatabase
    private lateinit var checkDao: CheckDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VitbonDatabase::class.java
        ).allowMainThreadQueries().build()
        checkDao = db.checkDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun findLatestSaleByIdentifier_resolvesByIdLocalUuidAndFiscalSign_forSaleOnly() = runBlocking {
        val saleById = localCheck(id = "sale-id-1", localUuid = "lu-1", fiscalSign = "fs-1", type = "sale", createdAt = 100L)
        val saleByLocalUuid = localCheck(id = "sale-id-2", localUuid = "lu-target", fiscalSign = "fs-2", type = "sale", createdAt = 200L)
        val saleByFiscalSign = localCheck(id = "sale-id-3", localUuid = "lu-3", fiscalSign = "fs-target", type = "sale", createdAt = 300L)
        val returnWithSameIdentifier = localCheck(
            id = "return-id",
            localUuid = "lu-target",
            fiscalSign = "fs-target",
            type = "return",
            createdAt = 400L
        )

        checkDao.insert(saleById)
        checkDao.insert(saleByLocalUuid)
        checkDao.insert(saleByFiscalSign)
        checkDao.insert(returnWithSameIdentifier)

        assertEquals(saleById.id, checkDao.findLatestSaleByIdentifier("sale-id-1")?.id)
        assertEquals(saleByLocalUuid.id, checkDao.findLatestSaleByIdentifier("lu-target")?.id)
        assertEquals(saleByFiscalSign.id, checkDao.findLatestSaleByIdentifier("fs-target")?.id)
        assertNull(checkDao.findLatestSaleByIdentifier("missing"))
    }

    @Test
    fun findLatestSaleByIdentifier_returnsLatestSale_whenMultipleMatchesExist() = runBlocking {
        val olderSale = localCheck(id = "sale-old", localUuid = "shared-uuid", fiscalSign = "fs-old", type = "sale", createdAt = 1_000L)
        val newerSale = localCheck(id = "sale-new", localUuid = "shared-uuid", fiscalSign = "fs-new", type = "sale", createdAt = 2_000L)

        checkDao.insert(olderSale)
        checkDao.insert(newerSale)

        val resolved = checkDao.findLatestSaleByIdentifier("shared-uuid")
        assertEquals(newerSale.id, resolved?.id)
    }

    private fun localCheck(
        id: String,
        localUuid: String,
        fiscalSign: String?,
        type: String,
        createdAt: Long
    ): LocalCheck = LocalCheck(
        id = id,
        localUuid = localUuid,
        shiftId = "shift-1",
        cashierId = "cashier-1",
        deviceId = "device-1",
        type = type,
        fiscalSign = fiscalSign,
        ofdResponse = null,
        ffdVersion = "1.2",
        status = "SYNCED",
        subtotal = 100_00L,
        discount = 0L,
        total = 100_00L,
        taxAmount = 0L,
        paymentType = PaymentType.CASH.value,
        createdAt = createdAt,
        syncedAt = createdAt + 100L
    )
}
