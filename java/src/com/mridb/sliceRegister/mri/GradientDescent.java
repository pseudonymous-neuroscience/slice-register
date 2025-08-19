package com.mridb.sliceRegister.mri;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.mridb.sliceRegister.Parabola;
import com.mridb.sliceRegister.Util;
//import com.mridb.sliceRegister.plot.Plot;

public class GradientDescent {

	// cost function that is called for each probe
	private transient ICostFunction costFunction;
	private String costFunctionName = "";

	// data, which is constant across iterations and whose interpretation is handled by the cost function
	private Map<String, Object> data;

	// estimators, which change with each iteration
	private double[] estimators;			// initial value
	private double[] initialAdjustmentSpans;// initial span
	private double[] adjustmentSpans;		// initial span
	private double[] estimatorUpperLimits;	// upper limit
	private double[] estimatorLowerLimits;	// lower limit

	// probe stuff (details in setProbeDefaults())
	private double[] probeLocations;
	private double[] scaleFactors;
	private double[] initialProbeLocations;
	private double[] initialScaleFactors;

	private boolean useLargestScaleFactor = true; 
	private double mandatoryCostReductionFactor = 0; 
	//	private long[] iterationCutoffs;
	//	private double[] iterationProbeMultipliers;

	// finishing criteria
	private double adjustmentSizeThreshold = 1e-9;
	private double costImprovementThreshold = 0.001;
	private int maxStagnantIterations = 5;
	private int maxPristineIterations = 1000;
	private double maxIterations = 0; // zero => unlimited

	// probe method
	boolean useQuadradic = true;

	// runtime stuff
	private int verbose = 0;
	private boolean useParallel = false;


	// tracking
	private transient Instant computeStartTime;
	private transient Instant computeEndTime;
	private double computeSeconds;
	private long iterationCount;
	private double initialCost;
	private double finalCost;
	private String logPath;
	
	public GradientDescent clone() {
		GradientDescent result = new GradientDescent(this.costFunction);
		result.costFunctionName = costFunctionName;
		result.data = data; 
		result.estimators = copyArray(estimators);
		result.initialAdjustmentSpans = copyArray(this.initialAdjustmentSpans);
		result.adjustmentSpans= copyArray(this.adjustmentSpans);
		result.estimatorUpperLimits = copyArray(this.estimatorUpperLimits);
		result.estimatorLowerLimits= copyArray(this.estimatorLowerLimits);
		result.probeLocations = copyArray(this.probeLocations);
		result.scaleFactors = copyArray(this.scaleFactors);
		result.initialProbeLocations = copyArray(this.initialProbeLocations);
		result.initialScaleFactors = copyArray(this.initialScaleFactors);

		result.useLargestScaleFactor = this.useLargestScaleFactor; 
		result.mandatoryCostReductionFactor = this.mandatoryCostReductionFactor; 
		result.adjustmentSizeThreshold = this.adjustmentSizeThreshold;
		result.costImprovementThreshold = this.costImprovementThreshold;
		result.maxStagnantIterations = this.maxStagnantIterations;
		result.maxPristineIterations = this.maxPristineIterations;
		result.maxIterations = this.maxIterations;
		result.useQuadradic = this.useQuadradic;

		// runtime stuff
		result.verbose = this.verbose;
		result.useParallel = this.useParallel;
		// tracking
		result.computeStartTime = this.computeStartTime;
		result.computeEndTime = this.computeEndTime;
		result.computeSeconds = this.computeSeconds;
		result.iterationCount = this.iterationCount;
		result.initialCost = this.initialCost;
		result.finalCost = this.finalCost;
		result.logPath = this.logPath;
		return result;
	}
	public static double[] copyArray(double[] toCopy) {
		double[] result = new double[toCopy.length];
		for(int i = 0; i < result.length; i++) {
			result[i] = toCopy[i];
		}
		return result;
	}

