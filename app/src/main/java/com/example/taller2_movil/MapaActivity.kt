package com.example.taller2_movil

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import android.location.Geocoder
import java.io.IOException

class MapaActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var mapView: MapView
    private lateinit var marcador: Marker
    private lateinit var marcadorDireccion: Marker
    private lateinit var editTextDireccion: EditText
    private var ultimaUbicacion: Location? = null
    private val jsonArray = JSONArray()
    private val REQUEST_LOCATION = 1

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var isDarkMode = false

    // Fuentes de tiles OSM para modo claro y oscuro
    private val lightTileSource = TileSourceFactory.MAPNIK // OSM estándar (claro)
    private val darkTileSource = XYTileSource(
        "CartoDB Dark",
        0,
        19,
        256,
        ".png",
        arrayOf("https://a.basemaps.cartocdn.com/dark_all/"),
        "© OpenStreetMap contributors"
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configurar OSM
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_mapa)

        mapView = findViewById(R.id.mapView)
        editTextDireccion = findViewById(R.id.editTextDireccion)

        // Configurar sensor de luminosidad
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        mapView.setTileSource(lightTileSource)
        mapView.setMultiTouchControls(true)

        // Marcador para ubicación actual
        marcador = Marker(mapView)
        marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marcador.title = "Ubicación actual"

        // Marcador para dirección buscada
        marcadorDireccion = Marker(mapView)
        marcadorDireccion.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marcadorDireccion.title = "Dirección buscada"

        mapView.overlays.add(marcador)
        mapView.overlays.add(marcadorDireccion)

        // Configurar listener para el campo de dirección
        editTextDireccion.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.length > 3) {
                    buscarDireccion(s.toString())
                }
            }
        })

        // Permisos de ubicación
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
        } else {
            iniciarUbicacion()
        }
    }

    override fun onResume() {
        super.onResume()
        // Registrar sensor de luminosidad si existe
        lightSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        // Desregistrar sensor
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val luminosidad = event.values[0]
            // Cambiar estilo del mapa basado en la luminosidad
            val nuevoModoOscuro = luminosidad < 10 // Umbral de 10 lux para modo oscuro

            if (nuevoModoOscuro != isDarkMode) {
                isDarkMode = nuevoModoOscuro
                runOnUiThread {
                    cambiarEstiloMapa(isDarkMode)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun cambiarEstiloMapa(oscuro: Boolean) {
        mapView.setTileSource(if (oscuro) darkTileSource else lightTileSource)
        mapView.invalidate()
    }

    private fun iniciarUbicacion() {
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        locationManager.requestLocationUpdates(
            android.location.LocationManager.GPS_PROVIDER,
            5000L, // cada 5 segundos
            10f // cada 10 metros
        ) { location ->
            actualizarMarcador(location)
        }
    }

    private fun actualizarMarcador(location: Location) {
        val punto = GeoPoint(location.latitude, location.longitude)
        marcador.position = punto
        mapView.controller.setZoom(18.0)
        mapView.controller.setCenter(punto)
        mapView.invalidate()

        // Solo guardar si hay movimiento mayor a 30 metros
        if (ultimaUbicacion == null || location.distanceTo(ultimaUbicacion!!) > 30) {
            ultimaUbicacion = location
            guardarRegistro(location)
        }
    }

    private fun guardarRegistro(location: Location) {
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val registro = JSONObject().apply {
            put("latitud", location.latitude)
            put("longitud", location.longitude)
            put("fecha", fecha)
        }
        jsonArray.put(registro)

        val archivo = File(getExternalFilesDir(null), "ubicaciones.json")
        FileWriter(archivo).use {
            it.write(jsonArray.toString(4))
        }
    }

    private fun buscarDireccion(direccion: String) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(direccion, 1)

            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val punto = GeoPoint(address.latitude, address.longitude)

                marcadorDireccion.position = punto
                marcadorDireccion.title = direccion
                marcadorDireccion.snippet = address.getAddressLine(0)

                // Mover cámara al punto encontrado
                mapView.controller.setCenter(punto)
                mapView.controller.setZoom(17.0)
                mapView.invalidate()

                Toast.makeText(this, "Dirección encontrada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error buscando dirección", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarUbicacion()
        }
    }
}