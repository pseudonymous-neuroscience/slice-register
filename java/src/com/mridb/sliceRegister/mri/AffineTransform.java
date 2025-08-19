package com.mridb.sliceRegister.mri;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import Jama.Matrix;
import com.mridb.sliceRegister.Util;
//import com.mridb.sliceRegister.fourier.Fourier;
import com.mridb.sliceRegister.mri.AffineTransform.SliceRegistration;
//import com.mridb.sliceRegister.mutualInformation.MutualInformation;
//import com.mridb.sliceRegister.plot.Imagesc;

public class AffineTransform {

	private String sourcePath;
	private String maskPath;
	private String referencePath;
	private String logFolder;
	private Matrix4 transform;
	private Matrix4 inverseTransform;
	private int referenceVolume = 0;

	private transient Nifti source;
	private transient Nifti refMask;
	private transient Nifti reference;
	private int gradientProbeCount = 7;
	private double gradientMaxScaleFactor = 1.1;
	private double gradientMinScaleFactor = 0.8;
	private double costReductionRatio = 0;
	private long maxIterations = 1000;
	private int maxStagnantIterations = 10;
	private int maxPristineIterations = 100;

	private double initialSpanFraction = 0.01;
	private double gradientCostImprovementThreshold = 1e-12;

	private double initialTranslateX = 0;
	private double initialTranslateY = 0;
	private double initialTranslateZ = 0;
	private double initialRotateX = 0;
	private double initialRotateY = 0;
	private double initialRotateZ = 0;
	private double initialFreqEncScale = 1;

	private double minTranslateX;
	private double maxTranslateX;
	private double spanTranslateX;
	private double minTranslateY;
	private double maxTranslateY;
	private double spanTranslateY;
	private double minTranslateZ;
	private double maxTranslateZ;
	private double spanTranslateZ;
	private double minRotateX;
	private double maxRotateX;
	private double spanRotateX;
	private double minRotateY;
	private double maxRotateY;
	private double spanRotateY;
	private double minRotateZ;
	private double maxRotateZ;
	private double spanRotateZ;

	private double minFreqEncScale;
	private double maxFreqEncScale;
	private double spanFreqEncScale;

	private transient int sliceDigitCount;
	private transient int volumeDigitCount;

	private int verbosity = 1;

	private boolean useParallel = true;
	private boolean useParabolic = true;

	private transient GradientDescent gd;
//	private boolean useFrequencyScaling = false;

	public AffineTransform() {
		this.transform = new Matrix4();
		this.inverseTransform = transform.getInverse();
		//		this.gradientDescentScaleFactors = new double[] {1.2, 1.0, 0.80, 0.60};

	}

	public AffineTransform clone() {
		AffineTransform result = new AffineTransform();

		result.sourcePath = sourcePath;
		result.maskPath = maskPath;
		result.referencePath = referencePath;
		result.logFolder = logFolder;
		result.transform = transform.clone();
		result.inverseTransform = inverseTransform.clone();
		result.referenceVolume = referenceVolume;

		result.source = source;
		result.refMask = refMask;
		result.reference = reference;
		result.gradientProbeCount = gradientProbeCount;
		result.gradientMaxScaleFactor = gradientMaxScaleFactor;
		result.gradientMinScaleFactor = gradientMinScaleFactor;
		result.costReductionRatio = costReductionRatio;
		result.maxIterations = maxIterations;
		result.maxStagnantIterations = maxStagnantIterations;

		result.initialSpanFraction = initialSpanFraction;
		result.gradientCostImprovementThreshold = gradientCostImprovementThreshold;

		result.initialTranslateX = initialTranslateX;
		result.initialTranslateY = initialTranslateY;
		result.initialTranslateZ = initialTranslateZ;
		result.initialRotateX = initialRotateX;
		result.initialRotateY = initialRotateY;
		result.initialRotateZ = initialRotateZ;
		result.initialFreqEncScale = initialFreqEncScale;

		result.minTranslateX = minTranslateX;
		result.maxTranslateX = maxTranslateX;
		result.spanTranslateX = spanTranslateX;
		result.minTranslateY = minTranslateY;
		result.maxTranslateY = maxTranslateY;
		result.spanTranslateY = spanTranslateY;
		result.minTranslateZ = minTranslateZ;
		result.maxTranslateZ = maxTranslateZ;
		result.spanTranslateZ = spanTranslateZ;
		result.minRotateX = minRotateX;
		result.maxRotateX = maxRotateX;
		result.spanRotateX = spanRotateX;
		result.minRotateY = minRotateY;
		result.maxRotateY = maxRotateY;
		result.spanRotateY = spanRotateY;
		result.minRotateZ = minRotateZ;
		result.maxRotateZ = maxRotateZ;
		result.spanRotateZ = spanRotateZ;

		result.minFreqEncScale = minFreqEncScale;
		result.maxFreqEncScale = maxFreqEncScale;
		result.spanFreqEncScale = spanFreqEncScale;

		result.sliceDigitCount = sliceDigitCount;
		result.volumeDigitCount = volumeDigitCount;

		result.verbosity = verbosity;

		result.useParallel = useParallel;
		result.useParabolic = useParabolic;
		if(gd != null) {
			result.gd = gd.clone();
		}
//		result.useFrequencyScaling = useFrequencyScaling;
		return result;
	}





	public AffineTransform(Nifti sourceAndReference) throws FileNotFoundException, IOException {
		this();
		this.setReference(sourceAndReference);
		this.source = sourceAndReference;
		this.sourcePath = "";
		this.referencePath = "";		
	}
	public AffineTransform(Nifti source, Nifti reference) throws FileNotFoundException, IOException {
		this();
		this.setReference(reference);
		this.source = source;
		this.sourcePath = "";
		this.referencePath = "";		
	}

	public AffineTransform(String sourcePath, String referencePath) throws FileNotFoundException, IOException {
		this();
		this.setSourcePath(sourcePath);
		this.setReferencePath(referencePath);
	}
	public static AffineTransform create(String sourcePath, String referencePath) throws FileNotFoundException, IOException {
		return new AffineTransform(sourcePath, referencePath);
	}



	public void setLogFolder(String value) {
		this.logFolder = value;
	}
	public void setUseParallel(boolean value) {
		this.useParallel = value;
	}
	public void setUseParabolic(boolean value) {
		this.useParabolic = value;
	}
	public void setCostImprovementThreshold(double value) {
		this.gradientCostImprovementThreshold = value;
	}
	public void addMask(String path) throws Exception {
		this.maskPath = path;
		this.refMask = Nifti.loadFromFile(maskPath);
		boolean doThrow = false;
		if(this.refMask.getDimX() != this.reference.getDimX()) { doThrow = true; } 
		else if(this.refMask.getDimY() != this.reference.getDimY()) { doThrow = true; }
		else if(this.refMask.getDimZ() != this.reference.getDimZ()) { doThrow = true; }
		if(doThrow) {
			String sourceDim = "[" + this.reference.getDimX() + ", " + this.reference.getDimY() + ", " + this.reference.getDimZ() + "]";
			String maskDim = "[" + this.refMask.getDimX() + ", " + this.refMask.getDimY() + ", " + this.refMask.getDimZ() + "]";
			throw new Exception("source and mask dimensions do not have the same dimensions (source = " + sourceDim + ", mask = " + maskDim + ")");
		}
	}
	public void addBorderMask() {
		this.refMask = this.source.clone();
		int xDim = this.source.getDimX();
		int yDim = this.source.getDimY();
		int zDim = this.source.getDimZ();
		double xMin = xDim * .1;
		double xMax = xDim * .9;
		double yMin = yDim * .1;
		double yMax = yDim * .9;
		double zMin = zDim * .1;
		double zMax = zDim * .9;
		double[][][][] data = this.refMask.getData();
		for(int i = 0; i < xDim; i++) {
			boolean goodX = xMin <= i && i <= xMax;
			for(int j = 0; j < yDim; j++) {
				boolean goodY = yMin <= j && j <= yMax;
				for(int k = 0; k < zDim; k++) {
					boolean goodZ = zMin <= k && k <= zMax;
					data[i][j][k] = new double[1];
					if(goodX && goodY && goodZ) {
						data[i][j][k][0] = 1;
					} else {
						data[i][j][k][0] = 0;
					}
				}
			}
		}
	}

	public Nifti getMask() {
		return this.refMask;
	}
	public void setTransform(double[] values) throws Exception {
		this.transform.setValues(values);
		this.inverseTransform = this.transform.getInverse();
	}
	public void setRigidBody(double xT, double yT, double zT, double xR, double yR, double zR) throws Exception {
		double[] matrix = rigidToMatrixValues(xT, yT, zT, xR, yR, zR);
		this.setTransform(matrix);
	}
//	public void setUseFrequencyScaling(boolean value) {
//		this.useFrequencyScaling = value;
//	}

	public static Matrix4 rigidToMatrix(double xT, double yT, double zT, double xR, double yR, double zR, double xC, double yC, double zC) throws Exception {
		// https://math.stackexchange.com/questions/4397763/3d-rotation-matrix-around-a-point-not-origin
		// T(x) = R(x−v)+v = Rx+(v−Rv)
		Matrix4 pureRotation = new Matrix4(rigidToMatrixValues(0, 0, 0, xR, yR, zR));
		double[] rotatedOffset = pureRotation.transformVertex(new double[] {xC, yC, zC});
		double offX = xC - rotatedOffset[0];
		double offY = yC - rotatedOffset[1];
		double offZ = zC - rotatedOffset[2];


		Matrix4 rotation = new Matrix4(rigidToMatrixValues(offX + xT, offY + yT, offZ + zT, xR, yR, zR));
		return rotation;
	}

	public static double[] rigidToMatrixValues(double xT, double yT, double zT, double xR, double yR, double zR, double xC, double yC, double zC) throws Exception {
		Matrix4 rotation = rigidToMatrix(xT, yT, zT, xR, yR, zR, xC, yC, zC);
		double[][] val = rotation.getMatrix().getArray();
		double[] result = new double[] {
				val[0][0], val[1][0], val[2][0], val[3][0],
				val[0][1], val[1][1], val[2][1], val[3][1],
				val[0][2], val[1][2], val[2][2], val[3][2]
		};
		return result;
	}

	public static double[] rigidToMatrixValues(double xT, double yT, double zT, double xR, double yR, double zR) throws Exception {

		// confirmed to match FSL's matrices from Euler angles: 
		// https://github.com/fithisux/FSL/blob/fsl-5.0.9/src/miscmaths/miscmaths.cc
		// line 806
		// int construct_rotmat_euler(const ColumnVector& params, int n, Matrix& aff,
		// disagreements might come from operations on coordinates vs. those of subindexes


		// https://en.wikipedia.org/wiki/Rotation_matrix

		double cosX = Math.cos(xR);
		double sinX = Math.sin(xR);
		double cosY = Math.cos(yR);
		double sinY = Math.sin(yR);
		double cosZ = Math.cos(zR);
		double sinZ = Math.sin(zR);

		double m00 = cosY * cosZ;
		double m01 = cosY * sinZ;
		double m02 = -sinY;
		double m10 = sinX * sinY * cosZ - cosX * sinZ;
		double m11 = sinX * sinY * sinZ + cosX * cosZ;
		double m12 = sinX * cosY;
		double m20 = cosX * sinY * cosZ + sinX * sinZ;
		double m21 = cosX * sinY * sinZ - sinX * cosZ;
		double m22 = cosX * cosY;

		double[] matrixValues = new double[] {
				m00, m01, m02, xT,
				m10, m11, m12, yT,
				m20, m21, m22, zT,
		};

		return matrixValues;

	}

