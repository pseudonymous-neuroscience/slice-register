package com.mridb.sliceRegister.mri;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sound.sampled.SourceDataLine;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import com.mridb.sliceRegister.Executor;
import com.mridb.sliceRegister.Util;
import com.mridb.sliceRegister.clientInterface.gui.FileFinder;
import com.mridb.sliceRegister.mri.AffineTransform.SliceRegistration;


public class SliceRegistrationPerformer {


	public SliceRegistrationPerformer() {
	}

//	public static void reallyGo() throws Exception {
//
//		RegistrationSchedule set = RegistrationSchedule.create();
//		set.addScale();
//		set.addScale();
//		set.addScale();
//		String filePath = "G:\\data\\analyzed\\G1-1007\\toy.nii.gz";
//		set.addTrainingFile(filePath, false);
//		String[] fileparts = Util.fileparts(filePath);
//		String outputFolder = Util.fullfile(fileparts[0], fileparts[1] + "_logs");
//		set.setLogFolder(outputFolder);
//		set.tuneHyperparameters();
//
//		String outputPath = "G:\\data\\analyzed\\hyperParameters_" + Util.dateString() + ".json";
//		Util.save(outputPath, Util.newGson().toJson(set));
//
//		//		set.c
//
//
//	}

//	public static void thoroughSolve() {
//
//
//	}



	// stores metadata about training data used to generate hyperparameters
	public static class TrainingVolumePairing {
		// given by user
		String sourceFilePath;
		String referenceFilePath;
		int[] sourceVolumes;
		int referenceVolume;

		// from nifti file metadata
		int dimX;
		int dimY;
		int dimZ;
		int dimT;
		double pixDimX;
		double pixDimY;
		double pixDimZ;
		double pixDimT;

		Integer refDimX;
		Integer refDimY;
		Integer refDimZ;
		Integer refDimT;
		Double refPixDimX;
		Double refPixDimY;
		Double refPixDimZ;
		Double refPixDimT;


		int sourceVolumeDigitCount;
		int referenceVolumeDigitCount;


		public TrainingVolumePairing() {

		}


		private String getNiftiFileExtension() {
			return ".nii";
		}
		public String getSourceShardFilename(int volumeNumber) {
			String[] fileparts = Util.fileparts(this.sourceFilePath);
			String format = String.format("%%s_vol-%%0%dd%s", sourceVolumeDigitCount, getNiftiFileExtension());
			String result = String.format(format, fileparts[1], volumeNumber);
			return result;
		}
		public String getReferenceShardFilename() {
			String[] fileparts = Util.fileparts(this.referenceFilePath);
			String format = String.format("%%s_vol-%%0%dd%s", referenceVolumeDigitCount, getNiftiFileExtension());
			String result = String.format(format, fileparts[1], this.referenceVolume);
			return result;
		}
		public String[] getShardFilenames() {
			String[] result = new String[this.sourceVolumes.length + 1];
			for(int i = 0; i < this.sourceVolumes.length; i++) {
				result[i] = getSourceShardFilename(this.sourceVolumes[i]);
			}
			result[result.length - 1] = getReferenceShardFilename();
			return result;
		}
		public String getRegistrationFilename(int referenceVolume, int sourceVolume) {
			String[] fileParts = Util.fileparts(this.sourceFilePath);
			String sourceFile = fileParts[1];
			String outputFormat = String.format("%s_vol-%%0%dd-%%0%dd.json", sourceFile, this.referenceVolumeDigitCount, this.sourceVolumeDigitCount);
			String outputFile = String.format(outputFormat, referenceVolume, sourceVolume);
			return outputFile;
		}



		public static TrainingVolumePairing loadAllVolumes(String niftiFilePath, int referenceVolumeIndex) throws FileNotFoundException, IOException {
			Nifti header = Nifti.loadHeader(niftiFilePath);
			int dimT = header.getDimT();
			int[] volumes = new int[dimT - 1];
			int counter = 0;
			for(int i = 0; i < dimT; i++) {
				if(i != referenceVolumeIndex) {
					volumes[counter++] = i;					
				}
			}
			return load(niftiFilePath, volumes, referenceVolumeIndex);
		}

		public void setReferenceVolume(int index) {
			this.referenceVolume = index;
			this.sourceVolumes = new int[this.dimT - 1];
			int counter = 0;
			for(int i = 0; i < dimT; i++) {
				if(i != referenceVolume) {
					sourceVolumes[counter++] = i;					
				}
			}
		}
		public int getReferenceVolume() {
			return this.referenceVolume;
		}

		public static TrainingVolumePairing load(String niftiFilePath, int[] sourceVolumeIndexes, int referenceVolumeIndex) throws FileNotFoundException, IOException {

			return load(niftiFilePath, niftiFilePath, sourceVolumeIndexes, referenceVolumeIndex);
			//			TrainingVolumePairing pairing = new TrainingVolumePairing();
			//			pairing.sourceFilePath = niftiFilePath;
			//			pairing.referenceVolume = referenceVolumeIndex;
			//			pairing.sourceVolumes = new int[sourceVolumeIndexes.length];
			//			for(int i = 0; i < pairing.sourceVolumes.length; i++) {
			//				pairing.sourceVolumes[i] = sourceVolumeIndexes[i];
			//			}
			//			Nifti nii = Nifti.loadHeader(niftiFilePath);
			//			pairing.dimX = nii.getDimX();
			//			pairing.dimY = nii.getDimY();
			//			pairing.dimZ = nii.getDimZ();
			//			pairing.dimT = nii.getDimT();
			//			pairing.pixDimX = nii.getVoxelSizeX();
			//			pairing.pixDimY = nii.getVoxelSizeY();
			//			pairing.pixDimZ = nii.getVoxelSizeZ();
			//			pairing.pixDimT = nii.getRepetitionTime();
			//			return pairing;
		}

		public static TrainingVolumePairing load(String sourceNiftiFilePath, String referenceNiftiFilePath, int[] sourceVolumeIndexes, int referenceVolumeIndex) throws FileNotFoundException, IOException {
			TrainingVolumePairing pairing = new TrainingVolumePairing();
			pairing.sourceFilePath = sourceNiftiFilePath;
			pairing.referenceFilePath = referenceNiftiFilePath;
			pairing.referenceVolume = referenceVolumeIndex;
			pairing.sourceVolumes = new int[sourceVolumeIndexes.length];
			for(int i = 0; i < pairing.sourceVolumes.length; i++) {
				pairing.sourceVolumes[i] = sourceVolumeIndexes[i];
			}
			Nifti nii = Nifti.loadHeader(sourceNiftiFilePath);
			pairing.dimX = nii.getDimX();
			pairing.dimY = nii.getDimY();
			pairing.dimZ = nii.getDimZ();
			pairing.dimT = nii.getDimT();
			pairing.pixDimX = nii.getVoxelSizeX();
			pairing.pixDimY = nii.getVoxelSizeY();
			pairing.pixDimZ = nii.getVoxelSizeZ();
			pairing.pixDimT = nii.getRepetitionTime();
			pairing.sourceVolumeDigitCount = (int)Math.floor(Math.log10(pairing.dimT)) + 1;

			if(referenceNiftiFilePath != null && !referenceNiftiFilePath.contentEquals(sourceNiftiFilePath)) {
				nii = Nifti.loadHeader(referenceNiftiFilePath);
			}
			pairing.refDimX = (int)nii.getDimX();
			pairing.refDimY = (int)nii.getDimY();
			pairing.refDimZ = (int)nii.getDimZ();
			pairing.refDimT = (int)nii.getDimT();
			pairing.refPixDimX = nii.getVoxelSizeX();
			pairing.refPixDimY = nii.getVoxelSizeY();
			pairing.refPixDimZ = nii.getVoxelSizeZ();
			pairing.refPixDimT = nii.getRepetitionTime();
			pairing.referenceVolumeDigitCount = (int)Math.floor(Math.log10(pairing.dimT)) + 1;

			return pairing;
		}


	} // class TrainingVolumePairSet (file & volume numbers used for hyperparameter tuning)

