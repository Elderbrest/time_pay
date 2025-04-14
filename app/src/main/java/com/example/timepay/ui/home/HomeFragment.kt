package com.example.timepay.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.timepay.databinding.FragmentHomeBinding
import com.example.timepay.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.bumptech.glide.Glide

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadImageToFirebase(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadUserData()
        
        // Set click listener for profile image
        binding.profileImage.setOnClickListener {
            openImagePicker()
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val user = document.toObject(User::class.java)
                    user?.let {
                        // Set full name
                        val fullName = "${it.firstname} ${it.lastname}".trim()
                        binding.fullNameText.text = fullName
                        
                        // Set company name
                        binding.workplaceText.text = it.company.ifEmpty { "Add your workplace" }
                        
                        // Load profile image if URL exists
                        if (it.photoURL.isNotEmpty()) {
                            updateProfileImage(Uri.parse(it.photoURL))
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun uploadImageToFirebase(imageUri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("profile_images/$userId.jpg")

        binding.profileImage.alpha = 0.5f // Show upload in progress

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                // Get the download URL and update the UI
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Update Firestore with the new photo URL
                    db.collection("users").document(userId)
                        .update("photoURL", downloadUri.toString())
                        .addOnSuccessListener {
                            updateProfileImage(downloadUri)
                            Toast.makeText(context, "Profile image updated!", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                binding.profileImage.alpha = 1.0f
                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadProfileImage() {
        val userId = auth.currentUser?.uid ?: return
        storage.reference.child("profile_images/$userId.jpg")
            .downloadUrl
            .addOnSuccessListener { uri ->
                updateProfileImage(uri)
            }
    }

    private fun updateProfileImage(uri: Uri) {
        context?.let {
            binding.profileImage.alpha = 1.0f
            Glide.with(it)
                .load(uri)
                .circleCrop()
                .into(binding.profileImage)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}