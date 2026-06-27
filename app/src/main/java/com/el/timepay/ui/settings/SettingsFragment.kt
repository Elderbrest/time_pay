package com.el.timepay.ui.settings

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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.el.timepay.LoginActivity
import com.el.timepay.R
import com.el.timepay.databinding.FragmentSettingsBinding
import com.el.timepay.models.User
import com.el.timepay.repository.PhotoRepository
import com.el.timepay.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()
    private val photoRepository = PhotoRepository()

    /** True once a profile photo has loaded, so initials don't paint over it. */
    private var hasPhoto = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> uploadProfilePhoto(uri) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProfileImageForCurrentUser()
        loadUserData()

        binding.changePhotoButton.setOnClickListener { showPhotoOptions() }
        binding.profileImage.setOnClickListener { showPhotoOptions() }
        binding.saveSettingsButton.setOnClickListener { saveUserData() }
        binding.logoutButton.setOnClickListener { showLogoutConfirmationDialog() }
    }

    private fun loadUserData() {
        setUiLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = withTimeoutOrNull(5000) { userRepository.getCurrentUserOnce() }
                if (_binding == null) return@launch

                if (user != null) {
                    bindUser(user)
                } else {
                    Toast.makeText(context, "Couldn't load your profile data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error loading user data", e)
                if (_binding != null) {
                    Toast.makeText(context, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (_binding != null) setUiLoading(false)
            }
        }
    }

    private fun bindUser(user: User) {
        binding.firstNameInput.setText(user.firstName)
        binding.lastNameInput.setText(user.lastName)

        if (user.salaryRate > 0) {
            // Locale.US so the value always uses a dot — the parse on save is dot-only.
            binding.salaryRateInput.setText(String.format(java.util.Locale.US, "%.2f", user.salaryRate))
        } else {
            binding.salaryRateInput.setText("")
        }

        val fullName = "${user.firstName} ${user.lastName}".trim()
        binding.profileNameText.text =
            if (fullName.isNotBlank()) fullName else getString(R.string.settings_default_name)
        binding.profileEmailText.text = user.email

        // Show initials immediately unless a photo has already loaded (avoids painting
        // initials over a loaded photo when the user fetch resolves after the image).
        if (!hasPhoto) showInitialsPlaceholder(user)
    }

    private fun setUiLoading(isLoading: Boolean) {
        binding.firstNameInput.isEnabled = !isLoading
        binding.lastNameInput.isEnabled = !isLoading
        binding.salaryRateInput.isEnabled = !isLoading
        binding.saveSettingsButton.isEnabled = !isLoading
        binding.saveSettingsButton.text = if (isLoading) {
            getString(R.string.loading_button)
        } else {
            getString(R.string.settings_save_button)
        }
    }

    private fun saveUserData() {
        val firstName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val salaryRateText = binding.salaryRateInput.text.toString().trim()

        val salaryRate = if (salaryRateText.isNotEmpty()) {
            // Accept a comma decimal (locale keyboards) by normalizing to a dot.
            salaryRateText.replace(',', '.').toDoubleOrNull() ?: run {
                binding.salaryRateInputLayout.error = "Invalid number"
                return
            }
        } else {
            0.0
        }
        binding.salaryRateInputLayout.error = null

        setUiLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                userRepository.updateUserFields(
                    mapOf(
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "salaryRate" to salaryRate,
                    )
                )
                if (_binding == null) return@launch
                Toast.makeText(context, getString(R.string.settings_updated_message), Toast.LENGTH_SHORT).show()
                loadUserData()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Failed to update settings", e)
                if (_binding != null) {
                    Toast.makeText(context, "Failed to update: ${e.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    setUiLoading(false)
                }
            }
        }
    }

    // region profile photo

    private fun showPhotoOptions() {
        val options = arrayOf("Choose from Gallery", "Remove Photo")
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_change_photo)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openGallery()
                    1 -> removeProfilePhoto()
                }
            }
            .show()
    }

    private fun openGallery() {
        imagePickerLauncher.launch(
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        )
    }

    private fun uploadProfilePhoto(imageUri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.profileImage.alpha = 0.5f
                photoRepository.uploadProfilePhoto(imageUri)
                if (_binding == null) return@launch
                binding.profileImage.alpha = 1.0f
                loadProfileImageForCurrentUser()
                Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) {
                    binding.profileImage.alpha = 1.0f
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun removeProfilePhoto() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                photoRepository.deleteProfilePhoto()
                val user = userRepository.getCurrentUserOnce()
                if (_binding == null) return@launch
                hasPhoto = false
                showInitialsPlaceholder(user)
                Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) {
                    Toast.makeText(context, "Failed to remove photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadProfileImageForCurrentUser() {
        val userId = auth.currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance()
            .reference.child("profile_images/$userId.jpg")
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            if (!isAdded || _binding == null) return@addOnSuccessListener
            hasPhoto = true
            Glide.with(requireContext())
                .load(uri)
                .circleCrop()
                .into(binding.profileImage)
            binding.profileInitial.visibility = View.GONE
        }
        // On failure (e.g. no photo) the initials from bindUser stay shown — no refetch.
    }

    /** Initials on the green tint when there's no photo (e.g. "MU"). */
    private fun showInitialsPlaceholder(user: User?) {
        val first = user?.firstName?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        val last = user?.lastName?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        val initials = (first + last).ifBlank { "?" }
        binding.profileImage.setImageDrawable(null)
        binding.profileInitial.text = initials
        binding.profileInitial.visibility = View.VISIBLE
    }

    // endregion

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_logout_dialog_title)
            .setMessage(R.string.settings_logout_dialog_message)
            .setPositiveButton(R.string.settings_logout_confirm) { _, _ -> logout() }
            .setNegativeButton(R.string.cancel_button, null)
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