	// this reperesents a training scale, with several of them chained together to form a schedule.
	// smaller scales (more halvings) are faster to compute but ultimately less accurate.
	// how to chain together halvings up to full scale is the question. the hyperparameters 
	// represent how one scale is glued to the next. starting adjustment span, max and min scale factors,
	// and stopping threshold will all affect this. one thing we want to avoid is having the intial adjustment
	// span of the final (full) scale be too large, thereby stopping the before a good solution is found.
	// making sure the initial
	public static class TrainingScale {

		// default estimator starting points and limits
		double adjustmentStart = 0.5;
		transient double adjustmentLowerLimit = 1e-6;
		transient double adjustmentUpperLimit = 5; // this should be very small in the final (full) scale, but how small needs to be determined experimentally

		double maxScaleStart = 1.1;
		transient double maxScaleLowerLimit = 1 + 1e-6;
		transient double maxScaleUpperLimit = 5;

		double minScaleStart = 0.8;
		transient double minScaleUpperLimit = 1 - 1e-6;
		transient double minScaleLowerLimit = 1e-6;

		static final double FINAL_STOP_THRESHOLD = 1e-12; // used in the final scale to ensure
		double stopThresholdStart = 1e-12;
		boolean tweakStopThreshold = true;
		transient double stopThresholdUpperLimit = 1;
		transient double stopThresholdLowerLimit = 1e-100;

		int maxStagnantIterations = 20;
		boolean doSlices = false;
		boolean doFrequencyScaling = false;

		// measure of the degree this instance is scaled down. 0 indicates full scale, 1 is half, etc.
		int downSampleCount = 0;
		boolean isParabolic = false;

		// how to chain together optimizations
		transient boolean inheritSmallerScale = true; // this will inherit starting parameters (translation & rotation) from a smaller scale unless none exists 
		transient boolean inheritPreviousVolume = true; // this will inherit starting parameters (translation & rotation) from the previous volume unless none exists


		int degreesOfFreedom;
		//		boolean useFsl;


		public String toString() {
			return Util.newGson().toJson(this);
		}
		public static TrainingScale fromString(String json) {
			return Util.newGson().fromJson(json, TrainingScale.class);
		}

	} // class TrainingScale ()

	// this combines the previous two classes along with computer parameters to full specify a set of optimized hyperparameters
	public static class RegistrationSchedule {
		// inputs
		ArrayList<TrainingScale> scales; // these contain the specifications for each step of registration, containing hyperparameters
		ArrayList<TrainingVolumePairing> volumePairs; // data to be used for tweaking of hyperparameters
		boolean overwrite = false;
		boolean isSharding = true;
		boolean deferExecution = true;
		ArrayList<String> commands;

		// registration logs, per input file (multiple scales & volumes)
		ArrayList<AffineTransform> transformOps; // input granular operations  
		ArrayList<SliceRegistration> volumeRegs; // output volume registrations (all scales, all files)
		ArrayList<SliceRegistration> sliceRegs; // output slice registrations
		ArrayList<GradientDescent> descents; // gradient descent details, including adjustment spans to carry between scales

		// optional folder where individual registrations are logged
		String logFolder;

		// time to compute is the cost function's output when hyperparameters are tweaked
		double minTime;

		// data specific to the computer where optimization was performed 
		int processorCount;
		double processorSpeed;
		long maxMem;
		long availMem;


		private RegistrationSchedule() {
			this.commands = new ArrayList<String>();
			this.scales = new ArrayList<TrainingScale>();
			this.volumePairs = new ArrayList<TrainingVolumePairing>();
			this.transformOps = new ArrayList<AffineTransform>();
			this.descents = new ArrayList<GradientDescent>();
			this.volumeRegs = new ArrayList<SliceRegistration>();
			this.sliceRegs = new ArrayList<SliceRegistration>();
		}




		public void setLogFolder(String folder) {
			this.logFolder = folder;
		}
		public void setDeferExecution(boolean value) {
			this.deferExecution = value;
		}
		private void getComputerSpecs() {
			Runtime runtime = Runtime.getRuntime();
			this.processorCount = runtime.availableProcessors();
			this.maxMem = runtime.maxMemory();
			this.availMem = runtime.freeMemory();

			// no built-in method exists in Java for reporting raw processor
			// speed, so we perform a fixed number of operations and track
			// how long it takes. there are caveats here: bytecode gets compiled
			// after a certain number of iterations, competing processes can slow
			// down the execution, etc. this is a rough estimate when determining
			// whether previously computed hyperparameters are applicable to a
			// given machine.
			long startTime = System.nanoTime();
			long iterations = (long)1e8; 
			double sum = 0;
			for (long i = 0; i < iterations; i++) {
				// Perform some computation
				sum += Math.sqrt(i); 
			}
			long endTime = System.nanoTime();

			double timeTaken = (endTime - startTime) / 1e9; // Convert to seconds
			processorSpeed = iterations / timeTaken;
		}
		public static RegistrationSchedule create() {
			RegistrationSchedule set = new RegistrationSchedule();
			set.getComputerSpecs();
			return set;
		}
		public void addScale() {
			// create scale one iteration smaller than previous
			TrainingScale scale = new TrainingScale(); 
			int iterationCount = this.scales.size();
			if(iterationCount == 0) {
				// the final scale needs to have a tight adjustment span in order to avoid stopping prematurely
				scale.adjustmentStart = 0.000055;
				scale.adjustmentUpperLimit = 0.0055;
			} 
			scale.downSampleCount = iterationCount;
			// insert new at the beginning (smallest first)
			this.scales.add(0, scale);
		}
		public void addScale(TrainingScale scale) {
			this.scales.add(scale);
		}
		public String[] getCommands() {
			String[] result = new String[this.commands.size()];
			for(int i = 0; i < result.length; i++) {
				result[i] = commands.get(i);
			}
			return result;
		}

		// intended for matching e.g. an fMRI file, to a T2 reference volume
		public void addTrainingFile(String sourcePath, String referencePath, int[] sourceVolumes, int referenceVolume) throws FileNotFoundException, IOException {
			TrainingVolumePairing pairs = TrainingVolumePairing.load(sourcePath, referencePath, sourceVolumes, referenceVolume);
			this.volumePairs.add(pairs);
		}

		// intended for a single fMRI file, motion-correcting to a given reference volume from the same file
		public void addTrainingFile(String filePath, boolean allVolumes) throws FileNotFoundException, IOException {
			addTrainingFile(filePath, allVolumes, 0);
		}
		public void addTrainingFile(String filePath, boolean allVolumes, int referenceVolume) throws FileNotFoundException, IOException {
			TrainingVolumePairing pairs = TrainingVolumePairing.load(filePath, new int[0], referenceVolume);
			if(allVolumes) {
				int[] volumes = new int[pairs.dimT - 1];
				int counter = 0;
				for(int i = 0; i < pairs.dimT; i++) {
					if(i != referenceVolume) {
						volumes[counter] = i;
						counter++;
					}
				}
				pairs.sourceVolumes = volumes;

			} else {
				if(pairs.dimT <= 1) {
					pairs.sourceVolumes = new int[0];
				} else if(pairs.dimT == 2) {
					pairs.sourceVolumes = new int[] { 0 };
					if(pairs.sourceVolumes[0] == referenceVolume) { pairs.sourceVolumes[0] = 1; }
				} else if(pairs.dimT == 3) {
					pairs.sourceVolumes = new int[] { 0, 2 };
					if(pairs.sourceVolumes[0] == referenceVolume) { pairs.sourceVolumes[0] = 1; }
					if(pairs.sourceVolumes[1] == referenceVolume) { pairs.sourceVolumes[1] = 1; }
				} else {
					int mid = pairs.dimT / 2;
					int end = pairs.dimT - 1;
					pairs.sourceVolumes = new int[] { 0, mid, end };
					if(pairs.sourceVolumes[0] == referenceVolume) { pairs.sourceVolumes[0] = 1; }
					if(pairs.sourceVolumes[2] == referenceVolume) { pairs.sourceVolumes[2]--; }
					if(pairs.sourceVolumes[1] == referenceVolume) { pairs.sourceVolumes[1]++; }
				}

			}
			this.volumePairs.add(pairs);
		}

