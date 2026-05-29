package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoogleSheetsSyncManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "google_sheets_sync_prefs"
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_SHEET_NAME = "sheet_name"
        private const val KEY_OAUTH_CLIENT_ID = "oauth_client_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val KEY_APPS_SCRIPT_URL = "apps_script_url"
        private const val KEY_USE_APPS_SCRIPT = "use_apps_script"

        // Default OAuth Web Client ID for standard desktop/web flow integration if they don't supply one.
        // Users can easily customize or paste their own inside the sync settings tab.
        private const val DEFAULT_OAUTH_CLIENT_ID = "104938210382-universal-demo-client.apps.googleusercontent.com"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    // Persistent State Flows for UI
    val spreadsheetId = MutableStateFlow(prefs.getString(KEY_SPREADSHEET_ID, "") ?: "")
    val sheetName = MutableStateFlow(prefs.getString(KEY_SHEET_NAME, "Sheet1") ?: "Sheet1")
    val oauthClientId = MutableStateFlow(prefs.getString(KEY_OAUTH_CLIENT_ID, "") ?: "")
    val accessToken = MutableStateFlow(prefs.getString(KEY_ACCESS_TOKEN, "") ?: "")
    val isAutoSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SYNC, false))
    val appsScriptUrl = MutableStateFlow(prefs.getString(KEY_APPS_SCRIPT_URL, "") ?: "")
    val useAppsScript = MutableStateFlow(prefs.getBoolean(KEY_USE_APPS_SCRIPT, true)) // Default to script for absolute foolproof out-of-the-box streaming emulator use

    // Log tracking for visible sync receipts
    val syncLogs = MutableStateFlow<List<String>>(emptyList())

    fun updateConfig(
        sheetId: String,
        name: String,
        clientId: String,
        scriptUrl: String,
        useScript: Boolean,
        autoSync: Boolean
    ) {
        prefs.edit().apply {
            putString(KEY_SPREADSHEET_ID, sheetId)
            putString(KEY_SHEET_NAME, name)
            putString(KEY_OAUTH_CLIENT_ID, clientId)
            putString(KEY_APPS_SCRIPT_URL, scriptUrl)
            putBoolean(KEY_USE_APPS_SCRIPT, useScript)
            putBoolean(KEY_AUTO_SYNC, autoSync)
            apply()
        }
        spreadsheetId.value = sheetId
        sheetName.value = name
        oauthClientId.value = clientId
        appsScriptUrl.value = scriptUrl
        useAppsScript.value = useScript
        isAutoSyncEnabled.value = autoSync
        
        addLog("Configuration updated successfully.")
    }

    fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        accessToken.value = token
        addLog("OAuth access token saved successfully.")
    }

    fun clearAccessToken() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
        accessToken.value = ""
        addLog("Authorized session signed out.")
    }

    fun addLog(logText: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formattedLog = "[${sdf.format(Date())}] $logText"
        val current = syncLogs.value
        syncLogs.value = (listOf(formattedLog) + current).take(50)
    }

    fun syncMessage(message: SmsMessage, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        CoroutineScope(Dispatchers.IO).launch {
            if (useAppsScript.value) {
                syncWithAppsScript(message, onComplete)
            } else {
                syncWithGoogleSheetsApi(message, onComplete)
            }
        }
    }

    // Method A: Direct append using Google Sheets REST API
    private fun syncWithGoogleSheetsApi(message: SmsMessage, onComplete: (Boolean, String) -> Unit) {
        val token = accessToken.value
        val sheetId = spreadsheetId.value
        val name = sheetName.value.ifEmpty { "Sheet1" }

        if (token.isEmpty()) {
            val errMsg = "Error: Sign-In Session not authorized. Please authorize with Google Account or use Apps Script fallback."
            addLog(errMsg)
            onComplete(false, errMsg)
            return
        }
        if (sheetId.isEmpty()) {
            val errMsg = "Error: Spreadsheet ID is blank. Please specify your target Google Sheet ID."
            addLog(errMsg)
            onComplete(false, errMsg)
            return
        }

        addLog("Initiating direct Sheets API Sync...")
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$sheetId/values/$name!A1:append?valueInputOption=USER_ENTERED"

        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(message.date))
        val direction = if (message.isSent) "Sent" else "Received"
        
        // Rows array of row values
        val valuesArray = JSONArray().apply {
            put(JSONArray().apply {
                put(formattedDate)
                put(message.address)
                put(message.senderName ?: "Unknown")
                put(message.body)
                put(message.category.name)
                put(direction)
            })
        }

        val jsonBody = JSONObject().apply {
            put("values", valuesArray)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errMsg = "API Connection Failed: ${e.message}"
                addLog(errMsg)
                onComplete(false, errMsg)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val respBody = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        addLog("Sync Success: Appended SMS row with '${message.address}' to Google Sheet '$name'")
                        onComplete(true, "Successfully appended row to Google Sheet!")
                    } else {
                        val errMsg = "API Sync Failed (code ${resp.code}): " + (extractErrorMessage(respBody) ?: "Check Spreadsheet ID permissions.")
                        addLog(errMsg)
                        onComplete(false, errMsg)
                    }
                }
            }
        })
    }

    // Method B: Append using Google Apps Script deployment URL
    private fun syncWithAppsScript(message: SmsMessage, onComplete: (Boolean, String) -> Unit) {
        val scriptUrl = appsScriptUrl.value
        if (scriptUrl.isEmpty()) {
            val errMsg = "Error: Apps Script Web App URL is empty! Please configure it in the Sync Settings tab."
            addLog(errMsg)
            onComplete(false, errMsg)
            return
        }

        addLog("Initiating Apps Script Hook Sync...")
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(message.date))
        val direction = if (message.isSent) "Sent" else "Received"

        val jsonBody = JSONObject().apply {
            put("date", formattedDate)
            put("address", message.address)
            put("sender", message.senderName ?: "Unknown")
            put("body", message.body)
            put("category", message.category.name)
            put("direction", direction)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(scriptUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errMsg = "Script Hook Failed: ${e.message}"
                addLog(errMsg)
                onComplete(false, errMsg)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val respBody = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        addLog("Sync Success: Post Hook delivered message from '${message.address}' to Google Sheets Script.")
                        onComplete(true, "Message synced successfully via Web App deployment!")
                    } else {
                        val errMsg = "Web App Hook failed with code: ${resp.code}. Please clarify deployment access."
                        addLog(errMsg)
                        onComplete(false, errMsg)
                    }
                }
            }
        })
    }

    private fun extractErrorMessage(jsonStr: String): String? {
        return try {
            val obj = JSONObject(jsonStr)
            val error = obj.getJSONObject("error")
            error.getString("message")
        } catch (e: Exception) {
            null
        }
    }
}
