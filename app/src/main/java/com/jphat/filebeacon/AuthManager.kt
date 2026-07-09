package com.jphat.filebeacon

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages authentication for the web server.
 * Provides simple password-based authentication with secure storage.
 */
object AuthManager {
    private const val TAG = "AuthManager"
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_PASSWORD_SALT = "password_salt"
    private const val KEY_AUTH_ENABLED = "auth_enabled"
    private const val KEY_AUTH_TOKEN = "auth_token"

    private var prefs: SharedPreferences? = null
    private val secureRandom = SecureRandom()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if authentication is enabled
     */
    fun isAuthEnabled(): Boolean {
        return prefs?.getBoolean(KEY_AUTH_ENABLED, false) ?: false
    }

    /**
     * Enable or disable authentication
     */
    fun setAuthEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTH_ENABLED, enabled)?.apply()
    }

    /**
     * Set a new password for authentication
     */
    fun setPassword(password: String): Boolean {
        return try {
            if (password.length < 4) {
                Log.w(TAG, "Password too short")
                return false
            }

            // Generate a random salt
            val salt = ByteArray(16)
            secureRandom.nextBytes(salt)

            // Hash the password with the salt
            val hash = hashPassword(password, salt)

            // Store the hash and salt
            prefs?.edit()
                ?.putString(KEY_PASSWORD_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                ?.putString(KEY_PASSWORD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                ?.apply()

            Log.i(TAG, "Password set successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting password", e)
            false
        }
    }

    /**
     * Verify a password
     */
    fun verifyPassword(password: String): Boolean {
        return try {
            val storedHashStr = prefs?.getString(KEY_PASSWORD_HASH, null) ?: return false
            val storedSaltStr = prefs?.getString(KEY_PASSWORD_SALT, null) ?: return false

            val storedHash = Base64.decode(storedHashStr, Base64.NO_WRAP)
            val salt = Base64.decode(storedSaltStr, Base64.NO_WRAP)

            val inputHash = hashPassword(password, salt)

            // Constant-time comparison to prevent timing attacks
            MessageDigest.isEqual(storedHash, inputHash)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying password", e)
            false
        }
    }

    /**
     * Generate a session token for authenticated clients
     */
    fun generateSessionToken(): String {
        val tokenBytes = ByteArray(32)
        secureRandom.nextBytes(tokenBytes)
        val token = Base64.encodeToString(tokenBytes, Base64.NO_WRAP or Base64.URL_SAFE)

        // Store the token (in production, you'd want to store multiple tokens with expiration)
        prefs?.edit()?.putString(KEY_AUTH_TOKEN, token)?.apply()

        return token
    }

    /**
     * Verify a session token
     */
    fun verifySessionToken(token: String): Boolean {
        val storedToken = prefs?.getString(KEY_AUTH_TOKEN, null) ?: return false
        return MessageDigest.isEqual(
            token.toByteArray(),
            storedToken.toByteArray()
        )
    }

    /**
     * Check if authentication is required for a request
     */
    fun isAuthRequired(): Boolean {
        if (!isAuthEnabled()) return false

        val hasPassword = prefs?.getString(KEY_PASSWORD_HASH, null) != null
        return hasPassword
    }

    /**
     * Verify HTTP Basic Auth header
     */
    fun verifyBasicAuth(authHeader: String?): Boolean {
        if (!isAuthRequired()) return true
        if (authHeader == null) return false

        try {
            // Parse Basic Auth header: "Basic base64(username:password)"
            if (!authHeader.startsWith("Basic ", ignoreCase = true)) {
                return false
            }

            val base64Credentials = authHeader.substring(6)
            val credentials = String(Base64.decode(base64Credentials, Base64.NO_WRAP))
            val parts = credentials.split(":", limit = 2)

            if (parts.size != 2) return false

            // We only check the password (username can be anything)
            return verifyPassword(parts[1])
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying basic auth", e)
            return false
        }
    }

    /**
     * Hash a password with salt using SHA-256
     */
    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(password.toByteArray())
    }

    /**
     * Clear all authentication data
     */
    fun clearAuth() {
        prefs?.edit()
            ?.remove(KEY_PASSWORD_HASH)
            ?.remove(KEY_PASSWORD_SALT)
            ?.remove(KEY_AUTH_TOKEN)
            ?.putBoolean(KEY_AUTH_ENABLED, false)
            ?.apply()
        Log.i(TAG, "Authentication data cleared")
    }

    /**
     * Check if a password is set
     */
    fun hasPassword(): Boolean {
        return prefs?.getString(KEY_PASSWORD_HASH, null) != null
    }
}
