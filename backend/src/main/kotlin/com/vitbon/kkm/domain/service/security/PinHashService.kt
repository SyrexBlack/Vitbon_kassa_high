package com.vitbon.kkm.domain.service.security

import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class PinHashService {
    private val encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().apply {
        setEncodeHashAsBase64(true)
    }

    fun matches(pin: String, storedHash: String): Boolean {
        return if (isPbkdf2(storedHash)) {
            encoder.matches(pin, storedHash.removePrefix("pbkdf2$"))
        } else {
            sha256(pin) == storedHash
        }
    }

    fun needsRehash(storedHash: String): Boolean = !isPbkdf2(storedHash)

    fun hash(pin: String): String = "pbkdf2$${encoder.encode(pin)}"

    private fun isPbkdf2(hash: String): Boolean = hash.startsWith("pbkdf2$")

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
