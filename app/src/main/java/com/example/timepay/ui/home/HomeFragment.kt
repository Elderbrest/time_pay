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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.timepay.R
import com.example.timepay.models.User
import com.example.timepay.databinding.FragmentHomeBinding
import com.example.timepay.repository.PhotoRepository
import com.example.timepay.repository.UserRepository
import com.example.timepay.repository.CalendarDayRepository
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import android.util.Log
import com.example.timepay.models.CalendarDayInfo
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository()
    private val photoRepository = PhotoRepository()
    private val calendarRepository = CalendarDayRepository()

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

    private fun updateNextWorkDayCard(days: Map<String, CalendarDayInfo>) {
        val today = LocalDate.now()

        val nextWorkDate = days
            .filter { it.value.status == "working" }
            .keys
            .map { LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
            .filter { it.isAfter(today) || it.isEqual(today) }
            .minOrNull()

        if (nextWorkDate != null) {
            val daysUntil = java.time.Period.between(today, nextWorkDate).days

            val inText = when (daysUntil) {
                0 -> getString(R.string.home_workday_today)
                1 -> getString(R.string.home_workday_tomorrow)
                else -> getString(R.string.home_workday_in_days)
            }

            val formattedDate = nextWorkDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

            binding.nextWorkDayInText.text = inText
            binding.nextWorkDayDateText.text = formattedDate
        } else {
            binding.nextWorkDayInText.text = "No planned work days"
            binding.nextWorkDayDateText.text = ""
        }
    }

    private fun updateWeeklyOverviewCard(
        days: Map<String, CalendarDayInfo>,
        user: User
    ) {
        val today = LocalDate.now()
        val startOfWeek = today.with(java.time.DayOfWeek.MONDAY)
        val endOfWeek = today.with(java.time.DayOfWeek.SUNDAY)

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val hours = days
            .filterKeys { dateStr ->
                val date = LocalDate.parse(dateStr, formatter)
                !date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)
            }
            .values
            .filter { it.status == "done" }
            .sumOf { it.hoursWorked ?: 0.0 }

        val earnings = hours * user.salaryRate

        val hoursText = if (hours % 1.0 == 0.0) {
            "${hours.toInt()}"
        } else {
            "%.1f".format(hours)
        }

        val earningsText = if (earnings % 1.0 == 0.0) {
            "${getString(R.string.currency_code)} ${earnings.toInt()}"
        } else {
            "${getString(R.string.currency_code)} %.2f".format(earnings)
        }

        binding.hoursThisWeekText.text = hoursText
        binding.earningsThisWeekText.text = earningsText
    }

    private fun updateMonthlyOverviewCard(
        days: Map<String, CalendarDayInfo>,
        user: User
    ) {
        val hours = days.values
            .filter { it.status == "done" }
            .sumOf { it.hoursWorked ?: 0.0 }

        val earnings = hours * user.salaryRate

        val hoursText = if (hours % 1.0 == 0.0) {
            "${hours.toInt()}"
        } else {
            "%.1f".format(hours)
        }

        val earningsText = if (earnings % 1.0 == 0.0) {
            "${getString(R.string.currency_code)} ${earnings.toInt()}"
        } else {
            "${getString(R.string.currency_code)} %.2f".format(earnings)
        }

        binding.monthlyHoursText.text = hoursText
        binding.monthlyEarningsText.text = earningsText
    }

    private fun loadUserData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = userRepository.getCurrentUserOnce()
                val days = calendarRepository.getCurrentMonthDates(YearMonth.now())

                if (user != null) {
                   showUserData(user)
                    updateNextWorkDayCard(days)
                    updateWeeklyOverviewCard(days, user)
                    updateMonthlyOverviewCard(days, user)
                } else {
                    showEmptyState()
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error loading user data", e)
                Toast.makeText(context, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUserData(user: User) {
        // Full name
        val fullName = "${user.firstName} ${user.lastName}".trim()
        binding.fullNameText.text = if (fullName.isNotBlank()) fullName else "User"

        // Company
        binding.workplaceText.text = user.company.ifEmpty { "Add your workplace" }

        // Salary rate
        val formattedRate = "%.2f".format(user.salaryRate)
        binding.salaryRateText.text = getString(R.string.salary_rate, getString(R.string.currency_code), formattedRate)

        // Weekly stats
        binding.hoursThisWeekText.text = "0h"
        binding.earningsThisWeekText.text = "${getString(R.string.currency_code)} 0"

        // Monthly stats
        binding.monthlyHoursText.text = "0h"
        binding.monthlyEarningsText.text = "${getString(R.string.currency_code)} 0"

        // Profile photo
        loadProfileImageForCurrentUser()
    }

    private fun showEmptyState() {
        Log.w("HomeFragment", "No user data available")
        binding.fullNameText.text = "User"
        binding.workplaceText.text = "Add your workplace"
        binding.salaryRateText.text = "Set your rate"

        binding.hoursThisWeekText.text = "0h"
        binding.earningsThisWeekText.text = "$0"
        binding.monthlyHoursText.text = "0h"
        binding.monthlyEarningsText.text = "$0"
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

    private fun showInitialsPlaceholder(firstName: String) {
        val initial = firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.profileInitial.text = initial
        binding.profileInitial.visibility = View.VISIBLE
        binding.profileImage.setImageResource(R.drawable.circle_background)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}