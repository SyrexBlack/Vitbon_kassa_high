package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.ShiftDto
import com.vitbon.kkm.domain.service.ShiftService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/shifts")
class ShiftsController(private val shiftService: ShiftService) {
    @GetMapping("{cashierId}") fun getShifts(@PathVariable cashierId: String): List<ShiftDto> = shiftService.findByCashier(cashierId)
    @PostMapping fun openShift(@RequestBody shift: ShiftDto): ShiftDto = shiftService.open(shift)
    @PutMapping("{id}/close") fun closeShift(@PathVariable id: String) = shiftService.close(id)
}
