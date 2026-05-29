package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    private val viewModel: SmsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.applicationContext = this.applicationContext
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainAppScreen(viewModel: SmsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Set the application context on the ViewModel as soon as we compose
    LaunchedEffect(context) {
        viewModel.applicationContext = context.applicationContext
    }

    // Permission state observer using modern accompanist APIs
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
    )

    // Sync variables
    val isSandbox by viewModel.isMockSandboxMode.collectAsStateWithLifecycle()
    val activeAddress by viewModel.activeConversationAddress.collectAsStateWithLifecycle()
    val alertBanner by viewModel.incomingAlertBanner.collectAsStateWithLifecycle()

    // Dialog state for starting a new message
    var showNewMessageDialog by remember { mutableStateOf(false) }
    var showSheetsSyncDialog by remember { mutableStateOf(false) }

    // On granting permissions, auto load from Android content resolver databases if sandbox is off
    LaunchedEffect(permissionsState.allPermissionsGranted, isSandbox) {
        if (permissionsState.allPermissionsGranted && !isSandbox) {
            viewModel.loadFromSystem(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (activeAddress != null) {
                    // Chat Details screen
                    val address = activeAddress!!
                    ChatConversationScreen(
                        address = address,
                        viewModel = viewModel,
                        onBack = { viewModel.activeConversationAddress.value = null }
                    )
                } else {
                    // Message Thread List Dashboard Screen
                    InboxDashboardScreen(
                        viewModel = viewModel,
                        onThreadClick = { address ->
                            viewModel.markThreadAsRead(address)
                            viewModel.activeConversationAddress.value = address
                        },
                        onNewMessageClick = { showNewMessageDialog = true },
                        onSyncSettingsClick = { showSheetsSyncDialog = true },
                        permissionsGranted = permissionsState.allPermissionsGranted,
                        onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                    )
                }
            }
        }

        // Render Google Sheets Settings Dialog
        if (showSheetsSyncDialog) {
            GoogleSheetsSyncSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSheetsSyncDialog = false }
            )
        }

        // Animated overlay banner for receiving messages in real-time
        AnimatedVisibility(
            visible = alertBanner != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(stiffness = 300f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(stiffness = 300f)
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp)
                .zIndex(100f)
        ) {
            alertBanner?.let { message ->
                IncomingSmsNotificationBanner(
                    message = message,
                    onDismiss = { viewModel.dismissAlert() },
                    onReply = {
                        viewModel.dismissAlert()
                        viewModel.markThreadAsRead(message.address)
                        viewModel.activeConversationAddress.value = message.address
                    }
                )
            }
        }

        // New Message Creation Dialog UI
        if (showNewMessageDialog) {
            NewMessageDialog(
                viewModel = viewModel,
                onDismiss = { showNewMessageDialog = false },
                onChatStarted = { address ->
                    showNewMessageDialog = false
                    viewModel.activeConversationAddress.value = address
                }
            )
        }
    }
}

