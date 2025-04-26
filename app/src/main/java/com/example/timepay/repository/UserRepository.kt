package com.example.timepay.repository

import com.example.timepay.models.User
import com.example.timepay.models.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getCurrentUser(): Flow<User?> = callbackFlow {
        val userId = auth.currentUser?.uid ?: run {
            trySend(null)
            return@callbackFlow
        }

        val listener = db.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Handle error
                    return@addSnapshotListener
                }

                val user = snapshot?.toObject(User::class.java)
                trySend(user)
            }

        awaitClose { listener.remove() }
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
        db.collection("users")
            .document(userId)
            .set(user)
            .await()
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