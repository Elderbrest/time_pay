package com.el.timepay.repository

import com.el.timepay.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getCurrentUserOnce(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            // Cache-first default get(): serves from local cache when offline,
            // and refreshes from the server when a connection is available.
            val document = db.collection("users")
                .document(userId)
                .get()
                .await()

            document.toObject(User::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to fetch user data", e)
            null
        }
    }

    suspend fun createUserProfile(user: User) {
        val userId = auth.currentUser?.uid ?: return

        val document = db.collection("users")
            .document(userId)
            .get()
            .await()

        if (document.exists()) {
            val updates = mutableMapOf<String, Any>()

            if (user.firstName.isNotEmpty()) updates["firstName"] = user.firstName
            if (user.lastName.isNotEmpty()) updates["lastName"] = user.lastName
            if (user.email.isNotEmpty()) updates["email"] = user.email
            if (user.company.isNotEmpty()) updates["company"] = user.company
            if (user.role.isNotEmpty()) updates["role"] = user.role
            if (user.createdAt > 0) updates["createdAt"] = user.createdAt
            if (user.lastLogin > 0) updates["lastLogin"] = user.lastLogin

            updateUserFields(updates)
        } else {
            db.collection("users")
                .document(userId)
                .set(user.copy(id = userId))
                .await()
        }
    }

    suspend fun updateUserFields(fields: Map<String, Any>) {
        val userId = auth.currentUser?.uid ?: return
        
        if (fields.isNotEmpty()) {
            try {
                db.collection("users")
                    .document(userId)
                    .update(fields)
                    .await()
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "Error updating fields in Firestore: ${e.message}", e)
                throw e // Re-throw the exception so the caller can handle it
            }
        } else {
            android.util.Log.w("UserRepository", "No fields to update")
        }
    }
} 