	public static double[] affineToMatrixValues(double xT, double yT, double zT, double xR, double yR, double zR, double xSkew, double ySkew, double xScale, double yScale) throws Exception {

		double cosX = Math.cos(xR);
		double sinX = Math.sin(xR);
		double cosY = Math.cos(yR);
		double sinY = Math.sin(yR);
		double cosZ = Math.cos(zR);
		double sinZ = Math.sin(zR);

		double m00 = cosY * cosZ;
		double m01 = cosY * sinZ;
		double m02 = -sinY;
		double m10 = sinX * sinY * cosZ - cosX * sinZ;
		double m11 = sinX * sinY * sinZ + cosX * cosZ;
		double m12 = sinX * cosY;
		double m20 = cosX * sinY * cosZ + sinX * sinZ;
		double m21 = cosX * sinY * sinZ - sinX * cosZ;
		double m22 = cosX * cosY;

		double[] matrixValues = new double[] {
				m00 + xScale, m01 + xSkew, m02, xT,
				m10 + ySkew, m11 + yScale, m12, yT,
				m20, m21, m22, zT,
		};

		return matrixValues;

	}


	public static double[] matrixToRigidValues(double[] matrix) {

		double yR = Math.asin(-matrix[2]);
		double zR = Math.atan2(matrix[1], matrix[0]);
		double xR = Math.atan2(matrix[6], matrix[10]);



		//		final double halfPi = Math.PI * 0.5;

		//		if(Math.abs(zR) > halfPi && Math.abs(xR) > halfPi) {
		//
		//			if(zR < 0) {
		//				zR = zR + Math.PI;
		//			}else {
		//				zR = zR - Math.PI;
		//			}
		//			if(xR < 0) {
		//				xR = xR + Math.PI;
		//			} else {
		//				xR = xR - Math.PI;
		//			}
		//			if(yR < 0) {
		//				yR = -(yR + halfPi) - halfPi;
		//			} else {
		//				yR = -(yR - halfPi) + halfPi;
		//			}
		//		}


		double[] outputRigidBody = new double[6];
		outputRigidBody[3] = xR;
		outputRigidBody[4] = yR;
		outputRigidBody[5] = zR;
		outputRigidBody[0] = matrix[3];
		outputRigidBody[1] = matrix[7];
		outputRigidBody[2] = matrix[11];

		return outputRigidBody;
	}

	public static double[] matrixToRigidValuesOld(double[] matrix) {
		final double pi = Math.PI;
		final double twoPi = 2 * Math.PI;
		//		double[] matrix = new double[12];

		// matrix[2] = -sinY;
		double yR = Math.asin(-matrix[2]);
		// there are 4 possible values here between -2pi and 2pi
		double[] yRs = new double[4];
		if(yR > 0) {
			double delta = yR - pi;
			if(delta > 0){
				yRs[3] = yR;
				yRs[2] = pi - delta;
			} else {
				yRs[2] = yR;
				yRs[3] = pi - delta;
			}
			yRs[0] = yRs[2] - twoPi;
			yRs[1] = yRs[3] - twoPi;
		} else {
			double delta = yR + pi;
			if(delta < 0){
				yRs[0] = yR;
				yRs[1] = -pi - delta;
			} else {
				yRs[1] = yR;
				yRs[0] = -pi - delta;
			}
			yRs[2] = yRs[0] + twoPi;
			yRs[3] = yRs[1] + twoPi;
		}

		// m[0] = cosY * cosZ;
		// m[1] = cosY * sinZ;
		double zR = Math.atan2(matrix[1], matrix[0]);
		// there are 2 possible values here
		double[] zRs = new double[2];
		if(zR > 0){
			zRs[1] = zR;
			zRs[0] = zR - twoPi;
		} else {
			zRs[0] = zR;
			zRs[1] = zR + twoPi;
		}


		// matrix[6] = sinX * cosY;
		// matrix[10] = cosX * cosY;
		double xR = Math.atan2(matrix[6], matrix[10]);
		// there are 2 possible values here
		double[] xRs = new double[2];
		if(xR > 0){
			xRs[1] = xR;
			xRs[0] = xR - twoPi;
		} else {
			xRs[0] = xR;
			xRs[1] = xR + twoPi;
		}


		// use the remaining 4 equations to determine which
		// of the 32 combinations is closest
		//		 matrix[4] = sinX * sinY * cosZ - cosX * sinZ;
		//		 matrix[5] = sinX * sinY * sinZ + cosX * cosZ;
		//		 matrix[8] = cosX * sinY * cosZ + sinX * sinZ;
		//		 matrix[9] = cosX * sinY * sinZ - sinX * cosZ;

		double bestCost = Double.MAX_VALUE;
		double bestX = xR;
		double bestY = yR;
		double bestZ = zR;
		for(int i = 0; i < 2; i++) {
			double sinX = Math.sin(xRs[i]);
			double cosX = Math.cos(xRs[i]);
			for(int j = 0; j < 4; j++){
				double sinY = Math.sin(yRs[j]);
				double cosY = Math.cos(yRs[j]);
				for(int k = 0; k < 2; k++){
					double sinZ = Math.sin(zRs[k]);
					double cosZ = Math.cos(zRs[k]);
					double sum = 0;
					double del = matrix[4] - (sinX * sinY * cosZ - cosX * sinZ);
					sum += del * del;
					del = matrix[5] - (sinX * sinY * sinZ + cosX * cosZ);
					sum += del * del;
					del = matrix[8] - (cosX * sinY * cosZ + sinX * sinZ);
					sum += del * del;
					del = matrix[9] - (cosX * sinY * sinZ - sinX * cosZ);
					sum += del * del;

					if(sum < bestCost) {
						bestCost = sum;
						bestX = xRs[i];
						bestY = yRs[j];
						bestZ = zRs[k];
					}
				}
			}
		}

		double[] outputRigidBody = new double[6];
		outputRigidBody[3] = bestX;
		outputRigidBody[4] = bestY;
		outputRigidBody[5] = bestZ;
		outputRigidBody[0] = matrix[3];
		outputRigidBody[1] = matrix[7];
		outputRigidBody[2] = matrix[11];

		return outputRigidBody;

	}


