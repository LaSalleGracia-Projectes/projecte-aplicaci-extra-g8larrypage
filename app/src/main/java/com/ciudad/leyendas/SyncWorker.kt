import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ciudad.leyendas.SyncDataStore
import java.time.Instant
import java.time.temporal.ChronoUnit

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val syncDataStore = SyncDataStore.getInstance(context)
    private val healthConnectClient = HealthConnectClient.getOrCreate(context)

    override suspend fun doWork(): Result {
        return try {
            Log.d("SyncWorker", "Iniciando trabajo de sincronización")
            val steps = readSteps()
            syncDataStore.saveTotalSteps(steps)
            syncDataStore.saveLastSyncTime(Instant.now().toEpochMilli())
            Log.d("SyncWorker", "Datos guardados: Total Steps = $steps")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error al guardar los datos", e)
            Result.retry()
        }
    }

    private suspend fun readSteps(): Long {
        val now = Instant.now()
        val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
        )
        val response = healthConnectClient.readRecords(request)
        val steps = response.records.sumOf { it.count }
        Log.d("SyncWorker", "Pasos leídos: $steps")
        return steps
    }
}