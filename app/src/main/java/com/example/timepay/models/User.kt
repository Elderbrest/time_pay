package com.example.timepay.models

data class User(
    val id: String = "",  // Same as Firebase Auth UID
    val firstname: String = "",
    val lastname: String = "",
    val email: String = "",
    val company: String = "",
    val photoURL: String = "",
    val role: String = "user",  // For role-based access
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val settings: UserSettings = UserSettings()
)

data class UserSettings(
    val theme: String = "light",
    val notifications: Boolean = true,
    val language: String = "en"
) 