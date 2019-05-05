package org.twodee.hideaway

import com.google.android.gms.maps.model.LatLng

data class Hidden(val message: String, val location: LatLng) {
  val guesses = mutableListOf<LatLng>()

  override fun toString() = "$message | $location | $guesses"
}