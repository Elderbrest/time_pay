package com.example.timepay.models

data class User(
    val id: String = "",  // Same as Firebase Auth UID
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val company: String = "",
    val role: String = "user",  // For role-based access
    val salaryRate: Double = 0.0,
    val currency: String = "USD",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val settings: UserSettings = UserSettings()
)

data class UserSettings(
    val theme: String = "light",
    val notifications: Boolean = true,
    val language: String = "en"
) 