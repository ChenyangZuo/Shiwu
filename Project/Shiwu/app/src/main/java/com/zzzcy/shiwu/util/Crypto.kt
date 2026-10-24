package com.zzzcy.shiwu.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 基于 Android Keystore 的 AES-GCM 加解密（凭据不落明文） */
object Crypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "shiwu_webdav_credentials"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private const val IV_LENGTH = 12
    private const val GCM_TAG_BYTES = 16
    private const val TAG_BITS = 128

    /** 新格式：version(1) + ivLen(1) + iv + ciphertext；旧格式（仅 iv + ciphertext）仍可解密。 */
    private const val PAYLOAD_VERSION: Byte = 1

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** 加密失败返回 null，绝不把异常抛给 UI/调用方。 */
    fun encrypt(context: Context, plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(2 + iv.size + encrypted.size)
        payload[0] = PAYLOAD_VERSION
        payload[1] = iv.size.toByte()
        iv.copyInto(payload, destinationOffset = 2)
        encrypted.copyInto(payload, destinationOffset = 2 + iv.size)
        Base64.encodeToString(payload, Base64.NO_WRAP)
    }.getOrNull()

    /** 解密失败（含数据损坏、密钥失效、旧数据格式异常）返回 null，不抛异常。 */
    fun decrypt(context: Context, payload: String): String? = runCatching {
        val raw = Base64.decode(payload, Base64.NO_WRAP)
        if (raw.size < IV_LENGTH + GCM_TAG_BYTES) return@runCatching null
        decryptVersioned(raw) ?: decryptLegacy(raw)
    }.getOrNull()

    private fun decryptVersioned(raw: ByteArray): String? {
        if (raw.size < 2 + IV_LENGTH + GCM_TAG_BYTES) return null
        if (raw[0] != PAYLOAD_VERSION) return null
        val ivLen = raw[1].toInt() and 0xFF
        if (ivLen <= 0 || raw.size < 2 + ivLen + GCM_TAG_BYTES) return null
        return runCatching {
            decryptRaw(
                iv = raw.copyOfRange(2, 2 + ivLen),
                data = raw.copyOfRange(2 + ivLen, raw.size)
            )
        }.getOrNull()
    }

    /** 兼容改动前已保存的 iv + ciphertext 格式。 */
    private fun decryptLegacy(raw: ByteArray): String? {
        if (raw.size < IV_LENGTH + GCM_TAG_BYTES) return null
        return runCatching {
            decryptRaw(
                iv = raw.copyOfRange(0, IV_LENGTH),
                data = raw.copyOfRange(IV_LENGTH, raw.size)
            )
        }.getOrNull()
    }

    private fun decryptRaw(iv: ByteArray, data: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }
}
