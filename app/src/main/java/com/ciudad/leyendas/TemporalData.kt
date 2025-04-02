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
    val nuevosPasos: Int,

    @SerialName("day")
    val day: String? = null,
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
            val syncDataStore = SyncDataStore.getInstance(context)
            val ultimosPasosSincronizados = syncDataStore.totalSteps.first() ?: 0
            val encryptedId = encryptAndroidId(androidId)
            val fechaActual = java.time.LocalDate.now().toString()

            val existingRecords = supabase
                .from("temporal_data")
                .select() {
                    filter {
                        eq("android_id", encryptedId)
                    }
                }
                .decodeList<TemporalData>()

            if (existingRecords.isNotEmpty()) {
                val existingRecord = existingRecords.first()
                val pasosTotalesAnteriores = existingRecord.pasosTotales
                val pasosNuevosAnteriores = existingRecord.nuevosPasos
                val diaAnterior = existingRecord.day ?: fechaActual
                val esMismoDia = diaAnterior == fechaActual

                if (esMismoDia && pasosTotalesActuales == ultimosPasosSincronizados.toInt()) {
                    return@launch
                }

                val incrementoPasos = if (pasosTotalesActuales > ultimosPasosSincronizados) {
                    pasosTotalesActuales - ultimosPasosSincronizados.toInt()
                } else {
                    0
                }

                val debeActualizar = if (!esMismoDia) {
                    true
                } else {
                    incrementoPasos > 0
                }

                if (debeActualizar) {
                    val pasosNuevosActualizados = if (!esMismoDia) {
                        pasosTotalesActuales
                    } else {
                        pasosNuevosAnteriores + incrementoPasos
                    }

                    val pasosTotalesActualizados = if (!esMismoDia) {
                        pasosTotalesAnteriores + pasosTotalesActuales
                    } else {
                        pasosTotalesAnteriores + incrementoPasos
                    }

                    supabase.from("temporal_data")
                        .update({
                            set("pasos_totales", pasosTotalesActualizados)
                            set("pasos_nuevos_sync", pasosNuevosActualizados)
                            set("day", fechaActual)
                        }) {
                            filter { existingRecord.id?.let { eq("id", it) } }
                        }

                    syncDataStore.saveTotalSteps(pasosTotalesActuales.toLong())
                }
            } else {
                val temporalData = TemporalData(
                    id = null,
                    androidId = encryptedId,
                    pasosTotales = pasosTotalesActuales,
                    nuevosPasos = pasosTotalesActuales,
                    day = fechaActual
                )
                supabase.from("temporal_data").insert(temporalData)

                syncDataStore.saveTotalSteps(pasosTotalesActuales.toLong())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}