package co.joyatwork.pedometer;

import co.joyatwork.filters.MovingAverage;

class StepDetector {
	
	interface StepDetectingStrategy {
		public void update(float sampleValue, long sampleTimeInMilis);
	}
	
	class SearchingDetector implements StepDetectingStrategy {
		/**
		 * Step count is valid if 4 intervals between 5 consecutive steps
		 * are all in the range, and all interval variances are in range.
		 */
		private static final int VALID_STEPS_COUNT = 5;
		private int validStepsCount = 1; //anticipate the 1st step is ok, next steps will be validated
		private long avgStepIntervalSum = 0; 

		@Override
		public void update(float sampleValue /* not used */, long sampleTimeInMilis) {
			
			//TODO refactor this mess!
			calculateStepInterval(sampleTimeInMilis);
			if (isStepIntervalInRange()) {
				
				avgStepIntervalSum += stepInterval;
				
				if (validStepsCount < 2) { // 2 intervals for variance calculation not measured yet
					previousStepInterval = stepInterval;
					validStepsCount++; // add step, next steps will be validated at the next run
					setHasValidSteps(false);
					return;
				}
				
				calculateStepIntervalVarianceFor(previousStepInterval);
				if (isStepIntervalVarianceInRange() == false) {
					/*  if any one out of the step no. 3,4,5 has variance out of range,
					 *  the step counting is restarted!
					 */
					avgStepIntervalSum = 0;
					stepIntervalVariance = 0;
					validStepsCount = 1; // reset and quit
					setHasValidSteps(false);
					return;
				}

				validStepsCount++;
				if (validStepsCount >= VALID_STEPS_COUNT) {
					stepCount += validStepsCount;
					avgStepInterval = avgStepIntervalSum / (validStepsCount - 1);
					setHasValidSteps(true);
					detectingStrategy = countingDetector; // steps validated, switch to counting
				}
			}
			else { // step interval out of range
				avgStepIntervalSum = 0;
				validStepsCount = 1; //reset to 1 - the 1st step is added
				previousStepInterval = stepInterval;
				setHasValidSteps(false);
			}
		
		}

		public void reset(long initPreviousStepTime, long initPreviousStepInterval) {
			previousStepTime = initPreviousStepTime;
			previousStepInterval = initPreviousStepInterval;
			validStepsCount = 1;
			avgStepIntervalSum = 0;
			avgStepInterval = 0;
			stepIntervalVariance = 0;
			setHasValidSteps(false);
		}
	
	}
	
	class CountingDetector implements StepDetectingStrategy {

		@Override
		public void update(float sampleValue, long sampleTimeInMilis) {
			
			long tmpPreviousStepTime = previousStepTime;
			long tmpPreviousStepInterval = previousStepInterval;
			
			calculateStepInterval(sampleTimeInMilis);
			if (isStepIntervalInRange()) {
				
				calculateStepIntervalVarianceFor(avgStepInterval);
				if (isStepIntervalVarianceInRange()) {
					
					stepCount++;
					setHasValidSteps(true);

				}
				else { // step interval variance out of range - switch to searching
					detectingStrategy = searchingDetector;
					searchingDetector.reset(tmpPreviousStepTime, tmpPreviousStepInterval);
					searchingDetector.update(sampleValue, sampleTimeInMilis);
				}
			}
			else { // step interval out of range - switch to searching
				detectingStrategy = searchingDetector;
				searchingDetector.reset(tmpPreviousStepTime, tmpPreviousStepInterval);
				searchingDetector.update(sampleValue, sampleTimeInMilis);
			}
				
		}

	}
	
    private static final float ALPHA = 0.8f; // constant of low pass filter for eliminating gravity from acceleration 

	private static final int MIN_STEP_INTERVAL = 200;  //ms, people can walk/run as fast as 5 steps/sec
	private static final int MAX_STEP_INTERVAL = 2000; //ms, people can walk/run as slow as 1 step/2sec
	private static final float MIN_STEP_INTERVAL_VARIANCE = 0.7F; //-30%
	private static final float MAX_STEP_INERVAL_VARIANCE = 1.3F;//+30%
    private static final int THRESHOLD_WINDOW_SIZE = 50;
	private Threshold threshold;
	private StepDetectingStrategy detectingStrategy;
	private SearchingDetector searchingDetector;
	private CountingDetector countingDetector;
	
	private int crossingThresholdCount;
	private int stepCount;
	private float lastSample;
	private float thresholdValue;
	private long previousStepTime;
	private boolean hasValidSteps;
	private long stepInterval;
	private long avgStepInterval;
	private long previousStepInterval;
	private float stepIntervalVariance;
	private float gravity;
	private float linearAcceleration;

    private static final int MOVING_AVG_WINDOW_SIZE = 10;
	private MovingAverage movingAvgCalculator = new MovingAverage(MOVING_AVG_WINDOW_SIZE);

