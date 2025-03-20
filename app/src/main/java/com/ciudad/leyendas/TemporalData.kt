package com.ciudad.leyendas

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val iteraciones = 500  // Para PBKDF2
    val keyLength = 256

    // 1. Combinar salt con androidId (igual que antes)
    val saltedId = Base64.encodeToString(salt, Base64.NO_WRAP) + androidId
    val password = saltedId.toCharArray()

    // 2. Generar clave con PBKDF2 (igual que antes)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password, salt, iteraciones, keyLength)
    val tmp = factory.generateSecret(spec)
    val secretKey = SecretKeySpec(tmp.encoded, "AES")

    // 3. Configurar IV y cipher (igual que antes)
    val iv = salt.copyOf(16)
    val ivspec = IvParameterSpec(iv)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec)

    // 4. Encriptar 5 veces
    var textoEncriptado = saltedId
    repeat(5) {
        val bytesEncriptados = cipher.doFinal(textoEncriptado.toByteArray())
        textoEncriptado = Base64.encodeToString(bytesEncriptados, Base64.NO_WRAP)
    }

    return textoEncriptado
}
fun addData(androidId: String, nuevosPasos: Int) {
    val supabase = createSupabaseClient(
        supabaseUrl = "https://zlpjwwiqyssqrzlwinkj.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpscGp3d2lxeXNzcXJ6bHdpbmtqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzc0NzQ2NzUsImV4cCI6MjA1MzA1MDY3NX0.ju7cIcFBP7cFD3I9e-F1s1hgHgOJMtOS6AHGaEngcWM"
    ) {
        install(Postgrest)
    }

    val salt = generateSalt()
    val encryptedId = encryptAndroidId(androidId, salt)

    val temporalData = TemporalData(
        androidId = encryptedId,
        nuevosPasos = nuevosPasos,
        salt = Base64.encodeToString(salt, Base64.NO_WRAP)
    )

    CoroutineScope(Dispatchers.IO).launch {
        try {
            supabase.from("temporal_data").upsert(temporalData)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}