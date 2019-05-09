package org.twodee.hideaway

import com.google.android.gms.maps.model.LatLng

// Task
data class HiddenMessage(val message: String, val location: LatLng) {
  val guesses = mutableListOf<LatLng>()
  override fun toString() = "$message | $location | $guesses"
}