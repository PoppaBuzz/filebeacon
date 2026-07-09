package com.jphat.filebeacon

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DeviceDiscoveryManager(context: Context) {

    companion object {
        private const val TAG = "DeviceDiscovery"
        private const val SERVICE_TYPE = "_filebeacon._tcp.local."
        private const val SERVICE_NAME = "FileBeacon"
    }

    // Use application context to prevent memory leaks
    private val appContext = context.applicationContext

    private var jmdns: JmDNS? = null
    private var serviceInfo: ServiceInfo? = null
    private val discoveredDevices = mutableMapOf<String, DiscoveredDevice>()
    private var listener: DeviceDiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var executorService: ExecutorService? = null

    data class DiscoveredDevice(
        val name: String,
        val address: String,
        val port: Int,
        val url: String
    )

    interface DeviceDiscoveryListener {
        fun onDeviceFound(device: DiscoveredDevice)
        fun onDeviceRemoved(deviceName: String)
    }

    fun setListener(listener: DeviceDiscoveryListener?) {
        this.listener = listener
    }

    fun startAdvertising(port: Int, deviceName: String = android.os.Build.MODEL) {
        if (executorService == null || executorService?.isShutdown == true) {
            executorService = Executors.newSingleThreadExecutor()
        }

        executorService?.execute {
            try {
                val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

                multicastLock?.let { if (it.isHeld) it.release() }

                multicastLock = wifiManager.createMulticastLock("filebeacon_lock")
                multicastLock?.setReferenceCounted(true)
                multicastLock?.acquire()

                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                @Suppress("DEPRECATION")
                val ipInt = wifiInfo.ipAddress
                val ipAddress = InetAddress.getByName(
                    String.format(
                        java.util.Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                )

                jmdns = JmDNS.create(ipAddress, deviceName)

                serviceInfo = ServiceInfo.create(
                    SERVICE_TYPE,
                    deviceName,
                    port,
                    "FileBeacon Server"
                )

                jmdns?.registerService(serviceInfo)
                Log.d(TAG, "mDNS service registered: $deviceName on port $port")

                startDiscovery()

            } catch (e: IOException) {
                Log.e(TAG, "Failed to start mDNS advertising", e)
            }
        }
    }

    private fun startDiscovery() {
        jmdns?.addServiceListener(SERVICE_TYPE, object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent?) {
                Log.d(TAG, "Service added: ${event?.name}")
                jmdns?.requestServiceInfo(event?.type, event?.name)
            }

            override fun serviceRemoved(event: ServiceEvent?) {
                Log.d(TAG, "Service removed: ${event?.name}")
                event?.name?.let { name ->
                    discoveredDevices.remove(name)
                    listener?.onDeviceRemoved(name)
                }
            }

            override fun serviceResolved(event: ServiceEvent?) {
                Log.d(TAG, "Service resolved: ${event?.name}")
                event?.info?.let { info ->
                    val addresses = info.inet4Addresses
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0].hostAddress ?: return
                        val port = info.port
                        val device = DiscoveredDevice(
                            name = info.name,
                            address = address,
                            port = port,
                            url = "http://$address:$port"
                        )
                        discoveredDevices[info.name] = device
                        listener?.onDeviceFound(device)
                    }
                }
            }
        })
    }

    fun stopAdvertising() {
        try {
            jmdns?.unregisterAllServices()
            jmdns?.close()
            jmdns = null
            discoveredDevices.clear()

            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Multicast lock released")
                }
            }
            multicastLock = null

            executorService?.shutdown()
            executorService = null

            Log.d(TAG, "mDNS service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mDNS service", e)
        }
    }

    fun getDiscoveredDevices(): List<DiscoveredDevice> {
        return discoveredDevices.values.toList()
    }
}
