package com.mridb.sliceRegister.mri;

import com.mridb.sliceRegister.mri.SliceRegistrationPerformer.RegistrationSchedule;
import com.mridb.sliceRegister.mri.SliceRegistrationPerformer.TrainingScale;

public class HardcodedRegirstrationSchedules {
	// most work is performed in at a quarter scale in the first step, 
	// with changes to the adjustment span per step. might get too precise
	// too quickly at the low level
	public static RegistrationSchedule getMark1() {
		RegistrationSchedule schedule = RegistrationSchedule.create();
		String[] scaleTxts = new String[]{"{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": true,"
				+ "    \"downSampleCount\": 2,"
				+ "    \"isParabolic\": true,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 100"
				+ "}", "{"
				+ "    \"adjustmentStart\": NaN,"
				+ "    \"maxScaleStart\": 1.2,"
				+ "    \"minScaleStart\": 0.8,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": true,"
				+ "    \"downSampleCount\": 1,"
				+ "    \"isParabolic\": true,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 10"
				+ "}", "{"
				+ "    \"adjustmentStart\": NaN,"
				+ "    \"maxScaleStart\": 1.2,"
				+ "    \"minScaleStart\": 0.8,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": false,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": true,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 3"
				+ "}", "{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.2,"
				+ "    \"minScaleStart\": 0.8,"
				+ "    \"maxScaleStart\": 1.1,"
				+ "    \"minScaleStart\": 0.8,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": false,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": false,"
				+ "    \"doSlices\": true,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 20"
				+ "}"};
		for(int i = 0; i < scaleTxts.length; i++) {
			TrainingScale scale = TrainingScale.fromString(scaleTxts[i]);
			schedule.addScale(scale);
		}
		return schedule;
	}
	
	
	// most work is performed in at a half scale in the first step, 
	// with changes to the adjustment span per step. might get too precise
	// too quickly at the low level
	public static RegistrationSchedule getMark2() {
		RegistrationSchedule schedule = RegistrationSchedule.create();
		String[] scaleTxts = new String[]{"{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": true,"
				+ "    \"downSampleCount\": 1,"
				+ "    \"isParabolic\": true,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 30"
				+ "}", "{"
				+ "    \"adjustmentStart\": 0.001,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": false,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": true,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 5"
				+ "}", "{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"maxScaleStart\": 1.1,"
				+ "    \"minScaleStart\": 0.8,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": false,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": true,"
//				+ "    \"isParabolic\": false,"
				+ "    \"doSlices\": true,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 20"
				+ "}"};
		for(int i = 0; i < scaleTxts.length; i++) {
			TrainingScale scale = TrainingScale.fromString(scaleTxts[i]);
			schedule.addScale(scale);
		}
		return schedule;
	}


	
	// most work is performed in at a half scale in the first step, 
	// with changes to the adjustment span per step. might get too precise
	// too quickly at the low level
	public static RegistrationSchedule getMark3() {
		RegistrationSchedule schedule = RegistrationSchedule.create();
		String[] scaleTxts = new String[]{"{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": true,"
				+ "    \"downSampleCount\": 1,"
				+ "    \"isParabolic\": true,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 30"
				+ "}", "{"
				+ "    \"adjustmentStart\": 0.001,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": false,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": true,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 5"
				+ "}", "{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"maxScaleStart\": 1.1,"
				+ "    \"minScaleStart\": 0.8,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": false,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": true,"
//				+ "    \"isParabolic\": false,"
				+ "    \"doSlices\": true,"
				+ "    \"doFrequencyScaling\": true,"
				+ "    \"maxStagnantIterations\": 20"
				+ "}"};
		for(int i = 0; i < scaleTxts.length; i++) {
			TrainingScale scale = TrainingScale.fromString(scaleTxts[i]);
			schedule.addScale(scale);
		}
		return schedule;
	}

	// most work is performed in at a half scale in the first step, 
	// with changes to the adjustment span per step. might get too precise
	// too quickly at the low level
	public static RegistrationSchedule getMark4() {
		RegistrationSchedule schedule = RegistrationSchedule.create();
		String[] scaleTxts = new String[]{"{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": true,"
				+ "    \"downSampleCount\": 1,"
				+ "    \"isParabolic\": false,"
				+ "    \"degreesOfFreedom\": 6,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 30"
				+ "}", "{"
				+ "    \"adjustmentStart\": 0.001,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": false,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": false,"
				+ "    \"degreesOfFreedom\": 6,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 20"
				+ "}", "{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"maxScaleStart\": 1.1,"
				+ "    \"minScaleStart\": 0.8,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": false,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": false,"
				+ "    \"degreesOfFreedom\": 6,"
				+ "    \"doSlices\": true,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"maxStagnantIterations\": 20"
				+ "}"};
		for(int i = 0; i < scaleTxts.length; i++) {
			TrainingScale scale = TrainingScale.fromString(scaleTxts[i]);
			schedule.addScale(scale);
		}
		return schedule;
	}

