package com.jphat.filebeacon

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*

object NetworkUtils {

    /**
     * Checks if the WiFi adapter on the device is enabled.
     * This does not mean the device is connected to a network.
     */
    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    /**
     * Checks if the device is currently connected to a WiFi network.
     * This is the most reliable way to check for an active WiFi connection.
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Retrieves the SSID (network name) of the currently connected WiFi network.
     * Requires location permissions on Android 9+.
     * Returns null if not connected or if the SSID is unavailable.
     */
    fun getConnectedSsid(context: Context): String? {
        if (!isWifiConnected(context)) {
            return null
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // WifiInfo is deprecated but is the primary way to get SSID on many API levels.
        // We suppress the warning as it's a necessary evil for this functionality.
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo

        // A valid SSID will be enclosed in quotes, which we need to remove.
        // An unknown SSID is often returned as "<unknown ssid>".
        return if (wifiInfo != null && wifiInfo.ssid.isNotBlank() && wifiInfo.ssid != "<unknown ssid>") {
            wifiInfo.ssid.trim('"')
        } else {
            // Fallback for newer APIs or when SSID is not available directly
            "Connected (SSID unavailable)"
        }
    }

    /**
     * Retrieves the device's local IPv4 address.
     * It iterates through network interfaces to find the active WLAN IPv4 address.
     * Returns null if no suitable IPv4 address is found.
     */
    fun getDeviceIpAddress(context: Context): String? {
        if (!isWifiConnected(context)) return null

        // This is the most reliable method, as it doesn't rely on deprecated APIs.
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                // Filter for WiFi interfaces that are up and not a loopback address
                if (networkInterface.name.lowercase(Locale.getDefault()).contains("wlan") &&
                    networkInterface.isUp && !networkInterface.isLoopback) {

                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        // Ensure it's a private IPv4 address
                        if (!address.isLoopbackAddress && address is Inet4Address && isPrivateIPv4(address.hostAddress)) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log the exception or handle it as needed
            e.printStackTrace()
        }
        return null // Return null if no address is found
    }

    /**
     * Helper function to check if an IP address is in a private range (e.g., 192.168.x.x).
     */
    private fun isPrivateIPv4(ip: String?): Boolean {
        if (ip == null) return false
        return ip.startsWith("192.168.") ||
                ip.startsWith("10.") ||
                (ip.startsWith("172.") &&
                        ip.split(".").getOrNull(1)?.toIntOrNull()?.let { it in 16..31 } == true)
    }

    /**
     * Validates if a given string is a valid IPv4 address.
     */
    fun isValidIPv4(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } ?: false }
        } catch (e: Exception) {
            false
        }
    }
}
