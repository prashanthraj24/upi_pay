package com.drenther.upi_pay

import androidx.annotation.NonNull
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
// import androidx.activity.result.registerForActivityResult
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.result.ActivityResult
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
// import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import java.io.ByteArrayOutputStream
import io.flutter.embedding.engine.plugins.FlutterPlugin

/** UpiPayPlugin */
class UpiPayPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, AppCompatActivity() {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var activity: Activity? = null
  private var result: Result? = null
  private var requestCodeNumber = 201119
  private var getTransactionResult: ActivityResultLauncher<Intent>? = null

  var hasResponded = false

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    Log.d("upi_pay", "attached to engine")
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "upi_pay")
    channel.setMethodCallHandler(this)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    Log.d("upi_pay", "detached from engine")
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    Log.d("upi_pay", "attached to activity")
    val activity = binding.activity
    this.activity = activity
    getTransactionResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> parseActivityResult(result) }
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    Log.d("upi_pay", "reattached to activity for config changes")
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    Log.d("upi_pay", "detached from activity for config changes")
    onDetachedFromActivity()
  }

  override fun onDetachedFromActivity() {
    Log.d("upi_pay", "detached from activity")
    activity = null
    getTransactionResult = null
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    hasResponded = false
    this.result = result
    when (call.method) {
      "initiateTransaction" -> this.initiateTransaction(call)
      "getInstalledUpiApps" -> this.getInstalledUpiApps()
      else -> result.notImplemented()
    }
  }

  private fun initiateTransaction(call: MethodCall) {
    val app: String? = call.argument("app")
    val pa: String? = call.argument("pa")
    val pn: String? = call.argument("pn")
    val mc: String? = call.argument("mc")
    val tr: String? = call.argument("tr")
    val tn: String? = call.argument("tn")
    val am: String? = call.argument("am")
    val cu: String? = call.argument("cu")
    val url: String? = call.argument("url")
    try {
      /*
       * Some UPI apps extract incorrect format VPA due to url encoding of `pa` parameter.
       * For example, the VPA 'abc@upi' gets url encoded as 'abc%40upi' and is extracted as
       * 'abc 40upi' by these apps. The URI building logic is changed to avoid URL encoding
       * of the value of 'pa' parameter. - Reetesh
      */
      var uriStr: String? = "upi://pay?pa=" + pa +
              "&pn=" + Uri.encode(pn) +
              "&tr=" + Uri.encode(tr) +
              "&am=" + Uri.encode(am) +
              "&cu=" + Uri.encode(cu)
      if(url != null) {
        uriStr += ("&url=" + Uri.encode(url))
      }
      if(mc != null) {
        uriStr += ("&mc=" + Uri.encode(mc))
      }
      if(tn != null) {
        uriStr += ("&tn=" + Uri.encode(tn))
      }
      uriStr += "&mode=00" // &orgid=000000"
      val uri = Uri.parse(uriStr)
      Log.d("upi_pay", "initiateTransaction URI: " + uri.toString())
      Log.d("upi_pay", "initiateTransaction on app: " + app)
      val intent = Intent(Intent.ACTION_VIEW, uri)
      intent.setPackage(app)
      if (intent.resolveActivity(activity?.packageManager!!) == null) {
        this.success("activity_unavailable")
        return
      }
      getTransactionResult?.launch(intent)
      // activity?.startActivityForResult(intent, requestCodeNumber)
    } catch (ex: Exception) {
      Log.e("upi_pay", ex.toString())
      Log.e("upi_pay", ex.stackTraceToString())
      this.success("failed_to_open_app")
    }
  }

  private fun getInstalledUpiApps() {
    val uriBuilder = Uri.Builder()
    uriBuilder.scheme("upi").authority("pay")
    val uri = uriBuilder.build()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    val packageManager = activity?.packageManager
    try {
      val activities = packageManager?.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
      // Convert the activities into a response that can be transferred over the channel.
      val activityResponse = activities?.map {
        val packageName = it.activityInfo.packageName
        val drawable = packageManager?.getApplicationIcon(packageName)
        val bitmap = getBitmapFromDrawable(drawable!!)
        val icon = if (bitmap != null) {
          encodeToBase64(bitmap)
        } else {
          null
        }
        mapOf(
          "packageName" to packageName,
          "icon" to icon,
          "priority" to it.priority,
          "preferredOrder" to it.preferredOrder
        )
      }
      result?.success(activityResponse)
    } catch (ex: Exception) {
      Log.e("upi_pay", ex.toString())
      result?.error("getInstalledUpiApps", "exception", ex)
    }
  }

  private fun parseActivityResult(result: ActivityResult) {
    if (result.resultCode == Activity.RESULT_OK) {
      val data = result.data
      if (data != null) {
        try {
          val response = data.getStringExtra("response")!!
          Log.d("upi_pay", "response from intent call " + response)
          this.success(response)
        } catch (ex: Exception) {
          this.success("invalid_response")
        }
      } else {
        this.success("user_cancelled")
      }
    } else {
      this.success("intent_cancelled")
    }
  }

  private fun encodeToBase64(image: Bitmap): String? {
    val byteArrayOS = ByteArrayOutputStream()
    image.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOS)
    return Base64.encodeToString(byteArrayOS.toByteArray(), Base64.NO_WRAP)
  }

  private fun getBitmapFromDrawable(drawable: Drawable): Bitmap? {
    val bmp: Bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
    drawable.draw(canvas)
    return bmp
  }

  private fun success(o: String) {
    Log.d("upi_pay", "success " + o)
    if (!hasResponded) {
      hasResponded = true
      result?.success(o)
    }
  }

  /*
  @SuppressWarnings("deprecation")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCodeNumber == requestCode && result != null) {
      if (data != null) {
        try {
          val response = data.getStringExtra("response")!!
          this.success(response)
        } catch (ex: Exception) {
          this.success("invalid_response")
        }
      } else {
        this.success("user_cancelled")
      }
    }
    return true
  }

  @SuppressWarnings("deprecation")
  companion object {
    @JvmStatic
    fun registerWith(registrar: io.flutter.plugin.common.PluginRegistry.Registrar) {
      val channel = MethodChannel(registrar.messenger(), "upi_pay")
      val plugin = UpiPayPlugin(registrar, channel)
      registrar.addActivityResultListener(plugin)
      channel.setMethodCallHandler(plugin)
    }
  }
   */
}