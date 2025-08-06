package com.developer_rahul.docunova

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    private lateinit var emailET: EditText
    private lateinit var passwordET: EditText
    private lateinit var confirmPasswordET: EditText
    private lateinit var fullNameET: EditText
    private lateinit var registerBtn: Button
    private lateinit var have_acount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        fullNameET = findViewById(R.id.etFullName)
        emailET = findViewById(R.id.etEmail)
        passwordET = findViewById(R.id.etPassword)
        confirmPasswordET = findViewById(R.id.etConfirmPassword)
        registerBtn = findViewById(R.id.btnCreateAccount)
        have_acount = findViewById(R.id.already_have)

        have_acount.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        registerBtn.setOnClickListener {
            if (validateInputs()) {
                val name = fullNameET.text.toString()
                val email = emailET.text.toString()
                val password = passwordET.text.toString()
                saveUserToTable(name, email, password)
            }
        }
    }

    private fun validateInputs(): Boolean {
        val name = fullNameET.text.toString().trim()
        val email = emailET.text.toString().trim()
        val password = passwordET.text.toString()
        val confirm = confirmPasswordET.text.toString()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password != confirm) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun saveUserToTable(name: String, email: String, password: String) {
        val user = User(name, email, password)
        val service = RetrofitClient.instance.create(SupabaseService::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Response<Void> = service.createUser(user)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(applicationContext, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                        val i = Intent(applicationContext, LoginActivity::class.java)
                        startActivity(i);
                        finish()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("SupabaseInsert", "Failed: $errorBody")
                        Toast.makeText(applicationContext, "Error: $errorBody", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseInsert", "Exception: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Exception: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