		public void tuneHyperparameters() throws Exception {
			if(this.volumePairs.size() <= 0) {
				throw new Exception("missing training data");
			}

			Instant startTime = Instant.now();

			// set up our "meta" gradient descent
			TrainingSetCost hyperCost = new TrainingSetCost(this);
			GradientDescent gd = new GradientDescent(hyperCost);
			//			gd.putData("tuner", tuna);
			//			int i = 0;

			//			GradientDescentTuner.getHardCoded();

			// set boundaries and initial values for estimators
			int estNumber = 0;
			for(int i = 0; i < this.scales.size(); i++) {
				TrainingScale scale = scales.get(i);
				gd.setEstimator(estNumber, 0.5);
				gd.setEstimatorLowerLimit(estNumber, 1e-6);
				gd.setEstimatorUpperLimit(estNumber, 5);
				estNumber++;				

				gd.setEstimator(estNumber, 1.1);
				gd.setEstimatorLowerLimit(estNumber, 1 + 1e-6);
				gd.setEstimatorUpperLimit(estNumber, 5);
				estNumber++;				

				gd.setEstimator(estNumber, 0.8);
				gd.setEstimatorUpperLimit(estNumber, 1 - 1e-6);
				gd.setEstimatorLowerLimit(estNumber, 1e-6);
				estNumber++;				

				if(scale.tweakStopThreshold) {
					gd.setEstimator(estNumber, SliceRegistrationPerformer.TrainingScale.FINAL_STOP_THRESHOLD);
					gd.setEstimatorUpperLimit(estNumber, 1);
					gd.setEstimatorLowerLimit(estNumber, 1e-100);
					estNumber++;				
				} else {

				}
			}

			gd.setCostImprovementThreshold(0.1);

			gd.compute();

			Instant endTime = Instant.now();

			double[] estimators = gd.getEstimators();
			double bestTime = gd.getOptimizedCost();
			estNumber = 0;
			for(int i = 0; i < this.scales.size(); i++) {
				TrainingScale scale = this.scales.get(i);
				// double adjustmentStart = 0.5;
				scale.adjustmentStart = estimators[estNumber++];
				// double maxScaleStart = 1.1;
				scale.maxScaleStart = estimators[estNumber++];
				// double minScaleStart = 0.8;
				scale.minScaleStart = estimators[estNumber++];
				if(scale.tweakStopThreshold) {
					scale.stopThresholdStart = estimators[estNumber++];
				}
				//				this.scales.remove(i);
				//				this.scales.add(i, scale);
			}
			this.minTime = bestTime;

		} // class TrainingVolumePairSet

		public List<SliceRegistration> apply() throws FileNotFoundException, IOException {
			return apply(new String[0]);
		}
		public List<SliceRegistration> apply(String filePath) throws FileNotFoundException, IOException {
			return apply(new String[] {filePath});
		}
		public List<SliceRegistration> apply(String[] filePaths) throws FileNotFoundException, IOException {
			ArrayList<SliceRegistration> result = new ArrayList<SliceRegistration>(); 

			// load input data files
			if(filePaths.length > 0) {
				this.volumePairs.clear();				
			}
			for(int i = 0; i < filePaths.length; i++) {
				TrainingVolumePairing pairs = TrainingVolumePairing.loadAllVolumes(filePaths[i], 0);
				this.volumePairs.add(pairs);
			}

			// prepare cost function for one-shot execution
			Map<String, Object> data = new TreeMap<String, Object>(); // leave blank (cost function is non-static so it pulls its data from the parent object)
			TrainingSetCost cost = new TrainingSetCost(this);

			// we aren't interested in timing so we will skip files that are already done
			data.put("overwrite", this.overwrite);
			data.put("useShards", this.isSharding);

			// these "estimators" will not be tweaked, apply() uses the
			// given hyperparameters to perform registration. so there is
			// no need to set upper and lower limits for estimators.
			int estCount = cost.getEstimatorCount();
			double[] estimators = new double[estCount];
			int estNumber = 0;
			int scaleCount = this.scales.size();
			for(int i = 0; i < scaleCount; i++) {
				TrainingScale scale = this.scales.get(i);
				estimators[estNumber++] = scale.adjustmentStart; 
				estimators[estNumber++] = scale.maxScaleStart; 
				estimators[estNumber++] = scale.minScaleStart;
				if(scale.tweakStopThreshold) {
					estimators[estNumber++] = scale.stopThresholdStart;
				}
			}
			this.createShards();
			cost.logEveryStep = true;

			// perform gradient descent, iterating through the scales for each file
			cost.calculate(data, estimators);

			// copy results to output
			for(int i = 0; i < this.volumeRegs.size(); i++) {
				result.add(this.volumeRegs.get(i));
			}
			for(int i = 0; i < this.sliceRegs.size(); i++) {
				result.add(this.sliceRegs.get(i));
			}
			return result;
		} // method RegistrationSchedule::apply()

		private static boolean contains(List<String> list, String value) {
			return indexOf(list, value) > -1;
		}
		private static int indexOf(List<String> list, String value) {
			for(int i = 0; i < list.size(); i++) {
				String v = list.get(i);
				if(v.contentEquals(value)) {
					return i;
				}
			}
			return -1;
		}
		private static void addUnique(List<String> list, String value) {
			if(!contains(list, value)) {
				list.add(value);
			}
		}
		public void createShards() throws FileNotFoundException, IOException {
			if(this.isSharding) {
				for(int i = 0; i < this.volumePairs.size(); i++) {
					boolean isNeeded = false;
					TrainingVolumePairing pairs = this.volumePairs.get(i);
					String[] shardNames = pairs.getShardFilenames();
					for(int j = 0; j < shardNames.length && !isNeeded; j++) {
						String shardPath = Util.fullfile(this.logFolder, shardNames[j]);
						File shardFile = new File(shardPath);
						if(!shardFile.exists()) {
							isNeeded = true;
						}
					}
					if(isNeeded) {
						if(deferExecution) {
							String inputFile = pairs.sourceFilePath;
							String outputFolder = Util.fullfile(this.logFolder, "logs");
							String command = String.format("shard -i \"%s\" -o \"%s\"", inputFile, outputFolder);
							addUnique(this.commands, command);
							if(pairs.referenceFilePath != null && !pairs.referenceFilePath.contentEquals(pairs.sourceFilePath)) {
								inputFile = pairs.referenceFilePath;
								command = String.format("shard -i \"%s\" -o \"%s\"", inputFile, outputFolder);
								addUnique(this.commands, command);
							}
						} else {
							// save individual volumes from source nifti
							Nifti source = Nifti.loadFromFile(pairs.sourceFilePath);
							for(int j = 0; j < pairs.sourceVolumes.length; j++) {
								int volume = pairs.sourceVolumes[j];
								String filename = pairs.getSourceShardFilename(volume);
								String filePath = Util.fullfile(this.logFolder, "logs", filename);
								if(!new File(filePath).exists()) {
									System.out.println(String.format("sharding source volume %s", filename));
									Nifti slice = getVolume(source, volume);
									Util.ensureFileHasFolder(filePath);
									slice.save(filePath);
								}
							} // source volume loop
							// save individual volumes from reference nifti
							Nifti reference = source;
							if(pairs.referenceFilePath != null && !pairs.referenceFilePath.contentEquals(pairs.sourceFilePath)) {
								reference = Nifti.loadFromFile(pairs.referenceFilePath);
							}
							String filename = pairs.getReferenceShardFilename();
							System.out.println(String.format("sharding reference volume %s", filename));
							Nifti slice = getVolume(source, pairs.referenceVolume);
							String filePath = Util.fullfile(this.logFolder, "logs", filename);
							Util.ensureFileHasFolder(filePath);
							slice.save(filePath);
						}
					} // isNeeded conditional
				} // pairs loop
			} // isSharding conditional
		} // method RegistrationSchedule::createShards

