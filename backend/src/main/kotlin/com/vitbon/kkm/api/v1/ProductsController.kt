package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.service.ProductService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductsController(private val productService: ProductService) {

    @GetMapping
    fun getProducts(@RequestParam since: Long?): ProductSyncResponseDto {
        return productService.getProductsDelta(since)
    }

    @PostMapping("sync")
    fun syncProducts(@RequestBody req: ProductSyncResponseDto): ProductSyncResponseDto {
        return req
    }
}
