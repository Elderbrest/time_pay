package com.example.timepay

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.timepay.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // Style the Sign Up text
        val fullText = "Don't have an account? Sign Up"
        val spannableString = SpannableString(fullText)
        val signUpStart = fullText.indexOf("Sign Up")
        val signUpEnd = signUpStart + "Sign Up".length
        
        val purpleColor = ContextCompat.getColor(this, R.color.purple_500)
        spannableString.setSpan(
            ForegroundColorSpan(purpleColor),
            signUpStart,
            signUpEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        binding.signUpText.text = spannableString

        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
} 