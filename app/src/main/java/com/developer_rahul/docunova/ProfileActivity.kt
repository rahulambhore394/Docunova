package com.developer_rahul.docunova

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class ProfileActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ProfileActivity"
        private const val SUPABASE_URL = "https://biudcywgygbacfxfpuva.supabase.co"
        private const val SUPABASE_API_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJpdWRjeXdneWdiYWNmeGZwdXZhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM1Mzg1NDQsImV4cCI6MjA2OTExNDU0NH0.enJZKTQjKtOyB6VU5pYo_vf4p7ZLv2ayYuyqBWvLqbA"
    }

    private lateinit var tvUsername: TextView
    private lateinit var profileImage: ImageView
    private lateinit var btn_logOut: Button
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        // Adjust padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profileScrollView)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize GoogleSignInClient (for logout if needed)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Initialize views
        tvUsername = findViewById(R.id.userName)
        profileImage = findViewById(R.id.profileImage)
        btn_logOut = findViewById(R.id.signOutBtn)

        // Fetch and display user details
        fetchUserDetails()

        btn_logOut.setOnClickListener {
            logout()
        }
    }

    private fun fetchUserDetails() {
        val prefs = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)
        val accessToken = prefs.getString("access_token", null)

        if (loginMethod.isNullOrEmpty()) {
            tvUsername.text = "Guest"
            profileImage.setImageResource(R.drawable.ic_profile)
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (loginMethod == "google") {
            val fullName = prefs.getString("google_full_name", null)
            val avatarUrl = prefs.getString("google_avatar_url", null)

            tvUsername.text = fullName ?: "Google User"
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile)
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.ic_profile)
            }

            Log.d(TAG, "Loaded Google profile from SharedPreferences")
            return
        }

        if (loginMethod == "email") {
            if (accessToken.isNullOrEmpty()) {
                tvUsername.text = "Session Expired"
                profileImage.setImageResource(R.drawable.ic_profile)
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                return
            }

            val url = "$SUPABASE_URL/auth/v1/user"
            val request = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                { response ->
                    try {
                        Log.d(TAG, "User details response: $response")
                        val email = response.optString("email", "No Email")
                        val metadata = response.optJSONObject("user_metadata")
                        val fullName = metadata?.optString("full_name") ?: email
                        val avatarUrl = metadata?.optString("avatar_url") ?: ""

                        tvUsername.text = email
                        if (avatarUrl.isNotEmpty()) {
                            Glide.with(this)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_profile)
                                .into(profileImage)
                        } else {
                            profileImage.setImageResource(R.drawable.ic_profile)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user details", e)
                        Toast.makeText(this, "Error reading profile data", Toast.LENGTH_SHORT).show()
                    }
                },
                { error ->
                    val code = error.networkResponse?.statusCode
                    val body = error.networkResponse?.data?.let { String(it) }
                    Log.e(TAG, "Error fetching user: Code=$code, Body=$body", error)
                    Toast.makeText(this, "Failed to load user profile", Toast.LENGTH_SHORT).show()
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> {
                    return hashMapOf(
                        "apikey" to SUPABASE_API_KEY,
                        "Authorization" to "Bearer $accessToken",
                        "Accept" to "application/json"
                    )
                }
            }

            Volley.newRequestQueue(this).add(request)
        }
    }

    private fun logout() {
        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)

        // Clear SharedPreferences
        prefs.edit().clear().apply()

        if (loginMethod == "google") {
            googleSignInClient.signOut().addOnCompleteListener {
                Log.d(TAG, "Google Sign-Out successful")
                navigateToLogin()
            }
        } else {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