	public GradientDescent(ICostFunction function) {
		this.data = new TreeMap<String, Object>();
		this.setCostFunction(function);
		this.setProbeDefaults();
		this.finalCost = Double.NaN;
		this.initialCost = Double.NaN;
	}
	public void setYokedScaleFactors(boolean value) {
		this.useLargestScaleFactor = value;
	}
	public double getMaxIterations() {
		return this.maxIterations;
	}
	public void setMaxIterations(double value) {
		this.maxIterations = value;
	}
	public void setMaxPristineIterations(int value) {
		this.maxPristineIterations = value;
	}
	public double getMandatoryCostReductionFactor() {
		return this.mandatoryCostReductionFactor;
	}
	public void setMandatoryCostReductionFactor(double value) {
		this.mandatoryCostReductionFactor = value;
	}
	public void setCostFunction(ICostFunction function) {		
		this.costFunction = function;
		this.setEstimatorCount(function.getEstimatorCount());
		this.costFunctionName = function.toString();
	}
	public void setEstimatorCount(int estimatorCount) {
		this.estimators = new double[estimatorCount];
		this.estimatorLowerLimits = new double[estimatorCount];
		this.estimatorUpperLimits = new double[estimatorCount];
		this.adjustmentSpans = new double[estimatorCount];
		this.initialAdjustmentSpans = new double[estimatorCount];
		for(int i = 0; i < this.estimators.length; i++) {
			this.estimators[i] = 1;
			this.estimatorLowerLimits[i] = Double.NaN;
			this.estimatorUpperLimits[i] = Double.NaN;
			this.adjustmentSpans[i] = 1;
			this.initialAdjustmentSpans[i] = 1;
		}
	}
	public void setLogPath(String value) {
		this.logPath = value;
	}

	public void setProbeDefaults() {
		// probe locations are centered on the optimal estimator found so far
		// they are offset by the location multiplied by the corresponding adjustment span
		// scaleFactors will change the adjustment span by multipyling by it.
		// probes in the middle should be less than 1, and probes on the edges
		// should be more than 1

		// fewer probe locations means less time to execute an iteration, but may be less stable.
		// same for scalefactors that may be far away from 1.
		this.setSymmetricProbes(new double[] {1.1, 0.95, 0.9});
		for(int i = 0; i < this.adjustmentSpans.length; i++) {
			this.initialAdjustmentSpans[i] = 1;
		}

		// true prevents premature narrowing of some scaleFactors before others
		this.useLargestScaleFactor = true;

		// this allows us to use more probes on the first few iterations, which helps
		// to avoid falling into local minima early on
		//		this.iterationCutoffs = new long[] {3L, 10L};
		//		this.iterationProbeMultipliers = new double[] {2, 1};
	}
	// helper function that takes a monotonically decreasing set of probe scales, 
	// creates a mirror of it, and sets the locations at regular intervals
	// from -0.5 to 0.5
	public void setSymmetricProbes(double[] descendingScaleFactors) {
		this.probeLocations = new double[descendingScaleFactors.length * 2 - 1];
		this.scaleFactors = new double[this.probeLocations.length];
		for(int i = 0; i < descendingScaleFactors.length; i++) {
			this.scaleFactors[i] = descendingScaleFactors[i];
			int ind = this.scaleFactors.length - i - 1;
			this.scaleFactors[ind] = descendingScaleFactors[i];
		}
		double interval = 1.0 / (this.probeLocations.length - 1);
		for(int i = 0; i < this.probeLocations.length; i++) {
			this.probeLocations[i] = -0.5 + i * interval;
		}
	}
	public void setUseQuadratic(boolean value) {
		this.useQuadradic = value;
	}

	public void putData(String key, Object value){this.data.put(key, value);}
	public Object getData(String key, Object value){return this.data.get(key);}
	public Iterator<String> getDataIterator(){return this.data.keySet().iterator();}
	public double[] getEstimators() {return this.estimators;}
	public double getEstimator(int index) {return this.estimators[index];}
	public double getEstimatorUpperLimit(int index) {return this.estimatorUpperLimits[index];}
	public double getEstimatorLowerLimit(int index) {return this.estimatorLowerLimits[index];}
	public double getAdjustmentSpan(int index) {return this.adjustmentSpans[index];}
	public double getInitialAdjustmentSpan(int index) {return this.initialAdjustmentSpans[index];}
	public void setEstimator(int index, double value) {this.estimators[index] = value;}
	public void setEstimatorUpperLimit(int index, double value) {this.estimatorUpperLimits[index] = value;}
	public void setEstimatorLowerLimit(int index, double value) {this.estimatorLowerLimits[index] = value;}
	public void setInitialAdjustmentSpan(int index, double value) {this.initialAdjustmentSpans[index] = value;}
	public double getAdjustmentSizeThreshold() {return this.adjustmentSizeThreshold;}
	public void setAdjustmentSizeThreshold(double value) {this.adjustmentSizeThreshold = value;}
	public double getCostImprovementThreshold() {return this.costImprovementThreshold;}
	public void setCostImprovementThreshold(double value) {this.costImprovementThreshold = value;}
	public int getMaxStagnantIterations() {return this.maxStagnantIterations;}
	public void setMaxStagnantIterations(int value) {this.maxStagnantIterations = value;}
	public int getVerbosity() {return this.verbose;}
	public void setVerbosity(int value) {this.verbose = value;}
	public boolean getParallel() {return this.useParallel;}
	public void setParallel(boolean value) {this.useParallel = value;}

