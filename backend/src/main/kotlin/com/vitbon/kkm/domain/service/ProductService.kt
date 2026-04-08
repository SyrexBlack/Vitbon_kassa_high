package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import org.springframework.stereotype.Service

@Service
class ProductService {
    fun getProductsDelta(since: Long?): ProductSyncResponseDto {
        return ProductSyncResponseDto(
            products = emptyList(),
            deletedIds = emptyList(),
            serverTimestamp = System.currentTimeMillis()
        )
    }
}
