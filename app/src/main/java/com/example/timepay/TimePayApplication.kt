package com.example.timepay

import android.app.Application
import com.google.firebase.FirebaseApp

class TimePayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
} 