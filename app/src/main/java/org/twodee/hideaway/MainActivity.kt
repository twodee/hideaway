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
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.maps.android.SphericalUtil
import java.io.FileNotFoundException

class MainActivity : PermittedActivity(), OnMapReadyCallback {

  private lateinit var map: GoogleMap
  private var hiddenMessage: HiddenMessage? = null
  private var unlockMenuItem: MenuItem? = null

  // Task
  private val hasLocationPermissions
    get() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    loadHiddenMessage()

    // Task
    val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)

    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    requestPermissions(permissions, 100, {
      promptForGPS()
    }, {
      Toast.makeText(this, "GPS not permitted. You will not be able to unlock hiddenMessage messages.", Toast.LENGTH_LONG).show()
    })
  }

  override fun onStop() {
    super.onStop()
    hiddenMessage?.let {
      saveHiddenMessage()
    }
  }

  // Task
  private fun syncUnlock() {
    unlockMenuItem?.isVisible = hiddenMessage != null
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_actionbar, menu)
    unlockMenuItem = menu.findItem(R.id.unlockButton)
    syncUnlock()
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
    R.id.unlockButton -> {
      queryLocationForUnlock()
      true
    }
    R.id.clearHiddenButton -> {
      clearHiddenMessage()
      true
    }
    R.id.clearGuessesButton -> {
      hiddenMessage?.let {
        it.guesses.clear()
      }
      syncCircles()
      true
    }
    else -> super.onOptionsItemSelected(item)
  }

  // Task
  private fun promptForGPS() {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      AlertDialog.Builder(this).apply {
        setMessage("GPS is not enabled on your device. Enable it in the location settings.")
        setPositiveButton("Settings") { _, _ ->
          startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        setNegativeButton("Cancel") { _, _ -> }
        show()
      }
    }
  }

  // Task
  @SuppressLint("MissingPermission")
  override fun onMapReady(googleMap: GoogleMap) {
    map = googleMap

    map.setOnMapLongClickListener {
      promptForMessage(it)
    }

    if (hasLocationPermissions) {
      map.setMyLocationEnabled(true)
    }

    syncCircles()
  }

  // Task
  private fun syncCircles() {
    map.clear()
    hiddenMessage?.let {
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

  // Task
  private fun hideMessage(message: String, location: LatLng) {
    hiddenMessage = HiddenMessage(message, location)
    saveHiddenMessage()
    syncUnlock()
    syncCircles()
  }

  // Task
  private fun saveHiddenMessage() {
    val json = Gson().toJson(hiddenMessage)
    openFileOutput("hidden.json", Context.MODE_PRIVATE).use { writer ->
      writer.write(json.toByteArray())
    }
  }

  // Task
  private fun promptForMessage(location: LatLng) {
    AlertDialog.Builder(this).apply {
      setTitle("What message will you hide here?")
      val view = layoutInflater.inflate(R.layout.message_view, null)
      setView(view)
      setPositiveButton("Hide") { _, _ ->
        val messageEditor: EditText = view.findViewById(R.id.messageEditor)
        hideMessage(messageEditor.text.toString(), location)
      }
      show()
    }
  }

  // Task
  private fun clearHiddenMessage() {
    hiddenMessage = null
    map.clear()
    deleteFile("hidden.json")
    syncUnlock()
  }

  // Task
  private fun loadHiddenMessage() {
    try {
      openFileInput("hidden.json").use { reader ->
        val json = String(reader.readBytes())
        hiddenMessage = Gson().fromJson(json, HiddenMessage::class.java)
      }
    } catch (e: FileNotFoundException) {
    }
    syncUnlock()
  }

  // Task
  private fun unlockMessageSuccess() {
    hiddenMessage?.let {
      AlertDialog.Builder(this).apply {
        setTitle("You unlocked a message!")
        setMessage(it.message)
        setPositiveButton("Clear") { _, _ ->
          clearHiddenMessage()
        }
        setNegativeButton("Leave") { _, _ -> }
        show()
      }
    }
  }

  // Task
  private fun unlockMessageFailure(distance: Double) {
    AlertDialog.Builder(this).apply {
      setTitle("Too far away")
      setMessage(String.format("You are %.1fk away from the hiddenMessage message.", distance))
      setPositiveButton("OK") { _, _ -> }
      show()
    }
  }

  // Task
  private fun attemptUnlock(guessLocation: LatLng) {
    hiddenMessage?.let {
      it.guesses.add(guessLocation)
      syncCircles()
      val distance = SphericalUtil.computeDistanceBetween(guessLocation, it.location)
      if (distance < 100.0) {
        unlockMessageSuccess()
      } else {
        unlockMessageFailure(distance)
      }
    }
  }

  // Task
  @SuppressLint("MissingPermission")
  private fun queryLocationForUnlock() {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val listener = object : LocationListener {
      override fun onLocationChanged(location: Location) {
        hiddenMessage?.let {
          attemptUnlock(LatLng(location.latitude, location.longitude))
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
}
