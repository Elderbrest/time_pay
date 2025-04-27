package com.example.timepay.repository

import com.example.timepay.models.User
import com.example.timepay.models.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getCurrentUserOnce(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            val document = db.collection("users")
                .document(userId)
                .get()
                .await()
            
            document.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createUserProfile(user: User) {
        val userId = auth.currentUser?.uid ?: return
        
        // First check if user document already exists
        val document = db.collection("users")
            .document(userId)
            .get()
            .await()

        if (document.exists()) {
            // User already exists, update instead
            updateUser(user)
        } else {
            // Create new user document
            db.collection("users")
                .document(userId)
                .set(user.copy(id = userId))
                .await()
        }
    }

    suspend fun updateUser(user: User) {
        val userId = auth.currentUser?.uid ?: return
        
        // Create a map of non-empty fields to update
        val updates = mutableMapOf<String, Any>()
        
        if (user.firstname.isNotEmpty()) updates["firstname"] = user.firstname
        if (user.lastname.isNotEmpty()) updates["lastname"] = user.lastname
        if (user.email.isNotEmpty()) updates["email"] = user.email
        if (user.company.isNotEmpty()) updates["company"] = user.company
        if (user.role.isNotEmpty()) updates["role"] = user.role
        if (user.createdAt > 0) updates["createdAt"] = user.createdAt
        if (user.lastLogin > 0) updates["lastLogin"] = user.lastLogin
        if (user.settings != UserSettings()) updates["settings"] = user.settings

        // Only update if there are fields to update
        if (updates.isNotEmpty()) {
            db.collection("users")
                .document(userId)
                .update(updates)
                .await()
        }
    }

    suspend fun updateUserSettings(settings: UserSettings) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(userId)
            .update("settings", settings)
            .await()
    }

    suspend fun updateLastLogin() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(userId)
            .update("lastLogin", System.currentTimeMillis())
            .await()
    }
} 