	public static RegistrationSchedule getMark5() {
		RegistrationSchedule schedule = RegistrationSchedule.create();
		String[] scaleTxts = new String[]{"{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": true,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": true,"
				+ "    \"doSlices\": false,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"useFsl\": true,"
				+ "    \"degreesOfFreedom\": 6,"
				+ "    \"maxStagnantIterations\": 30"
				+ "}", "{"
				+ "    \"adjustmentStart\": 0.01,"
				+ "    \"maxScaleStart\": 1.5,"
				+ "    \"minScaleStart\": 0.5,"
				+ "    \"maxScaleStart\": 1.1,"
				+ "    \"minScaleStart\": 0.8,"
				+ "    \"stopThresholdStart\": 1.0E-12,"
				+ "    \"tweakStopThreshold\": false,"
				+ "    \"downSampleCount\": 0,"
				+ "    \"isParabolic\": true,"
//				+ "    \"isParabolic\": false,"
				+ "    \"doSlices\": true,"
				+ "    \"doFrequencyScaling\": false,"
				+ "    \"degreesOfFreedom\": 1,"
				+ "    \"maxStagnantIterations\": 20"
//				+ "}", "{"
//				+ "    \"adjustmentStart\": 0.01,"
//				+ "    \"maxScaleStart\": 1.5,"
//				+ "    \"minScaleStart\": 0.5,"
//				+ "    \"maxScaleStart\": 1.1,"
//				+ "    \"minScaleStart\": 0.8,"
//				+ "    \"stopThresholdStart\": 1.0E-12,"
//				+ "    \"tweakStopThreshold\": false,"
//				+ "    \"downSampleCount\": 0,"
//				+ "    \"isParabolic\": true,"
////				+ "    \"isParabolic\": false,"
//				+ "    \"doSlices\": true,"
//				+ "    \"doFrequencyScaling\": false,"
//				+ "    \"degreesOfFreedom\": 2,"
//				+ "    \"maxStagnantIterations\": 20"
//				+ "}", "{"
//				+ "    \"adjustmentStart\": 0.01,"
//				+ "    \"maxScaleStart\": 1.5,"
//				+ "    \"minScaleStart\": 0.5,"
//				+ "    \"maxScaleStart\": 1.1,"
//				+ "    \"minScaleStart\": 0.8,"
//				+ "    \"stopThresholdStart\": 1.0E-12,"
//				+ "    \"tweakStopThreshold\": false,"
//				+ "    \"downSampleCount\": 0,"
//				+ "    \"isParabolic\": true,"
////				+ "    \"isParabolic\": false,"
//				+ "    \"doSlices\": true,"
//				+ "    \"doFrequencyScaling\": false,"
//				+ "    \"degreesOfFreedom\": 3,"
//				+ "    \"maxStagnantIterations\": 20"
//				+ "}", "{"
//				+ "    \"adjustmentStart\": 0.01,"
//				+ "    \"maxScaleStart\": 1.5,"
//				+ "    \"minScaleStart\": 0.5,"
//				+ "    \"maxScaleStart\": 1.1,"
//				+ "    \"minScaleStart\": 0.8,"
//				+ "    \"stopThresholdStart\": 1.0E-12,"
//				+ "    \"tweakStopThreshold\": false,"
//				+ "    \"downSampleCount\": 0,"
//				+ "    \"isParabolic\": true,"
////				+ "    \"isParabolic\": false,"
//				+ "    \"doSlices\": true,"
//				+ "    \"doFrequencyScaling\": false,"
//				+ "    \"degreesOfFreedom\": 6,"
//				+ "    \"maxStagnantIterations\": 20"
				+ "}"};
		for(int i = 0; i < scaleTxts.length; i++) {
			TrainingScale scale = TrainingScale.fromString(scaleTxts[i]);
			schedule.addScale(scale);
		}
		return schedule;
	}

}
