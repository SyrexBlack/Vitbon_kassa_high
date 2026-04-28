package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.service.AuthService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("login")
    fun login(@RequestBody req: LoginRequestDto): LoginResponseDto {
        return authService.login(req.pin, req.deviceId)
    }

    @PostMapping("logout")
    fun logout(@RequestHeader("Authorization") token: String) {
        authService.logout(token.removePrefix("Bearer "))
    }
}
