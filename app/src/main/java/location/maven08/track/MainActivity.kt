package location.maven08.track

import android.Manifest
import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.app.Activity
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.*
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.util.Property
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.maven08.track.R

class MainActivity : AppCompatActivity() {
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null
    private var mLocationRequest: LocationRequest? = null
    private var mLocationCallback: LocationCallback? = null
    private var foreService: Button? = null
    private var bgService: Button? = null
    private var mapFragment: SupportMapFragment? = null
    var mMap: GoogleMap? = null
    private var mapLoaded = false
    private var locationMarker: Marker? = null
    private var oldLocation: Location? = null
    private var bearing = 0f
    private var registered = false
    private var isServiceStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        askForLocation()

        foreService = findViewById<View>(R.id.fore_action) as Button
        bgService = findViewById<View>(R.id.bg_service) as Button
        mapFragment = SupportMapFragment.newInstance()
        val transaction = supportFragmentManager.beginTransaction()
        mapFragment?.let { it ->
            transaction.add(R.id.map_fragment, it).commitAllowingStateLoss()
        }

        handler = Handler()
        mapFragment?.getMapAsync { googleMap ->
            if (googleMap != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return@getMapAsync
                }

            }
        }
        isServiceStarted = getSharedPreferences("track", Context.MODE_PRIVATE).getBoolean("isServiceStarted", false)
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            isServiceStarted = LocationJobgService.isJobRunning;
        }else{
            isServiceStarted = false;
        }*/
        changeServiceButton(isServiceStarted)
        if (!registered && isServiceStarted) {
            val i = IntentFilter(JOB_STATE_CHANGED)
            i.addAction(LOCATION_ACQUIRED)
            LocalBroadcastManager.getInstance(this).registerReceiver(jobStateChanged, i)
        }
    }


    fun askForLocation() {
        if (Build.VERSION.SDK_INT > 22) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSIONS_MAIN_ACCESS)
            } else {
                Log.i("sendParam", "")
            }
        } else {
            Log.i("sendParam", "")
        }
    }

    fun onButtonClick(view: View) {
        when (view.id) {
            R.id.fore_action -> if (view.tag == "s") {
                createLocationRequest(false)
            } else {
                Log.d("clicked", "foreService")
                stopLocationUpdates()
            }
            R.id.bg_service -> if (view.tag == "s") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Log.d("registered", " on start service")
                    startBackgroundService()
                } else {
                    Toast.makeText(baseContext, "service for pre lollipop will be available in next update", Toast.LENGTH_LONG).show()
                }
            } else {
                stopBackgroundService()
            }
        }
    }

    private val jobStateChanged: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == null) {
                return
            }
            if (intent.action == JOB_STATE_CHANGED) {
                changeServiceButton(intent.extras!!.getBoolean("isStarted"))
            } else if (intent.action == LOCATION_ACQUIRED) {
                if (intent.extras != null) {
                    val b = intent.extras
                    val l = b!!.getParcelable<Location>("location")
                    updateMarker(l)
                } else {
                    Log.d("intent", "null")
                }
            }
        }
    }

    private fun changeServiceButton(isStarted: Boolean) {
        if (isStarted) {
            bgService!!.tag = "f"
            bgService!!.text = getString(R.string.stop_bg_tracking)
            foreService!!.visibility = View.GONE
        } else {
            bgService!!.tag = "s"
            bgService!!.text = "START BACKGROUND TRACKING"
            foreService!!.visibility = View.VISIBLE
        }
    }

    private fun stopBackgroundService() {
        if (getSharedPreferences("track", Context.MODE_PRIVATE).getBoolean("isServiceStarted", false)) {
            Log.d("registered", " on stop service")
            var stopJobgService: Intent? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                stopJobgService = Intent(LocationJobService.ACTION_STOP_JOB)
                LocalBroadcastManager.getInstance(baseContext).sendBroadcast(stopJobgService)
                changeServiceButton(false)
            } else {
                Toast.makeText(applicationContext, "yet to be coded - stop service", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun startBackgroundService() {
        if (!registered) {
            val i = IntentFilter(JOB_STATE_CHANGED)
            i.addAction(LOCATION_ACQUIRED)
            LocalBroadcastManager.getInstance(this).registerReceiver(jobStateChanged, i)
        }
        val jobScheduler = (getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler)
        jobScheduler.schedule(JobInfo.Builder(LocationJobService.LOCATION_SERVICE_JOB_ID,
                ComponentName(this, LocationJobService::class.java))
                .setOverrideDeadline(500)
                .setPersisted(true)
                .setRequiresDeviceIdle(false)
                .build())
    }

    private fun createLocationRequest(isFromPermissionResult: Boolean) {
        mLocationRequest = LocationRequest()
        mLocationRequest!!.interval = 5000
        mLocationRequest!!.fastestInterval = 1000
        mLocationRequest!!.smallestDisplacement = 1f
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        val builder = LocationSettingsRequest.Builder().addLocationRequest(mLocationRequest)
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener(this) { // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
            if (isFromPermissionResult){}
            else {
                bgService!!.visibility = View.GONE
            }
            startLocationUpdates(isFromPermissionResult)
        }
        task.addOnFailureListener(this) { e ->
            val statusCode = (e as ApiException).statusCode
            when (statusCode) {
                CommonStatusCodes.RESOLUTION_REQUIRED ->                         // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        val resolvable = e as ResolvableApiException
                        resolvable.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)
                    } catch (sendEx: SendIntentException) {
                        // Ignore the error.
                    }
                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                }
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSIONS_MAIN_ACCESS -> {
                run {
                    // If request is cancelled, the result arrays are empty.
                    if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            createLocationRequest(true)
//                            Handler().postDelayed({
//
//                                createLocationRequest()
//                            }, 5000)



                            return

                        }
                    } else {
                        Toast.makeText(this, "Permission_denied", Toast.LENGTH_LONG).show()
                    }
                }
            }


        }
    }

    private fun startLocationUpdates(isFromPermissionResult:Boolean) {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    Toast.makeText(this@MainActivity,"lat: "+location.latitude +" long: "+location.longitude,Toast.LENGTH_LONG).show()

                    updateMarker(location)
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(applicationContext, "location permission required !!", Toast.LENGTH_SHORT).show()
            return
        }
        mFusedLocationProviderClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback, null /* Looper */)

        if(isFromPermissionResult){

        }else {
            foreService!!.tag = "f"
            foreService!!.text = "STOP FOREGROUND TRACKING"
            Toast.makeText(getApplicationContext(), "Location update started", Toast.LENGTH_SHORT).show();
        }
    }

    private fun stopLocationUpdates() {
        if (foreService!!.tag == "s") {
            Log.d("TRACK", "stopLocationUpdates: updates never requested, no-op.")
            return
        }

        mFusedLocationProviderClient!!.removeLocationUpdates(mLocationCallback)
        foreService!!.tag = "s"
        foreService!!.text = "START FOREGROUND TRACKING"
        bgService!!.visibility = View.VISIBLE
        Toast.makeText(getApplicationContext(), "Location update stopped.", Toast.LENGTH_SHORT).show();
    }

    override fun onDestroy() {
        try {
            if (registered) {
                unregisterReceiver(jobStateChanged)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.i("Dash", "User agreed to make required location settings changes.")
                    createLocationRequest(true)
                }
                Activity.RESULT_CANCELED -> //                    showTimeoutDialog("Without location access, GreenPool Enterprise can't be used !!", true);
                    Log.i("Dash", "User choose not to make required location settings changes.")
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    inner class MoveThread : Runnable {
        var newPoint: LatLng? = null
        var zoom = 16f
        fun setNewPoint(latLng: LatLng?, zoom: Float) {
            newPoint = latLng
            this.zoom = zoom
        }

        override fun run() {
            val point = CameraUpdateFactory.newLatLngZoom(newPoint, zoom)
            runOnUiThread { mMap!!.animateCamera(point) }
        }
    }

    private fun updateMarker(location: Location?) {
        if (location == null) {
            return
        }
        mapFragment?.getMapAsync { googleMap ->
            if (googleMap != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return@getMapAsync
                }
                mMap = googleMap
                mMap!!.setOnMapLoadedCallback {
                    mapLoaded = true
                    mMap!!.uiSettings.setAllGesturesEnabled(true)
                    mMap!!.uiSettings.isZoomControlsEnabled = true
                }
                mMap?.isMyLocationEnabled = true
                mMap?.uiSettings?.isZoomControlsEnabled = true
                mMap?.uiSettings?.isMapToolbarEnabled = true
                mMap?.uiSettings?.isCompassEnabled = true
                mMap?.uiSettings?.isScrollGesturesEnabled = true
                mMap?.isBuildingsEnabled = true
            }
        }

        if (mMap != null && mapLoaded) {
            if (locationMarker == null) {
                oldLocation = location
                val markerOptions = MarkerOptions()
                val car = BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher)
                markerOptions.icon(car)
                markerOptions.anchor(0.5f, 0.5f) // set the car image to center of the point instead of anchoring to above or below the location
                markerOptions.flat(true) // set as true, so that when user rotates the map car icon will remain in the same direction
                markerOptions.position(LatLng(location.latitude, location.longitude))
                locationMarker = mMap!!.addMarker(markerOptions)
                bearing = if (location.hasBearing()) { // if location has bearing set the same bearing to marker(if location is acquired using GPS bearing will be available)
                    location.bearing
                } else {
                    0f // no need to calculate bearing as it will be the first point
                }
                locationMarker?.rotation = bearing
                moveThread = MoveThread()
                moveThread!!.setNewPoint(LatLng(location.latitude, location.longitude), 16f)
                handler!!.post(moveThread!!)
            } else {
                bearing = if (location.hasBearing()) { // if location has bearing set the same bearing to marker(if location is acquired using GPS bearing will be available)
                    location.bearing
                } else { // if not, calculate bearing between old location and new location point
                    oldLocation!!.bearingTo(location)
                }
                locationMarker!!.rotation = bearing
                moveThread!!.setNewPoint(LatLng(location.latitude, location.longitude), mMap!!.cameraPosition.zoom) // set the map zoom to current map's zoom level as user may zoom the map while tracking.
                animateMarkerToICS(locationMarker, LatLng(location.latitude, location.longitude)) // animate the marker smoothly
            }
        } else {
            Log.e("map null or not loaded", "")
        }
    }

    companion object {
        internal var PERMISSIONS_MAIN_ACCESS = 4548
        protected const val REQUEST_CHECK_SETTINGS = 0x1
        var moveThread: MoveThread? = null
        var handler: Handler? = null
        const val JOB_STATE_CHANGED = "jobStateChanged"
        const val LOCATION_ACQUIRED = "locAcquired"
        fun animateMarkerToICS(marker: Marker?, finalPosition: LatLng?) {
            val typeEvaluator = TypeEvaluator<LatLng> { fraction, startValue, endValue -> interpolate(fraction, startValue, endValue) }
            val property = Property.of(Marker::class.java, LatLng::class.java, "position")
            val animator = ObjectAnimator.ofObject(marker, property, typeEvaluator, finalPosition)
            animator.duration = 3000
            animator.addListener(object : AnimatorListener {
                override fun onAnimationStart(animator: Animator) {}
                override fun onAnimationEnd(animator: Animator) {
                    handler!!.post(moveThread!!)
                }

                override fun onAnimationCancel(animator: Animator) {}
                override fun onAnimationRepeat(animator: Animator) {}
            })
            animator.start()
        }

        fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng {
            // function to calculate the in between values of old latlng and new latlng.
            // To get more accurate tracking(Car will always be in the road even when the latlng falls away from road), use roads api from Google apis.
            // As it has quota limits I didn't have used that method.
            val lat = (b.latitude - a.latitude) * fraction + a.latitude
            var lngDelta = b.longitude - a.longitude

            // Take the shortest path across the 180th meridian.
            if (Math.abs(lngDelta) > 180) {
                lngDelta -= Math.signum(lngDelta) * 360
            }
            val lng = lngDelta * fraction + a.longitude
            return LatLng(lat, lng)
        }
    }


}