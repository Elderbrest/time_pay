package com.el.timepay.models

data class User(
    val id: String = "",  // Same as Firebase Auth UID
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val company: String = "",
    val role: String = "user",  // For role-based access
    val salaryRate: Double = 0.0,
    // Alpha currency code for the salary rate, e.g. "PLN"/"INR". A pure display label:
    // changing it relabels the rate and never converts stored amounts (no FX).
    // The default is load-bearing — existing Firestore docs have no such field, so
    // toObject() must leave every current user on PLN with no visible change.
    val currencyCode: String = "PLN",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
)