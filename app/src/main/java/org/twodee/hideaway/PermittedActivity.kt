package org.twodee.rattler

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity

open class PermittedActivity : AppCompatActivity() {
  private val requests: MutableMap<Int, PermissionsRequest> = mutableMapOf()

  fun requestPermissions(permissions: Array<String>, requestId: Int, onSuccess: () -> Unit, onFailure: () -> Unit) {
    // The WRITE_SETTINGS permission must be granted using a different
    // scheme. Frustrating.
    val hasWriteSettings = permissions.contains(android.Manifest.permission.WRITE_SETTINGS)
    val needsWriteSettings = hasWriteSettings && !Settings.System.canWrite(this)
    val remaining = if (hasWriteSettings) {
      permissions.filter { it != android.Manifest.permission.WRITE_SETTINGS }
    } else {
      permissions.toList()
    }

    // If we're on early Android, runtime requests are not needed,
    // so we assume permission has already been granted by listing
    // the permissions in the manifest.
    val ungranted = when {
      Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> listOf()
      else -> remaining.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    }

    // If all but the WRITE_SETTINGS permission has been granted...
    if (ungranted.isEmpty()) {
      if (needsWriteSettings) {
        requests[requestId] = PermissionsRequest(needsWriteSettings, onSuccess, onFailure)
        promptForWriteSettings(requestId)
      } else {
        onSuccess()
      }
    }

    // Otherwise, request the ungranted permissions.
    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requests[requestId] = PermissionsRequest(needsWriteSettings, onSuccess, onFailure)
      ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), requestId)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    // If all the requested permissions have been granted, we're ready to
    // trigger success! Right? Maybe. We might still need WRITE_SETTINGS.
    requests[requestCode]?.let { request ->
      if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
        if (request.needsWriteSettings) {
          promptForWriteSettings(requestCode)
        } else {
          request.onSuccess.invoke()
          requests.remove(requestCode)
        }
      } else {
        request.onFailure.invoke()
        requests.remove(requestCode)
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    // Our last hurdle... Did we get WRITE_SETTINGS?
    requests[requestCode]?.let { request ->
      if (Settings.System.canWrite(this)) {
        request.onSuccess.invoke()
      } else {
        request.onFailure.invoke()
      }
      requests.remove(requestCode)
    }
  }

  private fun promptForWriteSettings(requestId: Int) {
    val builder = AlertDialog.Builder(this)
    builder.setMessage("This operation requires the ability to modify system settings. Please grant this permission on the next screen.")
    builder.setPositiveButton("Okay") { _, _ ->
      startActivityForResult(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")), requestId)
    }
    builder.show()
  }

  class PermissionsRequest(val needsWriteSettings: Boolean, val onSuccess: () -> Unit, val onFailure: () -> Unit)
}
