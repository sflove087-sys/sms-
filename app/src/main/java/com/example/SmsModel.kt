package com.example

import java.util.Locale

enum class MessageCategory {
    ALL,
    PERSONAL,
    TRANSACTIONS,
    SPAM,
    GENERAL
}

data class SmsMessage(
    val id: String,
    val threadId: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1 = Inbox, 2 = Sent
    val read: Boolean,
    val senderName: String? = null,
    val category: MessageCategory = MessageCategory.GENERAL
) {
    val isSent: Boolean get() = type == 2
}

data class SmsThread(
    val id: String,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val read: Boolean,
    val unreadCount: Int,
    val category: MessageCategory = MessageCategory.GENERAL,
    val isArchived: Boolean = false
)

object SmsClassifier {
    fun classify(address: String, body: String): MessageCategory {
        val lowerBody = body.lowercase(Locale.getDefault())
        val lowerAddress = address.lowercase(Locale.getDefault())

        // 1. Spam detection
        val spamKeywords = listOf(
            "win", "winner", "lottery", "casino", "free cash", "cash bonus", 
            "claim reward", "congratulations", "jackpot", "earn extra cash",
            "crypto double", "viagra", "gift card", "prize draw"
        )
        if (spamKeywords.any { lowerBody.contains(it) }) {
            return MessageCategory.SPAM
        }

        // 2. Transaction / OTP detection
        val otpKeywords = listOf(
            "otp", "verification code", "security code", "passcode", "verify your",
            "one-time password", "authorization code", "verification-code"
        )
        val financeKeywords = listOf(
            "debited", "credited", "spent of", "txn", "transaction", "bank",
            "atm withdrawal", "available balance", "statement", "credit card limit",
            "due alert", "chase", "paypal", "stripe", "venmo", "invoice"
        )
        
        val isOtp = otpKeywords.any { lowerBody.contains(it) }
        val isFinance = financeKeywords.any { lowerBody.contains(it) }
        
        // Short alphabetic sender IDs (e.g. AX-GOOGLE, CHASE-TX) are business alerts
        val isAlphabeticSender = address.any { it.isLetter() } && address.replace("+", "").length in 3..14

        if (isOtp || isFinance || isAlphabeticSender) {
            return MessageCategory.TRANSACTIONS
        }

        // 3. Personal detection
        // Normal numeric phone numbers without automations are highly likely personal
        val isNumeric = address.replace("+", "").all { it.isDigit() }
        if (isNumeric && address.length >= 7) {
            return MessageCategory.PERSONAL
        }

        return MessageCategory.GENERAL
    }
}
