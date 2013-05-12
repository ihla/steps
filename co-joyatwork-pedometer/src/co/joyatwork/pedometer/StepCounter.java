package co.joyatwork.pedometer;

public class StepCounter {
	
	public static final int X_AXIS = 0;
	public static final int Y_AXIS = 1;
	public static final int Z_AXIS = 2;

	private StepDetector[] stepDetector = { new StepDetector(),
			new StepDetector(),
			new StepDetector()
	};

	private int[] lastStepCount = new int[3];
	private int stepCounter;
	
	public StepCounter() {
		for (int i = 0; i < 3; i++) {
			lastStepCount[i] = 0;
		}
		stepCounter = 0;
	}
	
	public void countSteps(float[] accelerationSamples, long sampleTimeInMilis) {

		updateStepDetectors(accelerationSamples, sampleTimeInMilis);
		
		int maxPeakAxis = -1;
		float maxPeak2PeakValue = 0;
		for (int i = 0; i < 3; i++) {
			float peak2peakValue = stepDetector[i].getFixedPeak2PeakValue();
			if (peak2peakValue > maxPeak2PeakValue) {
				maxPeakAxis = i;
				maxPeak2PeakValue = peak2peakValue;
			}
		}
		
		if (maxPeakAxis >= 0) {
			if (stepDetector[maxPeakAxis].hasValidSteps()) {
				stepCounter += stepDetector[maxPeakAxis].getStepCount() - lastStepCount[maxPeakAxis]; // add delta
				
				// store counts for next delta calculation
				for (int i = 0; i < 3; i++) {
					lastStepCount[i] = stepDetector[i].getStepCount();
				}
			}
		} 

	}

	private void updateStepDetectors(float[] accelerationSamples, long sampleTimeInMilis) {
		for(int i = 0; i < 3; i++) {
			stepDetector[i].update(accelerationSamples[i], sampleTimeInMilis);
		}
	}

	public int getStepCount() {
		return stepCounter;
	}
	
	//for testing
	public int getCrossingThresholdCount(int axis) {
		return stepDetector[axis].getCrossingThresholdCount();
	}

	public int getAxisStepCount(int axis) {
		return stepDetector[axis].getStepCount();
	}

	public float[] getLinearAccelerationValues() {
		float[] linearAccelerationVector = new float[3];
		for (int axis = 0; axis < 3; axis++) {
			linearAccelerationVector[axis] = stepDetector[axis].getLinearAcceleration();
		}
		return linearAccelerationVector;
	}
	
	public float[] getSmoothedAccelerationValues() {
		float[] smoothedAccelerationVector = new float[3];
		for (int axis = 0; axis < 3; axis++) {
			smoothedAccelerationVector[axis] = stepDetector[axis].getSmoothedAcceleration();
		}
		return smoothedAccelerationVector;
	}
	
	public float[] getThresholdValues() {
		float[] thresholdValues = new float[3];
		for(int i = 0; i < 3; i++) {
			thresholdValues[i] = stepDetector[i].getThresholdValue();
		}
		return thresholdValues;
	}
    
	public float getLinearAcceleration(int axis) {
		return stepDetector[axis].getLinearAcceleration();
	}
	
	public float getSmoothedAcceleration(int axis) {
		return stepDetector[axis].getSmoothedAcceleration();
	}
	
	public float getFixedPeak2PeakValue(int axis) {
		return stepDetector[axis].getFixedPeak2PeakValue();
	}
	
	public float getCurrentPeak2PeakValue(int axis) {
		return stepDetector[axis].getCurrentPeak2PeakValue();
	}

	public float getFixedMinValue(int axis) {
		return stepDetector[axis].getFixedMinValue();
	}

	public float getFixedMaxValue(int axis) {
		return stepDetector[axis].getFixedMaxValue();
	}

	public boolean hasValidSteps(int axis) {
		return stepDetector[axis].hasValidSteps();
	}
	
	public long getStepInterval(int axis) {
		return stepDetector[axis].getStepInterval();
	}

	public long getAvgStepInterval(int axis) {
		return stepDetector[axis].getAvgStepInterval();
	}

	public float getStepIntervalVariance(int axis) {
		return stepDetector[axis].getStepIntervalVariance();
	}


}
