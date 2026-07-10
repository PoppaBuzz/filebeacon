@file:Suppress("DEPRECATION")

package com.jphat.filebeacon

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jphat.filebeacon.databinding.ActivityMainBinding
import com.jphat.filebeacon.databinding.ActivityPermissionsIntroBinding
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity(), DeviceDiscoveryManager.DeviceDiscoveryListener {

    private var introBinding: ActivityPermissionsIntroBinding? = null
    private var mainBinding: ActivityMainBinding? = null
    private var deviceDiscoveryManager: DeviceDiscoveryManager? = null
    private val discoveredDevices = mutableListOf<DeviceDiscoveryManager.DiscoveredDevice>()
    private var chosenPort: Int = 8080  // Moved inside class to prevent global state issues

    // Permissions
    private val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION

    private val mediaPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private val writeStoragePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE

    // Launchers
    private val requestLocationPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updatePermissionIntroUI()
            checkAndProceed()
        }

    private val requestMediaPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updatePermissionIntroUI()
            checkAndProceed()
        }

    private val requestWriteStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updatePermissionIntroUI()
            checkAndProceed()
        }

    private val requestManageExternalStorageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updatePermissionIntroUI()
            checkAndProceed()
        }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateWifiStatusIndicators()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply custom theme before super.onCreate
        val currentTheme = ThemeManager.getCurrentTheme(this)
        setTheme(ThemeManager.getThemeResourceId(currentTheme))
        ThemeManager.initializeTheme(this)
        
        super.onCreate(savedInstanceState)
        if (allPermissionsGranted()) {
            showMainUI()
        } else {
            showPermissionIntroUI()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction("android.net.conn.CONNECTIVITY_CHANGE")
        }
        registerReceiver(wifiReceiver, filter)
        updateWifiStatusIndicators()
        updateUIForServerState()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiverSafely()
    }

    // ===== Permission Checks =====
    private fun isLocationGranted() =
        ContextCompat.checkSelfPermission(this, locationPermission) == PackageManager.PERMISSION_GRANTED

    private fun isMediaGranted() =
        mediaPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun isWriteStorageGranted() =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            ContextCompat.checkSelfPermission(this, writeStoragePermission) == PackageManager.PERMISSION_GRANTED
        } else true

    private fun isManageExternalStorageGranted() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true

    private fun allPermissionsGranted(): Boolean {
        return isLocationGranted() &&
                isMediaGranted() &&
                isWriteStorageGranted() &&
                isManageExternalStorageGranted()
    }

    // ===== Intro Screen =====
    private fun showPermissionIntroUI() {
        introBinding = ActivityPermissionsIntroBinding.inflate(layoutInflater)
        setContentView(introBinding!!.root)

        // Two-color app name: "File" white, "Beacon" #0197FE
        introBinding!!.tvAppTitle.text = buildTwoColorTitle()

        introBinding!!.apply {
            btnRequestLocation.setOnClickListener {
                requestLocationPermissionsLauncher.launch(arrayOf(locationPermission))
            }
            btnRequestMedia.setOnClickListener {
                requestMediaPermissionsLauncher.launch(mediaPermissions)
            }
            btnRequestWriteStorage.setOnClickListener {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    requestWriteStoragePermissionLauncher.launch(writeStoragePermission)
                }
            }
            btnRequestManageStorage.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = "package:$packageName".toUri()
                    requestManageExternalStorageLauncher.launch(intent)
                }
            }
            btnSkipPermissions.setOnClickListener {
                showSkipWarningDialog()
            }
            updatePermissionIntroUI()
        }
    }

    private fun updatePermissionIntroUI() {
        introBinding?.apply {
            val grantedColor = ContextCompat.getColor(this@MainActivity, R.color.permission_granted)
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
            val primaryColor = typedValue.data

            fun styleButton(btn: com.google.android.material.button.MaterialButton, granted: Boolean) {
                btn.isEnabled = !granted
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (granted) grantedColor else primaryColor
                )
                btn.setIconResource(if (granted) R.drawable.ic_check_circle else 0)
                btn.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_END
            }

            styleButton(btnRequestLocation, isLocationGranted())
            styleButton(btnRequestMedia, isMediaGranted())
            styleButton(btnRequestWriteStorage, isWriteStorageGranted())
            styleButton(btnRequestManageStorage, isManageExternalStorageGranted())
        }
    }

    private fun showSkipWarningDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.skip_permissions)
            .setMessage(R.string.skip_warning_message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                proceedToMainUI(skippedPermissions = true)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun checkAndProceed() {
        if (allPermissionsGranted()) proceedToMainUI()
    }

    private fun proceedToMainUI(skippedPermissions: Boolean = false) {
        introBinding = null
        showMainUI()
        if (skippedPermissions) {
            Toast.makeText(this, getString(R.string.skip_warning_message), Toast.LENGTH_LONG).show()
        }
    }

    // ===== Main UI =====
    fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun buildTwoColorTitle(): SpannableString {
        val full = "FileBeacon"
        val span = SpannableString(full)
        // "File" = white
        span.setSpan(ForegroundColorSpan(Color.WHITE), 0, 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        // "Beacon" = #0197FE
        span.setSpan(ForegroundColorSpan(Color.parseColor("#0197FE")), 4, full.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    private fun showMainUI() {
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding!!.root)
        // Force insets to be re-dispatched to the new view tree — necessary when
        // setContentView is called a second time (e.g. transitioning from permissions screen).
        ViewCompat.requestApplyInsets(mainBinding!!.root)

        // Two-color app name: "File" white, "Beacon" #0197FE
        mainBinding!!.appNameTextView.text = buildTwoColorTitle()

        mainBinding?.wifiOptions?.setOnClickListener {
            val editText = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(chosenPort.toString())
                setTextColor("#0D1B2A".toColorInt())
                setHintTextColor("#299B80".toColorInt())
                hint = "Enter port (1-65535)"
            }

            val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Change Server Port")
                .setMessage("Enter new port number")
                .setView(editText)
                .setPositiveButton("OK", null) // Set to null initially
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .create()

            dialog.show()

// Override the positive button click listener after showing the dialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val port = editText.text.toString().toIntOrNull()
                if (port != null && port in 1..65535) {
                    chosenPort = port
                    restartServerWithNewPort()
                    dialog.dismiss() // Only dismiss if valid
                } else {
                    // Show error in hint and keep dialog open
                    editText.error = "Port must be between 1 and 65535"
                    // Or update the hint text
                    editText.hint = "Invalid port! Enter port (1-65535)"
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(mainBinding!!.root) { view, windowInsets ->
            val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val sysInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPadding = 16.dpToPx()

            val leftPadding = if (navInsets.left > 0) navInsets.left + extraPadding else extraPadding
            val rightPadding = if (navInsets.right > 0) navInsets.right + extraPadding else extraPadding

            // Apply left/right/bottom insets to the root, but NOT top — the header card
            // handles the status bar offset via its marginTop so the logo position stays consistent.
            view.setPadding(leftPadding, 0, rightPadding, sysInsets.bottom)

            // Push the header card down by the status bar height.
            mainBinding?.root?.getChildAt(0)?.let { headerCard ->
                val lp = headerCard.layoutParams as? android.widget.LinearLayout.LayoutParams
                lp?.topMargin = sysInsets.top
                headerCard.layoutParams = lp
            }

            windowInsets
        }

        mainBinding!!.btnServerToggle.setOnClickListener {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            val wifiEnabled = wifiManager?.isWifiEnabled ?: false
            val wifiInfo = wifiManager?.connectionInfo
            val connected = wifiInfo != null &&
                    wifiInfo.networkId != -1 &&
                    !wifiInfo.ssid.isNullOrEmpty() &&
                    !wifiInfo.ssid.equals("<unknown ssid>", true) &&
                    !wifiInfo.ssid.equals("0x", true)

            if (!wifiEnabled || !connected) {
                Toast.makeText(this, "Connect to a WiFi network before starting the server.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Existing logic:
            if (isServerRunning()) stopServer() else startServer()
            updateWifiStatusIndicators()
            updateUIForServerState()
        }

        mainBinding!!.btnNearbyDevices?.setOnClickListener {
            showNearbyDevicesDialog()
        }

        mainBinding!!.btnTheme?.setOnClickListener {
            showThemeSelectionDialog()
        }

        // Initialize AuthManager
        AuthManager.initialize(this)

        // Add authentication settings button
        mainBinding!!.btnSecurity?.setOnClickListener {
            showSecurityDialog()
        }

        // Initialize device discovery
        deviceDiscoveryManager = DeviceDiscoveryManager(this)
        deviceDiscoveryManager?.setListener(this)
    }

    override fun onDeviceFound(device: DeviceDiscoveryManager.DiscoveredDevice) {
        runOnUiThread {
            if (!discoveredDevices.any { it.name == device.name }) {
                discoveredDevices.add(device)
                Toast.makeText(this, "Found device: ${device.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDeviceRemoved(deviceName: String) {
        runOnUiThread {
            discoveredDevices.removeAll { it.name == deviceName }
        }
    }

    private fun showNearbyDevicesDialog() {
        if (discoveredDevices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Nearby Devices")
                .setMessage("No devices found on the network.\n\nMake sure:\n• Other devices are running FileBeacon\n• All devices are on the same WiFi network\n• The server is running on this device")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
            return
        }

        val deviceNames = discoveredDevices.map { device ->
            "${device.name}\n${device.url}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Nearby Devices (${discoveredDevices.size})")
            .setItems(deviceNames) { _, which ->
                val device = discoveredDevices[which]
                openDeviceUrl(device.url)
            }
            .setNegativeButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun openDeviceUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open URL: $url", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showThemeSelectionDialog() {
        ThemeSelectionDialog(this) { theme ->
            ThemeManager.setTheme(this, theme)
            Toast.makeText(this, "Theme changed to ${theme.displayName}", Toast.LENGTH_SHORT).show()
            // Recreate activity to apply theme
            recreate()
        }.show()
    }

    private fun showSecurityDialog() {
        val options = arrayOf(
            "Enable/Disable Authentication",
            "Set Password",
            "Clear Authentication"
        )

        AlertDialog.Builder(this)
            .setTitle("Security Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleAuthentication()
                    1 -> showSetPasswordDialog()
                    2 -> clearAuthentication()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun toggleAuthentication() {
        if (!AuthManager.hasPassword()) {
            Toast.makeText(this, "Please set a password first", Toast.LENGTH_SHORT).show()
            showSetPasswordDialog()
            return
        }

        val currentlyEnabled = AuthManager.isAuthEnabled()
        AuthManager.setAuthEnabled(!currentlyEnabled)

        val status = if (!currentlyEnabled) "enabled" else "disabled"
        Toast.makeText(this, "Authentication $status", Toast.LENGTH_SHORT).show()
    }

    private fun showSetPasswordDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter password (min 4 characters)"
        }

        AlertDialog.Builder(this)
            .setTitle("Set Server Password")
            .setMessage("Set a password to protect access to your files")
            .setView(input)
            .setPositiveButton("Set") { dialog, _ ->
                val password = input.text.toString()
                if (password.length < 4) {
                    Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                } else {
                    if (AuthManager.setPassword(password)) {
                        AuthManager.setAuthEnabled(true)
                        Toast.makeText(this, "Password set and authentication enabled", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to set password", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun clearAuthentication() {
        AlertDialog.Builder(this)
            .setTitle("Clear Authentication")
            .setMessage("This will remove the password and disable authentication. Continue?")
            .setPositiveButton("Yes") { dialog, _ ->
                AuthManager.clearAuth()
                Toast.makeText(this, "Authentication cleared", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    private fun restartServerWithNewPort() {
        stopServer()
        // Pass port to your FileExplorerService/WebServer (you may need to update the service to accept the port as an Intent extra)
        val intent = Intent(this, FileExplorerService::class.java).apply {
            putExtra("PORT", chosenPort)
        }
        startService(intent)
        updateUIForServerState()
    }
    private fun startServer() {
        startService(Intent(this, FileExplorerService::class.java))
        Toast.makeText(this, getString(R.string.server_starting), Toast.LENGTH_SHORT).show()
        
        // Start device discovery
        deviceDiscoveryManager?.startAdvertising(chosenPort, android.os.Build.MODEL)
        
        updateUIForServerState()
        updateBottomWifiIcon(true, true)
    }


    private fun stopServer() {
        stopService(Intent(this, FileExplorerService::class.java))
        Toast.makeText(this, getString(R.string.server_stopping), Toast.LENGTH_SHORT).show()
        
        // Stop device discovery
        deviceDiscoveryManager?.stopAdvertising()
        discoveredDevices.clear()
        
        updateUIForServerState()
        updateBottomWifiIcon(true, false)
    }

    private fun updateUIForServerState() {
        mainBinding?.apply {
            val serverRunning = isServerRunning()
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            val wifiEnabled = wifiManager?.isWifiEnabled ?: false

            if (serverRunning && wifiEnabled) {
                btnServerToggle.text = getString(R.string.server_running_button)
                tvWebUrl.text = getDeviceIpAddress()?.let { "http://$it:$chosenPort" } ?: getString(R.string.no_wifi)
            } else {
                // Either server is stopped, or wifi is off, treat as server stopped
                btnServerToggle.text = getString(R.string.start_server)
                tvWebUrl.text = getString(R.string.server_stopped_message)
            }
        }
    }


    // ===== Helpers =====
    private fun isServerRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == FileExplorerService::class.qualifiedName }
    }

    @SuppressLint("DefaultLocale")
    private fun getDeviceIpAddress(): String? {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo ?: return null
        val ipInt = wifiInfo.ipAddress
        val ip = String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )
        return if (ip == "0.0.0.0") null else ip
    }

    private fun getCurrentSsid(): String {
        if (!canReadWifiInfo()) return getString(R.string.location_denied)
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo ?: return getString(R.string.no_wifi)
        if (!wifiManager.isWifiEnabled) return getString(R.string.no_wifi)
        val ssid = wifiInfo.ssid
        return if (ssid.isNullOrEmpty() || ssid == "<unknown ssid>" || ssid.equals("0x", true)) {
            getString(R.string.no_wifi)
        } else ssid.trim('"')
    }

    private fun updateBottomWifiIcon(wifiEnabled: Boolean, serverRunning: Boolean) {
        val wifiImageView = mainBinding?.bottomWifiIcon ?: return
        if (!wifiEnabled) {
            wifiImageView.setImageResource(R.drawable.wifi_off_24)
            (wifiImageView.drawable as? AnimationDrawable)?.stop()
        } else {
            if (serverRunning) {
                wifiImageView.setImageResource(R.drawable.wifi_signal_anim)
                (wifiImageView.drawable as? AnimationDrawable)?.start()
            } else {
                wifiImageView.setImageResource(R.drawable.wifi_24)
                (wifiImageView.drawable as? AnimationDrawable)?.stop()
            }
        }
    }

    private fun updateWifiStatusIndicators() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        val wifiEnabled = wifiManager?.isWifiEnabled ?: false
        val wifiInfo = wifiManager?.connectionInfo
        val serverRunning = isServerRunning()
        val wifiReadable = canReadWifiInfo()

        mainBinding?.apply {
            wifiEnabledIndicator.setImageResource(
                if (wifiEnabled) R.drawable.ic_circle_green else R.drawable.ic_circle_red
            )
            wifiConnectedIndicator.setImageResource(
                if (wifiReadable && wifiEnabled && wifiInfo != null &&
                    wifiInfo.networkId != -1 && getCurrentSsid() != getString(R.string.no_wifi)
                ) R.drawable.ic_circle_green
                else if (isLocationGranted()) R.drawable.ic_circle_red else R.drawable.ic_triangle_yellow
            )
            tvSsid.text = getCurrentSsid()
            btnServerToggle.isEnabled = true
        }

        updateUIForServerState()
        updateBottomWifiIcon(wifiEnabled, serverRunning)
    }

    private fun unregisterReceiverSafely() {
        try {
            unregisterReceiver(wifiReceiver)
        } catch (ignored: IllegalArgumentException) { }
    }

    private fun canReadWifiInfo(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            true
        } else isLocationGranted() && isLocationServiceEnabled()
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }
}
