package com.example

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SmsEventBus {
    private val _incomingSms = MutableSharedFlow<SmsMessage>(extraBufferCapacity = 64)
    val incomingSms = _incomingSms.asSharedFlow()

    fun postIncomingSms(message: SmsMessage) {
        _incomingSms.tryEmit(message)
    }
}
