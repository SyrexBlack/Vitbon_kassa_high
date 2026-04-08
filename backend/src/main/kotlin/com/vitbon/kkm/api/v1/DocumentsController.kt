package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.DocumentDto
import com.vitbon.kkm.domain.service.DocumentService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("api/v1/documents")
class DocumentsController(private val documentService: DocumentService) {
    @PostMapping("acceptance") fun sendAcceptance(@RequestBody doc: DocumentDto) = documentService.save(doc, "ACCEPTANCE")
    @PostMapping("writeoff") fun sendWriteoff(@RequestBody doc: DocumentDto) = documentService.save(doc, "WRITEOFF")
    @PostMapping("inventory") fun sendInventory(@RequestBody doc: DocumentDto) = documentService.save(doc, "INVENTORY")
}
