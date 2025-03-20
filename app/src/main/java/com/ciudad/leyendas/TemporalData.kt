package com.ciudad.leyendas

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


@Serializable
data class TemporalData(
    @SerialName("id")
    val id: String? = null,

    @SerialName("android_id")
    val androidId: String,

    @SerialName("nuevos_pasos")
    val nuevosPasos: Int,

    @SerialName("salt")
    val salt: String
)

@SuppressLint("HardwareIds")
fun getAndroidId(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}

fun generateSalt(): ByteArray {
    val salt = ByteArray(16)
    val secureRandom = SecureRandom()
    secureRandom.nextBytes(salt)
    return salt
}

@Throws(Exception::class)
fun encryptAndroidId(androidId: String, salt: ByteArray): String {
    val iteraciones = 500
    val keyLength = 256

    // Combinar salt con androidId
    val saltedId = Base64.encodeToString(salt, Base64.NO_WRAP) + androidId
    val password = saltedId.toCharArray()

    // Generar clave
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password, salt, iteraciones, keyLength)
    val tmp = factory.generateSecret(spec)
    val secretKey = SecretKeySpec(tmp.encoded, "AES")

    // Usar un IV fijo (derivado del salt)
    val iv = salt.copyOf(16)
    val ivspec = IvParameterSpec(iv)

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec)

    var encryptedText = saltedId
    repeat(500) {
        val encrypted = cipher.doFinal(encryptedText.toByteArray())
        encryptedText = Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    return encryptedText
}