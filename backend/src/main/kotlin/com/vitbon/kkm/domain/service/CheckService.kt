package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CheckService {
    fun processSync(checks: List<CheckDto>): CheckSyncResponseDto {
        val failed = mutableListOf<FailedCheckDto>()
        val processed = checks.size
        return CheckSyncResponseDto(processed, failed)
    }

    fun findChecks(shiftId: String?, date: String?, since: Long?): List<CheckDto> {
        return emptyList()
    }
}
