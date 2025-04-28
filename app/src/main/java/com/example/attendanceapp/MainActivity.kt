package com.example.attendanceapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAdmin = findViewById<Button>(R.id.btnAdmin)
        val btnEmployee = findViewById<Button>(R.id.btnEmployee)

        btnAdmin.setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }

        btnEmployee.setOnClickListener {
            startActivity(Intent(this, EmployeeLoginActivity::class.java))
        }
    }
}
