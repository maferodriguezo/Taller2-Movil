package com.example.taller2_movil

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

class ContactosActivity : AppCompatActivity() {

    private val REQUEST_CONTACTS = 1
    private lateinit var listaContactos: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contactos)

        listaContactos = findViewById(R.id.listaContactos)

        // Verifica si ya tiene permiso
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {

            // Lo solicita si no lo tiene
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQUEST_CONTACTS
            )
        } else {
            // Carga los contactos desde el JSON
            cargarContactosDesdeJson()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CONTACTS &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cargarContactosDesdeJson()
        }
    }

    // Lee los contactos del archivo JSON
    private fun cargarContactosDesdeJson() {
        val contactos = mutableListOf<String>()

        try {
            val inputStream = assets.open("contactos.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonText = reader.use { it.readText() }
            val jsonArray = JSONArray(jsonText)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getInt("id")
                val nombre = obj.getString("nombre")
                contactos.add("$id    $nombre")
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, contactos)
            listaContactos.adapter = adapter

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
