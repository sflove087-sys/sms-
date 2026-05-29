package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages != null && messages.isNotEmpty()) {
                    val smsMap = mutableMapOf<String, StringBuilder>()
                    var date = System.currentTimeMillis()
                    
                    // Group parts of SMS by sender (MMS / multi-part messages)
                    for (sms in messages) {
                        val sender = sms.originatingAddress ?: "Unknown"
                        val body = sms.messageBody ?: ""
                        date = sms.timestampMillis
                        
                        if (smsMap.containsKey(sender)) {
                            smsMap[sender]?.append(body)
                        } else {
                            smsMap[sender] = StringBuilder(body)
                        }
                    }

                    for ((sender, bodyBuilder) in smsMap) {
                        val fullBody = bodyBuilder.toString()
                        val category = SmsClassifier.classify(sender, fullBody)
                        
                        val newMessage = SmsMessage(
                            id = "incoming_${System.currentTimeMillis()}_${sender.hashCode()}",
                            threadId = "", // Filled or matched by thread logic in ViewModel
                            address = sender,
                            body = fullBody,
                            date = date,
                            type = 1, // Inbox
                            read = false,
                            senderName = null,
                            category = category
                        )
                        Log.d("SmsReceiver", "Received SMS from $sender: '$fullBody' ($category)")
                        SmsEventBus.postIncomingSms(newMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing incoming SMS", e)
            }
        }
    }
}
