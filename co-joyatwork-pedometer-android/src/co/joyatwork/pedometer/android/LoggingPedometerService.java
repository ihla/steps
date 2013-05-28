package co.joyatwork.pedometer.android;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import co.joyatwork.pedometer.StepCounter;
import co.joyatwork.pedometer.StepCounter.StepCounterListener;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

public class LoggingPedometerService extends PedometerService {

	private final static String TAG = "LoggingPedometerService";
	
	private final class LoggingStepCounter extends StepCounter {

		public LoggingStepCounter(StepCounter.StepCounterListener l) {
			super(l);
			initializeLogging();
		}

	    @Override
		public void countSteps(float[] accelerationSamples, long sampleTimeInMilis) {
			super.countSteps(accelerationSamples, sampleTimeInMilis);
			writeLogIfEnabled(sampleTimeInMilis, accelerationSamples);
		}

	    private static final char CSV_DELIM = ',';
	    private static final String CSV_HEADER_FILTER_OUTPUT_FILE =
	            "Time,X-sensor,X-filter,Y-sensor,Y-filter,Z-sensor,Z-filter";
	    private static final String CSV_HEADER_STEPS_FILE =
	    		"Time,X-Avg,X-Thld,X-Step,X-Int,X-AvgInt,X-Var,X-Val,Y-Avg,Y-Thld,Y-Step,Y-Int,Y-AvgInt,Y-Var,Y-Val,Z-Avg,Z-Thld,Z-Step,Z-Int,Z-AvgInt,Z-Var,Z-Val";
	    private static final String CSV_HEADER_PEAK_DETECTION_FILE =
	            "Time,X-val,X-currPeak,X-fixedPeak,Y-val,Y-currPeak,Y-fixedPeak,Z-val,Z-currPeak,Z-fixedPeak";

		private PrintWriter filterOutputLogWriter = null;
		private PrintWriter stepsDataWriter = null;
		private PrintWriter peakDetectionLogWriter = null;
		private long startTime;
		private int[] oldCrossingThresholdCounts = new int[3];
		private float[] stepFlip = new float[3];
		private StringBuffer stringBuffer;

		private void initializeLogging() {
			// Data files are stored on the external cache directory so they can
	        // be pulled off of the device by the user
	        File filterOutputLogFile = new File(getExternalCacheDir(), "filters.csv");
	        File stepsDataFile = new File(getExternalCacheDir(), "steps.csv");
	        File peakDetectionLogFile = new File(getExternalCacheDir(), "peaks.csv");
			try {
				//FileWriter calls directly OS for every write request
				//For better performance the FileWriter is wrapped into BufferedWriter, which calls out for a batch of bytes
				//PrintWriter allows a human-readable writing into a file
				filterOutputLogWriter = new PrintWriter(new BufferedWriter(new FileWriter(filterOutputLogFile)));
				filterOutputLogWriter.println(CSV_HEADER_FILTER_OUTPUT_FILE);

				stepsDataWriter = new PrintWriter(new BufferedWriter(new FileWriter(stepsDataFile)));
				stepsDataWriter.println(CSV_HEADER_STEPS_FILE);
				
				peakDetectionLogWriter = new PrintWriter(new BufferedWriter(new FileWriter(peakDetectionLogFile)));
				peakDetectionLogWriter.println(CSV_HEADER_PEAK_DETECTION_FILE);

			} catch (IOException e) {
				Log.e(TAG, "Could not open CSV file(s)", e);
			}

			startTime = SystemClock.uptimeMillis();
			
			for (int i = 0; i < 3; i++) {
				oldCrossingThresholdCounts[i] = 0;
				stepFlip[i] = -0.5F;
			}
			
			stringBuffer = new StringBuffer();
		}

		private void writeLogIfEnabled(long sampleTimeInMillis, float[] values) {
			if (settings.getBoolean("logging", false)) {
				Log.d("LoggingPedometerService", "logging");
				writeFilterOutputData(sampleTimeInMillis, values);
				writePeakDetectionData(sampleTimeInMillis);
				writeStepDetectionData(sampleTimeInMillis);
			}
		}
		
		private void writeFilterOutputData(long sampleTimeInMillis, float[] values) {

			if (filterOutputLogWriter != null) {
				long timeStampInMilis = sampleTimeInMillis - startTime;
				stringBuffer.delete(0, stringBuffer.length())
					.append(timeStampInMilis).append(CSV_DELIM)
					.append(values[0]).append(CSV_DELIM) // x
					.append(getSmoothedAcceleration(0)).append(CSV_DELIM)
					.append(values[1]).append(CSV_DELIM) // y
					.append(getSmoothedAcceleration(1)).append(CSV_DELIM)
					.append(values[2]).append(CSV_DELIM) // z
					.append(getSmoothedAcceleration(2))
					;

				filterOutputLogWriter.println(stringBuffer.toString());
				if (filterOutputLogWriter.checkError()) {
					Log.w(TAG, "Error writing filter output log");
				}
			}

		}
		
