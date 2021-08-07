package location.maven08.track;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.maven08.track.R;

import java.util.ArrayList;

/**
 * Created by maven_08 on 07/08/2021.
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class LocationJobService extends JobService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    Handler handler;
    ConnectionDetector cd;
    FusedLocationProviderClient mFusedLocationProviderClient;
    public static final int LOCATION_SERVICE_JOB_ID = 111;
    LocationRequest mLocationRequest;
    LocationCallback mLocationCallback;
    JobParameters jobParameters;
    public static boolean isJobRunning = false;
    GoogleApiClient mGoogleApiClient;
    ArrayList<Location> updatesList = new ArrayList<>();

    public static final String ACTION_STOP_JOB = "actionStopJob";

    private BroadcastReceiver stopJobReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals(ACTION_STOP_JOB)) {
                Log.d("unregister", " job stop receiver");
            /*try {
                unregisterReceiver(this); //Unregister receiver to avoid receiver leaks exception
            }catch (Exception e){
                e.printStackTrace();
            }*/
                onJobFinished();
            }
        }
    };

    private void onJobFinished() {
        Log.d("job finish", " called");
        isJobRunning = false;
        stopLocationUpdates();
        jobFinished(jobParameters, false);
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        handler = new Handler();
        this.jobParameters = jobParameters;
        /*th = new LocationThread();
        handler.post(th);*/
        buildGoogleApiClient();
        config();
        isJobRunning = true;
        return true;
    }

    private void config() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setSmallestDisplacement(1);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        cd = new ConnectionDetector(getApplicationContext());
        startLocationUpdates();
        LocalBroadcastManager.getInstance(LocationJobService.this).registerReceiver(stopJobReceiver, new IntentFilter(ACTION_STOP_JOB));
    }

    private void startLocationUpdates() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    // Update UI with location data
                    // ...
