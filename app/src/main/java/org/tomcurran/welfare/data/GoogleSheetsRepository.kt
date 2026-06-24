package org.tomcurran.welfare.data

import android.accounts.Account
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = NetHttpTransport()

    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val email = dataStore.data.first()[KEY_GOOGLE_ACCOUNT_EMAIL] ?: return@withContext null
        try {
            val account = Account(email, "com.google")
            val scope = "oauth2:${SheetsScopes.SPREADSHEETS} $SCOPE_DRIVE_METADATA_READONLY"
            GoogleAuthUtil.getToken(context, account, scope)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get access token", e)
            null
        }
    }

    private fun buildSheetsService(accessToken: String): Sheets {
        return Sheets.Builder(
            httpTransport,
            jsonFactory,
        ) { request -> request.headers.authorization = "Bearer $accessToken" }
            .setApplicationName("Welfare")
            .build()
    }

    private fun buildDriveService(accessToken: String): Drive {
        return Drive.Builder(
            httpTransport,
            jsonFactory,
        ) { request -> request.headers.authorization = "Bearer $accessToken" }
            .setApplicationName("Welfare")
            .build()
    }

    fun getDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    suspend fun resolveSpreadsheetByName(displayName: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val accessToken = getAccessToken() ?: return@withContext null
            try {
                val drive = buildDriveService(accessToken)
                val escapedName = displayName.replace("'", "\\'")
                val result = drive.files().list()
                    .setQ("mimeType='application/vnd.google-apps.spreadsheet' and name='$escapedName'")
                    .setFields("files(id, name)")
                    .setOrderBy("modifiedTime desc")
                    .setPageSize(1)
                    .execute()
                val file = result.files?.firstOrNull() ?: return@withContext null
                file.id to file.name
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to resolve spreadsheet by name: $displayName", e)
                null
            }
        }

    private suspend fun getFirstSheetName(sheets: Sheets, spreadsheetId: String): String =
        withContext(Dispatchers.IO) {
            val spreadsheet = sheets.spreadsheets().get(spreadsheetId)
                .setFields("sheets.properties.title")
                .execute()
            spreadsheet.getSheets()?.firstOrNull()?.getProperties()?.getTitle() ?: "Sheet1"
        }

    suspend fun syncWeightsToSheet(entries: List<WeightEntity>) = withContext(Dispatchers.IO) {
        val spreadsheetId = dataStore.data.first()[KEY_SPREADSHEET_ID] ?: return@withContext
        val accessToken = getAccessToken() ?: return@withContext
        try {
            val sheets = buildSheetsService(accessToken)
            val zoneId = ZoneId.systemDefault()
            val sheetName = getFirstSheetName(sheets, spreadsheetId)
            val range = sheetName

            // Deduplicate entries: keep unique weights per day
            val deduped = entries
                .sortedBy { it.time }
                .groupBy { Instant.ofEpochMilli(it.time).atZone(zoneId).toLocalDate().toString() }
                .flatMap { (dateStr, dayEntries) ->
                    dayEntries.distinctBy { it.weight }.map { dateStr to it }
                }

            fun parseSheetWeight(value: Any?): Double? {
                if (value is Number) return value.toDouble()
                val str = value?.toString() ?: return null
                val cleanStr = str.replace(Regex("[^0-9.,]"), "").replace(',', '.')
                return cleanStr.toDoubleOrNull()
            }

            fun normalizeSheetDate(value: Any?): String? {
                val str = value?.toString() ?: return null
                return try {
                    // Try parsing as ISO date (which is what we write)
                    LocalDate.parse(str).toString()
                } catch (_: DateTimeParseException) {
                    // If parsing fails, it might be a different format or a serial number
                    // For now, we return as-is but could be expanded to handle more formats
                    str
                }
            }

            // Read existing rows from the sheet
            val existing = sheets.spreadsheets().values()
                .get(spreadsheetId, range)
                .setValueRenderOption("UNFORMATTED_VALUE")
                .execute()
                .getValues().orEmpty()

            // Collect existing date-weight pairs (skip header row)
            val existingKeys = existing.drop(1)
                .filter { it.size >= 2 }
                .map { "${normalizeSheetDate(it[0])}|${"%.2f".format(Locale.US, parseSheetWeight(it[1]) ?: 0.0)}" }
                .toSet()

            // Build new rows that aren't already in the sheet
            val newRows = deduped
                .sortedBy { it.first }
                .filter { (dateStr, entry) -> "${dateStr}|${"%.2f".format(Locale.US, entry.weight)}" !in existingKeys }
                .map { (dateStr, entry) -> listOf<Any>(dateStr, entry.weight) }

            if (newRows.isEmpty()) {
                AppLogger.d(TAG, "No new weight entries to sync")
                return@withContext
            }

            // Ensure header exists
            if (existing.isEmpty()) {
                val header = ValueRange().setValues(listOf(listOf<Any>("Date", "Weight (kg)")))
                sheets.spreadsheets().values()
                    .update(spreadsheetId, "$range!A1", header)
                    .setValueInputOption("USER_ENTERED")
                    .execute()
            }

            // Append new rows
            val appendRange = ValueRange().setValues(newRows)
            sheets.spreadsheets().values()
                .append(spreadsheetId, range, appendRange)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute()

            AppLogger.d(TAG, "Appended ${newRows.size} new weight entries to Google Sheets")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "Failed to sync weights to Google Sheets", e)
            throw e
        }
    }

    val selectedSpreadsheetName: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SPREADSHEET_NAME]
    }

    suspend fun selectSpreadsheet(id: String, name: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SPREADSHEET_ID] = id
            prefs[KEY_SPREADSHEET_NAME] = name
        }
    }

    suspend fun clearSpreadsheet() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SPREADSHEET_ID)
            prefs.remove(KEY_SPREADSHEET_NAME)
        }
    }

    suspend fun setAccountEmail(email: String) {
        dataStore.edit { prefs ->
            prefs[KEY_GOOGLE_ACCOUNT_EMAIL] = email
        }
    }

    suspend fun clearAccountEmail() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_GOOGLE_ACCOUNT_EMAIL)
        }
    }

    companion object {
        private val TAG: String = GoogleSheetsRepository::class.java.simpleName
        private val KEY_SPREADSHEET_ID = stringPreferencesKey("google_sheets_spreadsheet_id")
        private val KEY_SPREADSHEET_NAME = stringPreferencesKey("google_sheets_spreadsheet_name")
        private val KEY_GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        const val SCOPE_DRIVE_METADATA_READONLY = "https://www.googleapis.com/auth/drive.metadata.readonly"
        const val MIME_TYPE_GOOGLE_SPREADSHEET = "application/vnd.google-apps.spreadsheet"
    }
}
