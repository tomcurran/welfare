package org.tomcurran.welfare.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.tomcurran.welfare.BuildConfig
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AppLogger {

    private const val MAX_ENTRIES = 500
    private const val FILE_NAME = "app_logs.enc"
    private const val KEY_ALIAS = "welfare_app_log_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_SIZE = 12

    private lateinit var appContext: Context
    private val logEntries = ArrayDeque<LogEntry>()
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    private val _entriesFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val entriesFlow: StateFlow<List<LogEntry>> = _entriesFlow.asStateFlow()

    @Serializable
    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String,
        val throwable: String? = null,
    )

    fun init(context: Context) {
        appContext = context.applicationContext
        if (BuildConfig.DEBUG) {
            scope.launch { loadFromFile() }
        }
    }

    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun w(tag: String, msg: String, t: Throwable? = null) = log("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log("E", tag, msg, t)

    private fun log(level: String, tag: String, msg: String, t: Throwable? = null) {
        when (level) {
            "D" -> Log.d(tag, msg)
            "W" -> if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
            "E" -> if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        }
        if (!BuildConfig.DEBUG) return
        val entry = LogEntry(System.currentTimeMillis(), level, tag, msg, t?.stackTraceToString())
        scope.launch {
            logEntries.addLast(entry)
            if (logEntries.size > MAX_ENTRIES) logEntries.removeFirst()
            _entriesFlow.value = logEntries.reversed()
            saveToFile()
        }
    }

    fun clearEntries() {
        if (!BuildConfig.DEBUG) return
        scope.launch {
            logEntries.clear()
            _entriesFlow.value = emptyList()
            saveToFile()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
        keystore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        // Layout: [iv (12 bytes)][ciphertext + GCM tag]
        return iv + ciphertext
    }

    private fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_SIZE) { "Encrypted data too short to contain IV" }
        val iv = data.copyOfRange(0, IV_SIZE)
        val ciphertext = data.copyOfRange(IV_SIZE, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun loadFromFile() {
        try {
            val file = File(appContext.filesDir, FILE_NAME)
            if (!file.exists()) return
            val decrypted = decrypt(file.readBytes())
            val loaded = Json.decodeFromString<List<LogEntry>>(String(decrypted, Charsets.UTF_8))
            logEntries.clear()
            logEntries.addAll(loaded.takeLast(MAX_ENTRIES))
            _entriesFlow.value = logEntries.reversed()
        } catch (e: Exception) {
            Log.w("AppLogger", "Failed to load log entries from file", e)
        }
    }

    private fun saveToFile() {
        try {
            val content = Json.encodeToString(logEntries.toList()).toByteArray(Charsets.UTF_8)
            File(appContext.filesDir, FILE_NAME).writeBytes(encrypt(content))
        } catch (e: Exception) {
            Log.w("AppLogger", "Failed to save log entries to file", e)
        }
    }
}