	public double[] getProbeLocations() {return this.probeLocations;}
	public double[] getScaleFactors() {return this.scaleFactors;}
	public void setProbes(double[] locations, double[] scaleFactors) throws Exception {
		if(locations.length != scaleFactors.length) {
			throw new Exception("locations and scaleFactors must be the same length");
		}
		this.probeLocations = locations;
		this.scaleFactors = scaleFactors;
	}
	public void removeEstimator(int index) throws Exception {
		this.estimatorLowerLimits = remove(this.estimatorLowerLimits, index);
		this.estimatorUpperLimits = remove(this.estimatorUpperLimits, index);
		this.estimators = remove(this.estimators, index);
		this.initialAdjustmentSpans = remove(this.initialAdjustmentSpans, index);
		this.adjustmentSpans = remove(this.adjustmentSpans, index);
	}
	private static double[] remove(double[] input, int index) throws Exception {
		if(index < 0 || index >= input.length) { throw new Exception("invalid index " + index); }
		double[] result = new double[input.length - 1];
		int counter = 0;
		for(int i = 0; i < input.length; i++) {
			if(i != index) {
				result[counter++] = input[i];
			}
		}
		return result;
	}


	public void setLinearProbeArray(int count, double maxScaleFactor, double minScaleFactor) {

		// create symmetric probes from the new count
		int halfCount = (int)Math.ceil(count / 2);
		double[] newScales = new double[halfCount];
		double interval = (maxScaleFactor - minScaleFactor) / (halfCount - 1);
		for(int i = 0; i < newScales.length; i++) {
			newScales[i] = maxScaleFactor - i * interval; 
		}
		this.setSymmetricProbes(newScales);
	}

	public static double getMin(double[] values) {
		double result = Double.MAX_VALUE;
		for(int i = 0; i < values.length; i++) {
			if(values[i] < result) {
				result = values[i];
			}
		}
		return result;
	}
	public static int getMinInd(double[] values) {
		double result = Double.MAX_VALUE;
		int ind = -1;
		for(int i = 0; i < values.length; i++) {
			if(values[i] < result) {
				result = values[i];
				ind = i;
			}
		}
		if(ind == -1) {
			ind = values.length / 2;
		}
		return ind;
	}
	public static double getMax(double[] values) {
		double result = -Double.MAX_VALUE;
		for(int i = 0; i < values.length; i++) {
			if(values[i] > result) {
				result = values[i];
			}
		}
		return result;
	}
	public static int getMaxInd(double[] values) {
		double result = -Double.MAX_VALUE;
		int ind = -1;
		for(int i = 0; i < values.length; i++) {
			if(values[i] > result) {
				result = values[i];
				ind = i;
			}
		}
		return ind;
	}
	public static double[] clone(double[] values) {
		double[] result = new double[values.length];
		for(int i = 0; i < values.length; i++) {
			result[i] = values[i];
		}
		return result;
	}

