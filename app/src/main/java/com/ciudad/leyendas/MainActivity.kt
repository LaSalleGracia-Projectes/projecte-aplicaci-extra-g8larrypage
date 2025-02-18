package com.ciudad.leyendas

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var tvSteps: TextView
    private lateinit var btnSync: TextView
    private lateinit var tvLastSyncTime: TextView
    private val permission = HealthPermission.getReadPermission(StepsRecord::class)
    private lateinit var syncDataStore: SyncDataStore

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvSteps = findViewById(R.id.tvSteps)
        btnSync = findViewById(R.id.btnSync)
        tvLastSyncTime = findViewById(R.id.tvLastSyncTime)
        syncDataStore = SyncDataStore(this)

        val providerPackageName = "com.google.android.apps.healthdata"
        val context: Context = this

        val availabilityStatus = HealthConnectClient.getSdkStatus(context, providerPackageName)
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            return
        }
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val uriString = "https://play.google.com/store/apps/details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(uriString)
                    setPackage("com.android.vending")
                }
            )
            return
        }
        healthConnectClient = HealthConnectClient.getOrCreate(context)

        val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
        val requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            if (granted.contains(permission)) {
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val steps = readSteps(healthConnectClient)
                    tvSteps.text = "Pasos: $steps"
                    saveLastSyncTime()
                }
            } else {
                Toast.makeText(this, "Faltan permisos requeridos", Toast.LENGTH_SHORT).show()
            }
        }

        btnSync.setOnClickListener {
            lifecycleScope.launch {
                val hasPermission = checkPermissionsAndRun(healthConnectClient, requestPermissions)
                if (hasPermission) {
                    val steps = readSteps(healthConnectClient)
                    tvSteps.text = "Pasos: $steps"
                    saveLastSyncTime()
                } else {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
                    if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE && intent != null) {
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "Health Connect no está instalado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            syncDataStore.lastSyncTime.collect { lastSyncTime ->
                lastSyncTime?.let {
                    val date = Date(it)
                    val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    val formattedDate = format.format(date)
                    tvLastSyncTime.text = "Último sync: $formattedDate"
                }
            }
        }
    }

    private suspend fun checkPermissionsAndRun(
        healthConnectClient: HealthConnectClient,
        requestPermissions: ActivityResultLauncher<Set<String>>
    ): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return if (granted.contains(permission)) {
            true
        } else {
            requestPermissions.launch(setOf(permission))
            false
        }
    }

    private suspend fun readSteps(healthConnectClient: HealthConnectClient): Long {
        val now = Instant.now()
        val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf { it.count }
    }

    private suspend fun saveLastSyncTime() {
        val now = Instant.now().toEpochMilli()
        syncDataStore.saveLastSyncTime(now)
    }
}