	private float smoothedAcceleration;
	
	public StepDetector(Threshold t) {
		threshold = t;
		lastSample = 0;
		thresholdValue = 0;
		crossingThresholdCount = 0;
		stepCount = 0;
		stepInterval = 0;
		avgStepInterval = 0;
		previousStepTime = 0;
		previousStepInterval = 0;
		stepIntervalVariance = 0;
		hasValidSteps = false;
		gravity = 0;
		linearAcceleration = 0;
		smoothedAcceleration = 0; //we need this field for testing
		
		searchingDetector = new SearchingDetector();
		countingDetector = new CountingDetector();
		detectingStrategy = searchingDetector;
	}

	public StepDetector() {
		this(new Threshold(THRESHOLD_WINDOW_SIZE));
	}

	public void update(float newSample, long sampleTimeInMilis) {
		
		// digital filtering
		calculateLinearAcceleration(newSample);
		smoothLinearAcceleration();
		
		// dynamic threshold
		//TODO smoothedAcceleration - choose better names???
		calculateThreshold(smoothedAcceleration);
	
		setHasValidSteps(false); // will be set by detecting strategy if steps validated
		if (hasValidPeak() && isCrossingBelowThreshold(smoothedAcceleration)) {
			crossingThresholdCount++; //TODO this counter is for testing
			//detectingStrategy sets back the hasValidSteps flag!
			detectingStrategy.update(smoothedAcceleration, sampleTimeInMilis);
			restartPeakMeasurement(); //TODO better to put this into the update()
		}
		lastSample = smoothedAcceleration;
	}

	private void restartPeakMeasurement() {
		threshold.setCurrentMinMax(thresholdValue);
	}

	private boolean isCrossingBelowThreshold(float newSample) {
		return (lastSample > thresholdValue) && (newSample < thresholdValue);
	}

	private boolean hasValidPeak() {
		//TODO how to ignore low values?
		return threshold.getCurrentMaxValue() > 0.3F;
	}

	/**
	 * Calculates gravity-less values from acceleration samples by simple inverted Low Pass.
	 * @param acceleration - sensor samples 
	 */
	private void calculateLinearAcceleration(float acceleration) {
		//Low Pass
		gravity = ALPHA * gravity + (1 - ALPHA) * acceleration;
		//High Pass = Inverted Low Pass
		linearAcceleration = acceleration - gravity;
	}

	/**
	 * Smoothes values by Moving Average Filter
	 */
	private void smoothLinearAcceleration() {
		movingAvgCalculator.pushValue(linearAcceleration);
		smoothedAcceleration = movingAvgCalculator.getValue();
	}

    /**
     * Calculates thresholds for step detections
     */
	private void calculateThreshold(float newSample) {
		threshold.pushSample(newSample);
		thresholdValue = threshold.getThresholdValue();
	}

	private void calculateStepIntervalVarianceFor(long referenceValue) {
		if (stepInterval != 0) {
			//TODO performance optimization: use multiplication instead of division???
			stepIntervalVariance = referenceValue / ((float)stepInterval);
		}
		else {
			stepIntervalVariance = 0;
		}
		previousStepInterval = stepInterval;
	}

	private void calculateStepInterval(long sampleTimeInMilis) {
		stepInterval = sampleTimeInMilis - previousStepTime;
		previousStepTime = sampleTimeInMilis;
	}


	private boolean isStepIntervalVarianceInRange() {
		return stepIntervalVariance >= MIN_STEP_INTERVAL_VARIANCE && stepIntervalVariance <= MAX_STEP_INERVAL_VARIANCE;
	}

	private boolean isStepIntervalInRange() {
		return stepInterval >= MIN_STEP_INTERVAL && stepInterval <= MAX_STEP_INTERVAL;
	}

	private void setHasValidSteps(boolean value) {
		hasValidSteps = value;
	}

	public boolean hasValidSteps() {
		return hasValidSteps;
	}

	public int getStepCount() {
		return stepCount;
	}
	
	public int getCrossingThresholdCount() {
		return crossingThresholdCount;
	}

	public long getStepInterval() {
		return stepInterval;
	}

	public long getAvgStepInterval() {
		return avgStepInterval;
	}

	public float getStepIntervalVariance() {
		return stepIntervalVariance;
	}
	
	public float getThresholdValue() {
		return thresholdValue;
	}
	
	public float getFixedPeak2PeakValue() {
		return threshold.getFixedPeak2PeakValue();
	}
	
	public float getCurrentPeak2PeakValue() {
		return threshold.getCurrentPeak2PeakValue();
	}

	public float getFixedMinValue() {
		return threshold.getFixedMinValue();
	}

	public float getFixedMaxValue() {
		return threshold.getFixedMaxValue();
	}

	float getLinearAcceleration() {
		return linearAcceleration;
	}

	float getSmoothedAcceleration() {
		return smoothedAcceleration;
	}
}
