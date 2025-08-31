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
import com.developer_rahul.docunova.DriveServiceHelper
import com.developer_rahul.docunova.LoginActivity
import com.developer_rahul.docunova.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.CharacterIterator
import java.text.StringCharacterIterator

class SettingFragment : Fragment() {

    companion object {
        private const val TAG = "SettingFragment"
        private const val SUPABASE_URL = "https://biudcywgygbacfxfpuva.supabase.co"
        private const val SUPABASE_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJpdWRjeXdneWdiYWNmeGZwdXZhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM1Mzg1NDQsImV4cCI6MjA2OTExNDU0NH0.enJZKTQjKtOyB6VU5pYo_vf4p7ZLv2ayYuyqBWvLqbA"
    }

    private lateinit var tvUsername: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvDriveFilesCount: TextView
    private lateinit var tvDriveStorage: TextView
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
        tvUserEmail = view.findViewById(R.id.emailTag)
        tvDriveFilesCount = view.findViewById(R.id.tv_file_count)
        tvDriveStorage = view.findViewById(R.id.tv_drive_size)
        profileImage = view.findViewById(R.id.profileImage)
        btnLogOut = view.findViewById(R.id.signOutBtn)

        // Fetch and display user details
        fetchUserDetails()

        // Load Google Drive information
        loadDriveInfo()

        btnLogOut.setOnClickListener {
            logout()
        }
    }

    private fun loadDriveInfo() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                    val files = DriveServiceHelper.listFilesFromAppFolder(drive)

                    var totalSize: Long = 0
                    for (file in files) {
                        totalSize += file.size
                    }

                    val sizeFormatted = formatFileSize(totalSize)

                    withContext(Dispatchers.Main) {
                        tvDriveFilesCount.text = files.size.toString()
                        tvDriveStorage.text = sizeFormatted
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                            tvDriveFilesCount.text = "Sign in required"
                            tvDriveStorage.text = "Authentication expired"
                        } else {
                            tvDriveFilesCount.text = "Error loading"
                            tvDriveStorage.text = "Failed to load"
                            Log.e(TAG, "Failed to load Drive info: ${e.message}")
                        }
                    }
                }
            }
        } else {
            tvDriveFilesCount.text = "Not connected"
            tvDriveStorage.text = "Sign in to Google Drive"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        val units = arrayOf("KB", "MB", "GB", "TB")
        var unitIndex = 0

        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }

        return String.format("%.1f %s", value, units[unitIndex])
    }

    private fun fetchUserDetails() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)
        val accessToken = prefs.getString("access_token", null)

        if (loginMethod.isNullOrEmpty()) {
            tvUsername.text = "Guest User"
            tvUserEmail.text = "Not logged in"
            profileImage.setImageResource(R.drawable.ic_profile)
            return
        }

        if (loginMethod == "google") {
            val fullName = prefs.getString("google_full_name", null)
            val email = prefs.getString("google_email", null)
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
            return
        }

        if (loginMethod == "email") {
            if (accessToken.isNullOrEmpty()) {
                tvUsername.text = "Session Expired"
                tvUserEmail.text = "Please log in again"
                profileImage.setImageResource(R.drawable.ic_profile)
                return
            }

            val url = "$SUPABASE_URL/auth/v1/user"
            val request = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                { response ->
                    try {
                        val email = response.optString("email", "No Email")
                        val metadata = response.optJSONObject("user_metadata")
                        val fullName = metadata?.optString("full_name") ?: email
                        val avatarUrl = metadata?.optString("avatar_url") ?: ""

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
                    }
                },
                { error ->
                    Log.e(TAG, "Error fetching user details", error)
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