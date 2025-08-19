package com.mridb.sliceRegister.mri;

import java.util.Map;

public interface ICostFunction {	
	public double calculate(Map<String, Object> data, double[] estimators);
	public Map<String, String> getDataNames();
	public int getEstimatorCount();
}
