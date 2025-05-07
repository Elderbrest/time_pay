package com.example.timepay.repository

import com.example.timepay.models.User
import com.example.timepay.models.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getCurrentUserOnce(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            // Always fetch from server first
            val document = db.collection("users")
                .document(userId)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            
            android.util.Log.d("UserRepository", "Fetched document from server: ${document.id}")
            android.util.Log.d("UserRepository", "Document data: ${document.data}")
            
            // Check specific fields
            android.util.Log.d("UserRepository", "firstname field: ${document.getString("firstname")}")
            android.util.Log.d("UserRepository", "lastName field: ${document.getString("lastName")}")
            android.util.Log.d("UserRepository", "lastname field: ${document.getString("lastname")}")
            android.util.Log.d("UserRepository", "firstName field: ${document.getString("firstName")}")
            
            document.toObject(User::class.java)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to fetch from server, trying cache", e)
            try {
                // Fallback to cache if server fetch fails
                val document = db.collection("users")
                    .document(userId)
                    .get()
                    .await()
                
                document.toObject(User::class.java)
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "Failed to fetch user data", e)
                null
            }
        }
    }

    // New method: Get user directly from server with no cache fallback
    suspend fun getCurrentUserStrict(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            // Force server fetch only, no cache fallback
            val document = db.collection("users")
                .document(userId)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            
            document.toObject(User::class.java)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to fetch from server (strict mode)", e)
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
        
        if (user.firstName.isNotEmpty()) updates["firstname"] = user.firstName
        if (user.lastName.isNotEmpty()) updates["lastname"] = user.lastName
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

    suspend fun updateCompanyField(company: String?) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(userId)
            .update("company", company ?: "")
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

    suspend fun updateSalaryRateAndCurrency(salaryRate: Double, currency: String) {
        val userId = auth.currentUser?.uid ?: return
        val updates = mapOf(
            "salaryRate" to salaryRate,
            "currency" to currency
        )
        
        db.collection("users")
            .document(userId)
            .update(updates)
            .await()
    }
} 