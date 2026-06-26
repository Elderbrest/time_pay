package com.el.timepay.ui.home

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
import com.el.timepay.R
import com.el.timepay.models.User
import com.el.timepay.databinding.FragmentHomeBinding
import com.el.timepay.repository.PhotoRepository
import com.el.timepay.repository.UserRepository
import com.el.timepay.repository.CalendarDayRepository
import com.el.timepay.ui.shared.LogHoursBottomSheet
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import android.util.Log
import com.el.timepay.models.CalendarDayInfo
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository()
    private val photoRepository = PhotoRepository()
    private val calendarRepository = CalendarDayRepository()

    private var loadedDays: Map<String, CalendarDayInfo> = emptyMap()
    private var salaryRate: Double = 0.0

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

        binding.logTodayButton.setOnClickListener {
            openLogSheet(LocalDate.now())
        }

        // Refresh stats after the shared log/plan sheet writes a day.
        childFragmentManager.setFragmentResultListener(
            LogHoursBottomSheet.DEFAULT_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, _ -> loadUserData() }
    }

    /** Most recent "done" day's start/end, used to pre-fill the sheet's time pickers. */
    private fun lastShift(): Pair<String?, String?> {
        val last = loadedDays.entries
            .filter { it.value.status == "done" && it.value.startTime != null && it.value.endTime != null }
            .maxByOrNull { it.key }
            ?.value
        return last?.startTime to last?.endTime
    }

    private fun openLogSheet(date: LocalDate) {
        val key = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val info = loadedDays[key]
        val (lastStart, lastEnd) = lastShift()
        LogHoursBottomSheet.newInstance(
            targetDate = date,
            existingStatus = info?.status,
            existingStartTime = info?.startTime,
            existingEndTime = info?.endTime,
            existingHours = info?.hoursWorked,
            existingNote = info?.note,
            salaryRate = salaryRate,
            lastShiftStart = lastStart,
            lastShiftEnd = lastEnd,
            requestKey = LogHoursBottomSheet.DEFAULT_REQUEST_KEY,
            allowPlan = false
        ).show(childFragmentManager, "log_hours")
    }

    private fun updateNextWorkDayCard(days: Map<String, CalendarDayInfo>) {
        val today = LocalDate.now()

        val nextWorkDate = days
            .filter { it.value.status == "working" }
            .keys
            .map { LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
            .filter { it.isAfter(today) || it.isEqual(today) }
            .minOrNull()

        val dateView = binding.nextWorkDayDateText
        if (nextWorkDate != null) {
            // Planned shift: big bold date, e.g. "Thu 20 Jun" + green chip.
            dateView.text =
                nextWorkDate.format(DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH))
            dateView.setTextAppearance(R.style.TextAppearance_TimePay_HeadlineMedium)
            dateView.setTextColor(
                com.google.android.material.color.MaterialColors.getColor(dateView, com.google.android.material.R.attr.colorOnSurface)
            )
            dateView.setTypeface(dateView.typeface, android.graphics.Typeface.BOLD)
            binding.nextShiftChip.visibility = View.VISIBLE
        } else {
            // Empty: quiet, muted line — not a giant headline.
            dateView.text = getString(R.string.home_no_planned_days)
            dateView.setTextAppearance(R.style.TextAppearance_TimePay_BodyMedium)
            dateView.setTextColor(
                com.google.android.material.color.MaterialColors.getColor(dateView, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            binding.nextShiftChip.visibility = View.GONE
        }
    }

    /** Formats hours without a trailing ".0" (8.0 -> "8", 8.5 -> "8.5"), locale-stable. */
    private fun formatHours(hours: Double): String =
        if (hours % 1.0 == 0.0) hours.toInt().toString()
        else String.format(java.util.Locale.US, "%.1f", hours)

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

        binding.hoursThisWeekText.text = getString(R.string.home_hours_short, formatHours(hours))
        binding.earningsThisWeekText.text = formatEarnings(earnings)
    }

    /** "PLN 920" / "PLN 920.50", locale-stable. */
    private fun formatEarnings(earnings: Double): String {
        val code = getString(R.string.currency_code)
        return if (earnings % 1.0 == 0.0) "$code ${earnings.toInt()}"
        else "$code " + String.format(java.util.Locale.US, "%.2f", earnings)
    }

    private fun updateMonthlyOverviewCard(
        days: Map<String, CalendarDayInfo>,
        user: User
    ) {
        val hours = days.values
            .filter { it.status == "done" }
            .sumOf { it.hoursWorked ?: 0.0 }

        val daysWorked = days.values.count { it.status == "done" }

        val earnings = hours * user.salaryRate
        val hoursText = formatHours(hours)
        val earningsText = formatEarnings(earnings)

        binding.monthlyHoursText.text = hoursText
        binding.monthlyEarningsText.text = earningsText
        binding.monthlyDaysText.text = daysWorked.toString()

        // This-month stat card (same data, distinct views from the hero)
        binding.monthHoursCardText.text = getString(R.string.home_hours_short, hoursText)
        binding.monthEarningsCardText.text = earningsText
    }

    private fun loadUserData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = userRepository.getCurrentUserOnce()
                val currentMonth = YearMonth.now()
                val days = calendarRepository.getCurrentMonthDates(currentMonth)

                // Cache for the shared log sheet (today's info + last-shift pre-fill).
                loadedDays = days
                salaryRate = user?.salaryRate ?: 0.0

                // If the current week (Mon..Sun) spans into the previous month,
                // fetch the previous month's data as well so weekly stats don't undercount.
                val today = LocalDate.now()
                val startOfWeek = today.with(java.time.DayOfWeek.MONDAY)
                val startOfWeekMonth = YearMonth.from(startOfWeek)
                val weeklyDays = if (startOfWeekMonth != currentMonth) {
                    val prevMonthDays = calendarRepository.getCurrentMonthDates(startOfWeekMonth)
                    prevMonthDays + days
                } else {
                    days
                }

                if (user != null) {
                   showUserData(user)
                    updateNextWorkDayCard(days)
                    updateWeeklyOverviewCard(weeklyDays, user)
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
        // Greeting name (minimal header — just greeting + name)
        val fullName = "${user.firstName} ${user.lastName}".trim()
        binding.fullNameText.text = if (fullName.isNotBlank()) fullName else getString(R.string.home_default_user)

        resetStats()

        // Profile photo
        loadProfileImageForCurrentUser()
    }

    private fun showEmptyState() {
        Log.w("HomeFragment", "No user data available")
        binding.fullNameText.text = getString(R.string.home_default_user)
        resetStats()
    }

    /** Zeroed placeholders shown before/without real data. */
    private fun resetStats() {
        val zeroHours = getString(R.string.home_hours_short, "0")
        val zeroEarnings = formatEarnings(0.0)
        binding.hoursThisWeekText.text = zeroHours
        binding.earningsThisWeekText.text = zeroEarnings
        binding.monthlyHoursText.text = "0"
        binding.monthlyDaysText.text = "0"
        binding.monthlyEarningsText.text = zeroEarnings
        binding.monthHoursCardText.text = zeroHours
        binding.monthEarningsCardText.text = zeroEarnings
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
            if (!isAdded || _binding == null) return@addOnSuccessListener
            Glide.with(requireContext())
                .load(uri)
                .circleCrop()
                .into(binding.profileImage)
            binding.profileInitial.visibility = View.GONE
        }.addOnFailureListener {
            if (!isAdded || _binding == null) return@addOnFailureListener
            // fallback to initials
            viewLifecycleOwner.lifecycleScope.launch {
                val user = userRepository.getCurrentUserOnce()
                if (!isAdded || _binding == null) return@launch
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