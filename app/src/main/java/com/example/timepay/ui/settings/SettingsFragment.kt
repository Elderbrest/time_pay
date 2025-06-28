package com.example.timepay.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.timepay.LoginActivity
import com.example.timepay.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.example.timepay.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()

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

        try {
            // Load current user data
            loadUserData()
    
            // Save button logic
            binding.saveSettingsButton.setOnClickListener {
                saveUserData()
            }
    
            binding.logoutButton.setOnClickListener {
                showLogoutConfirmationDialog()
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "Error setting up fragment", e)
            Toast.makeText(context, "Error setting up settings: ${e.message}", Toast.LENGTH_SHORT).show()
            resetUI()
        }
    }
    
    private fun loadUserData() {
        // Show loading state
        setUiLoading(true)
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = withTimeoutOrNull(5000) {
                    userRepository.getCurrentUserOnce()
                }
                
                if (user != null) {
                    // Load first name and last name
                    binding.firstNameInput.setText(user.firstName)
                    binding.lastNameInput.setText(user.lastName)
                    
                    // Load company
                    binding.companyInput.setText(user.company)
                    
                    // Load salary rate
                    if (user.salaryRate > 0) {
                        binding.salaryRateInput.setText("%.2f".format(user.salaryRate))                    } else {
                        binding.salaryRateInput.setText("")
                    }
                    
                    android.util.Log.d("SettingsFragment", "Successfully loaded user data")
                } else {
                    android.util.Log.w("SettingsFragment", "Could not load user data")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Couldn't load your profile data", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error loading user data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                setUiLoading(false)
            }
        }
    }
    
    private fun setUiLoading(isLoading: Boolean) {
        binding.firstNameInput.isEnabled = !isLoading
        binding.lastNameInput.isEnabled = !isLoading
        binding.companyInput.isEnabled = !isLoading
        binding.salaryRateInput.isEnabled = !isLoading
        binding.saveSettingsButton.isEnabled = !isLoading
        binding.saveSettingsButton.text = if (isLoading) "Loading..." else "Save"
    }
    
    private fun resetUI() {
        setUiLoading(false)
    }
    
    private fun saveUserData() {
        try {
            // Get input values
            val firstName = binding.firstNameInput.text.toString().trim()
            val lastName = binding.lastNameInput.text.toString().trim()
            val company = binding.companyInput.text.toString().trim()
            val salaryRateText = binding.salaryRateInput.text.toString().trim()

            android.util.Log.d("SettingsFragment", "Preparing to save: firstName=$firstName, lastName=$lastName, company=$company")
            
            // Simple validation for salary rate
            val salaryRate = if (salaryRateText.isNotEmpty()) {
                try {
                    salaryRateText.toDouble()
                } catch (e: Exception) {
                    binding.salaryRateInputLayout.error = "Invalid number"
                    return
                }
            } else 0.0
            
            binding.salaryRateInputLayout.error = null
            
            // Show saving state
            setUiLoading(true)
            
            // Perform the update
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Create a map of fields to update in a single operation
                    val updates = mapOf(
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "company" to company,
                        "salaryRate" to salaryRate,
                    )
                    
                    android.util.Log.d("SettingsFragment", "Updating user fields: $updates")
                    
                    // Update all fields in a single Firestore operation
                    userRepository.updateUserFields(updates)
                    
                    android.util.Log.d("SettingsFragment", "Successfully updated user fields")
                    
                    // Show success toast
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Settings updated!", Toast.LENGTH_SHORT).show()
                    }
                    
                    // Reload data to reflect changes
                    loadUserData()
                } catch (e: Exception) {
                    // Show error toast with detailed message
                    android.util.Log.e("SettingsFragment", "Failed to update settings", e)
                    withContext(Dispatchers.Main) {
                        val errorMsg = e.message ?: "Unknown error"
                        Toast.makeText(context, "Failed to update: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                    setUiLoading(false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "Error in saveUserData", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            setUiLoading(false)
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                logout()
            }
            .setNegativeButton("No", null)
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