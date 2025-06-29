package com.example.timepay.models

data class User(
    val id: String = "",  // Same as Firebase Auth UID
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val company: String = "",
    val role: String = "user",  // For role-based access
    val salaryRate: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
)