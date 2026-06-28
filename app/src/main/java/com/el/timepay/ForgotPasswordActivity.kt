package com.el.timepay

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.el.timepay.databinding.ActivityForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Lets a signed-out user request a password-reset email. On success the card swaps to a
 * neutral "check your email" confirmation. Messaging is intentionally generic regardless of
 * whether the email exists, to avoid account enumeration.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill the email if the user typed one on the login screen.
        intent.getStringExtra(EXTRA_EMAIL)?.let { binding.emailInput.setText(it) }

        binding.sendButton.setOnClickListener { sendResetLink() }
        binding.backToLoginText.setOnClickListener { goToLogin() }
    }

    private fun sendResetLink() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.forgot_invalid_email)
            return
        }
        binding.emailLayout.error = null

        setLoading(true)
        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            setLoading(false)
            if (task.isSuccessful) {
                showSentState(email)
            } else {
                // Generic — do not reveal whether the address is registered.
                showSentState(email)
            }
        }
    }

    /** Swap the form for a calm confirmation message. */
    private fun showSentState(email: String) {
        binding.forgotTitle.text = getString(R.string.forgot_sent_title)
        binding.forgotSubtitle.text = getString(R.string.forgot_sent_body, email)
        binding.emailLabel.visibility = View.GONE
        binding.emailLayout.visibility = View.GONE
        binding.sendButton.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        binding.sendButton.isEnabled = !loading
        binding.emailInput.isEnabled = !loading
        binding.sendButton.text =
            if (loading) getString(R.string.loading_button) else getString(R.string.forgot_send_button)
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }
}
