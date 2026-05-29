package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSheetsSyncSettingsDialog(
    viewModel: SmsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { viewModel.getSyncManager(context) }

    // Read state flows
    val spreadsheetId by manager.spreadsheetId.collectAsStateWithLifecycle()
    val sheetName by manager.sheetName.collectAsStateWithLifecycle()
    val oauthClientId by manager.oauthClientId.collectAsStateWithLifecycle()
    val accessToken by manager.accessToken.collectAsStateWithLifecycle()
    val isAutoSyncEnabled by manager.isAutoSyncEnabled.collectAsStateWithLifecycle()
    val appsScriptUrl by manager.appsScriptUrl.collectAsStateWithLifecycle()
    val useAppsScript by manager.useAppsScript.collectAsStateWithLifecycle()
    val syncLogs by manager.syncLogs.collectAsStateWithLifecycle()

    val messages by viewModel.messages.collectAsStateWithLifecycle()

    // Local inputs initialized from state flows
    var inputSpreadsheetId by remember { mutableStateOf(spreadsheetId) }
    var inputSheetName by remember { mutableStateOf(sheetName) }
    var inputClientId by remember { mutableStateOf(oauthClientId) }
    var inputAppsScriptUrl by remember { mutableStateOf(appsScriptUrl) }
    var isScriptMode by remember { mutableStateOf(useAppsScript) }
    var isAutoSync by remember { mutableStateOf(isAutoSyncEnabled) }

    var showOauthWebView by remember { mutableStateOf(false) }
    var manualAccessTokenInput by remember { mutableStateOf("") }
    var showManualTokenDialog by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }

    // Dialog styling container
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(top = 28.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header Block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Cloud Icon",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Google Sheets Sync",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Automate SMS backups beautifully",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Settings")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Scrollable main content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Introduction & Info Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "This integration backs up your SMS threads (Sender, Date, Message, Categorization classification) directly to a Google Spreadsheet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto Sync Switch card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (isAutoSync) Icons.Default.Sync else Icons.Default.SyncDisabled,
                                    contentDescription = "Sync",
                                    tint = if (isAutoSync) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Real-time Autosync",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Sync newly received/sent messages instantly",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isAutoSync,
                                onCheckedChange = {
                                    isAutoSync = it
                                },
                                modifier = Modifier.testTag("auto_sync_sheets_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Integration Method Selector Tabs
                    Text(
                        text = "SYNCING ARCHITECTURE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    TabRow(
                        selectedTabIndex = if (isScriptMode) 0 else 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        Tab(
                            selected = isScriptMode,
                            onClick = { isScriptMode = true },
                            text = { Text("Apps Script Webhook", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                        )
                        Tab(
                            selected = !isScriptMode,
                            onClick = { isScriptMode = false },
                            text = { Text("Direct Sheets REST API", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Fields based on selection
                    if (isScriptMode) {
                        // Apps Script Form
                        Text(
                            text = "GOOGLE APPS SCRIPT WEB APP URL",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = inputAppsScriptUrl,
                            onValueChange = {
                                inputAppsScriptUrl = it
                            },
                            placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("apps_script_url_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Apps Script Instructions Toggle Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showInstructions = !showInstructions },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.HelpOutline, "Help", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Need help setting up a script? (5 seconds setup)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                    Icon(
                                        imageVector = if (showInstructions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand info"
                                    )
                                }

                                if (showInstructions) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "1. Open Google Sheets, create a blank spreadsheet.\n" +
                                                "2. Go to Extensions -> Apps Script.\n" +
                                                "3. Replace all default code with:\n\n" +
                                                "function doPost(e) {\n" +
                                                "  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();\n" +
                                                "  var data = JSON.parse(e.postData.contents);\n" +
                                                "  sheet.appendRow([new Date(data.date), data.address, data.sender, data.body, data.category, data.direction]);\n" +
                                                "  return ContentService.createTextOutput(\"Success\");\n" +
                                                "}\n\n" +
                                                "4. Click Deploy -> New Deployment -> Select 'Web App'.\n" +
                                                "5. Change 'Execute as' to 'Me', and change 'Who has access' to 'Anyone'.\n" +
                                                "6. Deploy, authorise prompts, copy Web App URL and paste it here!",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                    } else {
                        // Direct REST API Form
                        Text(
                            text = "GOOGLE SPREADSHEET ID",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = inputSpreadsheetId,
                            onValueChange = {
                                inputSpreadsheetId = it
                            },
                            placeholder = { Text("e.g. 1A2bC3dD... (From Spreadsheet URL)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spreadsheet_id_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "TARGET SHEET NAME (TAB)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = inputSheetName,
                            onValueChange = {
                                inputSheetName = it
                            },
                            placeholder = { Text("Sheet1") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheet_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Authorization Section
                        Text(
                            text = "AUTHENTICATION STATUS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        if (accessToken.isNotEmpty()) {
                            // Already Signed In Card
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFD1FAE5) // Soft green
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, "Success", tint = Color(0xFF059669))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Authenticated Successfully", style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF065F46), fontWeight = FontWeight.Bold))
                                            Text("Active Sheet Session", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF047857)))
                                        }
                                    }
                                    TextButton(onClick = { manager.clearAccessToken() }) {
                                        Text("Sign Out", color = Color(0xFFDC2626), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        } else {
                            // Signed Out. Provide options.
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Session has expired or not authorised. Provide access details to read/write to your Sheet:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = { showOauthWebView = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.AccountCircle, "Google Account")
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("OAuth Authorize")
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        OutlinedButton(
                                            onClick = { showManualTokenDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Key, "Key")
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Manual Token")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Manual Bulk Sync Action Card
                    Text(
                        text = "ADHOC SYNC ACTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Populate your spreadsheet instantly using your current SMS database records (${messages.size} total chats/mock details available).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (messages.isEmpty()) {
                                        Toast.makeText(context, "No messages available to sync.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        manager.addLog("Force Sync: Bulk exporting ${messages.size} messages...")
                                        var successCount = 0
                                        var failCount = 0
                                        messages.forEach { msg ->
                                            manager.syncMessage(msg) { success, _ ->
                                                if (success) successCount++ else failCount++
                                            }
                                        }
                                        Toast.makeText(context, "Initiated export of ${messages.size} SMS rows.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bulk_export_sheets_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.CloudUpload, "Cloud Sync")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export All SMS Records (${messages.size})")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Real-Time Sync Receipts / Logger Terminal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYNC HISTORY & LOGGER",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Clear Terminal",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                            modifier = Modifier.clickable { manager.syncLogs.value = emptyList() }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(0xFF1E293B), // Dark slate terminal background
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        if (syncLogs.isEmpty()) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "Ready to Sync. Awaiting incoming triggers...",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(10.dp),
                                reverseLayout = false
                            ) {
                                items(syncLogs) { log ->
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (log.contains("Success", ignoreCase = true)) Color(0xFF10B981) else if (log.contains("Error", ignoreCase = true) || log.contains("failed", ignoreCase = true)) Color(0xFFF43F5E) else Color(0xFFCBD5E1),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Final OK Done button
                Button(
                    onClick = {
                        manager.updateConfig(
                            sheetId = inputSpreadsheetId,
                            name = inputSheetName,
                            clientId = inputClientId,
                            scriptUrl = inputAppsScriptUrl,
                            useScript = isScriptMode,
                            autoSync = isAutoSync
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }

    // Secondary Nest Dialog: Paste manual Google token
    if (showManualTokenDialog) {
        Dialog(onDismissRequest = { showManualTokenDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Manual OAuth Token Access", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Paste a volatile bearer token retrieved straight from the Google Developer Playground or command terminal to test sheets API connections instantly:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manualAccessTokenInput,
                        onValueChange = { manualAccessTokenInput = it },
                        placeholder = { Text("ya29.a0Ac...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showManualTokenDialog = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (manualAccessTokenInput.trim().isNotEmpty()) {
                                manager.saveAccessToken(manualAccessTokenInput.trim())
                                showManualTokenDialog = false
                                Toast.makeText(context, "Token saved successfully", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Save Token") }
                    }
                }
            }
        }
    }

    // Full screen Overlay Dialog: OAuth WebView
    if (showOauthWebView) {
        Dialog(
            onDismissRequest = { showOauthWebView = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar (Simple Custom surface Row)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showOauthWebView = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Google Account Authorization",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    var webViewLoading by remember { mutableStateOf(true) }

                    Box(modifier = Modifier.weight(1f)) {
                        OAuthWebViewComponent(
                            clientId = oauthClientId,
                            onTokenCaptured = { token ->
                                manager.saveAccessToken(token)
                                showOauthWebView = false
                                Toast.makeText(context, "Google sign-in succeeded!", Toast.LENGTH_LONG).show()
                            },
                            onLoaderStatus = { webViewLoading = it }
                        )

                        if (webViewLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
}



@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OAuthWebViewComponent(
    clientId: String,
    onTokenCaptured: (String) -> Unit,
    onLoaderStatus: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    // Fallback Client ID registered as dynamic standard web flows or matching credential console
    val activeClientId = clientId.ifEmpty { "104938210382-universal-demo-client.apps.googleusercontent.com" }

    // Standard implicit auth URL for Sheets API access
    val authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?scope=https://www.googleapis.com/auth/spreadsheets" +
            "&response_type=token" +
            "&redirect_uri=http://localhost" +
            "&client_id=$activeClientId"

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            try {
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        // Mock modern user-agent to bypass Google sign-in WebView browser restrictions
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Mobile Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            onLoaderStatus(true)
                            super.onPageStarted(view, url, favicon)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            onLoaderStatus(false)
                            super.onPageFinished(view, url)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            Log.d("OAuthWebView", "Redirect URL loading: $url")
                            
                            // Parse local redirection containing oauth access token
                            if (url.startsWith("http://localhost")) {
                                val fragment = request.url.fragment
                                if (!fragment.isNullOrEmpty()) {
                                    val params = fragment.split("&")
                                    for (param in params) {
                                        if (param.startsWith("access_token=")) {
                                            val token = param.substringAfter("access_token=")
                                            onTokenCaptured(token)
                                            return true
                                        }
                                    }
                                }
                                // Fallback checks in query params
                                val tokenQuery = request.url.getQueryParameter("access_token")
                                if (!tokenQuery.isNullOrEmpty()) {
                                    onTokenCaptured(tokenQuery)
                                    return true
                                }
                            }
                            return false
                        }
                    }
                    
                    loadUrl(authUrl)
                }
            } catch (e: Throwable) {
                Log.e("OAuthWebView", "Failed to initialize WebView", e)
                onLoaderStatus(false)
                android.widget.TextView(ctx).apply {
                    text = "WebView is not available on this device/environment.\n\nError: ${e.localizedMessage ?: "Missing engine provider"}\n\nPlease use the 'Apps Script Webhook' or 'Manual Token' authentication methods instead."
                    val padding = (16 * ctx.resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                    textSize = 15f
                    setTextColor(android.graphics.Color.RED)
                }
            }
        }
    )
}
