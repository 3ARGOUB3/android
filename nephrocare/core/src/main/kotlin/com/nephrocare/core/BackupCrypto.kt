package com.nephrocare.core

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Wrong password or damaged file")

/**
 * Password-based encryption for backup files: PBKDF2-HMAC-SHA256 key derivation and AES-256-GCM.
 * PBKDF2 is implemented on top of HmacSHA256 so it also works on Android versions
 * whose SecretKeyFactory lacks PBKDF2WithHmacSHA256.
 *
 * File layout: "NCB1" | iterations (int) | salt (16) | iv (12) | ciphertext+tag.
 */
object BackupCrypto {
    private val MAGIC = "NCB1".toByteArray()
    const val DEFAULT_ITERATIONS = 60_000

    fun encrypt(plain: ByteArray, password: String, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = pbkdf2(password.toByteArray(Charsets.UTF_8), salt, iterations, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val body = cipher.doFinal(plain)
        return ByteBuffer.allocate(4 + 4 + 16 + 12 + body.size)
            .put(MAGIC).putInt(iterations).put(salt).put(iv).put(body).array()
    }

    fun decrypt(data: ByteArray, password: String): ByteArray {
        if (data.size < 36 || !data.copyOfRange(0, 4).contentEquals(MAGIC)) throw WrongPasswordException()
        val buf = ByteBuffer.wrap(data)
        buf.position(4)
        val iterations = buf.int
        if (iterations !in 1..10_000_000) throw WrongPasswordException()
        val salt = ByteArray(16).also { buf.get(it) }
        val iv = ByteArray(12).also { buf.get(it) }
        val body = ByteArray(buf.remaining()).also { buf.get(it) }
        val key = pbkdf2(password.toByteArray(Charsets.UTF_8), salt, iterations, 32)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(body)
        } catch (e: Exception) {
            throw WrongPasswordException()
        }
    }

    /** Salted hash for the optional app PIN. */
    fun hashPin(pin: String, salt: ByteArray): String =
        pbkdf2(pin.toByteArray(Charsets.UTF_8), salt, 20_000, 32).joinToString("") { "%02x".format(it) }

    fun newSalt(): ByteArray = ByteArray(16).also(SecureRandom()::nextBytes)

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (password.isEmpty()) ByteArray(1) else password, "HmacSHA256"))
        val hLen = mac.macLength
        val blocks = (length + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)
        for (i in 1..blocks) {
            mac.update(salt)
            mac.update(ByteBuffer.allocate(4).putInt(i).array())
            var u = mac.doFinal()
            val t = u.copyOf()
            for (j in 1 until iterations) {
                u = mac.doFinal(u)
                for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (i - 1) * hLen, hLen)
        }
        return out.copyOf(length)
    }
}
