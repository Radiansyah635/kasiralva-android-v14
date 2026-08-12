package com.kasiralva.basic.data

import android.content.Context
import android.provider.Settings
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * License system backed by Firestore (no Cloud Functions).
 *
 * Document path: licenses/{LICENSE_KEY}
 * Fields: status, deviceHash, createdAt, activatedAt
 *
 * Security is enforced by Firestore Rules:
 * - get only if status == ACTIVE
 * - update only first activation (deviceHash empty -> filled)
 */
class LicenseManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("kasiralva_license", Context.MODE_PRIVATE)
    private val db = FirebaseFirestore.getInstance()

    private val deviceId: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown-device"

    fun isActivated(): Boolean = prefs.getBoolean("activated", false)

    fun licenseKey(): String = prefs.getString("licenseKey", "") ?: ""

    fun deviceHash(): String = sha256(deviceId).take(8).uppercase()

    fun deactivateLocal() {
        prefs.edit().clear().apply()
    }

    /**
     * Offline re-check: if already activated locally, stay activated.
     * Online activation goes through [activateOnline].
     */
    fun activateOffline(rawKey: String): Boolean {
        val key = normalize(rawKey)
        if (!isValidFormat(key)) return false
        // Local-only fallback (e.g. after reinstall while still bound online).
        // Real binding is done via Firestore.
        prefs.edit()
            .putBoolean("activated", true)
            .putString("licenseKey", key)
            .putString("deviceHash", deviceHash())
            .apply()
        return true
    }

    /**
     * Activate against Firestore.
     * Returns one of: OK, INVALID, USED, REVOKED, NETWORK_ERROR, ERROR
     *
     * Blocks the calling thread (must be called from background).
     */
    fun activateOnline(rawKey: String): String {
        val key = normalize(rawKey)
        if (!isValidFormat(key)) return "INVALID"

        val hash = deviceHash()
        val latch = CountDownLatch(1)
        var result = "ERROR"

        val docRef = db.collection("licenses").document(key)

        docRef.get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    result = "INVALID"
                    latch.countDown()
                    return@addOnSuccessListener
                }

                val status = snap.getString("status") ?: ""
                val existingHash = snap.getString("deviceHash") ?: ""

                when {
                    status != "ACTIVE" -> {
                        result = "REVOKED:status=[$status]len${status.length}"
                        latch.countDown()
                    }
                    existingHash.isNotEmpty() && existingHash != hash -> {
                        result = "USED"
                        latch.countDown()
                    }
                    existingHash == hash -> {
                        // Same device re-activation
                        saveLocal(key, hash)
                        result = "OK"
                        latch.countDown()
                    }
                    else -> {
                        // First activation
                        val updates = mapOf(
                            "deviceHash" to hash,
                            "activatedAt" to FieldValue.serverTimestamp()
                        )
                        docRef.update(updates)
                            .addOnSuccessListener {
                                saveLocal(key, hash)
                                result = "OK"
                                latch.countDown()
                            }
                            .addOnFailureListener { e ->
                                result = "ERROR:${e.javaClass.simpleName}:${e.message}"
                                latch.countDown()
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                result = "NETWORK_ERROR:${e.javaClass.simpleName}:${e.message}"
                latch.countDown()
            }

        latch.await(12, TimeUnit.SECONDS)
        return result
    }

    /**
     * Verify current license still valid online (optional periodic check).
     * Returns OK / REVOKED / USED / NETWORK_ERROR / INVALID
     */
    fun verifyOnline(): String {
        val key = licenseKey()
        if (key.isBlank() || !isActivated()) return "INVALID"

        val hash = deviceHash()
        val latch = CountDownLatch(1)
        var result = "NETWORK_ERROR"

        db.collection("licenses").document(key).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    result = "INVALID"
                } else {
                    val status = snap.getString("status") ?: ""
                    val existingHash = snap.getString("deviceHash") ?: ""
                    result = when {
                        status != "ACTIVE" -> "REVOKED"
                        existingHash.isNotEmpty() && existingHash != hash -> "USED"
                        else -> "OK"
                    }
                }
                latch.countDown()
            }
            .addOnFailureListener {
                result = "NETWORK_ERROR"
                latch.countDown()
            }

        latch.await(10, TimeUnit.SECONDS)
        return result
    }

    private fun saveLocal(key: String, hash: String) {
        prefs.edit()
            .putBoolean("activated", true)
            .putString("licenseKey", key)
            .putString("deviceHash", hash)
            .apply()
    }

    private fun isValidFormat(key: String): Boolean {
        val parts = key.split("-")
        if (parts.size != 4) return false
        if (parts[0] != "ALVA") return false
        if (parts[1].length != 4 || parts[2].length != 4 || parts[3].length != 8) return false
        return parts.drop(1).all { part -> part.all { it.isLetterOrDigit() } }
    }

    private fun normalize(value: String): String =
        value.trim().uppercase().replace(" ", "")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
