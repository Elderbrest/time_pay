package com.example.timepay.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class PhotoRepository {
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun uploadProfilePhoto(imageUri: Uri) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val storageRef = storage.reference.child("profile_images/$userId.jpg")
        storageRef.putFile(imageUri).await()
    }

    suspend fun deleteProfilePhoto() {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val storageRef = storage.reference.child("profile_images/$userId.jpg")
        storageRef.delete().await()
    }
} 