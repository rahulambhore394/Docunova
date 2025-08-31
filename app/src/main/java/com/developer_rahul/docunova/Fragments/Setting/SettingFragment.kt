package com.developer_rahul.docunova.Fragments.Setting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.LoginActivity
import com.developer_rahul.docunova.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class SettingFragment : Fragment() {

    companion object {
        private const val TAG = "SettingFragment"
        private const val SUPABASE_URL = "https://biudcywgygbacfxfpuva.supabase.co"
        private const val SUPABASE_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJpdWRjeXdneWdiYWNmeGZwdXZhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM1Mzg1NDQsImV4cCI6MjA2OTExNDU0NH0.enJZKTQjKtOyB6VU5pYo_vf4p7ZLv2ayYuyqBWvLqbA"
    }

    private lateinit var tvUsername: TextView
    private lateinit var tvUserEmail: TextView // Added for email
    private lateinit var profileImage: ImageView
    private lateinit var btnLogOut: Button
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setting, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize GoogleSignInClient
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        // Initialize views
        tvUsername = view.findViewById(R.id.userName)
        tvUserEmail = view.findViewById(R.id.emailTag) // Initialize email TextView
        profileImage = view.findViewById(R.id.profileImage)
        btnLogOut = view.findViewById(R.id.signOutBtn)

        // Fetch and display user details
        fetchUserDetails()

        btnLogOut.setOnClickListener {
            logout()
        }
    }

    private fun fetchUserDetails() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)
        val accessToken = prefs.getString("access_token", null)

        if (loginMethod.isNullOrEmpty()) {
            tvUsername.text = "Guest User"
            tvUserEmail.text = "Not logged in"
            profileImage.setImageResource(R.drawable.ic_profile)
            Toast.makeText(requireContext(), "Please log in first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (loginMethod == "google") {
            val fullName = prefs.getString("google_full_name", null)
            val email = prefs.getString("google_email", null) // Get email from prefs
            val avatarUrl = prefs.getString("google_avatar_url", null)

            tvUsername.text = fullName ?: "Google User"
            tvUserEmail.text = email ?: "No email available"

            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(requireContext())
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
                tvUserEmail.text = "Please log in again"
                profileImage.setImageResource(R.drawable.ic_profile)
                Toast.makeText(requireContext(), "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
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

                        // Display both name and email
                        tvUsername.text = fullName
                        tvUserEmail.text = email

                        if (avatarUrl.isNotEmpty()) {
                            Glide.with(requireContext())
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_profile)
                                .into(profileImage)
                        } else {
                            profileImage.setImageResource(R.drawable.ic_profile)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user details", e)
                        Toast.makeText(requireContext(), "Error reading profile data", Toast.LENGTH_SHORT).show()
                    }
                },
                { error ->
                    val code = error.networkResponse?.statusCode
                    val body = error.networkResponse?.data?.let { String(it) }
                    Log.e(TAG, "Error fetching user: Code=$code, Body=$body", error)
                    Toast.makeText(requireContext(), "Failed to load user profile", Toast.LENGTH_SHORT).show()
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

            Volley.newRequestQueue(requireContext()).add(request)
        }
    }

    private fun logout() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
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
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}