		private void writePeakDetectionData(long sampleTimeInMillis) {

			if (peakDetectionLogWriter != null) {
				long timeStampInMilis = sampleTimeInMillis - startTime;
				stringBuffer.delete(0, stringBuffer.length())
					.append(timeStampInMilis).append(CSV_DELIM)
					.append(getSmoothedAcceleration(0)).append(CSV_DELIM)
					.append(getCurrentPeak2PeakValue(0)).append(CSV_DELIM)
					.append(getFixedPeak2PeakValue(0)).append(CSV_DELIM)
					.append(getSmoothedAcceleration(1)).append(CSV_DELIM)
					.append(getCurrentPeak2PeakValue(1)).append(CSV_DELIM)
					.append(getFixedPeak2PeakValue(1)).append(CSV_DELIM)
					.append(getSmoothedAcceleration(2)).append(CSV_DELIM)
					.append(getCurrentPeak2PeakValue(2)).append(CSV_DELIM)
					.append(getFixedPeak2PeakValue(2))
					;

				peakDetectionLogWriter.println(stringBuffer.toString());
				if (stepsDataWriter.checkError()) {
					Log.w(TAG, "Error writing peak detection log");
				}
			}

		}
		
		private void writeStepDetectionData(long sampleTimeInMillis) {
			flipSteps();
			
			if (stepsDataWriter != null) {
				long timeStampInMilis = sampleTimeInMillis - startTime;
				stringBuffer.delete(0, stringBuffer.length())
					.append(timeStampInMilis).append(CSV_DELIM)
					.append(getSmoothedAcceleration(0)).append(CSV_DELIM)
					.append(getThresholdValue(0)).append(CSV_DELIM)
					.append(stepFlip[0]).append(CSV_DELIM)
					.append(getStepInterval(0)).append(CSV_DELIM)
					.append(getAvgStepInterval(0)).append(CSV_DELIM)
					.append(getStepIntervalVariance(0)).append(CSV_DELIM)
					.append(hasValidSteps(0)).append(CSV_DELIM)
					.append(getSmoothedAcceleration(1)).append(CSV_DELIM)
					.append(getThresholdValue(1)).append(CSV_DELIM)
					.append(stepFlip[1]).append(CSV_DELIM)
					.append(getStepInterval(1)).append(CSV_DELIM)
					.append(getAvgStepInterval(1)).append(CSV_DELIM)
					.append(getStepIntervalVariance(1)).append(CSV_DELIM)
					.append(hasValidSteps(1)).append(CSV_DELIM)
					.append(getSmoothedAcceleration(2)).append(CSV_DELIM)
					.append(getThresholdValue(2)).append(CSV_DELIM)
					.append(stepFlip[2]).append(CSV_DELIM)
					.append(getStepInterval(2)).append(CSV_DELIM)
					.append(getAvgStepInterval(2)).append(CSV_DELIM)
					.append(getStepIntervalVariance(2)).append(CSV_DELIM)
					.append(hasValidSteps(2))
					/*
					.append(CSV_DELIM)
					.append(getFixedMinValue(0)).append(CSV_DELIM)
					.append(getFixedMaxValue(0)).append(CSV_DELIM)
					.append(getFixedMinValue(1)).append(CSV_DELIM)
					.append(getFixedMaxValue(1)).append(CSV_DELIM)
					.append(getFixedMinValue(2)).append(CSV_DELIM)
					.append(getFixedMaxValue(2)).append(CSV_DELIM)
					*/
					;

				stepsDataWriter.println(stringBuffer.toString());
				if (stepsDataWriter.checkError()) {
					Log.w(TAG, "Error writing step detection log");
				}
			}
		}

		private void flipSteps() {
			int[] crossingThresholdCounts = new int[3];
			for (int i = 0; i < 3; i++) {
				crossingThresholdCounts[i] = getCrossingThresholdCount(i);
			}
			
			if (oldCrossingThresholdCounts[0] != crossingThresholdCounts[0]) {
				stepFlip[0] *= -1;
			}
			if (oldCrossingThresholdCounts[1] != crossingThresholdCounts[1]) {
				stepFlip[1] *= -1;
			}
			if (oldCrossingThresholdCounts[2] != crossingThresholdCounts[2]) {
				stepFlip[2] *= -1;
			}
			for (int i = 0; i < 3; i++) {
				oldCrossingThresholdCounts[i] = crossingThresholdCounts[i];
			}
		}
	}

	private SharedPreferences settings;

	@Override
	public void onCreate() {
		super.onCreate();
		
		settings = PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
	protected StepCounter createStepCounter(StepCounterListener listener) {
		return new LoggingStepCounter(listener);
	}

}