	public void compute() throws IOException {

		// start logging
		BufferedWriter writer = null;
		if(this.logPath != null && logPath.length() > 0) {
			Util.ensureFileHasFolder(logPath);
			File logFile = new File(logPath);
			StandardOpenOption fileMode = StandardOpenOption.CREATE;
			if(logFile.exists()) {
				fileMode = StandardOpenOption.APPEND;
			}
			writer = java.nio.file.Files.newBufferedWriter(logFile.toPath(), StandardCharsets.UTF_8, fileMode);
			//			writer.write("*** Start Gradient Descent ***\n");
		}

		// allocate resources for parallel processing


		// set epsilon for determining cost equivalence
		double epsilon = this.costImprovementThreshold * 0.8;
		long invalidParabolicMinimumCount = 0;


		// copy initial values
		for(int i = 0; i < this.adjustmentSpans.length; i++) {
			this.adjustmentSpans[i] = this.initialAdjustmentSpans[i];
		}
		this.initialProbeLocations = new double[this.probeLocations.length];
		this.initialScaleFactors = new double[this.scaleFactors.length];
		for(int i = 0; i < this.probeLocations.length; i++) {
			this.initialProbeLocations[i] = this.probeLocations[i];
		}
		for(int i = 0; i < this.scaleFactors.length; i++) {
			this.initialScaleFactors[i] = this.scaleFactors[i];
		}

		// allocate probes. we are counting on the highest multiplier
		// being first; because probes must be final, it may be larger
		// than the number of probeLocations being actually used
		//		if(false) {
		//			this.multiplyProbes();
		//		}
		double[] probes;
		if(this.useQuadradic) {
//			probes = new double[2];			
			probes = new double[3];
		} else {
			probes = new double[this.probeLocations.length];
		}
		double[] probeCosts = new double[probes.length];
		double[] probeSurplus = new double[probes.length];
		final double[][] estimatorCopies = new double[probes.length][];
		if(useParallel) {
			for(int j = 0; j < estimatorCopies.length; j++) {
				estimatorCopies[j] = new double[this.estimators.length];
			}
		}


		int stagnantIterations = 0;
		boolean isPristine = true;

		//		ArrayList<Integer> minInds = new ArrayList<Integer>();


		// set timer
		this.iterationCount = 0;
		this.computeStartTime = Instant.now();

		// compute the first cost
		double cost = costFunction.calculate(data, estimators);	    
		if(writer != null) {
			//			String logLine = Util.dateString() + ": "
			String logLine = "{\"intialCost\": " + Double.toString(cost);
			writer.write(logLine);
			writer.write(", \"iterations\": [");
		}
		double oldMinCost = cost;
		double minCost = cost;
		this.initialCost = cost;

		boolean doPlot = false;
//		Plot p = null;
		
		double minScaleFactor = getMin(this.scaleFactors);
		double maxScaleFactor = getMax(this.scaleFactors);

		// iteration loop
		boolean finished = false;
		double minAdjustmentSize = Double.MAX_VALUE;
		while(!finished) {			
			this.iterationCount++;
			if(writer != null) {
				writer.write("\n{\"iteration\": " + iterationCount + ", \"descents\": [");
			}

			double maxAdjustmentSize = Double.MIN_VALUE;

			// largestScaleFactor tracks the largest seen on this estimator loop
			// it starts at the smallest available
			double largestScaleFactor = Double.NaN;
			if(this.useLargestScaleFactor) {
				largestScaleFactor = Double.MAX_VALUE;
				for(int i = 0; i < this.scaleFactors.length; i++) {
					if(this.scaleFactors[i] < largestScaleFactor) {
						largestScaleFactor = this.scaleFactors[i];						
					}
				}
			}

			// resize the probes if called for by iterationCutoffs
			//			this.multiplyProbes();
			IndexGenerator indexGenerator = new IndexGenerator(probes.length);

			// estimator loop
			//			for(int estimatorNumber = 0; this.estimators[0] < this.estimators.length; estimatorNumber++) {
			for(int estimatorNumber = 0; estimatorNumber < this.estimators.length; estimatorNumber++) {

				// set probe locations
				double oldEstimator = estimators[estimatorNumber];
				double adjustmentSpan = adjustmentSpans[estimatorNumber];
				double lowerLimit = this.estimatorLowerLimits[estimatorNumber];
				double upperLimit = this.estimatorUpperLimits[estimatorNumber];
				boolean outsideUpperLimit = false;
				boolean outsideLowerLimit = false;
				if(!this.useQuadradic) {
					for(int i = 0; i < this.probeLocations.length; i++) {
						probes[i] = oldEstimator + adjustmentSpan * this.probeLocations[i];
						if(probes[i] < lowerLimit) {
							outsideLowerLimit = true;
						}
						if(probes[i] > upperLimit) {
							outsideUpperLimit = true;
						}
					}
				} else {
					for(int i = 0; i < probes.length; i++) {
						double probeLoc;
						if(i == 0) {
							probeLoc = this.probeLocations[i];
						} else if(i == probes.length - 1) {
							probeLoc = this.probeLocations[this.probeLocations.length - 1];
						} else {
							probeLoc = 0;
						}
						probes[i] = oldEstimator + adjustmentSpan * probeLoc;
						if(probes[i] < lowerLimit) {
							outsideLowerLimit = true;
						}
						if(probes[i] > upperLimit) {
							outsideUpperLimit = true;
						}
					}

				}

				// readjust if any probes are outside the limits
				// (and the adjustment span if it's bigger than max)
				double maxSpan = this.estimatorUpperLimits[estimatorNumber] - this.estimatorLowerLimits[estimatorNumber];
				if(!Double.isNaN(maxSpan)) {
					double differenceFromMax = this.adjustmentSpans[estimatorNumber] - maxSpan;
					if(Math.abs(differenceFromMax) / maxSpan < 1e-6) {
						// avoid getting stuck on the edge by trying random locations
//						this.adjustmentSpans[estimatorNumber] *= 0.5;
						probes[0] = this.estimators[estimatorNumber];
						for(int i = 1; i < probes.length; i++) {
							probes[i] = this.estimatorLowerLimits[estimatorNumber] + Math.random() * maxSpan;
						}
						
						////						try zooming in far to see if we can find descending slope
						//						// close to the edge
						//						double logBig = Math.log(this.adjustmentSpans[estimatorNumber]);
						//						double logSmall = Math.log(minAdjustmentSize);
						//						double bigWeight = 0.3;
						//						double logAvg = logBig * bigWeight + logSmall * (1 - bigWeight);
						//						double newAvg = Math.exp(logAvg);
						//						this.adjustmentSpans[estimatorNumber] = newAvg;
						//						double lowDiff = oldEstimator - this.estimatorLowerLimits[estimatorNumber]; 
						//						double highDiff = this.estimatorUpperLimits[estimatorNumber] - oldEstimator;
						//						if(Math.abs(highDiff) < Math.abs(lowDiff)) {
						//							outsideUpperLimit = true;
						//						} else {
						//							outsideLowerLimit = true;
						//						}
						
					}
				}
				if(outsideUpperLimit && outsideLowerLimit) {
					// we never seem to reach this code because it's capped at the end of the loop
					double minProbe = this.estimatorLowerLimits[estimatorNumber];
					double maxProbe = this.estimatorUpperLimits[estimatorNumber];
					double interval = (maxProbe - minProbe) / (probes.length - 1);
					for(int i = 0; i < probes.length; i++) {
						probes[i] = minProbe + i * interval;
					}
					this.adjustmentSpans[estimatorNumber] = maxSpan;
				}else if(outsideUpperLimit) {
//					double maxSpan = Double.NaN;
//					if(!Double.isNaN(this.estimatorLowerLimits[estimatorNumber])) {
//						maxSpan = this.estimatorUpperLimits[estimatorNumber] - this.estimatorLowerLimits[estimatorNumber];
//					}
					if(!Double.isNaN(maxSpan) && this.adjustmentSpans[estimatorNumber] > maxSpan) {
						this.adjustmentSpans[estimatorNumber] = maxSpan;						
					}

					double maxProbe = this.estimatorUpperLimits[estimatorNumber];					
					double interval = this.adjustmentSpans[estimatorNumber] / (probes.length - 1);
					for(int i = 0; i < probes.length; i++) {
						probes[probes.length - i - 1] = maxProbe - i * interval;
					}
				}else if(outsideLowerLimit) {
//					double maxSpan = Double.NaN;
//					if(!Double.isNaN(this.estimatorUpperLimits[estimatorNumber])) {
//						maxSpan = this.estimatorUpperLimits[estimatorNumber] - this.estimatorLowerLimits[estimatorNumber];
//					}
					if(!Double.isNaN(maxSpan) && this.adjustmentSpans[estimatorNumber] > maxSpan) {
						this.adjustmentSpans[estimatorNumber] = maxSpan;						
					}

					double minProbe = this.estimatorLowerLimits[estimatorNumber];
					double interval = this.adjustmentSpans[estimatorNumber] / (probes.length - 1);
					for(int i = 0; i < probes.length; i++) {
						probes[i] = minProbe + i * interval;
					}
				}

				// copy estimators for parallel use
				if(useParallel) {
					for(int i = 0; i < probes.length; i++) {
						for(int j = 0; j < this.estimators.length; j++) {
							if(j == estimatorNumber) {
								estimatorCopies[i][j] = probes[i];								
							} else {
								estimatorCopies[i][j] = this.estimators[j];
							}														
						}
					}
				}

				// calculate costs	
				if(useParallel) {
					indexGenerator.getStream().forEach(i -> {
						int ii = (int) i;
						probeCosts[ii] = costFunction.calculate(this.data, estimatorCopies[ii]);
					});		
				}else {
					for(int i = 0; i < probes.length; i++) {
						this.estimators[estimatorNumber] = probes[i];
						probeCosts[i] = costFunction.calculate(this.data, this.estimators);
					}
				}



				// find the probe with the lowest cost
				double minProbe = oldEstimator;
				double scaleFactor = 1;
				if(!this.useQuadradic) {
					minCost = Double.MAX_VALUE;
					int minCostInd = -1;
					for(int i = 0; i < probes.length; i++) {
						if(probeCosts[i] < minCost) {
							minCost = probeCosts[i];
						}
					}

					// debug
					if(minCost < 0) {
						int dummy = 1;
					}
					// end debug

					// the selected probe is the middle one with the lowest cost;
					// if multiple probes are within epsilon of each other,
					// select the middle one
					//				minInds.clear();
					double indSum = 0;
					double indCount = 0;
					int lastInd = -1;
					for(int i = 0; i < probeCosts.length; i++) {
						probeSurplus[i] = probeCosts[i] - minCost;
						if(probeSurplus[i] < epsilon) {
							// don't select the middle one if there is a discontinuty
							//						minInds.add(i); 
							if(indCount == 0) {
								indSum += i;
								indCount++;							
							}else if(lastInd > -1){
								if(i - lastInd == 1) {
									indSum += i;
									indCount++;								
								}else {
									int dummy = 1;
								}

							}
							lastInd = i;
						}
					}
					minCostInd = (int)Math.round(indSum / indCount);
					minProbe = probes[minCostInd];
					scaleFactor = scaleFactors[minCostInd];
					// debug
					if(minProbe < 0){
						int dummy = 1;						
					}
					// end debug
				} else { // use quadratic to find min cost
//					double x2 = oldEstimator;
//					double y2 = oldMinCost;
					double x2 = probes[1];
					double y2 = probeCosts[1];
					double x1 = probes[0];
					double y1 = probeCosts[0];
					double x3 = probes[2];
					double y3 = probeCosts[2];
					Parabola prab = Parabola.create(x1,  y1,  x2, y2, x3, y3);

					if(prab.isValidMinimum()) {
						// verify vertex is better than others
						double vertexProbe = prab.getVertexX();
						double vertexCost = prab.getVertexY();
//						double actualCost = this.costFunction();
						//	only track actual costs, not interpolated ones
						int minInd = getMinInd(probeCosts);
						minCost = probeCosts[minInd];
						minProbe = probes[minInd];
						if(minInd == 1) {
							scaleFactor = minScaleFactor;
						} else {
							scaleFactor = maxScaleFactor;
						}
//						scaleFactor = scaleFactors[minInd];
						double[] est = clone(this.estimators);
						est[estimatorNumber] = vertexProbe;
						double verifyCost = costFunction.calculate(this.data, est);
						if(verifyCost < minCost) {
							// clamp scaleFactor and probe to prevent 
							// extrapolating into extremes
							minCost = verifyCost;
							minProbe = vertexProbe;
							if(minProbe < probes[0]) {
								scaleFactor = maxScaleFactor;
								minProbe = probes[0];
							} else if(minProbe > probes[probes.length - 1]) {
								scaleFactor = maxScaleFactor;
								minProbe = probes[probes.length - 1];
							} else {
								// interpolate
								double probeVec = (minProbe - probes[1]) / (probes[2] - probes[1]);
								double interpScaleFactor = minScaleFactor + (maxScaleFactor - minScaleFactor) * Math.abs(probeVec);
								scaleFactor = interpScaleFactor;
							}
						}
						
					} else { // parabola didn't find valid minimum
						invalidParabolicMinimumCount++;
						int minInd = getMinInd(probeCosts);
						minCost = probeCosts[minInd];
						scaleFactor = this.scaleFactors[minInd];
						minProbe = probes[minInd];
					} // parabola found valid minimum conditional
					
				} // find min cost block (quadratic conditional)


				// log results
				if(this.logPath != null && !this.logPath.isEmpty()) {
					Duration dur = Duration.between(computeStartTime, Instant.now());
					double seconds = ((double)dur.toMillis()) / 1000.0;
					String toLog = String.format(
							//							"{\"iteration\": %d, \"estimatorNumber\": %d, \"seconds\": %f, \"minProbe\": %f, \"minCost\": %f, \"probes\": [", 
							//							this.iterationCount, estimatorNumber, seconds, minProbe, minCost);
							"{\"estimatorNumber\": %d, \"seconds\": %f, \"minProbe\": %f, \"minCost\": %f, \"probes\": [", 
							estimatorNumber, seconds, minProbe, minCost);
					writer.append("\n" + toLog);
					for(int i = 0; i < probes.length; i++) {
						writer.append(String.format("%f", probes[i]));
						if(i < probes.length - 1) {
							writer.append(", ");
						} else {
							writer.append("], \"probeCosts\": [");							
						}
					}
					for(int i = 0; i < probeCosts.length; i++) {
						writer.append(String.format("%f", probeCosts[i]));
						if(i < probes.length - 1) {
							writer.append(", ");
						} else {
							writer.append("]}");
						}
					}

					if(estimatorNumber < (this.estimators.length - 1)) {
						writer.write(", ");
					} else {
						writer.write("], ");						
					}
				} // log conditional

				// update
				this.estimators[estimatorNumber] = minProbe;

				// copy new optimum to estimator copies
				if(useParallel) {
					for(int i = estimatorNumber + 1; i < this.estimators.length; i++) {
						for(int j = 0; j < probes.length; j++) {
							estimatorCopies[j][estimatorNumber] = minProbe;
						}
					}
				}

				//	adjust the probe span based on how close to the center the
				//	minimum is.  if it's on the edge, then it needs to grow.
				if(!useLargestScaleFactor) {
					this.adjustmentSpans[estimatorNumber] *= scaleFactor;
					if(this.adjustmentSpans[estimatorNumber] > maxAdjustmentSize) {
						maxAdjustmentSize = this.adjustmentSpans[estimatorNumber];
					}
					if(this.adjustmentSpans[estimatorNumber] < minAdjustmentSize) {
						minAdjustmentSize = this.adjustmentSpans[estimatorNumber];
					}

				} else {
					if(largestScaleFactor < scaleFactor) {
						largestScaleFactor = scaleFactor;
					}
				}

			} // estimator loop

			if(useLargestScaleFactor) {
				for(int i = 0; i < this.adjustmentSpans.length; i++) {
					this.adjustmentSpans[i] *= largestScaleFactor;
					maxAdjustmentSize = Math.max(this.adjustmentSpans[i], maxAdjustmentSize);
				}
			}

			// check stopping criteria
			double costChange = oldMinCost - minCost;
			if(Double.isNaN(costChange)) {
				if(Double.isInfinite(oldMinCost) && Double.isInfinite(minCost)) {
					if(oldMinCost > 0 && minCost > 0) {
						costChange = 0;
					} else if(oldMinCost < 0 && minCost < 0) {
						costChange = 0;
					}
				} else if(Double.isNaN(oldMinCost) && Double.isNaN(minCost)) {
					costChange = 0;
				}
			}
			if(costChange < this.costImprovementThreshold 
					|| maxAdjustmentSize < this.adjustmentSizeThreshold) {
				stagnantIterations++;
				if(stagnantIterations > this.maxStagnantIterations) {
					if(Double.isNaN(mandatoryCostReductionFactor) || mandatoryCostReductionFactor == 0) {
						finished = true;												
					} else if(this.initialCost / minCost >= mandatoryCostReductionFactor) {
						finished = true;																		
					}
				}
			} else {
				stagnantIterations = 0;
			}
			// track whether any changes have happened
			if(isPristine) {
//				double difference = this.estimators[estimatorNumber] - minProbe;
//				if(Math.abs(difference) > epsilon) {
				if(Math.abs(costChange) > epsilon) {
					isPristine = false;
				}
			}

			oldMinCost = minCost;

			if(maxIterations > 0) {
				if(this.iterationCount > this.maxIterations) {
					finished = true;
				}
			}

			if(isPristine) {
				if(this.iterationCount < this.maxPristineIterations) {
					finished = false;
				}
			}

			//	        
			//	        % display progress to the user
			//	        if(mod(iterationCounter, iterationsPerUpdate) == 0)
			//	            fprintf('%s: iteration %d, cost %d, cost change %d, adjustment %f\n', lesson.misc.dateString(), iterationCounter, minCost, costChange, mean(mean(adjustmentSpans)));

			//			String logLine = Util.dateString() 
			String logLine = "" 
					//					+ "\"iteration\": " + Long.toString(this.iterationCount)
					+ "\"cost\": " + Double.toString(minCost) 
					+ ", \"change\": " + Double.toString(costChange)
					+ ", \"adjustment\": " + Double.toString(maxAdjustmentSize)
					+ ", ";

			if(writer != null) {
				writer.write("\n" + logLine + "\n\"estimators\": [");
				for(int i = 0; i < this.estimators.length; i++) {
					writer.write(Double.toString(this.estimators[i]));
					if(i < estimators.length - 1) {
						writer.write(", ");
					}else {
						writer.write("]}");						
					}
				}
				if(finished) {
					writer.write("]");
				} else {
					writer.write(",");
				}
			}

			if(verbose > 0) {
				if(this.iterationCount % verbose == 0) {
					System.out.println("\"iteration\": " + Long.toString(this.iterationCount) + ", " + logLine);
				}
			}

			int dummy = 1;
			//	        end

		} // iteration loop

		this.computeEndTime = Instant.now();
		this.getComputeSeconds();
		this.finalCost = minCost;
		if(writer != null) {
			writer.write(", \n\"parameters\": ");
			writer.write(Util.newGson().toJson(this));
			//			writer.write("\n*** End Gradient Descent ***\n\n");
			writer.write("\n}");
			writer.close();
		}
	} // method GradientDescentTuner::compute()