//                        Toast.makeText(getBaseContext(),"new point",Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(MainActivity.LOCATION_ACQUIRED);
                    i.putExtra("location", location);

                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(i);

                    if (cd.isConnectingToInternet()) { // check whether internet is available or not
                        updatesList.add(location); //if available add latest location point and send list to server
                        Intent i1 = new Intent(LocationJobService.this, UploadLocationService.class);
                        i1.putParcelableArrayListExtra("points", updatesList);
                            startService(i1);
                        updatesList.clear();
                    } else { // if there is no internet connection
                        updatesList.add(location); // add location points to the list
                        Intent i1 = new Intent(LocationJobService.this, UploadLocationService.class);
                        i1.putParcelableArrayListExtra("points", updatesList);
                        startService(i1);
                        updatesList.clear();
                    }
                }
            }

            ;
        };
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Toast.makeText(getApplicationContext(), "permission required !!", Toast.LENGTH_SHORT).show();
            return;
        }
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(LocationJobService.this);
        mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                null /* Looper */);
        getSharedPreferences("track", MODE_PRIVATE).edit().putBoolean("isServiceStarted", true).apply();
        Intent jobStartedMessage = new Intent(MainActivity.JOB_STATE_CHANGED);
        jobStartedMessage.putExtra("isStarted", true);
        Log.d("send broadcast", " as job started");
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(jobStartedMessage);
        updatesList.size();
        createNotification();
        Toast.makeText(getApplicationContext(), "Location job service started", Toast.LENGTH_SHORT).show();
    }

    private void buildGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
        } else {
            Log.e("api client", "not null");
        }
    }


    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d("job", "stopped");
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        isJobRunning = false;
        stopLocationUpdates();
        /*try {
            LocalBroadcastManager.getInstance(getBaseContext()).unregisterReceiver(stopJobReceiver);
        }catch (Exception e){
            e.printStackTrace();
        }*/
        return true;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i("track.JobService", "google API client connected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i("track.JobService", "google API client suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i("track.JobService", "google API client failed");
    }

    private void createNotification() {
        Notification.Builder mBuilder = new Notification.Builder(
                getBaseContext());
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            if(updatesList.size()>0){
                notification = mBuilder.setSmallIcon(R.drawable.ic_noti).setTicker("Tracking").setWhen(0)
                        .setAutoCancel(false)
                        .setCategory(Notification.EXTRA_BIG_TEXT)
                        .setContentTitle("Tracking")
                        .setContentText("Your trip in progress")
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setColor(ContextCompat.getColor(getBaseContext(), R.color.colorPrimaryDark))
                        .setStyle(new Notification.BigTextStyle()
                                .bigText("lat: "+updatesList.get(0).getLongitude() +" long: "+updatesList.get(0).getLatitude()))
                        .setChannelId("track_marty")
                        .setShowWhen(true)
                        .setOngoing(true)
                        .build();
            }else{
                notification = mBuilder.setSmallIcon(R.drawable.ic_noti).setTicker("Tracking").setWhen(0)
                        .setAutoCancel(false)
                        .setCategory(Notification.EXTRA_BIG_TEXT)
                        .setContentTitle("Tracking")
                        .setContentText("Your trip in progress")
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setColor(ContextCompat.getColor(getBaseContext(), R.color.colorPrimaryDark))
                        .setStyle(new Notification.BigTextStyle()
                                .bigText("Track in process"))
                        .setChannelId("track_marty")
                        .setShowWhen(true)
                        .setOngoing(true)
                        .build();
            }


        } else {
            if(updatesList.size()>0){
            notification = mBuilder.setSmallIcon((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ? R.drawable.ic_noti : R.mipmap.ic_launcher).setTicker("Tracking").setWhen(0)
                    .setAutoCancel(false)
                    .setCategory(Notification.EXTRA_BIG_TEXT)
                    .setContentTitle("Tracking")
                    .setContentText("Track in progress")
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setColor(ContextCompat.getColor(getBaseContext(), R.color.colorPrimaryDark))
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("Track in progress"))
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setShowWhen(true)
                    .setOngoing(true)
                    .build();
            }else{
                notification = mBuilder.setSmallIcon((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ? R.drawable.ic_noti : R.mipmap.ic_launcher).setTicker("Tracking").setWhen(0)
                        .setAutoCancel(false)
                        .setCategory(Notification.EXTRA_BIG_TEXT)
                        .setContentTitle("Tracking")
                        .setContentText("Track in progress")
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setColor(ContextCompat.getColor(getBaseContext(), R.color.colorPrimaryDark))
                        .setStyle(new Notification.BigTextStyle()
                                .bigText("lat: "+updatesList.get(0).getLongitude() +" long: "+updatesList.get(0).getLatitude()))
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setShowWhen(true)
                        .setOngoing(true)
                        .build();
            }
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel("track_marty", "Track", NotificationManager.IMPORTANCE_HIGH);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(mChannel);
        }
        /*assert notificationManager != null;
        notificationManager.notify(0, notification);*/
        startForeground(1, notification); //for foreground service, don't use 0 as id. it will not work.
    }

    private void removeNotification() {
        /*NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.cancel(0);*/ //use this for normal service
        stopForeground(true); // use this for foreground service
    }

    private void stopLocationUpdates() {

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        Log.d("stop location ", " updates called");
        if (mLocationCallback != null && mFusedLocationProviderClient != null) {
            mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
            Toast.makeText(getApplicationContext(), "Location job service stopped.", Toast.LENGTH_SHORT).show();
        }
        getSharedPreferences("track", MODE_PRIVATE).edit().putBoolean("isServiceStarted", false).apply();
        Intent jobStoppedMessage = new Intent(MainActivity.JOB_STATE_CHANGED);
        jobStoppedMessage.putExtra("isStarted", false);
        Log.d("broadcasted", "job state change");
        removeNotification();
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(jobStoppedMessage);
    }
}
