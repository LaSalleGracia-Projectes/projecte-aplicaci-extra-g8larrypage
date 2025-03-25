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
import kotlinx.coroutines.flow.first
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

    @SerialName("pasos_totales")
    val pasosTotales: Int,

    @SerialName("pasos_nuevos_sync")
    val nuevosPasos: Int
)

@SuppressLint("HardwareIds")
fun getAndroidId(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}


@Throws(Exception::class)
fun encryptAndroidId(androidId: String): String {
    val keyLength = 256
    val iteraciones = 50000

    val fixedBytes = "CiudadLeyendas2025".toByteArray()

    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(androidId.toCharArray(), fixedBytes, iteraciones, keyLength)
    val tmp = factory.generateSecret(spec)
    val secretKey = SecretKeySpec(tmp.encoded, "AES")

    val iv = IvParameterSpec(fixedBytes.copyOf(16))
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)

    val bytesEncriptados = cipher.doFinal(androidId.toByteArray())
    return Base64.encodeToString(bytesEncriptados, Base64.NO_WRAP)
}

fun addData(context: Context, androidId: String, pasosTotalesActuales: Int) {
    val supabase = createSupabaseClient(
        supabaseUrl = "https://zlpjwwiqyssqrzlwinkj.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpscGp3d2lxeXNzcXJ6bHdpbmtqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzc0NzQ2NzUsImV4cCI6MjA1MzA1MDY3NX0.ju7cIcFBP7cFD3I9e-F1s1hgHgOJMtOS6AHGaEngcWM"
    ) {
        install(Postgrest)
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val encryptedId = encryptAndroidId(androidId)

            try {
                val existingRecords = supabase
                    .from("temporal_data")
                    .select() {
                        filter {
                            eq("android_id", encryptedId)
                        }
                    }
                    .decodeList<TemporalData>()

                val pasosNuevos = if (existingRecords.isNotEmpty()) {
                    val pasosTotalesAnteriores = existingRecords.first().pasosTotales
                    pasosTotalesActuales - pasosTotalesAnteriores
                } else {
                    pasosTotalesActuales
                }

                if (existingRecords.isNotEmpty()) {
                    val existingRecord = existingRecords.first()
                    supabase.from("temporal_data")
                        .update({
                            set("pasos_totales", pasosTotalesActuales)
                            set("pasos_nuevos_sync", pasosNuevos)
                        }) {
                            filter { existingRecord.id?.let { eq("id", it) } }
                        }
                } else {
                    val temporalData = TemporalData(
                        id = null,
                        androidId = encryptedId,
                        pasosTotales = pasosTotalesActuales,
                        nuevosPasos = pasosNuevos
                    )
                    supabase.from("temporal_data").insert(temporalData)
                }

                val syncDataStore = SyncDataStore.getInstance(context)
                syncDataStore.saveTotalSteps(pasosTotalesActuales.toLong())
                syncDataStore.saveRecentSteps(pasosNuevos.toLong())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}