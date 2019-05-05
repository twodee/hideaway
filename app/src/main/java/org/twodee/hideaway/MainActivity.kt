package org.twodee.hideaway

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.getSystemService
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.maps.android.SphericalUtil
import org.twodee.rattler.PermittedActivity
import java.io.FileNotFoundException

class MainActivity : PermittedActivity(), OnMapReadyCallback {

  private lateinit var map: GoogleMap
  private var hidden: Hidden? = null
  private var unlockMenuItem: MenuItem? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)
    loadHidden()

    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    requestPermissions(permissions, 100, {
      promptForGPS()
    }, {
      Toast.makeText(this, "GPS not permitted. Unable to unlock hidden messages.", Toast.LENGTH_LONG).show()
    })
  }

  private fun promptForGPS() {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      AlertDialog.Builder(this).apply {
        setMessage("GPS is not enabled on your device. Enable it in the location settings to unlock hidden messages.")
        setPositiveButton("Settings") { _, _ ->
          startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        setNegativeButton("Cancel") { _, _ -> }
        show()
      }
    }
  }

  override fun onMapReady(googleMap: GoogleMap) {
    map = googleMap
    map.setOnMapLongClickListener {
      promptForMessage(it)
    }

    syncCircles()
  }

  @SuppressLint("MissingPermission")
  private fun syncCircles() {
    map.clear()
    if (hasLocationPermissions) {
      map.setMyLocationEnabled(true)
    }

    hidden?.let {
      it.guesses.forEach { guess ->
        val distance = SphericalUtil.computeDistanceBetween(it.location, guess)
        map.addCircle(CircleOptions().apply {
          center(guess)
          radius(distance)
          strokeColor(Color.RED)
        })
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_actionbar, menu)
    unlockMenuItem = menu.findItem(R.id.unlockButton)
    syncUnlock()
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
    R.id.unlockButton -> {
      attemptUnlock()
      true
    }
    R.id.clearHiddenButton -> {
      clearHidden()
      true
    }
    R.id.clearGuessesButton -> {
      hidden?.let {
        it.guesses.clear()
      }
      syncCircles()
      true
    }
    else -> super.onOptionsItemSelected(item)
  }

  private fun clearHidden() {
    hidden = null
    map.clear()
    deleteFile("hidden.json")
    syncUnlock()
  }

  private fun promptForMessage(location: LatLng) {
    AlertDialog.Builder(this).apply {
      setTitle("What's your message?")
      val view = layoutInflater.inflate(R.layout.message_view, null)
      setView(view)
      setPositiveButton("OK") { _, _ ->
        val messageEditor: EditText = view.findViewById(R.id.messageEditor)
        hidden = Hidden(messageEditor.text.toString(), location)
        saveHidden()
        syncUnlock()
      }
      show()
    }
  }

  private fun syncUnlock() {
    unlockMenuItem?.let {
      it.setVisible(hidden != null)
    }
  }

  private fun saveHidden() {
    val json = Gson().toJson(hidden)
    openFileOutput("hidden.json", Context.MODE_PRIVATE).use { writer ->
      writer.write(json.toByteArray())
    }
  }

  private fun loadHidden() {
    try {
      openFileInput("hidden.json").use { reader ->
        val json = String(reader.readBytes())
        hidden = Gson().fromJson(json, Hidden::class.java)
      }
    } catch (e: FileNotFoundException) {
    }
    syncUnlock()
  }

  @SuppressLint("MissingPermission")
  private fun attemptUnlock() {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = object : LocationListener {
      override fun onLocationChanged(location: Location) {
        hidden?.let {
          val currentLocation = LatLng(location.latitude, location.longitude)
          it.guesses.add(currentLocation)
          syncCircles()
          unlockMessage(currentLocation)
        }
        locationManager.removeUpdates(this)
      }

      override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
      override fun onProviderEnabled(p0: String?) {}
      override fun onProviderDisabled(p0: String?) {}
    }

    if (hasLocationPermissions) {
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, listener)
    }
  }

  private fun unlockMessage(guessLocation: LatLng) {
    hidden?.let {
      val distance = SphericalUtil.computeDistanceBetween(guessLocation, it.location)
      if (distance < 100.0) {
        AlertDialog.Builder(this).apply {
          setTitle("You unlocked a message!")
          setMessage(it.message)
          setPositiveButton("Clear") { _, _ ->
            clearHidden()
          }
          setNegativeButton("Leave") { _, _ -> }
          show()
        }
      } else {
        AlertDialog.Builder(this).apply {
          setTitle("Too far away")
          setMessage(String.format("You are %.1fk away from the hidden message.", distance))
          setPositiveButton("OK") { _, _ -> }
          show()
        }
      }
    }
  }

  override fun onStop() {
    super.onStop()
    hidden?.let {
      saveHidden()
    }
  }

  private val hasLocationPermissions
    get() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
