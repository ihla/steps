package co.joyatwork.pedometer.android;

import co.joyatwork.pedometer.StepCounter;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

public class PedometerService extends Service {
	
	// the following fields are accessed from UI thread
	private static final String TAG = "PedometerService";
	private boolean isRunning = false;
	//TODO check if you can start this thread anonymously, if so you can remove private field
	private Thread helperThread;
	//TODO could you decrease the footprint of service if you have removed these fields (these are only references)? >>
	private SensorManager sensorManager;
	private Sensor sensor;
	//<<

	// the StepCounter object is created on UI thread but used by HelperThread 
	private StepCounter stepCounter;
	
	// the following fields are accessed on HelperThread >>
	private int lastStepCount = 0;
	private int stepsCount = 0;
	private boolean debugging = true; //TODO get value from preferences

	private final class StepsListener implements StepCounter.StepCounterListener {

		@Override
		public void onStepsCounted(int deltaStepCount) {
			stepsCount += deltaStepCount;
			Log.d(TAG, "onStepCounted: " + stepsCount);
		}
		
	}
	
	private final class AccelerometerListener implements SensorEventListener {

		private static final long NANO_TO_MILISECONDS = 1000000;

		@Override
		public void onSensorChanged(SensorEvent event) {
			//TODO check if correct sensor is calling

			long currentSampleTime = event.timestamp / NANO_TO_MILISECONDS;

			stepCounter.countSteps(event.values.clone(), currentSampleTime);
			broadcastStepCountUpdate(stepCounter.getStepCount());

			//Log.d(TAG, "onSensorChanged called on " + Thread.currentThread().getName() + " " + currentSampleTime + " ms");

		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// TODO Auto-generated method stub
			
		}
	}
	private AccelerometerListener accelerometerListener;
	//<<
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		Log.d(TAG, "onCreate - stepCount: " + stepsCount);
		
		stepCounter = new StepCounter(new StepsListener());
		
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
					
					Log.d(TAG, "runing looper in helper thread");
					
					Looper.prepare();

					Handler handler = new Handler();
					sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
					sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
					
					accelerometerListener = new AccelerometerListener();
					sensorManager.registerListener(accelerometerListener, sensor, 
							SensorManager.SENSOR_DELAY_GAME, handler);
					
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

		if (isRunning) {
			isRunning = false;
			// thread with looper is gracefully killed when service is killed
			// no need to call interrupt explicitly (anyway not sure if Looper can be interrupted explicitly?)
			// helperThread.interrupt();
			sensorManager.unregisterListener(accelerometerListener);
			//..
		}

	}

	@Override
	public void onRebind(Intent intent) {
		Log.d(TAG, "onRebind()");
		super.onRebind(intent);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d(TAG, "onUnbind()");
		return super.onUnbind(intent);
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Log.d(TAG, "onBind()");
		return null;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		Log.d(TAG, "onConfigurationChanged()");
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onLowMemory() {
		Log.d(TAG, "onLowMemory()");
		super.onLowMemory();
	}

	@SuppressLint("NewApi")
	@Override
	public void onTaskRemoved(Intent rootIntent) {
		Log.d(TAG, "onTaskRemoved()");
		super.onTaskRemoved(rootIntent);
	}

	@SuppressLint("NewApi")
	@Override
	public void onTrimMemory(int level) {
		Log.d(TAG, "onTrimMemory() level: " + level);
		super.onTrimMemory(level);
	}


	/**
	 * called on HelperThread!!!
	 * @param stepCount
	 */
	private void broadcastStepCountUpdate(int stepCount) {
		
		if (stepCount == lastStepCount) {
			return;
		}
		lastStepCount = stepCount;

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		Intent intent = new Intent(getResources().getString(R.string.step_count_update_action))
			.putExtra(getResources().getString(R.string.step_count), "" + stepCount) //TODO optimize string formating
			;
		
		if (debugging) {
			intent.putExtra(getResources().getString(R.string.step_axis), stepCounter.getDetectedAxis());
		}
		
		lbm.sendBroadcast(intent);
			
	}

}