		public static void shardNifti(String inputPath, String outputFolder) throws FileNotFoundException, IOException {
			RegistrationSchedule schedule = new RegistrationSchedule();

			schedule.isSharding = true;
			schedule.deferExecution = false;
			schedule.addTrainingFile(inputPath, true);
			schedule.setLogFolder(outputFolder);
			schedule.createShards();
		}

		//		public static void registerVolume(String sourcePath, String referencePath, int sourceVolume, int referenceVolume, String outputFolder, String schedulePath) throws FileNotFoundException, IOException {
		public static void registerVolume(int sourceVolume, int referenceVolume, String schedulePath) throws FileNotFoundException, IOException {
			// load the saved schedule
			String json = Util.load(schedulePath);
			RegistrationSchedule schedule = Util.newGson().fromJson(json, RegistrationSchedule.class);

			// set source and reference values in existing volumePairs
			// (this preserves digit count)
			for(int i = 0; i < schedule.volumePairs.size(); i++) {
				TrainingVolumePairing pairs = schedule.volumePairs.get(i);
				pairs.referenceVolume = referenceVolume;
				pairs.sourceVolumes = new int[] {sourceVolume};
			}
			schedule.deferExecution = false;
			schedule.isSharding = false;
			//			schedule.setLogFolder(outputFolder);
			schedule.apply();
		}


		private static Nifti getVolume(Nifti source, int volume) {
			Nifti slice = source.clone();
			double[][][][] sourceData = slice.getData();
			short dimX = source.getDimX();
			short dimY = source.getDimY();
			short dimZ = source.getDimZ();
			slice.resetData(dimX, dimY, dimZ, 1);
			for(int j0 = 0; j0 < dimX; j0++) {
				for(int j1 = 0; j1 < dimY; j1++) {
					for(int j2 = 0; j2 < dimZ; j2++) {
						double val = sourceData[j0][j1][j2][volume];
						slice.setData(j0, j1, j2, 0, val); 
					}
				}
			}
			return slice;
		}

