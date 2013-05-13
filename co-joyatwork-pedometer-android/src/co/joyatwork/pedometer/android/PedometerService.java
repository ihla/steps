package co.joyatwork.pedometer.android;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

public class PedometerService extends Service {
	
	private final class AccelerometerListener implements SensorEventListener {
		private static final long NANO_TO_MILISECONDS = 1000000;
		private long lastUpdateTime = 0;
		private long startTime = SystemClock.uptimeMillis();

		@Override
		public void onSensorChanged(SensorEvent event) {
			
			long currentSampleTime = event.timestamp / NANO_TO_MILISECONDS - startTime;
			
			if (updateTimeElapsed(currentSampleTime)) {
				Log.d(TAG, "onSensorChanged called on " + Thread.currentThread().getName()
					+ " " + currentSampleTime + " ms");
				
				broadcastUpdate(event.values.clone());
				
			}
			
		}

		private boolean updateTimeElapsed(long currentSampleTime) {
			long deltaTime = currentSampleTime - lastUpdateTime;
			if (deltaTime >= 200) {
				lastUpdateTime = currentSampleTime;
				return true;
			}
			return false;
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// TODO Auto-generated method stub
			
		}
	}

	private static final String TAG = "HelperService";
	private boolean isRunning = false;
	private Thread helperThread;
	protected SensorManager sensorManager;
	protected Sensor sensor;
	protected AccelerometerListener listener;

	@Override
	public void onCreate() {
		super.onCreate();
		
		Log.d(TAG, "onCreate");
		
		/*
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
		.setSmallIcon(R.drawable.notification_icon)
		.setContentTitle("Handler Service")
		.setContentText("press to launch");
	
		Intent launcActivity = new Intent(this, MainActivity.class);
		
		TaskStackBuilder backStackBuilder = TaskStackBuilder.create(this);
		backStackBuilder.addParentStack(MainActivity.class);
		backStackBuilder.addNextIntent(launcActivity);
		PendingIntent launchPendingActivity = backStackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);
		
		notificationBuilder.setContentIntent(launchPendingActivity);
		
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(0, notificationBuilder.build());
		*/

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		Log.d(TAG, "onStartCommand(" 
				+ (intent != null ? intent.toString() : "null") 
				+ ", " 
				+ flags 
				+ ", " 
				+ startId 
				+ ")");
		
		if (!isRunning) {
			
			isRunning = true;
			Toast.makeText(this, "service starting " + startId, Toast.LENGTH_SHORT).show();
			
			helperThread = new Thread(new Runnable() {

				@Override
				public void run() {
					
					Log.d(TAG, "starting helper thread");
					
					Looper.prepare();

					Handler handler = new Handler();
					sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
					sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
					
					listener = new AccelerometerListener();
					sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, handler);
					
					Looper.loop();
					
				}
				
			}, "HelperThread");
			helperThread.start();
			
			
		}

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		Log.d(TAG, "onDestroy()");

		Toast.makeText(this, "service stoping", Toast.LENGTH_LONG).show();

		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancelAll();
		
		if (isRunning) {
			isRunning = false;
			helperThread.interrupt();
			sensorManager.unregisterListener(listener);
			//..
		}

	}

	@Override
	public void onRebind(Intent intent) {
		// TODO Auto-generated method stub
		super.onRebind(intent);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// TODO Auto-generated method stub
		return super.onUnbind(intent);
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	protected void broadcastUpdate(float[] values) {

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		Intent intent = new Intent(getResources().getString(R.string.acc_update_action))
			.putExtra(getResources().getString(R.string.acc_x_value), "" + values[0])
			.putExtra(getResources().getString(R.string.acc_y_value), "" + values[1])
			.putExtra(getResources().getString(R.string.acc_z_value), "" + values[2])
			;
		lbm.sendBroadcast(intent);
			
	}


}
