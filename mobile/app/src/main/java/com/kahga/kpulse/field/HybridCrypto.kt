package com.kahga.kpulse.field

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

object HybridCrypto {

    // STATIC half: paste your public key (Base64, X.509 format) here
    private const val STATIC_PUBLIC_KEY_B64 = "PASTE_PUBLIC_KEY_BASE64_HERE"

    private val oaep = OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT
    )

    // ---------- One-time key generation ----------
    fun generateKeyPairBase64(): Pair<String, String> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = Base64.getEncoder().encodeToString(kp.public.encoded)    // embed in code
        val priv = Base64.getEncoder().encodeToString(kp.private.encoded)  // keep secret, supply at runtime
        return pub to priv
    }

    private fun loadPublicKey(b64: String): PublicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(b64)))

    // RUNTIME half: private key passed in when needed
    private fun loadPrivateKey(b64: String): PrivateKey =
        KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)))

    // ---------- Encrypt (uses static public key) ----------
    fun encrypt(plainText: String): String {
        // 1. Random AES-256 key + IV
        val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        // 2. Encrypt data with AES-GCM
        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val cipherData = aes.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // 3. Wrap AES key with RSA public key
        val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsa.init(Cipher.ENCRYPT_MODE, loadPublicKey(STATIC_PUBLIC_KEY_B64), oaep)
        val wrappedKey = rsa.doFinal(aesKey.encoded)

        // 4. Pack: [keyLen(2)][wrappedKey][iv(12)][cipherData]
        val out = ByteBuffer.allocate(2 + wrappedKey.size + iv.size + cipherData.size)
            .putShort(wrappedKey.size.toShort())
            .put(wrappedKey)
            .put(iv)
            .put(cipherData)
        return Base64.getEncoder().encodeToString(out.array())
    }

    // ---------- Decrypt (uses runtime private key) ----------
    fun decrypt(payloadB64: String, runtimePrivateKeyB64: String): String {
        val buf = ByteBuffer.wrap(Base64.getDecoder().decode(payloadB64))
        val keyLen = buf.short.toInt() and 0xFFFF
        val wrappedKey = ByteArray(keyLen).also { buf.get(it) }
        val iv = ByteArray(12).also { buf.get(it) }
        val cipherData = ByteArray(buf.remaining()).also { buf.get(it) }

        // Unwrap AES key with RSA private key
        val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsa.init(Cipher.DECRYPT_MODE, loadPrivateKey(runtimePrivateKeyB64), oaep)
        val aesKey = SecretKeySpec(rsa.doFinal(wrappedKey), "AES")

        // Decrypt data (GCM verifies integrity automatically)
        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        return String(aes.doFinal(cipherData), Charsets.UTF_8)
    }
}

fun main() {
    // Step 1 (once): generate keys, paste the public key into STATIC_PUBLIC_KEY_B64
    val (pub, priv) = HybridCrypto.generateKeyPairBase64()
    println("PUBLIC (embed):\n$pub\n\nPRIVATE (keep secret):\n$priv")

    // Step 2: after embedding the public key, encrypt and decrypt
    // val encrypted = HybridCrypto.encrypt("Hello, world")
    // val decrypted = HybridCrypto.decrypt(encrypted, System.getenv("APP_PRIVATE_KEY"))
}