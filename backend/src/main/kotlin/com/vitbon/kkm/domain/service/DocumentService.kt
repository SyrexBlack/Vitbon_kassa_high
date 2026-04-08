package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DocumentService {
    fun save(doc: DocumentDto, type: String): Unit {}
}

@Service
class ShiftService {
    fun findByCashier(cashierId: String): List<ShiftDto> {
        return emptyList()
    }

    fun open(shift: ShiftDto): ShiftDto = shift

    fun close(id: String): Unit {}
}

@Service
class StatusService {
    fun getStatuses(): StatusResponseDto = StatusResponseDto(
        ofdQueueLength = 0,
        lastSyncTimestamp = System.currentTimeMillis(),
        cloudServerOk = true,
        licenseStatus = "ACTIVE"
    )
}

@Service
class LicenseService {
    fun check(deviceId: String): LicenseCheckResponseDto = LicenseCheckResponseDto(
        status = "ACTIVE",
        expiryDate = System.currentTimeMillis() + 30L * 24 * 3600 * 1000,
        graceUntil = null
    )
}

@Service
class EgaisService {
    fun processIncoming(payload: String): String = "OK"
    fun processTara(payload: String): String = "OK"
}

@Service
class ChaseznakService {
    fun processSell(payload: String): String = "OK"
    fun verifyAge(payload: String): String {
        return """{"verified": true}"""
    }
}
