package com.mridb.sliceRegister.mri;

import java.util.Map;
import java.util.TreeMap;

public class CostNiftiRigid implements ICostFunction {

	@Override
	public double calculate(Map<String, Object> data, double[] estimators) {

		AffineTransform sourceAndRef = (AffineTransform)data.get("sourceAndRef");
//		Double sourceTime = (double)data.get("sourceTime");
//		Double referenceTime = (double)data.get("referenceTime");
		Integer sliceNumber = null;
		if(data.containsKey("sliceNumber")) {
			sliceNumber = (Integer)data.get("sliceNumber");
		}
		Integer volumeNumber = (int)data.get("volumeNumber");
		double cost = 0;
		double freqScale = Double.NaN;
		if(estimators.length > 6) {
			freqScale = estimators[6];
		}
		double rX, rY, rZ;
		double tX, tY, tZ;
		if(estimators.length >= 6) {
			rX = estimators[0];
			rY = estimators[1];
			rZ = estimators[2];
			tX = estimators[3];
			tY = estimators[4];
			tZ = estimators[5];
		} else if(estimators.length >= 3) {
			tX = estimators[0];
			tY = estimators[1];
			tZ = estimators[2];
			rX = (double)data.get("xR");
			rY = (double)data.get("yR");
			rZ = (double)data.get("zR");
		} else if(estimators.length >= 2) {
			tX = estimators[0];
			tY = estimators[1];
			rX = (double)data.get("xR");
			rY = (double)data.get("yR");
			rZ = (double)data.get("zR");
			tZ = (double)data.get("zT");
		} else {
			tY = estimators[0];
			rX = (double)data.get("xR");
			rY = (double)data.get("yR");
			rZ = (double)data.get("zR");
			tX = (double)data.get("xT");
			tZ = (double)data.get("zT");
		}

		//		try {

		if(sliceNumber != null) {
			try {
//				cost = sourceAndRef.getSliceDifference(volumeNumber, sliceNumber, estimators[0], estimators[1], estimators[2], estimators[3], estimators[4], estimators[5], freqScale);
//				cost = sourceAndRef.getSliceDifference(volumeNumber, sliceNumber, rX, rY, rZ, tX, tY, tZ, freqScale);
				cost = sourceAndRef.getSliceDifference(volumeNumber, sliceNumber, rX, rY, rZ, tX, tY, tZ);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			try {
//				cost = sourceAndRef.getVolumeDifference(volumeNumber, estimators[0], estimators[1], estimators[2], estimators[3], estimators[4], estimators[5]);
//				cost = sourceAndRef.getSliceDifference(volumeNumber, sliceNumber, rX, rY, rZ, tX, tY, tZ, freqScale);
				cost = sourceAndRef.getVolumeDifference(volumeNumber, rX, rY, rZ, tX, tY, tZ);
//				cost = sourceAndRef.getSliceDifference(volumeNumber, sliceNumber, rX, rY, rZ, tX, tY, tZ);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	

		return cost;		
	}


	@Override
	public Map<String, String> getDataNames() {
		TreeMap<String, String> map = new TreeMap<String, String>();		
		map.put("sourceAndRef", "AffineTransform");
		map.put("volumeNumber", "Integer");
		map.put("sliceNumber", "Integer");
		return map;
	}

	@Override
	public int getEstimatorCount() {
		return 7;
	}
}