@Composable
fun InboxDashboardScreen(
    viewModel: SmsViewModel,
    onThreadClick: (String) -> Unit,
    onNewMessageClick: () -> Unit,
    onSyncSettingsClick: () -> Unit,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val isSandbox by viewModel.isMockSandboxMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("inbox_dashboard_col")
    ) {
        // App top identity block (spacious padding and bold elements)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SMS Messages",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isSandbox) "Simulated Sandbox Mode" else "Real Network Device",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (isSandbox) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Google Sheets Sync Settings Button
                    IconButton(
                        onClick = onSyncSettingsClick,
                        modifier = Modifier
                            .testTag("google_sheets_sync_button")
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Sync to Google Sheets",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sandbox switch pill element
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewModel.toggleSandboxMode(context) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("sandbox_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (isSandbox) Icons.Default.Dns else Icons.Default.NetworkCheck,
                            contentDescription = "Sandbox Toggle",
                            tint = if (isSandbox) MaterialTheme.colorScheme.primary else Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSandbox) "Sandbox ON" else "Real SMS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Curved custom search widget nested under the titles
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search messages, phone numbers...") },
                leadingIcon = { Icon(Icons.Outlined.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, "Clear Search")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_sms_input"),
                singleLine = true
            )
        }

        // Horizontal visual filtering chips (using standard Material 3 tokens)
        ScrollableTabRow(
            selectedTabIndex = selectedCategory.ordinal,
            edgePadding = 16.dp,
            divider = {},
            indicator = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            MessageCategory.values().forEach { category ->
                val isSelected = category == selectedCategory
                val chipBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                val chipText = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                
                // Construct nice labels with matching symbols
                val (label, icon) = when (category) {
                    MessageCategory.ALL -> "All Chats" to Icons.Default.AllInbox
                    MessageCategory.PERSONAL -> "Personal" to Icons.Default.PersonOutline
                    MessageCategory.TRANSACTIONS -> "Transactions" to Icons.Default.Savings
                    MessageCategory.SPAM -> "Spam/Unknown" to Icons.Outlined.Security
                    MessageCategory.GENERAL -> "Business/Info" to Icons.Outlined.Mail
                }

                Box(
                    modifier = Modifier
                        .padding(end = 8.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .clickable { viewModel.selectedCategory.value = category }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("${category.name.lowercase()}_filter_chip"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = label, tint = chipText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = chipText
                        )
                    }
                }
            }
        }

        // Visual warning banner if Real mode is ON but permissions are absent
        if (!isSandbox && !permissionsGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = "Security Alert",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SMS Permissions Required",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To access device texts, send SMS alerts, and classify notifications, you need to grant background telephony permissions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Grant System Access")
                    }
                }
            }
        }

        // Messages list history block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (threads.isEmpty()) {
                EmptyStateLayout(
                    isSearch = searchQuery.isNotEmpty(),
                    hasCategory = selectedCategory != MessageCategory.ALL
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(threads, key = { it.id }) { thread ->
                        ConversationThreadRow(
                            thread = thread,
                            onClick = { onThreadClick(thread.address) },
                            onDelete = { viewModel.deleteThread(thread.address) },
                            onArchive = { viewModel.toggleArchiveThread(thread.address) }
                        )
                    }
                }
            }

            // New Conversation FAB Trigger
            FloatingActionButton(
                onClick = onNewMessageClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("fab_new_message")
            ) {
                Icon(Icons.Default.Chat, "Start New Conversation")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationThreadRow(
    thread: SmsThread,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit
) {
    var showDropdownMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDropdownMenu = true }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("thread_item_${thread.address}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored Avatar
            ContactAvatar(
                name = thread.contactName ?: thread.address,
                address = thread.address
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thread.contactName ?: thread.address,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (!thread.read) FontWeight.ExtraBold else FontWeight.Bold
                        ),
                        color = if (!thread.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = formatTimestamp(thread.date),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (!thread.read) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (!thread.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thread.snippet,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (!thread.read) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (!thread.read) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (!thread.read) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                        )
                    }
                }
            }
        }

        // Long press dropdown customization option menu
        DropdownMenu(
            expanded = showDropdownMenu,
            onDismissRequest = { showDropdownMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Delete Thread") },
                onClick = {
                    showDropdownMenu = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Outlined.Delete, "Delete") }
            )
            val archiveLabel = if (thread.isArchived) "Remove Archive" else "Archive Chat"
            DropdownMenuItem(
                text = { Text(archiveLabel) },
                onClick = {
                    showDropdownMenu = false
                    onArchive()
                },
                leadingIcon = { Icon(Icons.Default.Archive, "Archive") }
            )
        }
    }
}

