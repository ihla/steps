package co.joyatwork.pedometer.android;

import java.util.concurrent.atomic.AtomicInteger;

import co.joyatwork.pedometer.StepCounter;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
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

public abstract class PedometerService extends Service {
	
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
	protected Handler handler;
	
	//<<
	
    // BroadcastReceiver for handling ACTION_SCREEN_OFF.
    private BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Check action just to be on the safe side.
            if (!intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            	return;
            }

            Runnable runnable = new Runnable() {
                public void run() {
                    Log.i(TAG, "Runnable executing.");
                    // Unregisters the listener and registers it again.
                	if (isRunning) {
        				unregisterSensorListener();
        				registerSensorListener();
        			}
                    // release and acquire wake lock
                	wakeLock.release();
                	wakeLock.acquire();
                }
            };

            new Handler().postDelayed(runnable, 2500);
        }
    };

	private WakeLock wakeLock;
	
	@Override
	public void onCreate() {
		
		stepCounter = createStepCounter(new StepsListener());
		
		persistentState = getSharedPreferences(PERSISTENT_STATE_STEPS_FILE, 0);
		persistentStateEditor = persistentState.edit();
		
		stepsCount = new AtomicInteger();
		stepsCount.set((persistentState.getInt(PERSISTENT_SATE_STEPS_COUNT, 0)));
		
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PedometerService");
		
		// this workaround does not work on Samsung Ace 2 Android 2.3.6!!!
		//registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
		
		startForeground();

		Log.d(TAG, "onCreate - stepCount: " + stepsCount.get());
		
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		if (!isRunning) {
			
			isRunning = true;
			Toast.makeText(this, "service starting " + startId, Toast.LENGTH_SHORT).show();
			
			helperThread = new Thread(new Runnable() {

				@Override
				public void run() {
					
					Log.d(TAG, "runing looper in helper thread");
					
					Looper.prepare();

					handler = new Handler();
					sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
					sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
					
					accelerometerListener = new AccelerometerListener();
					registerSensorListener();
					
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

		Toast.makeText(this, "service stoping", Toast.LENGTH_LONG).show();

		//unregisterReceiver(screenOffReceiver);
		
		if (isRunning) {
			isRunning = false;
			// thread with looper is gracefully killed when service is killed
			// no need to call interrupt explicitly (anyway not sure if Looper can be interrupted explicitly?)
			unregisterSensorListener();
		}

		// hopefully onSensorChanged() will not be invoked when listener got unregistered!?
		// SharedPreferences should be thread safe
		// otherwise the race conditions on storing to state might occur!!!!
		persistentStateEditor.putInt(PERSISTENT_SATE_STEPS_COUNT, 0); // reset counter on service exit
		persistentStateEditor.commit();

		wakeLock.release();

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

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * abstract method called to run service in foreground 
	 * to be implemented in application package 
	 */
	abstract protected void startForeground();

	private void registerSensorListener() {
		sensorManager.registerListener(accelerometerListener, sensor, 
				SensorManager.SENSOR_DELAY_GAME, handler);
	}

	private void unregisterSensorListener() {
		sensorManager.unregisterListener(accelerometerListener);
	}

}
