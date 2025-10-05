package com.example.taller2_movil

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnContacts: ImageButton = findViewById(R.id.btnContacts)
        val btnCamera: ImageButton = findViewById(R.id.btnCamera)
        val btnMap: ImageButton = findViewById(R.id.btnMap)

        btnContacts.setOnClickListener {
            val intent = Intent(this, ContactosActivity::class.java)
            startActivity(intent)
        }

        btnCamera.setOnClickListener {
            startActivity(Intent(this, GaleriaCamaraActivity::class.java))
        }

        btnMap.setOnClickListener {
            val intent = Intent(this,  MapaActivity::class.java)
            startActivity(intent)
        }

    }
}
