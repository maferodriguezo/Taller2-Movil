package com.example.taller2_movil

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
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
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MapaActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var mapView: MapView
    private lateinit var editTextDireccion: EditText

    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var marcador: Marker
    private lateinit var marcadorDireccion: Marker // marcador por long click

    private var ultimaUbicacion: Location? = null
    private val jsonArray = JSONArray()
    private val fileName = "ubicaciones.json"
    private val REQUEST_LOCATION = 1

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var isDarkMode = false

    private val lightTileSource = TileSourceFactory.MAPNIK
    private val darkTileSource = XYTileSource(
        "CartoDB Dark", 0, 19, 256, ".png",
        arrayOf("https://a.basemaps.cartocdn.com/dark_all/"),
        "© OpenStreetMap contributors"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_mapa)

        mapView = findViewById(R.id.mapView)
        editTextDireccion = findViewById(R.id.editTextDireccion)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        mapView.setTileSource(lightTileSource)
        mapView.setMultiTouchControls(true)

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        mapView.overlays.add(myLocationOverlay)
        myLocationOverlay.runOnFirstFix {
            val p = myLocationOverlay.myLocation ?: return@runOnFirstFix
            val loc = Location("osmdroid").apply {
                latitude = p.latitude
                longitude = p.longitude
                time = System.currentTimeMillis()
            }
            runOnUiThread { actualizarMarcador(loc) }
        }

        marcador = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Ubicación actual"
            setOnMarkerClickListener { m, _ -> m.showInfoWindow(); true }
        }
        marcadorDireccion = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Dirección seleccionada"
            setOnMarkerClickListener { m, _ -> m.showInfoWindow(); true }
        }
        mapView.overlays.add(marcador)
        mapView.overlays.add(marcadorDireccion)

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p == null) return false
                crearMarcadorEn(p)
                enfocar(p, 18.0)
                return true
            }
        }
        mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))

        editTextDireccion.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: return
                if (q.length > 3) buscarDireccion(q)
            }
        })

        cargarJsonPrevio()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION
            )
        } else {
            iniciarUbicacion()
        }
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        myLocationOverlay.disableMyLocation()
        myLocationOverlay.disableFollowLocation()
    }

    private fun enfocar(p: GeoPoint, zoom: Double = 18.0) {
        mapView.controller.setZoom(zoom)
        mapView.controller.animateTo(p)
        mapView.invalidate()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            val nuevoModoOscuro = lux < 10f
            if (nuevoModoOscuro != isDarkMode) {
                isDarkMode = nuevoModoOscuro
                runOnUiThread { cambiarEstiloMapa(isDarkMode) }
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    private fun cambiarEstiloMapa(oscuro: Boolean) {
        mapView.setTileSource(if (oscuro) darkTileSource else lightTileSource)
        mapView.invalidate()
    }

    @Suppress("MissingPermission")
    private fun iniciarUbicacion() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val netOn = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsOn && !netOn) {
            Toast.makeText(this, "Activa la ubicación (GPS o Red) para centrar el mapa.", Toast.LENGTH_LONG).show()
        }

        val last = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null } }
            .maxByOrNull { it.time }
        if (last != null) actualizarMarcador(last)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lm.getCurrentLocation(LocationManager.GPS_PROVIDER, null, mainExecutor) { it?.let(::actualizarMarcador) }
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, mainExecutor) { it?.let(::actualizarMarcador) }
        }

        if (gpsOn) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f) { loc -> actualizarMarcador(loc) }
        if (netOn) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 10f) { loc -> actualizarMarcador(loc) }
    }

    private fun actualizarMarcador(location: Location) {
        val p = GeoPoint(location.latitude, location.longitude)
        marcador.position = p
        mapView.controller.setZoom(18.0)
        mapView.controller.setCenter(p) // centrar siempre al actualizar tu ubicación
        mapView.invalidate()

        if (ultimaUbicacion == null || location.distanceTo(ultimaUbicacion!!) > 30f) {
            ultimaUbicacion = location
            guardarRegistro(location)
        } else {
            ultimaUbicacion = location
        }
    }

    private fun cargarJsonPrevio() {
        try {
            val f = getFileStreamPath(fileName)
            if (f != null && f.exists()) {
                val texto = openFileInput(fileName).bufferedReader().use { it.readText() }
                val previo = JSONArray(texto)
                for (i in 0 until previo.length()) jsonArray.put(previo.getJSONObject(i))
            }
        } catch (_: Exception) { /* arranca vacío */ }
    }

    private fun escribirJsonInterno() {
        openFileOutput(fileName, MODE_PRIVATE).use { fos ->
            fos.write(jsonArray.toString(4).toByteArray())
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
        escribirJsonInterno()
    }

    private fun buscarDireccion(texto: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val results = geocoder.getFromLocationName(texto, 1)
            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                val p = GeoPoint(addr.latitude, addr.longitude)
                val titulo = listOfNotNull(
                    addr.thoroughfare, addr.subThoroughfare, addr.locality, addr.adminArea
                ).joinToString(" ").ifBlank { addr.getAddressLine(0) ?: texto }

                marcadorDireccion.position = p
                marcadorDireccion.title = titulo
                if (!mapView.overlays.contains(marcadorDireccion)) mapView.overlays.add(marcadorDireccion)
                marcadorDireccion.showInfoWindow()
                enfocar(p, 18.0) // <-- enfoca y hace zoom
            }
        } catch (_: IOException) { /* UX suave */ }
    }

    private fun crearMarcadorEn(p: GeoPoint) {
        val geocoder = Geocoder(this, Locale.getDefault())
        var titulo = "${"%.6f".format(p.latitude)}, ${"%.6f".format(p.longitude)}"
        try {
            val results = geocoder.getFromLocation(p.latitude, p.longitude, 1)
            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                val compuesto = listOfNotNull(
                    addr.thoroughfare, addr.subThoroughfare, addr.locality, addr.adminArea
                ).joinToString(" ")
                titulo = if (compuesto.isNotBlank()) compuesto else (addr.getAddressLine(0) ?: titulo)
            }
        } catch (_: IOException) { /* deja coords */ }

        marcadorDireccion.position = p
        marcadorDireccion.title = titulo
        if (!mapView.overlays.contains(marcadorDireccion)) mapView.overlays.add(marcadorDireccion)
        marcadorDireccion.showInfoWindow()
        mostrarDistanciaA(p)
    }

    private fun mostrarDistanciaA(p: GeoPoint) {
        val actual = ultimaUbicacion
        if (actual == null) {
            Toast.makeText(this, "Aún no tengo tu ubicación actual.", Toast.LENGTH_SHORT).show()
            return
        }
        val out = FloatArray(1)
        Location.distanceBetween(
            actual.latitude, actual.longitude,
            p.latitude, p.longitude,
            out
        )
        val d = out[0]
        val texto = if (d >= 1000) "Distancia: %.2f km".format(d / 1000f)
        else "Distancia: %.0f m".format(d)
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarUbicacion()
            myLocationOverlay.enableMyLocation()
            myLocationOverlay.enableFollowLocation()
        } else {
            Toast.makeText(this, "Se requiere el permiso de ubicación.", Toast.LENGTH_SHORT).show()
        }
    }
}