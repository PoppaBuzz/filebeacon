package com.jphat.filebeacon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class FileExplorerService : Service() {

    private var webServer: WebServer? = null
    private var deviceDiscovery: DeviceDiscoveryManager? = null
    private val defaultPort = 8080
    @Suppress("DEPRECATION")
    companion object {
        private const val TAG = "FileExplorerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "FileBeaconServiceChannel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        try {
            val port = intent?.getIntExtra("PORT", defaultPort) ?: defaultPort
            webServer?.stop()
            webServer = WebServer(this, port)
            webServer?.start()

            deviceDiscovery = DeviceDiscoveryManager(this)
            deviceDiscovery?.startAdvertising(port)

            val notification = createNotification("FileBeacon server is running on port $port.")
            startForeground(NOTIFICATION_ID, notification)
            Log.i(TAG, "Web server started on port $port.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start web server.", e)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceDiscovery?.stopAdvertising()
        deviceDiscovery = null
        webServer?.stop()
        webServer = null
        Log.i(TAG, "Web server stopped.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FileBeacon Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FileBeacon")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_wifi_file_explorer)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
