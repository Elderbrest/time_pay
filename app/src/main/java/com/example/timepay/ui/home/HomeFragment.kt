package com.example.timepay.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.timepay.LoginActivity
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
        
        Log.d("HomeFragment", "onViewCreated: Loading user data")
        loadUserData()
        
        // Set click listener for profile image
        binding.profileImage.setOnClickListener {
            openImagePicker()
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("HomeFragment", "loadUserData: No user ID found")
            return
        }
        
        Log.d("HomeFragment", "loadUserData: Fetching data for user $userId")
        
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Log.d("HomeFragment", "loadUserData: Document exists with data: ${document.data}")
                    val user = document.toObject(User::class.java)
                    user?.let {
                        // Set full name
                        val fullName = "${it.firstname} ${it.lastname}".trim()
                        Log.d("HomeFragment", "loadUserData: Setting full name to $fullName (firstname: ${it.firstname}, lastname: ${it.lastname})")
                        binding.fullNameText.text = fullName
                        
                        // Set company name
                        binding.workplaceText.text = it.company.ifEmpty { "Add your workplace" }
                        
                        // Load profile image if URL exists
                        if (it.photoURL.isNotEmpty()) {
                            updateProfileImage(Uri.parse(it.photoURL))
                        }
                    } ?: run {
                        Log.e("HomeFragment", "loadUserData: Failed to convert document to User object")
                    }
                } else {
                    Log.e("HomeFragment", "loadUserData: No document found for user $userId")
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "loadUserData: Error loading user data", e)
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

    private fun showSettingsDialog() {
        val options = arrayOf("Logout")
        AlertDialog.Builder(requireContext())
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> logout()
                }
            }
            .show()
    }

    private fun logout() {
        auth.signOut()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}