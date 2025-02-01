package com.example.strawberryremote_android.util

import androidx.lifecycle.ViewModel
import java.net.Socket

class SharedViewModel : ViewModel() {
    var socket: Socket? = null
}