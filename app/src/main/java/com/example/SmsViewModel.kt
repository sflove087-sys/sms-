package com.example

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SmsViewModel : ViewModel() {

    // Google Sheets Sync Properties
    var applicationContext: Context? = null
    private var sheetsSyncManager: GoogleSheetsSyncManager? = null

    fun getSyncManager(context: Context): GoogleSheetsSyncManager {
        if (sheetsSyncManager == null) {
            sheetsSyncManager = GoogleSheetsSyncManager(context.applicationContext)
        }
        return sheetsSyncManager!!
    }

    // Toggle between real Android telephony integration and sandbox simulated mode (default to true for streaming emulators with no SIM)
    val isMockSandboxMode = MutableStateFlow(true)

    // Current category filter
    val selectedCategory = MutableStateFlow(MessageCategory.ALL)

    // Quick search query for finding specific text or senders
    val searchQuery = MutableStateFlow("")

    // ID of the actively open chat thread. If null, display thread list.
    val activeConversationAddress = MutableStateFlow<String?>(null)

    // Local state for archived threads
    private val archivedThreadIds = MutableStateFlow<Set<String>>(emptySet())

    // Local cached contact names resolved from address keys
    private val resolvedContacts = MutableStateFlow<Map<String, String>>(emptyMap())

    // SMS messages list (either synced from content resolver or managed locally in mock)
    private val realMessages = MutableStateFlow<List<SmsMessage>>(emptyList())
    private val mockMessages = MutableStateFlow<List<SmsMessage>>(emptyList())

    // Active in-app notification banner for real-time incoming messages
    val incomingAlertBanner = MutableStateFlow<SmsMessage?>(null)

    // Compose all active message items
    val messages: StateFlow<List<SmsMessage>> = combine(
        isMockSandboxMode, realMessages, mockMessages
    ) { sandbox, real, mock ->
        if (sandbox) mock else real
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Build the list of conversation threads derived directly from the loaded messages
    val threads: StateFlow<List<SmsThread>> = combine(
        messages, resolvedContacts, archivedThreadIds, selectedCategory, searchQuery
    ) { msgList, contacts, archives, category, query ->
        if (msgList.isEmpty()) return@combine emptyList()

        // Group messages by their sender/receiver address to build threads
        val grouped = msgList.groupBy { it.address }
        val threadList = grouped.map { (address, msgs) ->
            val latestMsg = msgs.maxByOrNull { it.date } ?: msgs.first()
            val contactName = contacts[address] ?: latestMsg.senderName
            val unreadCount = msgs.count { !it.read && !it.isSent }
            
            // Derive thread category based on the classification of the latest message
            val threadCategory = latestMsg.category

            SmsThread(
                id = address, // Use address as the unique identifier for simplicity
                address = address,
                contactName = contactName,
                snippet = latestMsg.body,
                date = latestMsg.date,
                read = unreadCount == 0,
                unreadCount = unreadCount,
                category = threadCategory,
                isArchived = archives.contains(address)
            )
        }.sortedByDescending { it.date }

        // Apply filters
        threadList.filter { thread ->
            val matchesCategory = category == MessageCategory.ALL || thread.category == category
            val matchesSearch = query.isEmpty() || 
                    thread.address.contains(query, ignoreCase = true) || 
                    (thread.contactName?.contains(query, ignoreCase = true) == true) || 
                    thread.snippet.contains(query, ignoreCase = true)
            
            matchesCategory && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        seedMockMessages()
        
        // Listen to SMS events coming from the real-time Broadcast SmsReceiver
        viewModelScope.launch {
            SmsEventBus.incomingSms.collect { incomingMsg ->
                handleIncomingMessage(incomingMsg)
            }
        }
    }

    private fun seedMockMessages() {
        val now = System.currentTimeMillis()
        val mockList = listOf(
            // Personal Thread with Alice (Normal standard numeric address)
            SmsMessage("m1", "t1", "+1 (555) 234-5678", "Hey! Are we still on for dinner tonight at 7 PM?", now - 3600000 * 5, 1, true, "Alice Smith", MessageCategory.PERSONAL),
            SmsMessage("m2", "t1", "+1 (555) 234-5678", "Yeah! I was thinking that cozy Italian place downtown.", now - 3600000 * 4, 2, true, "Alice Smith", MessageCategory.PERSONAL),
            SmsMessage("m3", "t1", "+1 (555) 234-5678", "Awesome! See you there. Don't forget to bring that copy of the project notes.", now - 3600000 * 3, 1, false, "Alice Smith", MessageCategory.PERSONAL),
            
            // Transaction Thread with Chase Alert (Short code transactional)
            SmsMessage("m4", "t2", "242-73", "CHASE: A transaction of $42.50 at STARBUCKS was authorized on card x0481. Temp balance: $2,841.20.", now - 3600000 * 8, 1, true, "Chase Security", MessageCategory.TRANSACTIONS),
            SmsMessage("m5", "t2", "242-73", "CHASE: Your online card security security-code is 840291. It expires in 10 minutes. Do not share.", now - 600000, 1, false, "Chase Security", MessageCategory.TRANSACTIONS),
            
            // Transaction Thread with Google OTP
            SmsMessage("m6", "t3", "GOOGLE", "G-928410 is your Google verification code. Never share this with anyone.", now - 3600000 * 12, 1, true, "Google verification", MessageCategory.TRANSACTIONS),
            
            // Spam Thread with Lucky Win
            SmsMessage("m7", "t4", "+1 (800) 888-0199", "CONGRATS! Your phone number won a $1,000 Walmart Gift Card! Click here to claim your cash: http://walmart.draw.com/win91now", now - 3600000 * 1, 1, false, "+1 (800) 888-0199", MessageCategory.SPAM),
            SmsMessage("m8", "t4", "+1 (800) 888-0199", "URGENT: Claim spot expiring in 1 hour!", now - 200000, 1, false, "+1 (800) 888-0199", MessageCategory.SPAM),

            // Personal Thread with Bob
            SmsMessage("m9", "t5", "+1 (415) 301-4455", "Do you have the link to the Compose docs? I need to review proper padding conventions.", now - 3600000 * 24, 1, true, "Bob Johnson", MessageCategory.PERSONAL),
            SmsMessage("m10", "t5", "+1 (415) 301-4455", "Sure! Check out https://developer.android.com/compose", now - 3600000 * 23, 2, true, "Bob Johnson", MessageCategory.PERSONAL),
            SmsMessage("m11", "t5", "+1 (415) 301-4455", "Great, perfect! The layout looks spacious.", now - 3600000 * 22, 1, true, "Bob Johnson", MessageCategory.PERSONAL)
        )
        mockMessages.value = mockList
    }

    // Handles incoming real-time SMS messages
    fun handleIncomingMessage(msg: SmsMessage) {
        viewModelScope.launch {
            val updatedMessage = msg.copy(
                senderName = resolvedContacts.value[msg.address] ?: msg.senderName
            )

            if (isMockSandboxMode.value) {
                // Prepend/append to mock list
                mockMessages.value = mockMessages.value + updatedMessage
                
                // Show notification overlay
                incomingAlertBanner.value = updatedMessage
                delay(4000)
                incomingAlertBanner.value = null
            } else {
                // If in real mode and not pre-integrated, fetch database again
                realMessages.value = realMessages.value + updatedMessage
                incomingAlertBanner.value = updatedMessage
                delay(4000)
                incomingAlertBanner.value = null
            }

            // Trigger Google Sheets auto-sync
            applicationContext?.let { context ->
                val manager = getSyncManager(context)
                if (manager.isAutoSyncEnabled.value) {
                    manager.syncMessage(updatedMessage)
                }
            }
        }
    }

    // Clears active notification banner
    fun dismissAlert() {
        incomingAlertBanner.value = null
    }

    // Refresh core databases (contact & messages) from ContentResolver (Real Mode)
    fun loadFromSystem(context: Context) {
        if (isMockSandboxMode.value) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Load Contacts
                val contactMap = mutableMapOf<String, String>()
                val contactUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val contactCursor = context.contentResolver.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ),
                    null, null, null
                )
                contactCursor?.use { cursor ->
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (numIdx != -1 && nameIdx != -1) {
                        while (cursor.moveToNext()) {
                            val number = cursor.getString(numIdx)
                            val name = cursor.getString(nameIdx)
                            if (!number.isNullOrEmpty() && !name.isNullOrEmpty()) {
                                // Clean up number for quick matching
                                val cleanNum = number.replace(Regex("[\\s()\\-]"), "")
                                contactMap[number] = name
                                contactMap[cleanNum] = name
                            }
                        }
                    }
                }
                resolvedContacts.value = contactMap

                // 2. Load Real messages
                val smsList = mutableListOf<SmsMessage>()
                val uri = Uri.parse("content://sms")
                val smsCursor = context.contentResolver.query(
                    uri,
                    arrayOf("_id", "thread_id", "address", "body", "date", "type", "read"),
                    null, null, "date DESC"
                )
                
                smsCursor?.use { cursor ->
                    val idCol = cursor.getColumnIndex("_id")
                    val threadCol = cursor.getColumnIndex("thread_id")
                    val addressCol = cursor.getColumnIndex("address")
                    val bodyCol = cursor.getColumnIndex("body")
                    val dateCol = cursor.getColumnIndex("date")
                    val typeCol = cursor.getColumnIndex("type")
                    val readCol = cursor.getColumnIndex("read")

                    if (idCol != -1 && threadCol != -1 && addressCol != -1 && bodyCol != -1 && dateCol != -1 && typeCol != -1 && readCol != -1) {
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(idCol) ?: ""
                            val threadId = cursor.getString(threadCol) ?: ""
                            val address = cursor.getString(addressCol) ?: "Unknown"
                            val body = cursor.getString(bodyCol) ?: ""
                            val date = cursor.getLong(dateCol)
                            val type = cursor.getInt(typeCol)
                            val readInt = cursor.getInt(readCol)
                            val read = readInt == 1

                            val category = SmsClassifier.classify(address, body)
                            val senderName = contactMap[address] ?: contactMap[address.replace(Regex("[\\s()\\-]"), "")]

                            smsList.add(
                                SmsMessage(
                                    id = id,
                                    threadId = threadId,
                                    address = address,
                                    body = body,
                                    date = date,
                                    type = type,
                                    read = read,
                                    senderName = senderName,
                                    category = category
                                )
                            )
                        }
                    }
                }
                realMessages.value = smsList
                Log.d("SmsViewModel", "Loaded ${smsList.size} real SMS messages.")
            } catch (e: Exception) {
                Log.e("SmsViewModel", "Failed to query system SMS database", e)
            }
        }
    }

    // Sends an SMS and records the sent message in state
    fun sendMessage(context: Context, address: String, body: String, onComplete: (Boolean) -> Unit) {
        if (body.trim().isEmpty()) {
            onComplete(false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val senderName = resolvedContacts.value[address]
            val category = SmsClassifier.classify(address, body)

            if (isMockSandboxMode.value) {
                // Add message to sandbox mocks
                val newMsg = SmsMessage(
                    id = "mock_sent_${System.currentTimeMillis()}",
                    threadId = "", 
                    address = address,
                    body = body,
                    date = now,
                    type = 2, // Sent
                    read = true,
                    senderName = senderName,
                    category = category
                )
                mockMessages.value = mockMessages.value + newMsg
                
                // Trigger Google Sheets auto-sync for sent message
                applicationContext?.let { ctx ->
                    val manager = getSyncManager(ctx)
                    if (manager.isAutoSyncEnabled.value) {
                        manager.syncMessage(newMsg)
                    }
                }

                // Trigger auto reply for realistic chatbot engagement in sandbox!
                simulateMockAutoReply(address, body)
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } else {
                // Real Telephony Integration
                try {
                    val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
                    if (smsManager == null) {
                        withContext(Dispatchers.Main) {
                            onComplete(false)
                        }
                        return@launch
                    }
                    smsManager.sendTextMessage(address, null, body, null, null)
                    
                    // Manually write message to content resolver to persist inside standard Android threads
                    val values = ContentValues().apply {
                        put("address", address)
                        put("body", body)
                        put("date", now)
                        put("type", 2) // Sent
                        put("read", 1) // Read
                    }
                    val uri = context.contentResolver.insert(Uri.parse("content://sms/sent"), values)
                    val idStr = uri?.lastPathSegment ?: "sent_${System.currentTimeMillis()}"

                    val newMsg = SmsMessage(
                        id = idStr,
                        threadId = "",
                        address = address,
                        body = body,
                        date = now,
                        type = 2,
                        read = true,
                        senderName = senderName,
                        category = category
                    )
                    realMessages.value = realMessages.value + newMsg

                    // Trigger Google Sheets auto-sync for sent message
                    applicationContext?.let { ctx ->
                        val manager = getSyncManager(ctx)
                        if (manager.isAutoSyncEnabled.value) {
                            manager.syncMessage(newMsg)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onComplete(true)
                    }
                } catch (e: Exception) {
                    Log.e("SmsViewModel", "Error sending SMS securely", e)
                    withContext(Dispatchers.Main) {
                        onComplete(false)
                    }
                }
            }
        }
    }

    private fun simulateMockAutoReply(address: String, body: String) {
        viewModelScope.launch {
            delay(2000) // Delay to mimic human connection speed
            val lowerMsg = body.lowercase(Locale.getDefault())
            
            // Choose reply based on address key and body keywords
            val replyText = when {
                address.contains("234-5678") || address.contains("Alice") -> {
                    when {
                        lowerMsg.contains("dinner") || lowerMsg.contains("food") -> "Oh, perfect! I'll make a reservation for 7:00 PM then. Can't wait!"
                        lowerMsg.contains("papers") || lowerMsg.contains("notes") -> "Got them! Safely stored in my briefcase."
                        else -> "Haha super! Let's touch base when you arrive."
                    }
                }
                address.contains("4455") || address.contains("Bob") -> {
                    when {
                        lowerMsg.contains("compose") || lowerMsg.contains("link") -> "Awesome, that developer page is literally my bible right now."
                        else -> "Completely agree! Jetpack Compose makes visual updates extremely neat."
                    }
                }
                address.replace("+", "").all { it.isDigit() } -> {
                    "Thanks for the message! I'm currently using 'SMS Messenger' built with Material 3."
                }
                else -> {
                    "Auto-Response: This is structured mock automation. Thank you for testing the Sandbox!"
                }
            }

            val replyMsg = SmsMessage(
                id = "mock_reply_${System.currentTimeMillis()}",
                threadId = "",
                address = address,
                body = replyText,
                date = System.currentTimeMillis(),
                type = 1, // Inbox
                read = false,
                senderName = resolvedContacts.value[address] ?: address,
                category = SmsClassifier.classify(address, replyText)
            )
            mockMessages.value = mockMessages.value + replyMsg
            
            // Trigger overlay popup if the current thread isn't actively open
            if (activeConversationAddress.value != address) {
                incomingAlertBanner.value = replyMsg
                delay(4000)
                incomingAlertBanner.value = null
            }
        }
    }

    // Toggle sandbox mode
    fun toggleSandboxMode(context: Context) {
        isMockSandboxMode.value = !isMockSandboxMode.value
        if (!isMockSandboxMode.value) {
            loadFromSystem(context)
        }
    }

    // Mark all thread messages as read
    fun markThreadAsRead(address: String) {
        viewModelScope.launch {
            if (isMockSandboxMode.value) {
                mockMessages.value = mockMessages.value.map {
                    if (it.address == address && !it.read) it.copy(read = true) else it
                }
            } else {
                realMessages.value = realMessages.value.map {
                    if (it.address == address && !it.read) it.copy(read = true) else it
                }
            }
        }
    }

    // Archive thread
    fun toggleArchiveThread(address: String) {
        val current = archivedThreadIds.value
        if (current.contains(address)) {
            archivedThreadIds.value = current - address
        } else {
            archivedThreadIds.value = current + address
        }
    }

    // Delete single message
    fun deleteMessage(msgId: String) {
        if (isMockSandboxMode.value) {
            mockMessages.value = mockMessages.value.filterNot { it.id == msgId }
        } else {
            realMessages.value = realMessages.value.filterNot { it.id == msgId }
        }
    }

    // Delete entire thread
    fun deleteThread(address: String) {
        if (isMockSandboxMode.value) {
            mockMessages.value = mockMessages.value.filterNot { it.address == address }
        } else {
            realMessages.value = realMessages.value.filterNot { it.address == address }
        }
        if (activeConversationAddress.value == address) {
            activeConversationAddress.value = null
        }
    }
}
