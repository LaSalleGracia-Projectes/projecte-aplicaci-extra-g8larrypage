package com.ciudad.leyendas

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import java.time.Instant
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {
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

        val providerPackageName = "com.ciudad.leyendas"
        val context: Context = this
        val permission = HealthPermission.getReadPermission(StepsRecord::class)

        val availabilityStatus = HealthConnectClient.getSdkStatus(context, providerPackageName)
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            return
        }
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uriString)
                    putExtra("overlay", true)
                    putExtra("callerId", context.packageName)
                }
            )
            return
        }
        val healthConnectClient = HealthConnectClient.getOrCreate(context)

        // Create the permissions launcher
        val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()

        val requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            if (granted.contains(permission)) {
                Toast.makeText(this, "Permissions successfully granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Lack of required permissions", Toast.LENGTH_SHORT).show()
            }
        }

        suspend fun checkPermissionsAndRun(healthConnectClient: HealthConnectClient): Boolean {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.contains(permission)) {
                // Permissions already granted; proceed with inserting or reading data
                return true
            } else {
                requestPermissions.launch(setOf(permission))
                return false
            }
        }

        suspend fun readSteps(healthConnectClient: HealthConnectClient): Long {
            val now = Instant.now()
            val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
            )
            val response = healthConnectClient.readRecords(request)
            return response.records.sumOf { it.count }
        }

        val tvSteps = findViewById<TextView>(R.id.tvSteps)
        val btnSync = findViewById<TextView>(R.id.btnSync)
        var hasPermission = false

        btnSync.setOnClickListener {
            lifecycleScope.launch {
                hasPermission = checkPermissionsAndRun(healthConnectClient = healthConnectClient)
                if (hasPermission) {
                    val steps = readSteps(healthConnectClient)
                    tvSteps.text = "Pasos: $steps"
                }
            }
        }
    }
}