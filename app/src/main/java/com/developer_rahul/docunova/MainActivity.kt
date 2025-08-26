package com.developer_rahul.docunova

import android.content.Context
import com.developer_rahul.docunova.Fragments.Files.FilesFragment
import com.developer_rahul.docunova.Fragments.Setting.SettingFragment
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.Fragment
import com.developer_rahul.docunova.Fragments.Home.HomeFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Retrieve stored email from SharedPreferences
        val sharedPref = this.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        val userEmail = sharedPref.getString("email", "User")

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Default Fragment
        loadFragment(HomeFragment())

        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_files -> loadFragment(FilesFragment())
                R.id.nav_settings -> loadFragment(SettingFragment())
            }
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