	public static double[] matrixToRotation(Matrix4 matrix) {
		boolean useGradientDescent = false;
		if(useGradientDescent) {

			Mat2RigidCost costFn = new Mat2RigidCost();
			GradientDescent gd = new GradientDescent(costFn);
			gd.setEstimatorCount(3);
			gd.setEstimatorLowerLimit(0, -Math.PI);
			gd.setEstimatorUpperLimit(0, Math.PI);
			gd.setEstimatorLowerLimit(1, -Math.PI);
			gd.setEstimatorUpperLimit(1, Math.PI);
			gd.setEstimatorLowerLimit(2, -Math.PI);
			gd.setEstimatorUpperLimit(2, Math.PI);
			gd.setMaxStagnantIterations(20);
			//		gd.setVerbosity(1);
			gd.putData("matrix", matrix);
			try {
				gd.compute();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return gd.getEstimators();
		} else {
			return matrixToRigidValues(new double[] {
					matrix.get(0, 0), matrix.get(1, 0), matrix.get(2, 0), matrix.get(3, 0),
					matrix.get(0, 1), matrix.get(1, 1), matrix.get(2, 1), matrix.get(3, 1),
					matrix.get(0, 2), matrix.get(1, 2), matrix.get(2, 2), matrix.get(3, 2)});
		}
	}
	public static class Mat2RigidCost implements ICostFunction {

		@Override
		public double calculate(Map<String, Object> data, double[] estimators) {
			Matrix4 matrix = (Matrix4) data.get("matrix");
			double xR = estimators[0];
			double yR = estimators[1];
			double zR = estimators[2];
			Matrix4 rigid = null;
			try {
				rigid = AffineTransform.rigidToMatrix(0, 0, 0, xR, yR, zR);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			double cost = 0;
			double del = matrix.get(0,  0) - rigid.get(0, 0);
			cost += del * del;
			del = matrix.get(0,  1) - rigid.get(0, 1);
			cost += del * del;
			del = matrix.get(0,  2) - rigid.get(0, 2);
			cost += del * del;
			del = matrix.get(1,  0) - rigid.get(1, 0);
			cost += del * del;
			del = matrix.get(1,  1) - rigid.get(1, 1);
			cost += del * del;
			del = matrix.get(1,  2) - rigid.get(1, 2);
			cost += del * del;
			del = matrix.get(2,  0) - rigid.get(2, 0);
			cost += del * del;
			del = matrix.get(2,  1) - rigid.get(2, 1);
			cost += del * del;
			del = matrix.get(2,  2) - rigid.get(2, 2);
			cost += del * del;
			return cost;

		}

		@Override
		public Map<String, String> getDataNames() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getEstimatorCount() {
			// TODO Auto-generated method stub
			return 0;
		}

	}


	public static double[] rigidToMatrixValuesExperimental(double xT, double yT, double zT, double xR, double yR, double zR) throws Exception {
		double cosX = Math.cos(xR);
		double sinX = Math.sin(xR);
		double cosY = Math.cos(yR);
		double sinY = Math.sin(yR);
		double cosZ = Math.cos(zR);
		double sinZ = Math.sin(zR);


		//	X Rotation
		//    1     0     0    0
		//    0  cosX -sinX    0
		//    0  sinX  cosX    0
		//    0     0     0    1

		// Y Rotation
		//  cosY    0  sinY    0
		//     0    1     0    0
		// -sinY    0  cosY    0
		//     0    0     0    1

		// Z Rotation
		//  cosZ  -sinZ   0    0
		//  sinZ   cosZ   0    0
		//     0      0   1    0
		//     0      0   0    1

		// Xrot * Yrot
		// (cosY) (0) (sinY) (0)
		// (-sinX * -sinY) (cosX) (-sinX * cosY) (0)
		// (cosX * -sinY) (sinX) (cosX * cosY) (0)
		// (0) (0) (0) (1)

		// Xrot * Yrot * Zrot
		// (cosY * cosZ) (cosY * -sinZ) (sinY) (0)
		// (-sinX * -sinY * cosZ + cosX * sinZ) (-sinX * -sinY * -sinZ + cosX * cosZ) (-sinX * cosY) (0)
		// (cosX * -sinY * cosZ + sinX * sinZ) (cosX * -sinY * -sinZ + sinX * cosZ) (cosX * cosY) (0)
		// (0) (0) (0) (1)

		double m00 = cosY * cosZ;
		//		double m01 = cosY * sinZ;
		double m01 = cosY * -sinZ;
		//		double m02 = -sinY;
		double m02 = sinY;
		//		double m10 = sinX * sinY * cosZ - cosX * sinZ;
		double m10 = -sinX * -sinY * cosZ + cosX * sinZ;
		//		double m11 = sinX * sinY * sinZ + cosX * cosZ;
		double m11 = -sinX * -sinY * -sinZ + cosX * cosZ;
		//		double m12 = sinX * cosY;
		double m12 = -sinX * cosY;
		//		double m20 = cosX * sinY * cosZ + sinX * sinZ;
		double m20 = cosX * -sinY * cosZ + sinX * sinZ;
		//		double m21 = cosX * sinY * sinZ - sinX * cosZ;
		double m21 = cosX * -sinY * -sinZ + sinX * cosZ;
		//		double m22 = cosX * cosY;
		double m22 = cosX * cosY;

		double[] matrixValues = new double[] {
				m00, m01, m02, xT,
				m10, m11, m12, yT,
				m20, m21, m22, zT,
		};

		return matrixValues;

	}

	public static void motionCorrect(String fmriFile, String outputFolder) {
		AffineTransform transform = null;
		try {
			Nifti nii = Nifti.loadFromFile(fmriFile);
			transform = new AffineTransform(nii);
		} catch (FileNotFoundException e) {
			System.out.println("file not found: " + fmriFile);
		} catch (IOException e) {
			System.out.println("unable to open file: " + fmriFile);
		}
		if(transform != null) {
			transform.setLogFolder(outputFolder);
			try {
				transform.sliceMotionCorrect();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static Matrix4 rigidToMatrix(double xT, double yT, double zT, double xR, double yR, double zR) throws Exception {
		double[] matrixValues = rigidToMatrixValues(xT, yT, zT, xR, yR, zR);
		Matrix4 result = new Matrix4();
		result.setValues(matrixValues);
		return result;
	}

	public Matrix4 getTransformMatrix() {
		return this.transform;
	}
	public void setTransformMatrix(Matrix4 value) {
		this.transform = value;
	}
	public GradientDescent getGradientDescent() {
		return this.gd;
	}

	/// do this so we can execute multiple transforms in parallel
	public double[][][] getTransformedPureFunction(double sourceTimeSeconds, Matrix4 affineTransform){
		int refXSize = this.reference.getDimX();
		int refYSize = this.reference.getDimY();
		int refZSize = this.reference.getDimZ();
		int refTSize = this.reference.getDimT();
		double[][][] result = new double[refXSize][][];
		double[][] refSubs = new double[refZSize][];
		Matrix4 refSub2CoordMat = reference.getSubToCoordMatrix();
		Matrix4 affined = affineTransform.times(refSub2CoordMat);

		for(int i = 0; i < refZSize; i++) {
			refSubs[i] = new double[3];
		}

		//		boolean useParallel = true;
		if(useParallel) {
			long voxelCount = ((long)refXSize) * ((long)refYSize) * ((long)refZSize);
			for(int i = 0; i < refXSize; i++) {
				result[i] = new double[refYSize][];
				for(int j = 0; j < refYSize; j++) {
					result[i][j] = new double[refZSize];
				}
			}
			IndexGenerator generator = new IndexGenerator(voxelCount);
			generator.getStream().forEach(index -> {				
				long divisor = refXSize;
				int i = (int)(index % divisor);
				long remainder = (index - i) / divisor;
				divisor = refYSize;
				//				int k = (int)(remainder % divisor);
				//				int j = (int)((remainder - k) / divisor);
				int j = (int)(remainder % divisor);
				int k = (int)((remainder - j) / divisor);

				double[] coords = affined.transformVertex(new double[] {i, j, k});
				double value = source.getLinearInterpolationOfCoordinates(
						coords[0], coords[1], coords[2], sourceTimeSeconds);
				result[i][j][k] = value;
			});


		}else {


			for(int i = 0; i < refXSize; i++) {
				result[i] = new double[refYSize][];
				for(int j = 0; j < refYSize; j++) {
					result[i][j] = new double[refZSize];
					for(int k = 0; k < refZSize; k++) {
						//					result[i][j][k] = new double[1];
						refSubs[k][0] = i;
						refSubs[k][1] = j;
						refSubs[k][2] = k;
					}
					double[][] coords = affined.transformVertices(refSubs);
					for(int k = 0; k < refZSize; k++) {
						double[] coord = coords[k];
						double value = source.getLinearInterpolationOfCoordinates(
								coord[0], coord[1], coord[2], sourceTimeSeconds);
						result[i][j][k] = value;
					}
				}
			}
		}
		return result;
	} // AffineTransform::getTransformedPureFunction

	public Nifti getSource() {return this.source;}
	public String getSourcePath() {return this.sourcePath;}
	public Nifti getReference() {return this.reference;}
	public String getReferencePath() {return this.referencePath;}
	public Nifti getTransformed() {
		Nifti result = this.reference.clone();
		result.setDimT(source.getDimT());
		double time = 0;
		boolean finished = false;
		int volume = 0;
		while(!finished) {
			double[][][] txData = this.getTransformedPureFunction(time, this.transform);
			for(int i = 0; i < txData.length; i++) {
				for(int j = 0; j < txData[0].length; j++) {
					for(int k = 0; k < txData[0][0].length; k++) {
						double value = txData[i][j][k];
						result.setData(i, j, k, volume, value);						
					}
				}
			}
			time += source.getRepetitionTime();
			volume++;
			if(volume >= source.getDimT()) {
				finished = true;
			}
		}

		return result;
	}



	public void setReference(Nifti ref) {
		this.reference = ref;
		//		double zoomFactor = 10;
		this.minTranslateX = -this.reference.getDimX();
		this.maxTranslateX = this.reference.getDimX();
		this.minTranslateY = -this.reference.getDimY();
		this.maxTranslateY = this.reference.getDimY();
		this.minTranslateZ = -this.reference.getDimZ();
		this.maxTranslateZ = this.reference.getDimZ();

		this.minRotateX = -Math.PI;
		this.maxRotateX = Math.PI;
		this.minRotateY = -Math.PI;
		this.maxRotateY = Math.PI;
		this.minRotateZ = -Math.PI;
		this.maxRotateZ = Math.PI;

		this.minFreqEncScale = 0.9;
		this.maxFreqEncScale = 1.1;

		if(!Double.isNaN(this.initialSpanFraction)) {
			this.spanTranslateX = this.reference.getDimX() * this.initialSpanFraction;
			this.spanTranslateY = this.reference.getDimY() * this.initialSpanFraction;
			this.spanTranslateZ = this.reference.getDimZ() * this.initialSpanFraction;
			this.spanRotateX = Math.PI * this.initialSpanFraction;
			this.spanRotateY = Math.PI * this.initialSpanFraction;
			this.spanRotateZ = Math.PI * this.initialSpanFraction;
			this.spanFreqEncScale = (this.maxFreqEncScale - this.minFreqEncScale) * this.initialSpanFraction;
		}
	}

	public void setReferencePath(String path) throws FileNotFoundException, IOException {
		this.referencePath = path;
		if(new File(path).exists()) {
			this.setReference(Nifti.loadFromFile(path));
		}
	}
	public void setSourcePath(String path) throws FileNotFoundException, IOException {
		this.sourcePath = path;
		if(new File(sourcePath).exists()) {
			this.source = Nifti.loadFromFile(path);						
		}

		//		if(this.referencePath != null && this.sourcePath.contentEquals(referencePath)) {
		//			this.source = this.reference;			
		//		}else {
		//			if(new File(sourcePath).exists()) {
		//				this.source = Nifti.loadFromFile(path);						
		//			}
		//		}
		if(this.source != null) {
			this.sliceDigitCount = (int)Math.floor(Math.log10(source.getDimZ())) + 1;
			this.volumeDigitCount = (int)Math.floor(Math.log10(source.getDimT())) + 1;			
		}
	}

	public void setSourcePathWithoutLoading(String path) throws FileNotFoundException, IOException {
		this.sourcePath = path;
		if(source != null) {
			this.sliceDigitCount = (int)Math.floor(Math.log10(source.getDimZ())) + 1;
			this.volumeDigitCount = (int)Math.floor(Math.log10(source.getDimT())) + 1;
		}
	}
	public void setReferencePathWithoutLoading(String path) throws FileNotFoundException, IOException {
		this.referencePath = path;
	}




	public GradientDescent register(double sourceTime, double referenceTime, String logPath) throws Exception {
		Double source = sourceTime;
		Double ref = referenceTime;
		return register(source, ref, logPath);

	}
	public GradientDescent register(Double sourceTime, Double referenceTime, String logPath) throws Exception {

		// do an initial sweep on a smaller version
		double scale = 0.25;
		int probeCount = 7;
		//		int probeCount = 11;
		GradientDescent scaledDescent = registerScaled(sourceTime, referenceTime, logPath, scale, probeCount);


		double opt1 = this.transform.get(0, 0);
		double opt2 = this.transform.get(0, 1);
		double opt3 = this.transform.get(0, 2);
		double opt4 = this.transform.get(0, 3);
		double opt5 = this.transform.get(1, 0);
		double opt6 = this.transform.get(1, 1);
		double opt7 = this.transform.get(1, 2);
		double opt8 = this.transform.get(1, 3);
		double opt9 = this.transform.get(2, 0);
		double opt10 = this.transform.get(2, 1);
		double opt11 = this.transform.get(2, 2);
		double opt12 = this.transform.get(2, 3);
		double opt13 = this.transform.get(3, 0); // est[0] (translate x)
		double opt14 = this.transform.get(3, 1); // est[1] (translate y)
		double opt15 = this.transform.get(3, 2); // est[2] (translate z)
		double opt16 = this.transform.get(3, 3);

		double[] est = scaledDescent.getEstimators();

		// set translation bounds
		double voxX = this.reference.getVoxelSizeX();
		double voxY = this.reference.getVoxelSizeY();
		double voxZ = this.reference.getVoxelSizeZ();
		this.initialTranslateX = est[0];
		this.maxTranslateX = this.initialTranslateX + voxX * 4;
		this.minTranslateX = this.initialTranslateX - voxX * 4;
		this.spanTranslateX = voxX * 4;
		this.initialTranslateY = est[1];
		this.maxTranslateY = this.initialTranslateY + voxY * 4;
		this.minTranslateY = this.initialTranslateY - voxY * 4;
		this.spanTranslateY = voxY * 4;
		this.initialTranslateZ = est[2];
		this.maxTranslateZ = this.initialTranslateZ + voxY * 4;
		this.minTranslateZ = this.initialTranslateZ - voxY * 4;
		this.spanTranslateZ = voxZ * 4;

		// set rotation bounds
		double rotationLimit = Math.PI / 20;
		this.initialRotateX = est[3];
		this.minRotateX = this.initialRotateX - rotationLimit;
		this.maxRotateX = this.initialRotateX + rotationLimit;
		this.spanRotateX = rotationLimit;
		this.initialRotateY = est[4];
		this.minRotateY = this.initialRotateY - rotationLimit;
		this.maxRotateY = this.initialRotateY + rotationLimit;
		this.spanRotateY = rotationLimit;
		this.initialRotateZ = est[5];
		this.minRotateZ = this.initialRotateZ - rotationLimit;
		this.maxRotateZ = this.initialRotateZ + rotationLimit;
		this.spanRotateZ = rotationLimit;

		if(est.length > 6) {

			this.initialFreqEncScale = est[6];
			this.minFreqEncScale = 0.9;
			this.maxFreqEncScale = 1.1;
			this.spanFreqEncScale = maxFreqEncScale - minFreqEncScale;  
		}

		// final registration
		this.setGradientProbeCount(21);
		GradientDescent finalDescent = this.registerInternal(sourceTime, referenceTime, logPath);
		return finalDescent;
	} // AffineTransform::register
	public static double[][] transformVertices(double[][] vertices, double xR, double yR, double zR, double xT, double yT, double zT) throws Exception {
		Matrix4 mat = AffineTransform.rigidToMatrix(xT, yT, zT, xR, yR, zR);
		return mat.transformVertices(vertices);
	}
	public static double[][] transformVertices(double[][] vertices, double xR, double yR, double zR, double xT, double yT, double zT, double xC, double yC, double zC) throws Exception {
		Matrix4 mat = AffineTransform.rigidToMatrix(xT, yT, zT, xR, yR, zR, xC, yC, zC);
		return mat.transformVertices(vertices);
	}

	public double[][][] getTransformedReferenceVolume(double xR, double yR, double zR, double xT, double yT, double zT) throws Exception {
		int sourceXDim = this.source.getDimX();
		int sourceYDim = this.source.getDimY();
		int sourceZDim = this.source.getDimZ();
		double[] sourceI = new double[sourceXDim * sourceYDim * sourceZDim];
		double[] sourceJ = new double[sourceI.length];
		double[] sourceK = new double[sourceI.length];
		//		int refVolume = 0;
		int x = 0;
		int y = 0;
		int z = 0;
		for(int i = 0; i < sourceI.length; i++) {
			sourceI[i] = x;
			sourceJ[i] = y;
			sourceK[i] = z;
			x++;
			if(x >= sourceXDim) {
				x = 0;
				y++;
				if(y >= sourceYDim) {
					y = 0;
					z++;
				}
			}
		}
		double[][] sourceCoordinates = this.source.subIndex2Coord(sourceI, sourceJ, sourceK);
		double[][] refCoordinates = AffineTransform.transformVertices(sourceCoordinates, xR, yR, zR, xT, yT, zT);
		for(int i = 0; i < refCoordinates.length; i++) {
			double xx = refCoordinates[i][0];
			double yy = refCoordinates[i][1];
			double zz = refCoordinates[i][2];
			refCoordinates[i] = new double[4];
			refCoordinates[i][0] = xx;
			refCoordinates[i][1] = yy;
			refCoordinates[i][2] = zz;
			refCoordinates[i][3] = this.referenceVolume * this.reference.getRepetitionTime();
		}

		double[] values = this.reference.getParallelCubicInterpolationOfCoordinates(refCoordinates);
		double[][][] result = new double[sourceXDim][sourceYDim][sourceZDim];

		x = 0;
		y = 0;
		z = 0;
		for(int i = 0; i < values.length; i++) {
			result[x][y][z] = values[i];
			x++;
			if(x >= sourceXDim) {
				x = 0;
				y++;
				if(y >= sourceYDim) {
					y = 0;
					z++;
				}
			}
		}

		//		if(false) {
		//			double[][] slice = Imagesc.getSlice(result, 2, result[0][0].length / 2);
		//			Imagesc.imagesc(slice);
		//			System.out.println(String.format("xR=%f, yR=%f, zR=%f, xT=%f, yT=%f, zT=%f, vol=%d", xR, yR, zR, xT, yT, zT, this.referenceVolume));
		//			int dummy = 1;
		//		}

		return result;
	} // AffineTransform::transformReferenceVolume

	public double[][] getTransformedSliceCoordinates(int volumeNumber, int sliceNumber, double xR, double yR, double zR, double xT, double yT, double zT) throws Exception {
		int sourceXDim = this.source.getDimX();
		int sourceYDim = this.source.getDimY();

		double[] sourceI = new double[sourceXDim * sourceYDim];
		double[] sourceJ = new double[sourceI.length];
		double[] sourceK = new double[sourceI.length];
		double[] sourceV = new double[sourceI.length];

		int x = 0;
		int y = 0;
		for(int i = 0; i < sourceI.length; i++) {
			sourceI[i] = x;
			sourceJ[i] = y;
			sourceK[i] = sliceNumber;
			x++;
			if(x >= sourceXDim) {
				x = 0;
				y++;
			}
		}
		double[][] sourceCoordinates = this.source.subIndex2Coord(sourceI, sourceJ, sourceK);
		double minX = sourceCoordinates[0][0];
		double maxX = sourceCoordinates[sourceCoordinates.length - 1][0];
		double minY = sourceCoordinates[0][1];
		double maxY = sourceCoordinates[sourceCoordinates.length - 1][1];
		double minZ = sourceCoordinates[0][2];
		double maxZ = sourceCoordinates[sourceCoordinates.length - 1][2];
		double xC = (minX + maxX) * 0.5;
		double yC = (minY + maxY) * 0.5;
		double zC = (minZ + maxZ) * 0.5;

		double[][] refCoordinates = AffineTransform.transformVertices(sourceCoordinates, xR, yR, zR, xT, yT, zT, xC, yC, zC);
		return refCoordinates;

	} // AffineTransform::getTransformedSliceCoordinates

	public double[][] getTransformedReferenceSlice(int volumeNumber, int sliceNumber, double xR, double yR, double zR, double xT, double yT, double zT) throws Exception {
		//		int sourceXDim = this.source.getDimX();
		//		int sourceYDim = this.source.getDimY();
		//		
		//		double[] sourceI = new double[sourceXDim * sourceYDim];
		//		double[] sourceJ = new double[sourceI.length];
		//		double[] sourceK = new double[sourceI.length];
		//		double[] sourceV = new double[sourceI.length];
		//		int refVolume = 0;
		//		int x = 0;
		//		int y = 0;
		//		for(int i = 0; i < sourceI.length; i++) {
		//			sourceI[i] = x;
		//			sourceJ[i] = y;
		//			sourceK[i] = sliceNumber;
		//			x++;
		//			if(x >= sourceXDim) {
		//				x = 0;
		//				y++;
		//			}
		//		}
		//		double[][] sourceCoordinates = this.source.subIndex2Coord(sourceI, sourceJ, sourceK);
		//		double minX = sourceCoordinates[0][0];
		//		double maxX = sourceCoordinates[sourceCoordinates.length - 1][0];
		//		double minY = sourceCoordinates[0][1];
		//		double maxY = sourceCoordinates[sourceCoordinates.length - 1][1];
		//		double minZ = sourceCoordinates[0][2];
		//		double maxZ = sourceCoordinates[sourceCoordinates.length - 1][2];
		//		double xC = (minX + maxX) * 0.5;
		//		double yC = (minY + maxY) * 0.5;
		//		double zC = (minZ + maxZ) * 0.5;
		//		
		//		double[][] refCoordinates = AffineTransform.transformVertices(sourceCoordinates, xR, yR, zR, xT, yT, zT, xC, yC, zC);
		int sourceXDim = this.source.getDimX();
		int sourceYDim = this.source.getDimY();
		double[][] refCoordinates = getTransformedSliceCoordinates(volumeNumber, sliceNumber, xR, yR, zR, xT, yT, zT);
		for(int i = 0; i < refCoordinates.length; i++) {
			double xx = refCoordinates[i][0];
			double yy = refCoordinates[i][1];
			double zz = refCoordinates[i][2];
			refCoordinates[i] = new double[4];
			refCoordinates[i][0] = xx;
			refCoordinates[i][1] = yy;
			refCoordinates[i][2] = zz;
			refCoordinates[i][3] = this.referenceVolume;
		}

		double[] values = this.reference.getParallelCubicInterpolationOfCoordinates(refCoordinates);
		double[][] result = new double[sourceXDim][];
		for(int i = 0; i < sourceXDim; i++) {
			result[i] = new double[sourceYDim];
		}

		int x = 0;
		int y = 0;
		for(int i = 0; i < values.length; i++) {
			result[x][y] = values[i];
			x++;
			if(x >= sourceXDim) {
				x = 0;
				y++;
			}
		}
		return result;
	} // AffineTransform::getTransformedReferenceSlice


	public double getVolumeDifference(int volumeNumber, double xR, double yR, double zR, double xT, double yT, double zT) throws Exception {
		int sourceXDim = this.source.getDimX();
		int sourceYDim = this.source.getDimY();
		int sourceZDim = this.source.getDimZ();

		int refVolume = 0;
		//		double[][][] refVals = this.getTransformedReferenceVolume(volumeNumber, xR, yR, zR, xT, yT, zT);
		double[][][] refVals = this.getTransformedReferenceVolume(xR, yR, zR, xT, yT, zT);
		double sourceSum = 0;
		double referenceSum = 0;
		double[][][][] srcData = this.source.getData();
		//		for(int i = 0; i < sourceXDim; i++) {
		//			double srcSlice = 0;
		//			double refSlice = 0;
		//			for(int j = 0; j < sourceYDim; j++) {
		//				double srcRow = 0;
		//				double refRow = 0;
		//				for(int k = 0; k < sourceZDim; k++) {
		//					srcRow += srcData[i][j][k][volumeNumber];
		//					refRow += refVals[i][j][k];
		//				}
		//				srcSlice += srcRow;
		//				refSlice += refRow;
		//			}
		//			sourceSum += srcSlice;
		//			referenceSum += refSlice;
		//		}
		//		
		//		double sourceFactor = sourceXDim * sourceYDim * sourceZDim / sourceSum;
		//		double refFactor = sourceXDim * sourceYDim * sourceZDim / referenceSum;
		double sourceFactor = 1;
		double refFactor = 1;

		double diffSumSqr = 0;
		double maskTotal = 0;
		if(this.refMask == null) {
			maskTotal = sourceXDim * sourceYDim * sourceZDim;
		}
		for(int i = 0; i < sourceXDim; i++) {
			double diffSlice = 0;
			for(int j = 0; j < sourceYDim; j++) {
				double diffRow = 0;
				for(int k = 0; k < sourceZDim; k++) {
					double srcVal = sourceFactor * srcData[i][j][k][volumeNumber];
					double refVal = refFactor * refVals[i][j][k];
					double diff = srcVal - refVal;
					double maskVal = 1;
					if(this.refMask != null) {
						maskVal = this.refMask.getData()[i][j][k][0];
						maskTotal += maskVal;
					}
					double diff2 = diff * diff * maskVal;
					diffRow += diff2;
				}
				diffSlice += diffRow;
			}
			diffSumSqr += diffSlice;
		}
		double result = Math.sqrt(diffSumSqr) / maskTotal;
		return result;

	} // AffineTransform::getVolumeDifference (rotate/translate)

//	public double getSliceDifference(int volumeNumber, int sliceNumber, 
//			double xR, double yR, double zR, 
//			double xT, double yT, double zT, 
//			double xSkew, double ySkew, 
//			double xScale, double yScale) throws Exception {
//		int sourceXDim = this.source.getDimX();
//		int sourceYDim = this.source.getDimY();
//
//		// copy data
//		double[][] refVals = this.getTransformedReferenceSlice(this.referenceVolume, sliceNumber, xR, yR, zR, xT, yT, zT);
//		double sourceSum = 0;
//		double referenceSum = 0;
//		double[][][][] srcData = this.source.getData();
//		boolean doFrequencyScaling = true;
//
//		double[][] stretched;
//		stretched = new double[srcData.length][];
//		for(int i = 0; i < srcData.length; i++) {
//			stretched[i] = new double[srcData[i].length];
//			for(int j = 0; j < stretched[i].length; j++) {
//				stretched[i][j] = srcData[i][j][sliceNumber][volumeNumber];
//			}
//		}
//
//
//		for(int i = 0; i < sourceXDim; i++) {
//			double srcRow = 0;
//			double refRow = 0;
//			for(int j = 0; j < sourceYDim; j++) {
//				//				srcRow += srcData[i][j][sliceNumber][volumeNumber];
//				srcRow += stretched[i][j];
//				refRow += refVals[i][j];
//			}
//			sourceSum += srcRow;
//			referenceSum += refRow;
//		}
//
//		double sourceFactor = sourceXDim * sourceYDim / sourceSum;
//		double refFactor = sourceXDim * sourceYDim / referenceSum;
//
//		double diffSumSqr = 0;
//		for(int i = 0; i < sourceXDim; i++) {
//			double diffRow = 0;
//			for(int j = 0; j < sourceYDim; j++) {
//				//				double srcVal = sourceFactor * srcData[i][j][sliceNumber][volumeNumber];
//				double srcVal = sourceFactor * stretched[i][j];
//				double refVal = refFactor * refVals[i][j];
//				double diff = srcVal - refVal;
//				double diff2 = diff * diff;
//				diffRow += diff2;
//			}
//			diffSumSqr += diffRow;
//		}
//		double result = Math.sqrt(diffSumSqr) / (sourceXDim * sourceYDim);
//		return result;
//	} // AffineTransform::getSliceDifference (with skew/scale)

	public double getSliceDifference(int volumeNumber, int sliceNumber, 
			double xR, double yR, double zR, 
			double xT, double yT, double zT) throws Exception {
		int sourceXDim = this.source.getDimX();
		int sourceYDim = this.source.getDimY();

		// copy data
		double[][] refVals = this.getTransformedReferenceSlice(this.referenceVolume, sliceNumber, xR, yR, zR, xT, yT, zT);
		double sourceSum = 0;
		double referenceSum = 0;
		double[][][][] srcData = this.source.getData();

		double[][] slice;
		slice = new double[srcData.length][];
		for(int i = 0; i < srcData.length; i++) {
			slice[i] = new double[srcData[i].length];
			for(int j = 0; j < slice[i].length; j++) {
				slice[i][j] = srcData[i][j][sliceNumber][volumeNumber];
			}
		}


		for(int i = 0; i < sourceXDim; i++) {
			double srcRow = 0;
			double refRow = 0;
			for(int j = 0; j < sourceYDim; j++) {
				//				srcRow += srcData[i][j][sliceNumber][volumeNumber];
				srcRow += slice[i][j];
				refRow += refVals[i][j];
			}
			sourceSum += srcRow;
			referenceSum += refRow;
		}

		double sourceFactor = sourceXDim * sourceYDim / sourceSum;
		double refFactor = sourceXDim * sourceYDim / referenceSum;

		double diffSumSqr = 0;
		for(int i = 0; i < sourceXDim; i++) {
			double diffRow = 0;
			for(int j = 0; j < sourceYDim; j++) {
				//				double srcVal = sourceFactor * srcData[i][j][sliceNumber][volumeNumber];
				double srcVal = sourceFactor * slice[i][j];
				double refVal = refFactor * refVals[i][j];
				double diff = srcVal - refVal;
				double diff2 = diff * diff;
				diffRow += diff2;
			}
			diffSumSqr += diffRow;
		}
		double result = Math.sqrt(diffSumSqr) / (sourceXDim * sourceYDim);
		return result;

	} // AffineTransform::getSliceDifference (rotate/translate)
	
	public void setCostReductionRatio(double value) {
		this.costReductionRatio = value;
	}
	public void setMaxIterations(long value) {
		this.maxIterations = value;
	}
	public void setMaxStagnantIterations(int value) {
		this.maxStagnantIterations = value;
	}
	public double getInitialSpanFraction() {
		return this.initialSpanFraction;
	}
	public void setInitialSpanFraction(double value) {
		this.initialSpanFraction = value;
		if(!Double.isNaN(value)) {
			this.spanRotateX = (this.maxRotateX - this.minRotateX) * this.initialSpanFraction;
			this.spanRotateY = (this.maxRotateY - this.minRotateY) * this.initialSpanFraction;
			this.spanRotateZ = (this.maxRotateZ - this.minRotateZ) * this.initialSpanFraction;
			this.spanTranslateX = (this.maxTranslateX - this.minTranslateX) * this.initialSpanFraction;
			this.spanTranslateY = (this.maxTranslateY - this.minTranslateY) * this.initialSpanFraction;
			this.spanTranslateZ = (this.maxTranslateZ - this.minTranslateZ) * this.initialSpanFraction;
			this.spanFreqEncScale = (this.maxFreqEncScale - this.minFreqEncScale) * this.initialSpanFraction;
		}
	}

	private GradientDescent registerScaled(Double sourceTime, Double referenceTime, String logPath, double scale, int probeCount) throws Exception {

		Nifti scaledSource = this.source.scaleSpace(scale);
		Nifti scaledRef = this.reference.scaleSpace(scale);
		AffineTransform transform = new AffineTransform(scaledSource, scaledRef);

		transform.setGradientProbeCount(probeCount);
		transform.setCostReductionRatio(2);

		GradientDescent scaledDescent = registerInternal(sourceTime, referenceTime, logPath);
		return scaledDescent;
	}


	public SliceRegistration registerSlice(int sourceVolumeNumber, int sourceSliceNumber) throws Exception {
		return registerSliceCubic(sourceVolumeNumber, sourceSliceNumber);
	}

	private GradientDescent generateGradientDescent() {
		CostNiftiRigid costFunction = new CostNiftiRigid();
		GradientDescent gd = new GradientDescent(costFunction);

		gd.setMandatoryCostReductionFactor(this.costReductionRatio);
		gd.setMaxIterations(this.maxIterations);
		setGradientDescentRigidEstimators(gd);


		gd.setMaxStagnantIterations(this.maxStagnantIterations);
		gd.setMaxPristineIterations(maxPristineIterations);
		gd.setAdjustmentSizeThreshold(1e-18);
		gd.setCostImprovementThreshold(1e-12);

		gd.setLinearProbeArray(this.gradientProbeCount, this.gradientMaxScaleFactor, this.gradientMinScaleFactor);

		gd.putData("sourceAndRef", this);

		gd.setVerbosity(verbosity);

		gd.setParallel(this.useParallel);
		gd.setUseQuadratic(useParabolic);

		return gd;
	}
	public SliceRegistration registerSliceCubic(Integer sourceVolumeNumber, Integer sourceSliceNumber) throws Exception {
		return registerSliceCubic(sourceVolumeNumber, sourceSliceNumber, 6, 
				null, null, null, null, null, 
				null, null, null, null);
	}


	public SliceRegistration registerSliceCubic(Integer sourceVolumeNumber, Integer sourceSliceNumber, int degreesOfFreedom, 
			Double xR, Double yR, Double zR, Double xT, Double zT,
			Double xSkew, Double ySkew, Double xScale, Double yScale) throws Exception {
		CostNiftiRigid costFunction = new CostNiftiRigid();
		//		GradientDescent gd = new GradientDescent(costFunction);
		gd = generateGradientDescent();
		gd.putData("xR", xR);
		gd.putData("yR", yR);
		gd.putData("zR", zR);
		gd.putData("xT", xT);
		gd.putData("zT", zT);
		gd.putData("xSkew", xSkew);
		gd.putData("ySkew", ySkew);
		gd.putData("xScale", xScale);
		gd.putData("yScale", yScale);
		if(logFolder != null) {
			if(volumeDigitCount > 0 && sliceDigitCount > 0) {

				String[] sourceparts = Util.fileparts(this.sourcePath);
				String logFile = "";
				if(sourceSliceNumber == null) {
					String fileFormat = String.format("reg_%%s_vol-%%0%dd", volumeDigitCount);
					logFile = String.format(fileFormat, sourceparts[1], sourceVolumeNumber);
				} else {
					String fileFormat = String.format("reg_%%s_slice-%%0%dd_vol-%%0%dd", sliceDigitCount, volumeDigitCount);
					logFile = String.format(fileFormat, sourceparts[1], sourceSliceNumber, sourceVolumeNumber);
				}
				java.time.LocalDateTime now = java.time.LocalDateTime.now();
				int mills = (int)(now.getNano() / 1e6);
				String datedLogFile = String.format(
						"%s_%04d-%02d-%02d_%02d-%02d-%02d-%03d.json", 
						logFile, 
						now.getYear(), 
						now.getMonthValue(), 
						now.getDayOfMonth(), 
						now.getHour(), 
						now.getMinute(), 
						now.getSecond(),
						mills);
				String logPath = Util.fullfile(logFolder, datedLogFile);
				gd.setLogPath(logPath);
			}
		}
		gd.setParallel(this.useParallel);
		gd.setMaxStagnantIterations(maxStagnantIterations);
		gd.setAdjustmentSizeThreshold(1e-18);
		gd.setCostImprovementThreshold(1e-12);
		gd.setMaxIterations(maxIterations);
		gd.setLinearProbeArray(this.gradientProbeCount, this.gradientMaxScaleFactor, this.gradientMinScaleFactor);
		//		int estimatorCount = gd.getEstimators().length;
		gd.setEstimatorCount(degreesOfFreedom);
		double[] initialEstimators = new double[] {
				this.initialRotateX, this.initialRotateY, this.initialRotateZ,
				this.initialTranslateX, this.initialTranslateY, this.initialTranslateZ,
		};
		double[] upperLimits = new double[] {
				maxRotateX, maxRotateY, maxRotateZ, 
				maxTranslateX, maxTranslateY, maxTranslateZ
		};
		double[] lowerLimits = new double[] {
				minRotateX, minRotateY, minRotateZ, 
				minTranslateX, minTranslateY, minTranslateZ
		};
		int[] inds = null; 
		if(degreesOfFreedom == 1) {
			inds = new int[] {4};
		} else if(degreesOfFreedom == 2) {
			inds = new int[] {3, 4};			
		} else if(degreesOfFreedom == 3) {
			inds = new int[] {3, 4, 5};			
		} else if(degreesOfFreedom == 6) {
			inds = new int[] {0, 1, 2, 3, 4, 5};
		} else {
			throw new Exception("unhandled degrees of freedom: " + degreesOfFreedom);
		}


		int estimatorCount = degreesOfFreedom;
		for(int i = 0; i < estimatorCount; i++) {
			gd.setEstimator(i, initialEstimators[inds[i]]);
			double upper = upperLimits[inds[i]];
			double lower = lowerLimits[inds[i]];
			double init = initialEstimators[inds[i]];
//			double range = gd.getEstimatorUpperLimit(i) - gd.getEstimatorLowerLimit(i);
			double range = upper - lower;
			if(Double.isFinite(range)) {
				double spanFraction = this.getInitialSpanFraction();
				if(!Double.isNaN(spanFraction)) {
					gd.setInitialAdjustmentSpan(i, range * spanFraction);					
				}
			}
		}


		gd.setYokedScaleFactors(false);

		gd.putData("sourceAndRef", this);
		gd.putData("volumeNumber", sourceVolumeNumber);
		gd.putData("sliceNumber", sourceSliceNumber);
		gd.setVerbosity(verbosity);
		if(sourceSliceNumber == null) {
			gd.setVerbosity(1);
		} else {
			gd.setVerbosity(0);
		}



		//		if(!this.useFrequencyScaling) {
		//			gd.removeEstimator(6);
		//			//			gd.setEstimator(6, Double.NaN);
		//			//			gd.setEstimatorLowerLimit(6, Double.NaN);
		//			//			gd.setEstimatorUpperLimit(6, Double.NaN);
		//		}
		gd.setParallel(false);

		gd.compute();
		double[] estimators = gd.getEstimators();
		//		Matrix4 affine = AffineTransform.rigidToMatrix(estimators[0], estimators[1], estimators[2], estimators[3], estimators[4], estimators[5]);
		//		this.setTransform(AffineTransform.rigidToMatrixValues(estimators[0], estimators[1], estimators[2], estimators[3], estimators[4], estimators[5]));
		//		return gd;
		SliceRegistration reg = new SliceRegistration();
		//		if(estimators.length > 6) {
		//			reg.xR = estimators[0];
		//			reg.yR = estimators[1];
		//			reg.zR = estimators[2];
		//			reg.xT = estimators[3];
		//			reg.yT = estimators[4];
		//			reg.zT = estimators[5];
		//			reg.xSkew = estimators[6];
		//			reg.ySkew = estimators[7];
		//			reg.xScale = estimators[8];
		//			reg.yScale = estimators[9];
		//
		//		} else 
		if(estimators.length >= 6) {
			reg.xR = estimators[0];
			reg.yR = estimators[1];
			reg.zR = estimators[2];
			reg.xT = estimators[3];
			reg.yT = estimators[4];
			reg.zT = estimators[5];
		} else if(estimators.length >= 3) {
			reg.xT = estimators[0];
			reg.yT = estimators[1];
			reg.zT = estimators[2];
			reg.xR = xR;
			reg.yR = yR;
			reg.zR = zR;
		} else if(estimators.length >= 2) {
			reg.xT = estimators[0];
			reg.yT = estimators[1];
			reg.zT = zT;
			reg.xR = xR;
			reg.yR = yR;
			reg.zR = zR;
		} else {
			reg.xT = xT;
			reg.yT = estimators[0];
			reg.zT = zT;
			reg.xR = xR;
			reg.yR = yR;
			reg.zR = zR;
		}

		//		if(estimators.length > 6) {
		//			reg.freqSc = estimators[6];
		//		} else {
		//			reg.freqSc = null;
		//		}
		//		if(reg.freqSc != null && Double.isNaN(reg.freqSc)) {
		//			reg.freqSc = null;
		//		}
		reg.cost = gd.getOptimizedCost();
		if(sourceSliceNumber != null) {
			reg.sourceSlice = sourceSliceNumber;			
		} else {
			reg.sourceSlice = -1;
		}
		reg.sourceVolume = sourceVolumeNumber;

		int dummy = 1;

		return reg;


	} // AffineTransform::registerSliceCubic

	public String toString() {
		return Util.newGson(true).toJson(this);
	}

	public SliceRegistration registerVolumeCubic(int sourceVolumeNumber) throws Exception {
		return registerSliceCubic(sourceVolumeNumber, null);
	}

	private void setGradientDescentRigidEstimators(GradientDescent gd) {

		//		double spanScale = this.initialSpanFraction;

		int i = 0;
		gd.setEstimator(i, this.initialRotateX);
		gd.setInitialAdjustmentSpan(i, this.spanRotateX);
		gd.setEstimatorLowerLimit(i, this.minRotateX);
		gd.setEstimatorUpperLimit(i, this.maxRotateX);
		//		gd.setInitialAdjustmentSpan(i, 0.01 * spanScale);
		gd.setInitialAdjustmentSpan(i, this.spanRotateX);

		i++;
		gd.setEstimator(i, this.initialRotateY);
		gd.setInitialAdjustmentSpan(i, this.spanRotateY);
		gd.setEstimatorLowerLimit(i, this.minRotateY);
		gd.setEstimatorUpperLimit(i, this.maxRotateY);
		//		gd.setInitialAdjustmentSpan(i, 0.01 * spanScale);
		gd.setInitialAdjustmentSpan(i, this.spanRotateY);

		i++;
		gd.setEstimator(i, this.initialRotateZ);
		gd.setInitialAdjustmentSpan(i, this.spanRotateZ);
		gd.setEstimatorLowerLimit(i, this.minRotateZ);
		gd.setEstimatorUpperLimit(i, this.maxRotateZ);
		//		gd.setInitialAdjustmentSpan(i, 0.01 * spanScale);
		gd.setInitialAdjustmentSpan(i, this.spanRotateZ);

		i++;
		gd.setEstimator(i, this.initialTranslateX);
		gd.setInitialAdjustmentSpan(i, this.spanTranslateX);
		gd.setEstimatorLowerLimit(i, this.minTranslateX);
		gd.setEstimatorUpperLimit(i, this.maxTranslateX);
		//		gd.setInitialAdjustmentSpan(i, 1 * spanScale);
		gd.setInitialAdjustmentSpan(i, this.spanTranslateX);

		i++;
		gd.setEstimator(i, this.initialTranslateY);
		gd.setInitialAdjustmentSpan(i, this.spanTranslateY);
		gd.setEstimatorLowerLimit(i, this.minTranslateY);
		gd.setEstimatorUpperLimit(i, this.maxTranslateY);
		//		gd.setInitialAdjustmentSpan(i, 1 * spanScale);
		gd.setInitialAdjustmentSpan(i, this.spanTranslateY);

		i++;
		gd.setEstimator(i, this.initialTranslateZ);
		gd.setInitialAdjustmentSpan(i, this.spanTranslateZ);
		gd.setEstimatorLowerLimit(i, this.minTranslateZ);
		gd.setEstimatorUpperLimit(i, this.maxTranslateZ);
		//		gd.setInitialAdjustmentSpan(i, 1 * spanScale);
		gd.setInitialAdjustmentSpan(i, this.spanTranslateZ);


		i++;
		gd.setEstimator(i, this.initialFreqEncScale);
		gd.setInitialAdjustmentSpan(i, this.spanFreqEncScale);
		gd.setEstimatorLowerLimit(i, this.minFreqEncScale);
		gd.setEstimatorUpperLimit(i, this.maxFreqEncScale);
		//		gd.setInitialAdjustmentSpan(i, 0.01 * spanScale);
		gd.setInitialAdjustmentSpan(i, this.spanFreqEncScale);
	} // AffineTransform::setGradientDescentRigidEstimators


	private GradientDescent registerInternal(Double sourceTime, Double referenceTime, String logPath) throws Exception {

		GradientDescent gd = this.generateGradientDescent();

		//		gd.setVerbosity(verbosity);
		gd.compute();
		double[] estimators = gd.getEstimators();
		Matrix4 affine = AffineTransform.rigidToMatrix(estimators[0], estimators[1], estimators[2], estimators[3], estimators[4], estimators[5]);
		this.setTransform(AffineTransform.rigidToMatrixValues(estimators[0], estimators[1], estimators[2], estimators[3], estimators[4], estimators[5]));
		int dummy = 1;
		return gd;
	} // function AffineTransform::registerInternal

	public static class MotionCorrection {
		public int sourceVolume;
		public int referenceVolume;
		public double xT;
		public double yT;
		public double zT;
		public double xR;
		public double yR;
		public double zR;
		public double cost;
		public MotionCorrection() {

		}
	}
	public static class RigidBodyTransform {
		public String sourcePath;
		public int sourceVolume;
		public String referencePath;
		public int referenceVolume;
		public double xT;
		public double yT;
		public double zT;
		public double xR;
		public double yR;
		public double zR;
		public double cost;
		public RigidBodyTransform() {

		}
	}
	public static class AffineTransformation {
		public String sourcePath;
		public int sourceVolume;
		public String referencePath;
		public int referenceVolume;
		public Matrix4 matrix;
		public AffineTransformation() {

		}
	}

	public void setInitialRotation(double xR, double yR, double zR) {
		this.initialRotateX = xR;
		this.initialRotateY = yR;
		this.initialRotateZ = zR;
	}
	public void setInitialTranslation(double xT, double yT, double zT) {
		this.initialTranslateX = xT;
		this.initialTranslateY = yT;
		this.initialTranslateZ = zT;
	}
	public double[] getInitialRotation() {
		return new double[] {initialRotateX, initialRotateY, initialRotateZ};
	}
	public double[] getInitialTranslation() {
		return new double[] {initialTranslateX, initialTranslateY, initialTranslateZ};
	}

	public void setInitialRotationSpan(double xSpan, double ySpan, double zSpan) {
		this.spanRotateX = xSpan;
		this.spanRotateY = ySpan;
		this.spanRotateZ = zSpan;
	}
	public void setInitialTranslationSpan(double xSpan, double ySpan, double zSpan) {
		this.spanTranslateX = xSpan;
		this.spanTranslateY = ySpan;
		this.spanTranslateZ = zSpan;
	}

	public void sliceMotionCorrect() throws Exception {

		int sliceCount = this.getSource().getDimZ();
		int volumeCount = this.getSource().getDimT();

		int sliceDigitCount = (int)Math.floor(Math.log10(sliceCount) + 1);
		int volumeDigitCount = (int)Math.floor(Math.log10(volumeCount) + 1);
		//		SliceRegistration[] result = new SliceRegistration[sliceCount * volumeCount];

		int counter = 0;
		for(int volumeNumber = 0; volumeNumber < volumeCount; volumeNumber++) {

			// todo: do volume registration and use it as a starting point

			ArrayList<SliceRegistration> regs = new ArrayList<SliceRegistration>(); 
			String logPath = "";
			boolean writeThis = false;
			boolean doThis = true;
			if(this.logFolder != null && this.logFolder.length() > 0) {
				String volumeText = String.format("%0" + Integer.toString(volumeDigitCount) + "d", volumeNumber);
				Util.ensureDirectoryExists(this.logFolder);
				//				logPath = logFolder + File.separator + "slice-" + sliceText + "_volume-" + volumeText + ".json";
				logPath = logFolder + File.separator + "volume-" + volumeText + ".json";
				writeThis = true;
				File file = new File(logPath);
				if(file.exists()) {
					doThis = false;
				}

			}
			if(doThis) {
				for(int sliceNumber = 0; sliceNumber < sliceCount; sliceNumber++) {
					//					String sliceText = String.format("%0" + Integer.toString(sliceDigitCount) + "d", sliceNumber);
					SliceRegistration reg = null;
					reg = this.registerSlice(volumeNumber, sliceNumber);
					regs.add(reg);
					System.out.println(reg.toString());
					counter++;
				}
				if(writeThis) {
					String output = Util.newGson().toJson(regs);
					Util.save(logPath, output);
				}
			} // doThis conditional (output file exists)
		} // volume loop
		//		return result;
	} // AffineTransfrom::sliceMotionCorrect

	public ArrayList<MotionCorrection> motionCorrect(int referenceVolume) throws Exception {	
		ArrayList<MotionCorrection> result = new ArrayList<MotionCorrection>();
		int sourceVolumeCount = this.source.getDimT();
		double sourceTr = this.source.getRepetitionTime();
		System.out.println(Util.dateString() 
				+ ": Calculating motion correction to volume " + Integer.toString(referenceVolume)
				+ " for " + this.referencePath);
		//		for(int i = 0; i < 2; i++) {
		for(int i = 0; i < sourceVolumeCount; i++) {
			if(i != referenceVolume) {
				String logPath = "";
				int digitCount = (int)Math.floor(Math.log10(sourceVolumeCount) + 1);
				String volText = String.format("%0" + Integer.toString(digitCount) + "d", i);
				String refText = String.format("%0" + Integer.toString(digitCount) + "d", referenceVolume);
				if(this.logFolder != null && this.logFolder.length() > 0) {
					Util.ensureDirectoryExists(this.logFolder);
					logPath = logFolder + File.separator + "motionCorrection_" + volText + "-" + refText + ".txt";
				}
				GradientDescent gd = this.register(i * sourceTr, referenceVolume * sourceTr, logPath);
				double[] est = gd.getEstimators();
				MotionCorrection motionCorrection = new MotionCorrection();
				motionCorrection.sourceVolume = i;
				motionCorrection.referenceVolume = referenceVolume;
				motionCorrection.xT = est[0];
				motionCorrection.yT = est[1];
				motionCorrection.zT = est[2];
				motionCorrection.xR = est[3];
				motionCorrection.yR = est[4];
				motionCorrection.zR = est[5];
				motionCorrection.cost = gd.getOptimizedCost();
				System.out.println(Util.dateString() 
						+ ": volume " + Integer.toString(i) 						
						+ " xT=" + motionCorrection.xT
						+ ", yT=" + motionCorrection.yT
						+ ", zT=" + motionCorrection.zT
						+ ", xR=" + motionCorrection.xR
						+ ", yR=" + motionCorrection.yR
						+ ", zR=" + motionCorrection.zR
						);
				result.add(motionCorrection);
			}
		}
		return result;
	}

	public static void testTransform() throws FileNotFoundException, IOException {
		String sourcePath = "/source/neuro/+lesson/+docker/+node/snowpack1/public/data/sub-MINER116/anatomical/T1.nii.gz";
		String refPath = "/source/neuro/+lesson/+docker/+node/snowpack1/public/data/sub-MINER116/functional/sub-MINER116_ses-01_task-OddEven_run-01_bold.nii.gz";
		AffineTransform transform = new AffineTransform(sourcePath, refPath);
		//	    double[][][] result = transform.getTransformed();		
		//	    double sum = getSum(result);

		int dummy = 1;
	}
	public static void testMotionCorrection() throws Exception {
		boolean useFile = true;
		AffineTransform transform = null;
		String mriFolder = "C:\\source\\neuro\\+lesson\\+docker\\+node\\snowpack1\\public\\data\\sub-710-01\\mri\\";
		//		String mriFolder = "Z:\\data\\analyzed\\sub-710-01\\mri\\";
		String mriFilename = "sub-710-01_ses-01_run-01_RSFC_901";
		if(useFile) {
			//			String refPath = "C:\\source\\neuro\\+lesson\\+docker\\+node\\snowpack1\\public\\data\\sub-710-07\\mri\\ses-01\\func\\sub-710-07_ses-01_run-01_RSFC_1001.nii.gz";
			String refPath = mriFolder + "ses-01\\func\\" + mriFilename + ".nii.gz";
			transform = new AffineTransform(refPath, refPath);
		}else {
//			transform = new AffineTransform(generateTestMotion());
		}

		//		ArrayList<MotionCorrection> corrections = transform.motionCorrect(0);
		//		String json = Util.newGson().toJson(corrections);
		//		String outputPath = "C:\\source\\neuro\\+lesson\\+docker\\+node\\snowpack1\\public\\data\\sub-710-07\\mri\\functional\\volumeCorrected\\sub-710-07_ses-01_run-01_RSFC_1001.nii.gz";
		//		String outputJson = "C:\\source\\neuro\\+lesson\\+docker\\+node\\snowpack1\\public\\data\\sub-710-07\\mri\\functional\\volumeCorrected\\sub-710-07_ses-01_run-01_RSFC_1001_mc.json";
		String outputPath = mriFolder + "functional\\volumeCorrected\\" + mriFilename + ".nii.gz";
		String outputJson = mriFolder + "functional\\volumeCorrected\\" + mriFilename + "_mc.json";
		//		Util.save(outputPath, json);
		//		transform.motionCorrect(outputPath, outputJson);
		Nifti transformed = transform.getTransformed();
		int dummy = 1;
	}
	public static void testSliceRegistration() throws Exception {
		//		String sourcePath = "Z:\\data\\analyzed\\sub-G1-1001_01\\mri\\ses-01\\func\\sub-GI-1001_01_ses-01_run-02_rsfMRI_701.nii.gz";
		//		String outputPath = "Z:\\data\\analyzed\\sub-G1-1001_01\\mri\\sliceVolume\\sub-GI-1001_01_ses-01_run-02_rsfMRI_701";

		String sourcePath = "Z:\\data\\analyzed\\sub-710-01\\mri\\ses-01\\func\\sub-710-01_ses-01_run-01_RSFC_901.nii.gz";
		String outputPath = "Z:\\data\\analyzed\\sub-710-01\\mri\\functional\\sliceVolumeLong\\sub-710-01_ses-01_run-01_RSFC_901";

		AffineTransform.motionCorrect(sourcePath, outputPath);
		//		String refPath = "Z:\\data\\analyzed\\sub-G1-1001_01\\mri\\functional\\sliceCorrected\\sub-GI-1001_01_ses-01_run-02_rsfMRI_701_mcf_avg.nii.gz";
		//		AffineTransform transform = new AffineTransform(sourcePath, refPath);
		//		int sliceNumber = 0;
		//		int volumeNumber = 0;
		//		double diff0 = transform.getSliceDifference(volumeNumber, sliceNumber, 0, 0, 0, 0, 0, 0);
		//		double diff1 = transform.getSliceDifference(volumeNumber, sliceNumber, 0, 0, 0, 0, 0, 1);
		//		SliceRegistration regV = transform.registerVolumeCubic(1);
		//		SliceRegistration regS = transform.registerSliceCubic(1, 1);

		//transform.motionCorrect();



		int dummy = 1;
	}
	public static void testRegistration() throws FileNotFoundException, IOException {
		String sourcePath = "Z:\\data\\analyzed\\sub-710-01\\mri\\functional\\sliceFreqPhase\\sub-710-01_ses-01_run-01_RSFC_901_mcf_avg_unwarp.nii.gz";
		String refPath = "Z:\\data\\analyzed\\sub-710-01\\mri\\anatomical\\T1_cerebroSpinalFluid_mask.nii.gz";
		AffineTransform transform = new AffineTransform(sourcePath, refPath);
		Nifti transformed = transform.getTransformed();
		int dummy = 1;
	}

	//	private static double getSum(double[][][] data){
	//		double sum = 0;
	//		for(int i = 0; i < data.length; i++) {
	//			double jSum = 0;
	//			for(int j = 0; j < data[i].length; j++) {
	//				double kSum = 0;
	//				for(int k = 0; k < data[i][j].length; k++) {
	//					kSum += data[i][j][k];
	//				}
	//				jSum += kSum;
	//			}			 
	//			sum += jSum;
	//		}
	//		return sum;
	//	}

	public int getGradientProbeCount() {
		return this.gradientProbeCount;
	}
	public void setGradientProbeCount(int value) {
		this.gradientProbeCount = value;
	}
	public double getGradientMaxScaleFactor() {
		return this.gradientMaxScaleFactor;
	}

	public void setGradientMaxScaleFactor(double value) {
		//		if(value <= 1) { throw new Exception("max scale factor should be greater than 1"); }
		this.gradientMaxScaleFactor = value;
	}
	public double getGradientMinScaleFactor() {
		return this.gradientMinScaleFactor;
	}
	public void setGradientMinScaleFactor(double value) {
		//		if(value >= 1) { throw new Exception("min scale factor should be less than 1"); }
		this.gradientMinScaleFactor = value;
	}
	public void setVerbosity(int value) {
		this.verbosity = value;
	}

	public static class SliceRegistration {
		int sourceVolume;
		int sourceSlice;
		double xR;
		double yR;
		double zR;
		double xT;
		double yT;
		double zT;
		//		double xSkew;
		//		double ySkew;
		//		double zSkew;
		//		double xScale;
		//		double yScale;
		//		double zScale;
		//		Double freqSc;

		double cost;
		public String toString() {
			return Util.newGson().toJson(this);
		}
		public SliceRegistration clone() {
			SliceRegistration result = new SliceRegistration();
			result.xR = xR;
			result.yR = yR;
			result.zR = zR;
			result.xT = xT;
			result.yT = yT;
			result.zT = zT;
			//			result.freqSc = null;
			//			if(freqSc != null) {
			//				result.freqSc = freqSc.doubleValue();				
			//			}
			result.cost = cost;
			result.sourceSlice = sourceSlice;
			result.sourceVolume = sourceVolume;
			return result;
		}
		public static SliceRegistration[] loadRegistrations(String folderPath, boolean loadVolumeRegistrations) throws IOException {
			ArrayList<SliceRegistration> regs = new ArrayList<SliceRegistration>();
			File regFolder = new File(folderPath);
			if(regFolder.exists()) {
				File[] regFiles = regFolder.listFiles();
				for(int i = 0; i < regFiles.length; i++) {
					String regPath = regFiles[i].getAbsolutePath();
					if(regPath.toLowerCase().endsWith(".json") && regPath.toLowerCase().contains("_vol-000-")) {
						String json = Util.load(regPath);
						SliceRegistration[] reg = Util.newGson().fromJson(json, SliceRegistration[].class);
						for(int j = 0; j < reg.length; j++) {
							if(loadVolumeRegistrations) {
								if(reg[j].sourceSlice < 0) {
									regs.add(reg[j]);
								}
							} else {
								if(reg[j].sourceSlice > -1) {
									regs.add(reg[j]);
								}
							}
						}
					}
				}
			}
			SliceRegistration[] result = new SliceRegistration[regs.size()];
			for(int i = 0; i < regs.size(); i++) {
				result[i] = regs.get(i);
			}
			return result;
		}
		public Matrix4 getMatrix() {
			Matrix4 result = null;
			try {
				result = AffineTransform.rigidToMatrix(xT, yT, zT, xR, yR, zR);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return result;
		}
	}
	public static class VolumeRegistration {
		int sourceVolume;
		double xR;
		double yR;
		double zR;
		double xT;
		double yT;
		double zT;
		double cost;
		public String toString() {
			return Util.newGson().toJson(this);
		}
	}


//	public static Nifti generateTestMotion() throws Exception {
//		int volumeCount = 3;
//		int dim1 = 50;
//		int dim2 = 50;
//		int dim3 = 50;
//
//		double[] xT = new double[] {-0.1, 0.5};
//		double[] yT = new double[] {0, 0};
//		double[] zT = new double[] {0, 0};
//		double[] xR = new double[] {0, 0.001};
//		double[] yR = new double[] {0, 0.01};
//		double[] zR = new double[] {0, 0.001};
//
//		double[][][] volume = VolumeAdder.generateTestData(dim1, dim2, dim3);
//		Nifti nii = new Nifti(dim1, dim2, dim3, volumeCount);
//		AffineTransform affine = new AffineTransform(nii);
//		double[][][][] data = nii.getData();
//		double[][][][] niiData = nii.getData();
//		double[][][] toFill = null;
//		double tR = nii.getRepetitionTime();
//
//		for(int v = 0; v < volumeCount; v++) {
//			if(v == 0) {
//				toFill = volume;
//			}else {
//				Matrix4 affineMatrix = AffineTransform.rigidToMatrix(xT[v-1], yT[v-1], zT[v-1], xR[v-1], yR[v-1], zR[v-1]);
//				toFill = affine.getTransformedPureFunction(tR, affineMatrix);
//				//				affine.setRigidBody(xT[v-1], yT[v-1], zT[v-1], xR[v-1], yR[v-1], zR[v-1]);
//				//				toFill = affine.getTransformed();
//			}
//			for(int i = 0; i < dim1; i++) {
//				for(int j = 0; j < dim1; j++) {
//					for(int k = 0; k < dim1; k++) {
//						data[i][j][k][v] = toFill[i][j][k];
//					}					
//				}				
//			}			
//		}
//
//		double toFillSum = VolumeAdder.squaredSum(toFill);
//		double volumeSum = VolumeAdder.squaredSum(volume);
//		double preSourceSum = VolumeAdder.squaredSum(nii.getData());
//
//		int dummy = 1;
//		return nii;
//	}

	public static void testSphere() {

	}

//	public static void testRegister() throws Exception {
//		//	    String sourcePath = "/source/neuro/+lesson/+docker/+node/snowpack1/public/data/sub-MINER116/anatomical/T1.nii.gz";
//		//	    String refPath = "/source/neuro/+lesson/+docker/+node/snowpack1/public/data/sub-MINER116/functional/sub-MINER116_ses-01_task-OddEven_run-01_bold.nii.gz";
//		AffineTransform transform = new AffineTransform(generateTestMotion());
//		Double sourceTime = 0.0;
//		Double referenceTime = 0.0;	    
//		transform.register(sourceTime, referenceTime, "");
//		int dummy = 0;
//	}

	private static void printDoubles(double[][] points) {
		for(int i = 0; i < points.length; i++) {
			for(int j = 0; j < points[i].length; j++) {
				System.out.print(Double.toString(Math.round(points[i][j])));
				System.out.print("  ");
			}
			System.out.print("\n");
		}
	}
	private static void printMatrix(Matrix toPrint) {
		printDoubles(toPrint.getArray());
	}
	private static void printMatrix4(Matrix4 toPrint) {
		printDoubles(toPrint.getMatrix().getArray());
	}

	public static void testRotateAroundCenter() throws Exception {

		ArrayList<Matrix> testResults = new ArrayList<Matrix>();
		ArrayList<String> testDescs = new ArrayList<String>();
		testDescs.add("no transform");
		testResults.add(testRotationChunk( // +x axis, +y center
				0, 0, 0, 
				0, 0, 0));
		testDescs.add("+x axis, +y center");
		testResults.add(testRotationChunk( // +x axis, +y center
				Math.PI / 2, 0, 0, 
				0, 100, 0));
		testDescs.add("+x axis, +z center");
		testResults.add(testRotationChunk( // +x axis, +z center
				Math.PI / 2, 0, 0, 
				0, 100, 0));
		testDescs.add("+y axis, +x center");
		testResults.add(testRotationChunk( // +y axis, +x center
				0, Math.PI / 2, 0, 
				100, 0, 0));
		testDescs.add("+y axis, +z center");
		testResults.add(testRotationChunk( // +y axis, +z center
				0, Math.PI / 2, 0, 
				100, 0, 0));
		testDescs.add("+z axis, +x center");
		testResults.add(testRotationChunk( // +z axis, +x center
				0, 0, Math.PI / 2, 
				100, 0, 0));
		testDescs.add("+z axis, +y center");
		testResults.add(testRotationChunk( // +z axis, +y center
				0, 0, Math.PI / 2, 
				0, 100, 0));

		for(int i = 0; i < testResults.size(); i++) {
			System.out.println(testDescs.get(i));
			printMatrix(testResults.get(i));
			System.out.println();
		}

		double xT = 0;
		double yT = 0;
		double zT = 0;
		double xR = Math.PI / 2;
		double yR = 0;
		double zR = 0;
		double xC = 0;
		double yC = 100;
		double zC = 0;
		Matrix4 offsetMatrix = new Matrix4(AffineTransform.rigidToMatrixValues(xT, yT, zT, xR, yR, zR, xC, yC, zC));
		Matrix4 originMatrix = new Matrix4(AffineTransform.rigidToMatrixValues(xT, yT, zT, xR, yR, zR, 0, 0, 0));
		Matrix4 basicMatrix = new Matrix4(AffineTransform.rigidToMatrixValues(xT, yT, zT, xR, yR, zR));
		double[][] points = new double[][] {
			new double[] {0, 0, 0},
			new double[] {0, 0, 1},
			new double[] {0, 1, 0},
			new double[] {0, 2, 0}
		};
		double[][] centered = originMatrix.transformVertices(points);
		double[][] offset = offsetMatrix.transformVertices(points);

		System.out.println("center matrix");
		printMatrix4(originMatrix);
		System.out.println("\noffset matrix [" + Double.toString(xC) + ", " + Double.toString(yC) + ", " + Double.toString(zC) + "]");
		printMatrix4(offsetMatrix);

		System.out.print("\noriginal\n");
		printDoubles(points);

		System.out.print("\ncentered\n");
		printDoubles(centered);

		System.out.print("\noffset\n");
		printDoubles(offset);

		int dummy = 1;

	}
	private static Matrix testRotationChunk(double xR, double yR, double zR, 
			double xC, double yC, double zC) throws Exception {
		return testRotationChunk(0, 0, 0, xR, yR, zR, xC, yC, zC);
	}

	private static Matrix testRotationChunk(double xT, double yT, double zT, 
			double xR, double yR, double zR, 
			double xC, double yC, double zC) throws Exception {
		Matrix4 offsetMatrix = new Matrix4(AffineTransform.rigidToMatrixValues(xT, yT, zT, xR, yR, zR, xC, yC, zC));
		double[][] vertices = new double[][] {
			new double[] {0, 0, 0},
			new double[] {0, 0, 1},
			new double[] {0, 1, 0},
			new double[] {0, 1, 1},
			new double[] {1, 0, 0},
			new double[] {1, 0, 1},
			new double[] {1, 1, 0},
			new double[] {1, 1, 1},
		};

		double[][] offset = offsetMatrix.transformVertices(vertices);
		Matrix mat = new Matrix(offset);
		return mat;
	}
	public static void testLoadRegs() throws IOException {
		AffineTransform transform = new AffineTransform(); 
		SliceRegistration[] regs = SliceRegistration.loadRegistrations("Z:\\data\\analyzed\\sub-710-01\\mri\\functional\\sliceVolumeLong\\sub-710-01_ses-01_run-01_RSFC_901", false);
	}

	public static void testNewRotation() throws Exception {
		Matrix4 test1 = AffineTransform.rigidToMatrix(1, 2, 3, Math.PI / 2, 0, 0);
		Matrix4 test2 = AffineTransform.rigidToMatrix(1, 2, 3, 0, Math.PI / 2, 0);
		Matrix4 test3 = AffineTransform.rigidToMatrix(1, 2, 3, Math.PI / 2, Math.PI / 2,  0);
		Matrix4 test1Old = new Matrix4(AffineTransform.rigidToMatrixValuesExperimental(1, 2, 3, Math.PI / 2, 0, 0));
		Matrix4 test2Old = new Matrix4(AffineTransform.rigidToMatrixValuesExperimental(1, 2, 3, 0, Math.PI / 2, 0));
		Matrix4 test3Old = new Matrix4(AffineTransform.rigidToMatrixValuesExperimental(1, 2, 3, Math.PI / 2, Math.PI / 2,  0));
		int dummy = 1;
	}

	public static void testMatrixToRigid() throws Exception {
		double[] matrix = rigidToMatrixValues(0.1, 0.2, 0.3, 4, 5, 6);
		//		double[] matrix = rigidToMatrixValues(4, 5, 6, 0.1, 0.2, 0.3);
		double[] rigid = matrixToRigidValues(matrix);

		int dummy = 1;

	}

	public static void main(String[] args) throws Exception {
		testMatrixToRigid();
		testNewRotation();
		testSliceRegistration();
		testLoadRegs();
		testRotateAroundCenter();
		testRegistration();
		testMotionCorrection();
		//		testRegister();
		//		testTransform();

	}



}

