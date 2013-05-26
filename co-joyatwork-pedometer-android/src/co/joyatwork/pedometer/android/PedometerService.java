package co.joyatwork.pedometer.android;

import java.util.concurrent.atomic.AtomicInteger;

import co.joyatwork.pedometer.StepCounter;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

public class PedometerService extends Service {
	
	private static final String PERSISTENT_STATE_STEPS_FILE = "steps";
	private static final String PERSISTENT_SATE_STEPS_COUNT = "stepsCount";
	// the following fields are accessed from UI thread
	private static final String TAG = "PedometerService";
	private static final long NANO_TO_MILISECONDS = 1000000;
	private boolean isRunning = false;
	//TODO check if you can start this thread anonymously, if so you can remove private field
	private Thread helperThread;
	//TODO could you decrease the footprint of service if you have removed these fields (these are only references)? >>
	private SensorManager sensorManager;
	private Sensor sensor;
	//<<

	// the following objects are created on UI thread but used by HelperThread 
	private StepCounter stepCounter;
	private SharedPreferences persistentState;
	private Editor persistentStateEditor; //TODO called on UI Thread in onDestroy()!!! 
	
	private AtomicInteger stepsCount; // to allow concurrent access from UI and Helper thread
	
	private boolean debugging = true; //TODO get value from preferences

	private final class StepsListener implements StepCounter.StepCounterListener {

		@Override
		public void onStepsCounted(int deltaStepCount) {

			stepsCount.addAndGet(deltaStepCount);
			
			persistentStateEditor.putInt(PERSISTENT_SATE_STEPS_COUNT, stepsCount.get());
			persistentStateEditor.apply();

			broadcastStepCount(stepsCount.get());
			
			Log.d(TAG, "onStepCounted: " + stepsCount.get());
		}
		
	}
	
	private final class AccelerometerListener implements SensorEventListener {


		@Override
		public void onSensorChanged(SensorEvent event) {
			//TODO check if correct sensor is calling

			//Log.d(TAG, "onSensorChanged called on " + Thread.currentThread().getName() + " " + event.timestamp / NANO_TO_MILISECONDS + " ms");

			countSteps(event);

		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// TODO Auto-generated method stub
			
		}
	}
	private AccelerometerListener accelerometerListener;
	
	//<<
	
	private WakeLock wakeLock;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		
		stepCounter = createStepCounter(new StepsListener());
		
		persistentState = getSharedPreferences(PERSISTENT_STATE_STEPS_FILE, 0);
		persistentStateEditor = persistentState.edit();
		
		stepsCount = new AtomicInteger();
		stepsCount.set((persistentState.getInt(PERSISTENT_SATE_STEPS_COUNT, 0)));
		
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PedometerService");

		Log.d(TAG, "onCreate - stepCount: " + stepsCount.get());
		
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

		// Parent Activity recreated, broadcast update 
		broadcastStepCount(stepsCount.get());
		
		wakeLock.acquire();
		
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
			sensorManager.unregisterListener(accelerometerListener);
		}

		// hopefully onSensorChanged() will not be invoked when listener got unregistered!?
		// SharedPreferences should be thread safe
		// otherwise the race conditions on storing to state might occur!!!!
		persistentStateEditor.putInt(PERSISTENT_SATE_STEPS_COUNT, 0); // reset counter on service exit
		persistentStateEditor.commit();

		wakeLock.release();

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
	private void broadcastStepCount(int stepCount) {
		
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		Intent intent = new Intent(getResources().getString(R.string.step_count_update_action))
			.putExtra(getResources().getString(R.string.step_count), "" + stepCount) //TODO optimize string formating
			;
		
		if (debugging) {
			intent.putExtra(getResources().getString(R.string.step_axis), stepCounter.getDetectedAxis());
		}
		
		lbm.sendBroadcast(intent);
			
	}

	/**
	 * called on HelperThread!!!
	 * @param event
	 */
	private void countSteps(SensorEvent event) {
		long currentSampleTime = event.timestamp / NANO_TO_MILISECONDS;
		stepCounter.countSteps(event.values.clone(), currentSampleTime);
	}

	/**
	 * can subclass and override for debugging
	 * @param listener
	 * @return
	 */
	protected StepCounter createStepCounter(StepCounter.StepCounterListener listener) {
		return new StepCounter(listener);
	}
}
