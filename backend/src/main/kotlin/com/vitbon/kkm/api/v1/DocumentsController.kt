package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.DocumentDto
import com.vitbon.kkm.domain.service.DocumentService
import com.vitbon.kkm.domain.service.security.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/documents")
class DocumentsController(private val documentService: DocumentService) {
    @PostMapping("acceptance")
    fun sendAcceptance(@RequestBody doc: DocumentDto) {
        val principal = SecurityContextHolder.requirePrincipal()
        documentService.save(doc, "ACCEPTANCE", principal.cashierId.toString(), principal.deviceId)
    }

    @PostMapping("writeoff")
    fun sendWriteoff(@RequestBody doc: DocumentDto) {
        val principal = SecurityContextHolder.requirePrincipal()
        documentService.save(doc, "WRITEOFF", principal.cashierId.toString(), principal.deviceId)
    }

    @PostMapping("inventory")
    fun sendInventory(@RequestBody doc: DocumentDto) {
        val principal = SecurityContextHolder.requirePrincipal()
        documentService.save(doc, "INVENTORY", principal.cashierId.toString(), principal.deviceId)
    }
}