@Composable
fun ChatConversationScreen(
    address: String,
    viewModel: SmsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSandbox by viewModel.isMockSandboxMode.collectAsStateWithLifecycle()

    // Find all messages in this thread
    val threadMessages = remember(messages, address) {
        messages.filter { it.address == address }.sortedBy { it.date }
    }

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Map thread address to clean displayName helper
    val displayName = remember(messages, address) {
        val lastMsg = messages.firstOrNull { it.address == address }
        lastMsg?.senderName ?: address
    }

    // Auto scroll list to bottom on chat opening or new segment arrived
    LaunchedEffect(threadMessages.size) {
        if (threadMessages.isNotEmpty()) {
            listState.animateScrollToItem(threadMessages.size - 1)
        }
    }

    // Capture standard device Back press to safely pop the chat detail screen
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("chat_conversation_screen")
    ) {
        // Conversation Top Header Panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 6.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Inbox")
                }

                Spacer(modifier = Modifier.width(4.dp))

                ContactAvatar(name = displayName, address = address, size = 38.dp)

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Header Call/Security visual badges
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    IconButton(onClick = {
                        Toast.makeText(context, "Voice dialing is simulated on this platform.", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Phone, "Dial Contact", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(onClick = {
                        val classification = threadMessages.firstOrNull()?.category ?: MessageCategory.GENERAL
                        Toast.makeText(context, "Conversation classified as: $classification", Toast.LENGTH_LONG).show()
                    }) {
                        Icon(Icons.Outlined.Info, "Information", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Active Bubble Stream View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            if (threadMessages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Empty chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Say Hello!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Draft your first message below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(threadMessages, key = { it.id }) { msg ->
                        BubbleItem(
                            message = msg,
                            onLongPress = { viewModel.deleteMessage(msg.id) }
                        )
                    }
                }
            }
        }

        // Text Composer Bar Input Tool (Elevated with Edge-to-Edge Navigation Padding protection)
        Surface(
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    Toast.makeText(context, "MMS Attachments are mock-simulated", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.AddCircleOutline, "Attach file", tint = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Text Message") },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (messageText.isNotEmpty()) {
                                viewModel.sendMessage(context, address, messageText) { success ->
                                    if (success) messageText = ""
                                }
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input_field"),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotEmpty()) {
                            viewModel.sendMessage(context, address, messageText) { success ->
                                if (success) messageText = ""
                            }
                        }
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send text",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BubbleItem(
    message: SmsMessage,
    onLongPress: () -> Unit
) {
    val isSent = message.isSent
    val bubbleShape = if (isSent) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    val containerColor = if (isSent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val textColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    var showDeleteOption by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(containerColor)
                .combinedClickable(
                    onClick = { /* Tap action could show details */ },
                    onLongClick = { showDeleteOption = true }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                
                Spacer(modifier = Modifier.height(3.dp))
                
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    val formattedTime = remember(message.date) {
                        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                        sdf.format(Date(message.date))
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    
                    if (isSent) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Delivered",
                            tint = textColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // Floating delete alert dropdown nested on the single bubble row
        DropdownMenu(
            expanded = showDeleteOption,
            onDismissRequest = { showDeleteOption = false }
        ) {
            DropdownMenuItem(
                text = { Text("Delete Message") },
                onClick = {
                    showDeleteOption = false
                    onLongPress()
                },
                leadingIcon = { Icon(Icons.Outlined.Delete, "Delete") }
            )
        }
    }
}

@Composable
fun ContactAvatar(name: String, address: String, size: Dp = 44.dp) {
    val colors = listOf(
        Color(0xFF0D9488), Color(0xFF0284C7), Color(0xFF2563EB),
        Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFEA580C),
        Color(0xFF16A34A), Color(0xFFD97706)
    )
    val colorIndex = remember(address) {
        Math.abs(address.hashCode()) % colors.size
    }
    val avatarColor = colors[colorIndex]
    
    // Extract first valid character, fallback to '?'
    val initialLabel = remember(name, address) {
        val filteredName = name.trim().filter { it.isLetter() }
        if (filteredName.isNotEmpty()) {
            filteredName.first().uppercase()
        } else {
            val digits = address.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                digits.first().toString()
            } else {
                "?"
            }
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(avatarColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

@Composable
fun EmptyStateLayout(isSearch: Boolean, hasCategory: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isSearch) Icons.Default.SearchOff else Icons.Default.ChatBubbleOutline,
            contentDescription = "No threads available",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearch) "No Search Results" else "Your Inbox is Empty",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                isSearch -> "Try typing a different name, standard phrase, or exact number digits."
                hasCategory -> "No messages fit this specific local categorized filter criteria right now."
                else -> "Draft a conversation by tapping the Chat bubble icon down below."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun IncomingSmsNotificationBanner(
    message: SmsMessage,
    onDismiss: () -> Unit,
    onReply: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onReply),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(
                name = message.senderName ?: message.address,
                address = message.address,
                size = 36.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message.senderName ?: message.address,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close overlay alert",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun NewMessageDialog(
    viewModel: SmsViewModel,
    onDismiss: () -> Unit,
    onChatStarted: (String) -> Unit
) {
    var queryAddress by remember { mutableStateOf("") }
    var initialMessageText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "New Conversation",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = queryAddress,
                    onValueChange = { queryAddress = it },
                    label = { Text("To (Phone number or Name)") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = initialMessageText,
                    onValueChange = { initialMessageText = it },
                    label = { Text("Initial message (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanAddr = queryAddress.trim()
                            if (cleanAddr.isNotEmpty()) {
                                if (initialMessageText.trim().isNotEmpty()) {
                                    // Send immediate initial message
                                    viewModel.sendMessage(context, cleanAddr, initialMessageText) { success ->
                                        if (success) {
                                            onChatStarted(cleanAddr)
                                        }
                                    }
                                } else {
                                    // Open blank thread log
                                    onChatStarted(cleanAddr)
                                }
                            } else {
                                Toast.makeText(context, "Please input a valid phone address", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Start Chat")
                    }
                }
            }
        }
    }
}

// Utility to nicely represent readable timestamps (e.g. 5:10 PM, Yesterday, May 27)
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()

    val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val threadDay = dayFormat.format(date)
    val today = dayFormat.format(now)

    return when {
        threadDay == today -> {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            timeFormat.format(date)
        }
        (today.toInt() - threadDay.toInt() == 1) -> {
            "Yesterday"
        }
        else -> {
            val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            monthDayFormat.format(date)
        }
    }
}