		public String[] getOutputFiles() {
			List<String> outputs = new ArrayList<String>();
			for(int pairNumber = 0; pairNumber < this.volumePairs.size(); pairNumber++) {
				TrainingVolumePairing pairs = this.volumePairs.get(pairNumber);
				String[] fileparts = Util.fileparts(pairs.sourceFilePath);
				String sourceFile = fileparts[1]; 
				Nifti sourceHeader = null;
				try {
					sourceHeader = Nifti.loadHeader(pairs.sourceFilePath);

				} catch (FileNotFoundException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				} catch (IOException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				int volumeCount = sourceHeader.getDimT();
				int volumeDigitCount = (int)(Math.floor(Math.log10(volumeCount)) + 1);
				String outputFormat = String.format("%s_vol-%%0%dd-%%0%dd.json", sourceFile, volumeDigitCount, volumeDigitCount);
				for(int sourceNumber = 0; sourceNumber < pairs.sourceVolumes.length; sourceNumber++) {
					int sourceVolume = pairs.sourceVolumes[sourceNumber];
					String outputFile = String.format(outputFormat, pairs.getReferenceVolume(), sourceVolume);
					String outputPath = Util.fullfile(logFolder, outputFile);
					outputs.add(outputPath);
				}
			}
			String[] result = new String[outputs.size()];
			for(int i = 0; i < result.length; i++) {
				result[i] = outputs.get(i);
			}
			return result;
		} // method RegistrationSchedule::getOutputFiles
		public boolean isCompleted() {
			String[] outputFiles = this.getOutputFiles();
			for(int i = 0; i < outputFiles.length; i++) {
				File file = new File(outputFiles[i]);
				if(!file.exists()) {
					return false;
				}
			}
			return true;
		} // method RegistrationSchedule::isCompleted

		private static class TrainingSetCost implements ICostFunction{
			RegistrationSchedule schedule;
			boolean useParallel = true;
			boolean logEveryStep = false;

			public TrainingSetCost(RegistrationSchedule registrationSchedule){
				schedule = registrationSchedule;
			}

			// method TrainingSetCost::calculate
			@Override
			public double calculate(Map<String, Object> data1, double[] estimators) {

				Instant startTime = Instant.now(); // time to compute factors into cost
				String schedulePath = Util.fullfile(this.schedule.logFolder, "logs", "schedule_" + Util.dateString() + ".json");
				boolean isScheduleSaved = false;
				boolean overwrite = (Boolean)data1.get("overwrite");
				boolean useShards = (Boolean)data1.get("useShards");
				boolean deferExecution = this.schedule.deferExecution;

				for(int pairNumber = 0; pairNumber < schedule.volumePairs.size(); pairNumber++) {
					TrainingVolumePairing pairs = schedule.volumePairs.get(pairNumber);

					// check whether all outputs from this pairing already exist
					// if so, we can skip loading the input data
					boolean isAnyComputeNeeded = false;
					Nifti sourceHeader = null;
					try {
						sourceHeader = Nifti.loadHeader(pairs.sourceFilePath);

					} catch (FileNotFoundException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					} catch (IOException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}
					int volumeCount = sourceHeader.getDimT();
					int volumeDigitCount = (int)(Math.floor(Math.log10(volumeCount)) + 1);
					String[] fileparts = Util.fileparts(pairs.sourceFilePath);
					String sourceFile = fileparts[1]; 
					String outputFormat = String.format("%s_vol-%%0%dd-%%0%dd.json", sourceFile, volumeDigitCount, volumeDigitCount);
					for(int sourceNumber = 0; sourceNumber < pairs.sourceVolumes.length; sourceNumber++) {
						int sourceVolume = pairs.sourceVolumes[sourceNumber];
						String outputFile = String.format(outputFormat, pairs.getReferenceVolume(), sourceVolume);
						String outputPath = Util.fullfile(this.schedule.logFolder, outputFile);
						File outFile = new File(outputPath);
						if(!outFile.exists()) {
							isAnyComputeNeeded = true;
						}
					}
					if(overwrite) {
						isAnyComputeNeeded = true;
					}


					if(isAnyComputeNeeded) {

						// reset log of previous scales and volumes
						schedule.transformOps.clear();
						schedule.descents.clear();
						schedule.volumeRegs.clear();
						schedule.sliceRegs.clear();

						// save used schedule in logs
						if(!isScheduleSaved) {
							try {
								Util.ensureDirectoryExists(schedulePath);
								Util.save(schedulePath, Util.newGson().toJson(this.schedule));
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							isScheduleSaved = true;
						}

						if(deferExecution) {
							for(int i = 0; i < this.schedule.volumePairs.size(); i++) {
								TrainingVolumePairing pairs0 = this.schedule.volumePairs.get(i);
								int referenceVol = pairs0.getReferenceVolume();
								//								String referenceFile = pairs0.getReferenceShardFilename();
								//								String referencePath = Util.fullfile(this.schedule.logFolder, "logs", referenceFile);
								for(int j = 0; j < pairs0.sourceVolumes.length; j++) {
									int sourceVol = pairs0.sourceVolumes[j];
									//									String sourceFilename = pairs0.getSourceShardFilename(sourceVol);
									//									String sourcePath = Util.fullfile(this.schedule.logFolder, "logs", sourceFilename);
									//									String outputFile = String.format(outputFormat, pairs.getReferenceVolume(), sourceVol);
									//									String outputPath = Util.fullfile(this.schedule.logFolder, outputFile);
									String command = String.format("register -s %d -r %d -i \"%s\"", 
											sourceVol, referenceVol, schedulePath);
									this.schedule.commands.add(command);
								}
							}
						} else { // don't defer execution


							// load data (outside volume loop if we're not using shards
							Nifti sourceNii = null;
							Nifti refNii = sourceNii;
							Nifti[] scaledSources = new Nifti[schedule.scales.size()];
							Nifti[] scaledRefs = new Nifti[schedule.scales.size()];
							if(!useShards) {
								try {
									sourceNii = Nifti.loadFromFile(pairs.sourceFilePath);
									refNii = sourceNii;
									if(pairs.referenceFilePath != null && !pairs.sourceFilePath.contentEquals(pairs.referenceFilePath)) {
										refNii = Nifti.loadFromFile(pairs.sourceFilePath);
									}
								} catch (FileNotFoundException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

								// scale source & reference data
								Nifti scaledSource = sourceNii.clone();
								Nifti scaledRef = refNii;
								int scaledCount = 0;
								for(int i = schedule.scales.size() - 1; i >= 0; i--) {
									int downCount = schedule.scales.get(i).downSampleCount;
									while(scaledCount < downCount) {
										scaledSource = scaledSource.halfScale().clone();
										scaledRef = scaledRef.halfScale().clone();
										scaledCount++;
									}
									scaledSources[i] = scaledSource;
									scaledRefs[i] = scaledRef;
								}
							} else { // useShards == true
								String referenceShardFilename = pairs.getReferenceShardFilename();
								String referenceShardPath = Util.fullfile(schedule.logFolder, "logs", referenceShardFilename);
								try {
									refNii = Nifti.loadFromFile(referenceShardPath);
								} catch (FileNotFoundException e) {
									e.printStackTrace();
								} catch (IOException e) {
									e.printStackTrace();
								}
								Nifti scaledRef = refNii;
								int scaledCount = 0;
								for(int i = schedule.scales.size() - 1; i >= 0; i--) {
									int downCount = schedule.scales.get(i).downSampleCount;
									while(scaledCount < downCount) {
										scaledRef = scaledRef.halfScale().clone();
										scaledCount++;
									}
									scaledRefs[i] = scaledRef;
								}
							}

							// volume loop
							for(int sourceNumber = 0; sourceNumber < pairs.sourceVolumes.length; sourceNumber++) {
								// load source volumes separately if we're sharding 
								// (ref will stay constant for this pairing)
								int sourceVolume = pairs.sourceVolumes[sourceNumber];
								if(useShards) {
									String sourceShardFilename = pairs.getSourceShardFilename(sourceVolume);
									try {
										sourceNii = Nifti.loadFromFile(pairs.sourceFilePath);
									} catch (FileNotFoundException e) {
										e.printStackTrace();
									} catch (IOException e) {
										e.printStackTrace();
									}

									// scale source & reference data
									Nifti scaledSource = sourceNii.clone();
									int scaledCount = 0;
									for(int i = schedule.scales.size() - 1; i >= 0; i--) {
										int downCount = schedule.scales.get(i).downSampleCount;
										while(scaledCount < downCount) {
											scaledSource = scaledSource.halfScale().clone();
											scaledCount++;
										}
										scaledSources[i] = scaledSource;
									}
								}

								// check if this volume needs to be computed 
								// (i.e. if output file doesn't exist yet)
								int estNumber = 0;
								String outputFile = String.format(outputFormat, pairs.getReferenceVolume(), sourceVolume);
								String outputPath = Util.fullfile(this.schedule.logFolder, outputFile);
								boolean isThisComputeNeeded = true;
								if(!overwrite) {
									File outFile = new File(outputPath);
									if(outFile.exists()) {
										isThisComputeNeeded = false;
									}
								}

								// finally do the computation
								if(isThisComputeNeeded) {
									System.out.println(Util.dateString() + ": fitting volume " + sourceVolume + " of " + sourceFile);
									ArrayList<SliceRegistration> regs = new ArrayList<SliceRegistration>(); 

									// scale loop
									for(int scaleNumber = 0; scaleNumber < scaledSources.length; scaleNumber++) {
										TrainingScale scale = schedule.scales.get(scaleNumber);
										AffineTransform t = null;
										try {
											t = new AffineTransform(scaledSources[scaleNumber], scaledRefs[scaleNumber]);
											t.setSourcePathWithoutLoading(pairs.sourceFilePath);
										} catch (FileNotFoundException e) {
											e.printStackTrace();
										} catch (IOException e) {
											e.printStackTrace();
										}

										// set meta parameters 
										t.setInitialSpanFraction(estimators[estNumber++]);
										t.setGradientMaxScaleFactor(estimators[estNumber++]);
										t.setGradientMinScaleFactor(estimators[estNumber++]);

										t.setUseParabolic(scale.isParabolic);
										if(scale.tweakStopThreshold) {
											t.setCostImprovementThreshold(estNumber++);
										} else {
											t.setCostImprovementThreshold(TrainingScale.FINAL_STOP_THRESHOLD);
										}
										if(logEveryStep) {
											if(schedule.logFolder != null) {
												t.setLogFolder(Util.fullfile(schedule.logFolder, "logs"));
											}
										}
										t.setMaxStagnantIterations(scale.maxStagnantIterations);


										// chain transforms from previous volumes and scales
										if(!schedule.volumeRegs.isEmpty()) {
											SliceRegistration lastVolReg = schedule.volumeRegs.get(schedule.volumeRegs.size() - 1);
											// inherit transform
											t.setInitialRotation(lastVolReg.xR, lastVolReg.yR, lastVolReg.zR); 
											t.setInitialTranslation(lastVolReg.xT, lastVolReg.yT, lastVolReg.zT); 
										}
										if(scale.doSlices) {
											t.setInitialTranslationSpan(1, 1, 1);
											t.setInitialRotationSpan(0.001, 0.001, 0.001);
										}


										if(!schedule.descents.isEmpty()) {

											GradientDescent lastDescent = schedule.descents.get(schedule.descents.size() - 1);
//											GradientDescent lastDescent = schedule.volumeRegs.get(schedule.volumeRegs.size() - 1);

											// if smaller scales from this volume exist, inherit adjustment spans
											if(scaleNumber > 0 && !scale.doSlices) {
												double xTSpan = lastDescent.getAdjustmentSpan(0);
												double yTSpan = lastDescent.getAdjustmentSpan(1);
												double zTSpan = lastDescent.getAdjustmentSpan(2);
												double xRSpan = lastDescent.getAdjustmentSpan(3);
												double yRSpan = lastDescent.getAdjustmentSpan(4);
												double zRSpan = lastDescent.getAdjustmentSpan(5);
												xTSpan = Math.sqrt(xTSpan);
												yTSpan = Math.sqrt(yTSpan);
												zTSpan = Math.sqrt(zTSpan);
												xRSpan = Math.sqrt(xRSpan);
												yRSpan = Math.sqrt(yRSpan);
												zRSpan = Math.sqrt(zRSpan);
												t.setInitialTranslationSpan(xTSpan, yTSpan, zTSpan);
												t.setInitialRotationSpan(xRSpan, yRSpan, zRSpan);
												double[] optimized = lastDescent.getEstimators();
												t.setInitialRotation(optimized[0], optimized[1], optimized[2]);
												t.setInitialTranslation(optimized[3], optimized[4], optimized[5]);
											}
											//											AffineTransform lastTransform = schedule.transformOps.get(schedule.transformOps.size() - 1);

										} // chain previous transforms block
										t.setVerbosity(0);
										t.setUseParallel(useParallel);


										if(!scale.doSlices) {
											//											if(scale.useFsl) {
											//												FileFinder finder = new FileFinder();
											//												finder.setInputFilePath(pairs.sourceFilePath);
											//												String basePath = finder.getFuctionalVersionPath("");
											//												String[] sourceFileParts = Util.fileparts(pairs.sourceFilePath);
											//												String motionParamPath = Util.fullfile(basePath, sourceFileParts[1] + "_mcf.par");
											//												String motionParamText = null;
											//												try {
											//													motionParamText = Util.load(motionParamPath);
											//												} catch (IOException e) {
											//													// TODO Auto-generated catch block
											//													e.printStackTrace();
											//												}
											//												String[] lines = motionParamText.split("\n");
											//												String line = lines[sourceVolume];
											//												//												for(int i = 0; i < lines.length; i++) {
											//												String[] items = line.split(" +", -1);
											//												SliceRegistration volReg = new SliceRegistration();
											//												volReg.xR = Double.parseDouble(items[0]);
											//												volReg.yR = -Double.parseDouble(items[1]);
											//												volReg.zR = -Double.parseDouble(items[2]);
											//												volReg.xT = Double.parseDouble(items[3]);
											//												volReg.yT = Double.parseDouble(items[4]);
											//												volReg.zT = Double.parseDouble(items[5]);
											//												volReg.sourceVolume = sourceVolume;
											//												volReg.sourceSlice = -1;
											//												volReg.cost = Double.NaN;
											//												regs.add(volReg);
											//												schedule.volumeRegs.add(volReg);
											//												//												}
											//												int dummy = 1;
											//
											//											} else {
											// calculate volumes
											SliceRegistration volReg = null;
											try {
												volReg = t.registerVolumeCubic(sourceVolume);
												System.out.println(volReg.toString());
												schedule.volumeRegs.add(volReg);
												schedule.transformOps.add(t);
												schedule.descents.add(t.getGradientDescent());
												regs.add(volReg);
											} catch (Exception e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
											//											}
										} else {
											// calculate slices
											if(useParallel) {
												SliceRegistration[] sliceRegArray = new SliceRegistration[t.getSource().getDimZ()];
												AffineTransform[] tArray = new AffineTransform[sliceRegArray.length];
												GradientDescent[] gdArray = new GradientDescent[sliceRegArray.length];
												IndexGenerator gen = new IndexGenerator(sliceRegArray.length);
												//												t.setUseFrequencyScaling(scale.doFrequencyScaling);
												final int degOfFree = scale.degreesOfFreedom;
												double[] rotation = t.getInitialRotation();
												double[] translation = t.getInitialTranslation();
												final double xR = rotation[0];
												final double yR = rotation[1];
												final double zR = rotation[2];
												final double xT = translation[0];
												final double zT = translation[2];

												for(int i = 0; i < sliceRegArray.length; i++) {
													tArray[i] = t.clone();
												}
												gen.getStream().forEach(i -> {
													int ii = (int) i;
													SliceRegistration sliceReg = null;
													try {
														//sliceReg = tArray[ii].registerSliceCubic(sourceVolume, ii);
														sliceReg = tArray[ii].registerSliceCubic(sourceVolume, ii, degOfFree, xR, yR, zR, xT, zT, 0.0, 0.0, 1.0, 1.0);
													} catch (Exception e) {
														// TODO Auto-generated catch block
														e.printStackTrace();
													}
													System.out.println(sliceReg.toString());
													sliceRegArray[ii] = sliceReg;
													gdArray[ii] = tArray[ii].getGradientDescent();
												});		
												for(int i = 0; i < sliceRegArray.length; i++) {
													schedule.sliceRegs.add(sliceRegArray[i]);
													schedule.transformOps.add(tArray[i]);
													schedule.descents.add(gdArray[i]);
													regs.add(sliceRegArray[i]);
												}
											} else { // don't use parallel
												SliceRegistration sliceReg = null;
												try {
													final int degOfFree = scale.degreesOfFreedom;
													double[] rotation = t.getInitialRotation();
													double[] translation = t.getInitialTranslation();
													final double xR = rotation[0];
													final double yR = rotation[1];
													final double zR = rotation[2];
													final double xT = translation[0];
													final double zT = translation[2];

													for(int sliceNumber = 0; sliceNumber < t.getSource().getDimZ(); sliceNumber++) {
														//										System.out.println(Util.dateString() + ": fitting volume " + sourceVolume + " slice " + sliceNumber);
														//										t.setVerbosity(1);
														//														t.setUseFrequencyScaling(scale.doFrequencyScaling);
														//														sliceReg = t.registerSliceCubic(sourceVolume, sliceNumber);
														sliceReg = t.registerSliceCubic(sourceVolume, sliceNumber, degOfFree, xR, yR, zR, xT, zT, 0.0, 0.0, 1.0, 1.0);

														System.out.println(sliceReg.toString());
														schedule.sliceRegs.add(sliceReg);
														schedule.transformOps.add(t);
														schedule.descents.add(t.getGradientDescent());
														regs.add(sliceReg);
													}
												} catch(Exception ex) {
													ex.printStackTrace();
												}
											} // useParallel conditional
										} // scale.doSlices conditional
									} // scale loop

									// save output
									if(this.schedule.logFolder != null) {
										Util.ensureDirectoryExists(outputPath);
										try {
											Util.save(outputPath, Util.newGson().toJson(regs));
										} catch (IOException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
								} // isThisComputeNeeded conditional
							} // source volume loop
						} // deferExecution conditional
					} // isAnyComputeNeeded conditional
				} // source file loop (aka pair loop)

				Instant endTime = Instant.now();
				Duration dur = Duration.between(startTime, endTime);
				double elapsedSeconds = (double)dur.toMillis() / 1000.0;
				// garbage collect for long-running loops
				Runtime.getRuntime().gc();
				return elapsedSeconds;
			} // method TrainingSetCost::calculate

			@Override
			public Map<String, String> getDataNames() {			
				// input data parameter is empty, the data comes from the TrainingSetCost Object
				TreeMap<String, String> result = new TreeMap<String, String>();
				return result;
			}
			@Override
			public int getEstimatorCount() {
				int result = 0;
				int scaleCount = schedule.scales.size();
				// for each scale: adjustmentStart, maxScaleStart, and minScaleStart
				// optional for each scale: scale.stopThresholdStart
				for(int i = 0; i < scaleCount; i++) {
					result += 3;
					TrainingScale scale = schedule.scales.get(i);
					if(scale.tweakStopThreshold) {
						result++;
					} 
				}
				return result;
			}
		} // end class TrainingSetCost 
	} // end class TrainingSet (stores, executes, and tweaks plans for registration of slices and volumes)


	// resume class GradientDescentTuner, static methods
	public static void registerFolder() throws Exception {
		String rootSubjectFolder = "Z:\\data\\analyzed";
		registerFolder(rootSubjectFolder);
	}

	public static void registerFolder(String rootSubjectFolder) throws Exception {
		boolean overwrite = false;

		// sharding saves a ton of memory because 
		// so many arrays get allocated for each little operation.
		// the garbage collector just can't keep up.
		// sharding is 's like we're doing manual memory management.
		// useful for standalone mode where we want to be running
		// for hours or days at a time.
		boolean doSharding = true;
		boolean deferExecution = true;
		boolean doStandaloneMode = true;
		if(!doStandaloneMode) {
			doSharding = false;
			deferExecution = false;
		}

		registerFolder(rootSubjectFolder, overwrite, doSharding, deferExecution);
	}

	public static RegistrationSchedule getDefaultSchedule() {
		return HardcodedRegirstrationSchedules.getMark4();
		//		return HardcodedRegirstrationSchedules.getMark5();
	}

	public static void registerFolder(String rootSubjectFolder, boolean overwrite, boolean doSharding, boolean deferExecution) throws Exception {
		FileFinder finder = new FileFinder();
		String[] subjects = finder.getSubjectFolders(rootSubjectFolder);
		ArrayList<String> shardCommands = new ArrayList<String>();
		ArrayList<String> registerCommands = new ArrayList<String>();
		String jarPath = "";
		try {
			jarPath = new File(SliceRegistrationPerformer.class.getProtectionDomain().
					getCodeSource().getLocation().toURI()).getPath();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		String commandStart = String.format("java -jar \"%s\" ", jarPath);
		for(int subjectNumber = 0; subjectNumber < subjects.length; subjectNumber++) {
			System.out.println(String.format("%s: compiling commands for %s", Util.dateString(), subjects[subjectNumber]));
			String subjectFolder = Util.fullfile(rootSubjectFolder, subjects[subjectNumber]); 
			finder.setInputFilePath(subjectFolder);
			String[] fmriFiles = finder.getRawFmriFiles();
			for(int i = 0; i < fmriFiles.length; i++) {
				shardCommands.clear();
				registerCommands.clear();
				String inputFile = fmriFiles[i];
				finder.setInputFilePath(inputFile);
				String outputFolder;
				RegistrationSchedule schedule = getDefaultSchedule();
				String outputBaseFolder = finder.getFuctionalVersionPath("sliceMark6");
				//				int[] degreesOfFreedomList = new int[] {1, 2, 3, 6};
				int[] degreesOfFreedomList = new int[] {1, 2, 3};
				for(int dofInd = 0; dofInd < degreesOfFreedomList.length; dofInd++) {
					int degreesOfFreedom = degreesOfFreedomList[dofInd];
					outputFolder = String.format("%s_dof-%d", outputBaseFolder, degreesOfFreedom);
					schedule.scales.get(schedule.scales.size() - 1).degreesOfFreedom = degreesOfFreedom; 

					String[] parts1 = Util.fileparts(inputFile);
					String outputPath = Util.fullfile(outputFolder, parts1[1]);
					schedule.setLogFolder(outputPath);
					schedule.overwrite = overwrite;
					schedule.isSharding = doSharding;
					schedule.deferExecution = deferExecution;
					int refVolume = 0;
					schedule.addTrainingFile(inputFile, true, refVolume);
					boolean needsCompute = true;
					if(!overwrite && schedule.isCompleted()) {
						needsCompute = false;
					}
					if(needsCompute) {
						// generate commands
						schedule.apply(inputFile);
						List<String> commands = schedule.commands;
						// split commands into shard and register
						for(int j = 0; j < commands.size(); j++) {
							String command = commands.get(j);
							if(command.startsWith("shard")) {
								shardCommands.add(commandStart + command);
							} else if(command.startsWith("register")) {
								registerCommands.add(commandStart + command);
							} else {
							}
						}
						// do sharding
						for(int j = 0; j < shardCommands.size(); j++) {
							String command = shardCommands.get(j);
							System.out.println(String.format("%s: executing %s", Util.dateString(), command));
							Executor.executeSyncronously(command);
						}
						// do registration
						boolean doSerial = true;
						if(doSerial) {
							// execute one at a time
							for(int j = 0; j < registerCommands.size(); j++) {
								String command = registerCommands.get(j);
								System.out.println(String.format("%s: executing %s", Util.dateString(), command));
								Executor.executeSyncronously(command);
							}
						} else {
							// execute a few at a time
							Executor.executePoolSynchronously(registerCommands, 4);
						}
					} // needsCompute conditional
				} // degreesOfFreedom loop
			} // fmriFiles loop
		} // subject loop
		for(int i = 0; i < shardCommands.size(); i++) {
			String command = shardCommands.get(i);
			Executor.executeAsynchronously(command);
		}


	} // static method GradientDescentTuner::registerFolder  

//	public static void processFile(String inputPath, String outputFolder) throws FileNotFoundException, IOException {
//		processFile(inputPath, outputFolder, 0);
//	}

	public static String[] getOutputFiles(String inputPath, String outputFolder, int referenceVolume) throws FileNotFoundException, IOException {

		//		String shardFolder = Util.fullfile(outputFolder, "logs");
		//		String schedulePath = Util.fullfile(outputFolder, "logs", "schedule.json");
		//		RegistrationSchedule.shardNifti(inputPath, shardFolder);
		//		Nifti header = Nifti.loadHeader(inputPath);
		SliceRegistrationPerformer.RegistrationSchedule schedule = SliceRegistrationPerformer.getDefaultSchedule();
		schedule.addTrainingFile(inputPath, true);
		schedule.setLogFolder(outputFolder);
		return schedule.getOutputFiles();
		//		GradientDescentTuner.TrainingVolumePairing pairs = schedule.volumePairs.get(0);
		//		String scheduleJson = Util.newGson().toJson(schedule);
		//		Util.save(schedulePath, scheduleJson);
		//
		//		int volumeCount = header.getDimT();
		//		for(int i = 0; i < volumeCount; i++) {
		//			if(i != referenceVolume) {
		//				String regFile = pairs.getRegistrationFilename(referenceVolume, i);
		//				String regPath = Util.fullfile(outputFolder, regFile);
		//				if(!new File(regPath).exists()) {
		//					RegistrationSchedule.registerVolume(i, referenceVolume, schedulePath);									
		//				}
		//			}
		//		}
	}
	public static boolean doAllOutputFilesExist(String inputPath, String outputFolder, int referenceVolume) throws FileNotFoundException, IOException {
		String[] paths = getOutputFiles(inputPath, outputFolder, referenceVolume);
		for(int i = 0; i < paths.length; i++) {
			if(!(new File(paths[i]).exists())) {
				return false;				
			}
		}
		return true;
	}

	public static void processFile(String inputPath, String outputFolder) throws FileNotFoundException, IOException {

		String shardFolder = Util.fullfile(outputFolder, "shards");
		String schedulePath = Util.fullfile(outputFolder, "shards", "schedule.json");
		RegistrationSchedule.shardNifti(inputPath, shardFolder);
		Nifti header = Nifti.loadHeader(inputPath);
		SliceRegistrationPerformer.RegistrationSchedule schedule = SliceRegistrationPerformer.getDefaultSchedule();
		schedule.addTrainingFile(inputPath, true);
		schedule.setLogFolder(outputFolder);
		SliceRegistrationPerformer.TrainingVolumePairing pairs = schedule.volumePairs.get(0);
		String scheduleJson = Util.newGson().toJson(schedule);
		Util.save(schedulePath, scheduleJson);

		// register to first volume
		int volumeCount = header.getDimT();
		int referenceVolume = 0;
		for(int i = 0; i < volumeCount; i++) {
			if(i != referenceVolume) {
				String regFile = pairs.getRegistrationFilename(referenceVolume, i);
				String regPath = Util.fullfile(outputFolder, regFile);
				if(!new File(regPath).exists()) {
					RegistrationSchedule.registerVolume(i, referenceVolume, schedulePath);
				}
			}
		}
		
		// register to previous volume
		for(int i = 1; i < volumeCount; i++) {
			referenceVolume = i - 1;
			String regFile = pairs.getRegistrationFilename(referenceVolume, i);
			String regPath = Util.fullfile(outputFolder, regFile);
			if(!new File(regPath).exists()) {
				RegistrationSchedule.registerVolume(i, referenceVolume, schedulePath);
			}
		}
		
		// register to two volumes back
		for(int i = 2; i < volumeCount; i++) {
			referenceVolume = i - 2;
			String regFile = pairs.getRegistrationFilename(referenceVolume, i);
			String regPath = Util.fullfile(outputFolder, regFile);
			if(!new File(regPath).exists()) {
				RegistrationSchedule.registerVolume(i, referenceVolume, schedulePath);
			}
		}


		
	}

	public static void parseArgs(String[] args) throws Exception {
		if(args.length == 0) {
			registerFolder();
		} else {
			String[] verbs = new String[] {"shard", "register"};
			String verb = args[0];
			int matchIndex = -1;
			for(int i = 0; i < verbs.length; i++) {
				if(verbs[i].contentEquals(verb)) {
					matchIndex = i;
				}
			}
			String[] rgs = new String[args.length - 1];
			for(int i = 0; i < rgs.length; i++) {
				rgs[i] = args[i + 1];
			}
			CommandLineParser parser = new DefaultParser(); 
			boolean isVerbGood = true;
			Options options = null;
			if(matchIndex == -1) {
				StringBuilder sb = new StringBuilder();
				sb.append("invalid verb: \"");
				sb.append(verb);
				sb.append("\"; valid verbs are: [");
				for(int i = 0; i < verbs.length; i++) {
					sb.append(verbs[i]);
					if(i < (verbs.length - 1)) {
						sb.append(", ");
					} else {
						sb.append("]");
					}
				}
				System.out.println(sb.toString());
				isVerbGood = false;
			} else if(matchIndex == 0) { // shard
				Options shardOptions = new Options();
				shardOptions.addOption("i", true, "input nifti file");
				shardOptions.addOption("o", true, "output folder");
				options = shardOptions;
			} else if(matchIndex == 1) { // register
				Options registerOptions = new Options();
				registerOptions.addOption("s", true, "source nifti volume number");
				registerOptions.addOption("r", true, "reference nifti volume number");
				registerOptions.addOption("i", true, "schedule file");
				options = registerOptions;
			} else {
				System.out.println(String.format("unhandled verb %s", verb));
				isVerbGood = false;
			}

			if(isVerbGood) {
				CommandLine commandLine = parser.parse(options, rgs);
				Iterator<org.apache.commons.cli.Option> iterator = commandLine.iterator();
				if(matchIndex == 0) { // shard
					String inputPath = commandLine.getOptionValue("i");
					String outputPath = commandLine.getOptionValue("o");
					RegistrationSchedule.shardNifti(inputPath, outputPath);
				} else if(matchIndex == 1) { // register
					String schedulePath = commandLine.getOptionValue("i");
					String sourceVolTxt = commandLine.getOptionValue("s");
					String referenceVolTxt = commandLine.getOptionValue("r");
					int sourceVol = Integer.parseInt(sourceVolTxt);
					int referenceVol = Integer.parseInt(referenceVolTxt);
					RegistrationSchedule.registerVolume(sourceVol, referenceVol, schedulePath);
				}
			}
		}
	}

	//	public static void doCatchup() throws FileNotFoundException, IOException {
	//		String[] files = new String[] {
	//				"Z:\\data\\analyzed\\sub-710-01\\mri\\ses-01\\func\\sub-710-01_ses-01_run-13_BREATHE_3101.nii.gz", 
	//				"Z:\\data\\analyzed\\sub-710-01\\mri\\ses-01\\func\\sub-710-01_ses-01_run-14_BREATHE_3201.nii.gz", 
	//				"Z:\\data\\analyzed\\sub-710-02\\mri\\ses-01\\func\\sub-710-02_ses-01_run-05_RSFC_1401.nii.gz", 
	//				"Z:\\data\\analyzed\\sub-710-09\\mri\\ses-02\\func\\sub-710-09_ses-02_run-15_BREATHE_4601.nii.gz", 
	//				"Z:\\data\\analyzed\\sub-710-09\\mri\\ses-02\\func\\sub-710-09_ses-02_run-16_BREATHE_4701.nii.gz", 
	//		};
	//		for(int i = 0; i < files.length; i++) {
	//			String inputPath = files[i];
	//			FileFinder finder = new FileFinder();
	//			finder.setInputFilePath(inputPath);
	//			String subFolder = finder.getFuctionalVersionPath("sliceMark4");
	//			String[] fileParts = Util.fileparts(inputPath);
	//			String outputFolder = Util.fullfile(subFolder, fileParts[1]);
	//			SliceRegistrationPerformer.processFile(inputPath, outputFolder);
	//			
	//			int dummy = 1;
	//			
	//		}
	//	}

	//	public static void testProcess() throws FileNotFoundException, IOException {
	//		String srcPath = "Z:\\data\\analyzed\\sub-710-02\\mri\\ses-01\\func\\sub-710-02_ses-01_run-05_RSFC_1401.nii.gz";
	//		String outPath = "Z:\\data\\analyzed\\sub-710-02\\mri\\functional\\sliceMark4\\sub-710-02_ses-01_run-05_RSFC_1401.nii.gz";
	//		
	//		
	////		String srcPath = "Z:\\data\\analyzed\\sub-FUNC_TASK-ONEBACK\\mri\\ses-01\\func_task-oneback_run-1_401.nii.gz";
	////		String outPath = "Z:\\data\\analyzed\\sub-FUNC_TASK-ONEBACK\\mri\\functional\\sliceMark4\\func_task-oneback_run-1_401";
	//		SliceRegistrationPerformer.processFile(srcPath, outPath);
	//		
	//	}

	//	public static void testShardReg() {
	//		
	//		String srcPath = "Z:\\data\\analyzed\\sub-DS005533-07\\functional\\sliceMark4\\sub-07_ses-cap_task-checker_run-01_bold\\logs\\logs\\sub-07_ses-cap_task-checker_run-01_bold_vol-000.nii";
	//		String refPath = "Z:\\data\\analyzed\\sub-DS005533-07\\functional\\sliceMark4\\sub-07_ses-cap_task-checker_run-01_bold\\logs\\logs\\sub-07_ses-cap_task-checker_run-01_bold_vol-001.nii";
	//		String schedulePath = "Z:\\data\\analyzed\\sub-DS005533-07\\functional\\sliceMark4\\sub-07_ses-cap_task-checker_run-01_bold\\logs\\schedule_r-0_s-1.json";
	//		
	//		RegistrationSchedule shardSchedule = getDefaultSchedule();
	//		int[] sourceInds = new int[] { 0 };
	//		
	//		try {
	//			shardSchedule.addTrainingFile(srcPath, refPath, sourceInds, 0);
	//		} catch (FileNotFoundException e) {
	//			// TODO Auto-generated catch block
	//			e.printStackTrace();
	//		} catch (IOException e) {
	//			// TODO Auto-generated catch block
	//			e.printStackTrace();
	//		}
	//		try {
	//			shardSchedule.registerVolume(0, 0, schedulePath);
	//		} catch (FileNotFoundException e) {
	//			// TODO Auto-generated catch block
	//			e.printStackTrace();
	//		} catch (IOException e) {
	//			// TODO Auto-generated catch block
	//			e.printStackTrace();
	//		}
	//		
	//		int dummy = 1;
	//		
	//	}

	public static void main(String[] args) throws Exception {

		parseArgs(args);

	}
} // class GradientDescentTuner
