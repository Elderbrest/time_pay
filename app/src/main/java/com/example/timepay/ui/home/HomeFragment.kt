package com.example.timepay.ui.home

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.timepay.R
import com.example.timepay.databinding.FragmentHomeBinding
import com.example.timepay.models.User
import com.example.timepay.repository.PhotoRepository
import com.example.timepay.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import android.util.Log
import com.google.firebase.storage.FirebaseStorage

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()
    private val photoRepository = PhotoRepository()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfilePhoto(uri)
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
            showImagePickerOptions()
        }
    }

    private fun loadUserData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("HomeFragment", "Fetching user data...")
                val user = userRepository.getCurrentUserOnce()
                
                if (user != null) {
                    Log.d("HomeFragment", "User data retrieved: $user")
                    Log.d("HomeFragment", "First name: '${user.firstName}', Last name: '${user.lastName}'")
                    
                    // Set full name
                    val fullName = "${user.firstName} ${user.lastName}".trim()
                    Log.d("HomeFragment", "Full name constructed: '$fullName'")
                    
                    binding.fullNameText.text = if (fullName.isNotBlank()) fullName else "User"
                    Log.d("HomeFragment", "Text set to fullNameText: '${binding.fullNameText.text}'")
                    
                    // Set company name
                    binding.workplaceText.text = user.company.ifEmpty { "Add your workplace" }
                    
                    // Always try to load from storage
                    loadProfileImageForCurrentUser()
                } else {
                    Log.w("HomeFragment", "No user data available")
                    binding.fullNameText.text = "User"
                    binding.workplaceText.text = "Add your workplace"
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error loading user data", e)
                Toast.makeText(context, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")
        AlertDialog.Builder(requireContext())
            .setTitle("Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                    2 -> removeProfilePhoto()
                }
            }
            .show()
    }

    private fun openCamera() {
        // TODO: Implement camera functionality
        Toast.makeText(context, "Camera functionality coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun removeProfilePhoto() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                photoRepository.deleteProfilePhoto()
                loadProfileImageForCurrentUser()
                Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to remove photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadProfilePhoto(imageUri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.profileImage.alpha = 0.5f
                photoRepository.uploadProfilePhoto(imageUri) // Always uploads to profile_images/{userId}.jpg
                binding.profileImage.alpha = 1.0f
                loadProfileImageForCurrentUser()
                Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.profileImage.alpha = 1.0f
                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProfileImageForCurrentUser() {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val imagePath = "profile_images/$userId.jpg"
        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child(imagePath)
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            Glide.with(requireContext())
                .load(uri)
                .circleCrop()
                .into(binding.profileImage)
            binding.profileInitial.visibility = View.GONE
        }.addOnFailureListener {
            // fallback to initials
            viewLifecycleOwner.lifecycleScope.launch {
                val user = userRepository.getCurrentUserOnce()
                user?.let {
                    showInitialsPlaceholder(it.firstName)
                }
            }
        }
    }

    private fun showInitialsPlaceholder(firstname: String) {
        // Get initials from first letter of first name
        val initial = if (firstname.isNotEmpty()) {
            firstname.first().toString().uppercase()
        } else {
            "?"
        }

        Log.d("HomeFragment", "Showing initial: $initial")
        
        // Show the initials in the TextView
        binding.profileInitial.text = initial
        binding.profileInitial.visibility = View.VISIBLE
        
        // Hide the image view background if needed
        binding.profileImage.setImageResource(R.drawable.circle_background)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}