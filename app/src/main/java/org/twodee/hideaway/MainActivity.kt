package org.twodee.hideaway

import android.Manifest
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng

class MainActivity : PermittedActivity(), OnMapReadyCallback {
  private lateinit var map: GoogleMap
  private var hiddenMessage: HiddenMessage? = null
  private var unlockMenuItem: MenuItem? = null

  // Task

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    loadHiddenMessage()

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
  private fun syncUnlock() {
  }

  // Task
  private fun promptForGPS() {
  }

  // Task
  override fun onMapReady(googleMap: GoogleMap) {
  }

  // Task
  private fun syncCircles() {
  }

  // Task
  private fun hideMessage(message: String, location: LatLng) {
  }

  // Task
  private fun saveHiddenMessage() {
  }

  // Task
  private fun promptForMessage(location: LatLng) {
  }

  // Task
  private fun clearHiddenMessage() {
  }

  // Task
  private fun loadHiddenMessage() {
  }

  // Task
  private fun unlockMessageSuccess() {
  }

  // Task
  private fun unlockMessageFailure(distance: Double) {
  }

  // Task
  private fun attemptUnlock(guessLocation: LatLng) {
  }

  // Task
  private fun queryLocationForUnlock() {
  }
}