	public double getOptimizedCost() {
		return this.finalCost;
	}
	public long getIterationCount() {
		return this.iterationCount;
	}
	public double getComputeSeconds() {		
		Duration d = Duration.between(computeStartTime, computeEndTime);
		double seconds = d.getSeconds();
		seconds += d.getNano() * 0.000000001;
		this.computeSeconds = seconds;
		return seconds;
	}
	public String toString() {
		return Util.newGson().toJson(this);
	}

//	private static void oldTest() throws IOException {
//		double[][] source = new double[5][];
//		double[][] ref = new double[5][];
//		for(int i = 0; i < 5; i++) {
//			source[i] = new double[5];
//			ref[i] = new double[5];
//		}
//		source[3][3] = 1;
//		source[3][4] = 0.5;
//		ref[2][3] = 1;
//		ref[3][3] = 0.5;
//
//		Register2D reg = new Register2D(source, ref);
//		double[][] transformed = reg.getTransformed();
//		double rotation = reg.getRotation();
//		double xT = reg.getTranslateFirstDim();
//		double yT = reg.getTranslateSecondDim();
//
//		int dummy = 1;
//
//	}

	private static void testRegistration() throws Exception {
		//		String filePath = "C:\\data\\G1-1007\\mri\\ses-01\\func\\sub-G1-1007_01_ses-01_run-01_rsfMRI_601.nii.gz";
		String filePath = "C:\\data\\G1-1007\\mri\\ses-01\\func\\toy.nii.gz";
		String[] fileparts = Util.fileparts(filePath);
		AffineTransform t = new AffineTransform(filePath, filePath);
		t.setLogFolder(Util.fullfile(fileparts[0], fileparts[1] + "_logs"));
		t.setUseParallel(false);
		int sliceCount = t.getSource().getDimZ();
		int volumeCount = t.getSource().getDimT();
		Instant startTime = Instant.now();
		t.setGradientMaxScaleFactor(1.2);
		t.setGradientMinScaleFactor(0.5);
		AffineTransform.SliceRegistration volReg;
		AffineTransform.SliceRegistration sliceReg;
		for(int volumeNumber = 1; volumeNumber < volumeCount; volumeNumber++) {			
			t.setInitialSpanFraction(0.25);
			volReg = t.registerVolumeCubic(volumeNumber);
			for(int sliceNumber = 1; sliceNumber < sliceCount; sliceNumber++) {
				t.setInitialRotation(volReg.xR, volReg.yR, volReg.zR);
				t.setInitialTranslation(volReg.xT, volReg.xT, volReg.xT);
				t.setInitialSpanFraction(0.01);
				sliceReg = t.registerSlice(volumeNumber, sliceNumber);
			}
		}

		//		AffineTransform.SliceRegistration reg1 = t.registerVolumeCubic(1);
		//		Instant t1 = Instant.now();
		//		Duration d1 = Duration.between(startTime, t1);
		//		AffineTransform.SliceRegistration reg2 = t.registerSlice(1, 20);
		//		Instant t2 = Instant.now();
		//		Duration d2 = Duration.between(t1, t2);
		int dummy = 1;
	}

//	public static void main(String[] args) throws Exception {
//		testRegistration();
//	}

}
