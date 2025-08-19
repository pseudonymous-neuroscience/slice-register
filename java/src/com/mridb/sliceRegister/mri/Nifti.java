package com.mridb.sliceRegister.mri;


import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import Jama.Matrix;
import com.mridb.sliceRegister.Util;
//import com.mridb.sliceRegister.Imagesc;

public class Nifti{

	// all data types in specifications
	// only the cool ones are supported
	// (uncool data types marked with *)
	public enum DataType{
		NIFTI_TYPE_UINT8((short)2), 
		NIFTI_TYPE_INT16((short)4), 
		NIFTI_TYPE_INT32((short)8), 
		NIFTI_TYPE_FLOAT32((short)16), 
		NIFTI_TYPE_COMPLEX64((short)32), // * 
		NIFTI_TYPE_FLOAT64((short)64), 
		NIFTI_TYPE_RGB24((short)128),  // * 
		NIFTI_TYPE_INT8((short)256), 
		NIFTI_TYPE_UINT16((short)512), 
		NIFTI_TYPE_UINT32((short)768), 
		NIFTI_TYPE_INT64((short)1024), 
		NIFTI_TYPE_UINT64((short)1280), 
		NIFTI_TYPE_FLOAT128((short)1536),  // * 
		NIFTI_TYPE_COMPLEX128((short)1792),  // * 
		NIFTI_TYPE_COMPLEX256((short)2048),  // * 
		NIFTI_TYPE_RGBA32((short)2304); // * 
		public final short code;
		private DataType(short code) {
			this.code = code;
		}
	}

	// The Header Fields

	// hk: {
	private int            sizeof_hdr;
	private String         data_type;
	private String         db_name;
	private int            extents;
	private short          session_error;
	private String         regular;
	private byte           dim_info;
	//  }

	//    dime: {
	private short[]       dim;
	private float         intent_p1;
	private float         intent_p2;
	private float         intent_p3;
	private short         intent_code;
	private short         datatype;
	private short         bitpix;
	private short         slice_start;
	private float[]       pixdim;
	private float         vox_offset;
	private float         scl_slope;
	private float         scl_inter;
	private short         slice_end;
	private byte          slice_code;
	private byte          xyzt_units;
	private float         cal_max;
	private float         cal_min;
	private float         slice_duration;
	private float         toffset;
	private int           glmax;
	private int           glmin;
	//}
	//hist: {
	private String        descrip;
	private String        aux_file;
	private short         qform_code;
	private short         sform_code;
	private float         quatern_b;
	private float         quatern_c;
	private float         quatern_d;
	private float         qoffset_x;
	private float         qoffset_y;
	private float         qoffset_z;
	private float[]       srow_x;
	private float[]       srow_y;
	private float[]       srow_z;
	private String        intent_name;
	private String        magic;
	private byte[]        extension;
	//}

	// The Data
	// It is deliberately a 4-D array for compatibility with matlab
	// 1-D arrays are better for GPU processing and automagically handling
	// dimensions 2, 3, and 5 through 7. See the javascript library for an example.
	// Sticking with 4 here is deliberate as the focus is on fMRI data, and nested 
	// loops are easier to deal with than meta-loops. The code still works with
	// 2- and 3-D files, you just get a bunch of arrays of size 1. This is transient
	// so that serializing via gson omits the data.
	protected transient double[][][][] data;
	
	
	protected transient long dataByteCount;
	protected transient long bytesRemaining;

	// The Transients

	// default is little endian; inferred from the first int when loading files
	// this shouldn't be static but the slowdown from making it tied to the object
	// is unacceptable
	protected static ByteOrder _byteOrder = ByteOrder.LITTLE_ENDIAN;

	// size of buffers used in file i/o
	protected transient int bufferSize = 1024 * 256;

	// flags to prevent mid-stream changes
	// (set these in public load/save functions)
	protected transient boolean isReading = false;
	private transient boolean isWriting = false;
	private transient Matrix4 coord2SubMatrix = null;
	private transient Matrix4 sub2CoordMatrix = null;
	private transient double globalMinimum = Double.MAX_VALUE;
	//	private transient double globalMaximum = Double.MIN_VALUE;
	private transient double globalMaximum = -Double.MAX_VALUE;


	// The Public Methods

	public Nifti() {
		this.srow_x = new float[4];
		this.srow_y = new float[4];
		this.srow_z = new float[4];
		this.pixdim = new float[8];
		this.dim = new short[8];
		this.extension = new byte[4];
		//		this._byteOrder = ByteOrder.LITTLE_ENDIAN;
		this.srow_x[0] = 1;
		this.srow_x[1] = 0;
		this.srow_x[2] = 0;
		this.srow_x[3] = 0;
		this.srow_y[0] = 0;
		this.srow_y[1] = 1;
		this.srow_y[2] = 0;
		this.srow_y[3] = 0;
		this.srow_z[0] = 0;
		this.srow_z[1] = 0;
		this.srow_z[2] = 1;
		this.srow_z[3] = 0;
		this.pixdim[0] = 4;
		this.pixdim[1] = 1;
		this.pixdim[2] = 1;
		this.pixdim[3] = 1;
		this.pixdim[4] = 1;
		this.pixdim[5] = 0;
		this.pixdim[6] = 0;
		this.pixdim[7] = 0;
		this.dim[0] = -1;
		this.dim[1] = 1;
		this.dim[2] = 1;
		this.dim[3] = 1;
		this.dim[4] = 1;
		this.dim[5] = 1;
		this.dim[6] = 1;
		this.dim[7] = 1;
	}
	public Nifti(double xSize, double ySize, double zSize, double tSize) {
		this();
		this.dim[1] = (short)xSize;
		this.dim[2] = (short)ySize;
		this.dim[3] = (short)zSize;
		this.dim[4] = (short)tSize;
		this.data = new double[this.dim[1]][][][];
		for(int i = 0; i < this.dim[1]; i++) {
			this.data[i] = new double[dim[2]][][];
			for(int j = 0; j < this.dim[2]; j++) {
				this.data[i][j] = new double[dim[3]][];
				for(int k = 0; k < this.dim[3]; k++) {
					this.data[i][j][k] = new double[dim[4]];
					for(int t = 0; t < this.dim[4]; t++) {
					}
				}
			}
		}
	}
	public void cropVolumes(int startIndex, int endIndex) {
		if(startIndex < 0) {
			startIndex = 0;
		}
		if(endIndex >= this.data[0][0][0].length) {
			endIndex = this.data[0][0][0].length - 1;
		}
		if(endIndex < startIndex) {
			endIndex = startIndex;
		}
		int volCount = endIndex + startIndex + 1;
		double[][][][] newData = new double[this.data.length][][][];
		for(int x = 0; x < this.data.length; x++) {
			newData[x] = new double[this.data[0].length][][];
			for(int y = 0; y < this.data[0].length; y++) {
				newData[x][y] = new double[this.data[0][0].length][];
				for(int z = 0; z < this.data[0][0].length; z++) {
					newData[x][y][z] = new double[volCount];
					for(int t = 0; t < volCount; t++) {
						newData[x][y][z][t] = this.data[x][y][z][t + startIndex];
					}
				}
			}
		}
		this.data = newData;
		this.dim[4] = (short)volCount;

	}
	public void setSRowMatrix(double[] sRowX, double[] sRowY, double[] sRowZ) {
		for(int i = 0; i < 4; i++) {
			this.srow_x[i] = (float)sRowX[i];
			this.srow_y[i] = (float)sRowY[i];
			this.srow_z[i] = (float)sRowZ[i];
		}
		this.pixdim[1] = (float)Math.sqrt(sRowX[0]*sRowX[0] + sRowX[1]*sRowX[1] + sRowX[2]*sRowX[2]);
		this.pixdim[2] = (float)Math.sqrt(sRowY[0]*sRowY[0] + sRowY[1]*sRowY[1] + sRowY[2]*sRowY[2]);
		this.pixdim[3] = (float)Math.sqrt(sRowZ[0]*sRowZ[0] + sRowZ[1]*sRowZ[1] + sRowZ[2]*sRowZ[2]);
	}
	private static float[] clone(float[] input) {
		float[] result = new float[input.length];
		for(int i = 0; i < input.length; i++) {
			result[i] = input[i];
		}
		return result;
	}
	private static short[] clone(short[] input) {
		short[] result = new short[input.length];
		for(int i = 0; i < input.length; i++) {
			result[i] = input[i];
		}
		return result;
	}
	private static byte[] clone(byte[] input) {
		byte[] result = new byte[input.length];
		for(int i = 0; i < input.length; i++) {
			result[i] = input[i];
		}
		return result;
	}
	public double getGlobalMinimum() {
		return this.globalMinimum;
	}
	public double getGlobalMaximum() {
		return this.globalMaximum;
	}
	public double getFractionOfFileLoaded() {
		if(this.dataByteCount == 0) {
			return 0;
		} else {
			long bytesRead =  this.dataByteCount - this.bytesRemaining;
			return (double)bytesRead / (double)this.dataByteCount;
		}
	}

	public Nifti clone() {
		Nifti result = new Nifti(this.data.length, this.data[0].length, this.data[0][0].length, this.data[0][0][0].length);

		//    hk: {
		result.sizeof_hdr = this.sizeof_hdr;
		result.data_type = this.data_type;
		result.db_name = this.db_name;
		result.extents = this.extents;
		result.extents = this.extents;
		result.session_error = this.session_error;
		result.regular = this.regular;
		result.dim_info = this.dim_info;

		//    dime: {
		result.dim = clone(this.dim);
		result.intent_p1 = this.intent_p1;
		result.intent_p2 = this.intent_p2;
		result.intent_p3 = this.intent_p3;
		result.intent_code = this.intent_code;
		result.datatype = this.datatype;
		result.bitpix = this.bitpix;
		result.slice_start = this.slice_start;
		result.pixdim = clone(this.pixdim);
		result.vox_offset = this.vox_offset;
		result.scl_slope = this.scl_slope;
		result.scl_inter = this.scl_inter;
		result.slice_end = this.slice_end;
		result.slice_code = this.slice_code;
		result.xyzt_units = this.xyzt_units;
		result.cal_max = this.cal_max;
		result.cal_min = this.cal_min;
		result.slice_duration = this.slice_duration;
		result.toffset = this.toffset;
		result.glmax = this.glmax;
		result.glmin = this.glmin;

		//}
		//hist: {
		result.descrip = this.descrip;
		result.aux_file = this.aux_file;
		result.qform_code = this.qform_code;
		result.sform_code = this.sform_code;
		result.quatern_b = this.quatern_b;
		result.quatern_c = this.quatern_c;
		result.quatern_d = this.quatern_d;
		result.qoffset_x = this.qoffset_x;
		result.qoffset_y = this.qoffset_y;
		result.qoffset_z = this.qoffset_z;
		result.srow_x = clone(this.srow_x);
		result.srow_y = clone(this.srow_y);
		result.srow_z = clone(this.srow_z);
		result.intent_name = this.intent_name;
		result.magic = this.magic;
		result.extension = clone(this.extension);

		for(int i = 0; i < this.data.length; i++) {
			for(int j = 0; j < this.data[0].length; j++) {
				for(int k = 0; k < this.data[0][0].length; k++) {
					for(int t = 0; t < this.data[0][0][0].length; t++) {
						result.data[i][j][k][t] = this.data[i][j][k][t];
					}					
				}
			}
		}

		return result;
	}

	public static Nifti loadFromFile(String filePath) throws FileNotFoundException, IOException {
		Nifti result = new Nifti();
		result.isReading = true;
		result.load(filePath);
		result.isReading = false;
		return result;
	}
	
	public Nifti halfScale() {
		Nifti result = this.clone();
		int xDim = this.data.length / 2;
		int yDim = this.data[0].length / 2;
		int zDim = this.data[0][0].length / 2;
		int tDim = this.data[0][0][0].length;
		result.srow_x[0] = this.srow_x[0] * 2;
		result.srow_x[1] = this.srow_x[1] * 2;
		result.srow_x[2] = this.srow_x[2] * 2;
		result.srow_y[0] = this.srow_y[0] * 2;
		result.srow_y[1] = this.srow_y[1] * 2;
		result.srow_y[2] = this.srow_y[2] * 2;
		result.srow_z[0] = this.srow_z[0] * 2;
		result.srow_z[1] = this.srow_z[1] * 2;
		result.srow_z[2] = this.srow_z[2] * 2;
		result.srow_x[3] = this.srow_x[3] + this.srow_x[0] + this.srow_x[1] + this.srow_x[2]; 
		result.srow_y[3] = this.srow_y[3] + this.srow_y[0] + this.srow_y[1] + this.srow_y[2]; 
		result.srow_z[3] = this.srow_z[3] + this.srow_z[0] + this.srow_z[1] + this.srow_z[2];
		result.data = new double[xDim][yDim][zDim][tDim];
		for(int i = 0; i < xDim; i++) {
			for(int j = 0; j < yDim; j++) {
				for(int k = 0; k < zDim; k++) {
					for(int v = 0; v < tDim; v++) {
						double sum = 0;
						sum += this.data[i*2+0][j*2+0][k*2+0][v];
						sum += this.data[i*2+0][j*2+0][k*2+1][v];
						sum += this.data[i*2+0][j*2+1][k*2+0][v];
						sum += this.data[i*2+0][j*2+1][k*2+1][v];
						sum += this.data[i*2+1][j*2+0][k*2+0][v];
						sum += this.data[i*2+1][j*2+0][k*2+1][v];
						sum += this.data[i*2+1][j*2+1][k*2+0][v];
						sum += this.data[i*2+1][j*2+1][k*2+1][v];
						result.data[i][j][k][v] = sum / 8.0;
					}
				}
			}
		}
		// show a slice of the resulting data for debugging 
		//		if(false) {
		//			try {
		//				double[][][] vol = Imagesc.getSlice(result.data, 3, 0);
		//				double[][] slice = Imagesc.getSlice(vol, 2, vol[0][0].length / 2);
		//				Imagesc.imagesc(slice);
		//				int dummy = 1;
		//			} catch (Exception e) {
		//				// TODO Auto-generated catch block
		//				e.printStackTrace();
		//			}
		//		}
		result.dim[1] = (short)xDim;
		result.dim[2] = (short)yDim;
		result.dim[3] = (short)zDim;
		result.pixdim[1] = this.pixdim[1] * 2;
		result.pixdim[2] = this.pixdim[2] * 2;
		result.pixdim[3] = this.pixdim[3] * 2;
		
		return result;
	}
	
	public void resetData(int sizeX, int sizeY, int sizeZ, int sizeT) {
		this.data = new double[sizeX][][][];
		for(int i = 0; i < sizeX; i++) {
			this.data[i] = new double[sizeY][][];
			for(int j = 0; j < sizeY; j++) {
				this.data[i][j] = new double[sizeZ][];				
				for(int k = 0; k < sizeZ; k++) {
					this.data[i][j][k] = new double[sizeT];									
				}
			}
		}
		if(sizeT > 1) {
			this.dim[0] = 4;
			this.dim[4] = (short)sizeT;
		} else {
			this.dim[0] = 3;
			this.dim[4] = 1;
		}
		this.dim[1] = (short)sizeX;
		this.dim[2] = (short)sizeY;
		this.dim[3] = (short)sizeZ;
	}
	public void setData(int x, int y, int z, int t, double value) {
		this.data[x][y][z][t] = value;		
	}
	public void setAllData(double[][][][] values) {
		this.data = values;
	}


	public static Nifti loadHeader(String filePath) throws FileNotFoundException, IOException {
		Nifti result = new Nifti();
		result.isReading = true;
		result.loadHeader(filePath, true);
		result.isReading = false;
		return result;
	}

	public void save(String filePath) throws FileNotFoundException, IOException {
//		% (72) 16 -> 64
//		% (140) 255 -> 161
//		% (141) 15 -> 183
//		% (144) 0 -> 127
//		% (145) 0 -> 8482
//		% (146) 0 -> 255
//		% (147) 0 -> 255
		this.isWriting = true;
		OutputStream outputStream = this.getOutputFileStreamBasedOnExtension(filePath);
		saveHeader(outputStream);
		saveData(outputStream);
		outputStream.flush();
		outputStream.close();
		this.isWriting = false;
	}

	public Matrix4 getSubToCoordMatrix() {
		if(sub2CoordMatrix == null) {
			Matrix4 mat = new Matrix4();
			//			mat.set(0, 0, this.srow_x[0]);
			//			mat.set(1, 0, this.srow_x[1]);
			//			mat.set(2, 0, this.srow_x[2]);
			//			mat.set(3, 0, this.srow_x[3]);
			//			mat.set(0, 1, this.srow_y[0]);
			//			mat.set(1, 1, this.srow_y[1]);
			//			mat.set(2, 1, this.srow_y[2]);
			//			mat.set(3, 1, this.srow_y[3]);
			//			mat.set(0, 2, this.srow_z[0]);
			//			mat.set(1, 2, this.srow_z[1]);
			//			mat.set(2, 2, this.srow_z[2]);
			//			mat.set(3, 2, this.srow_z[3]);
			mat.set(0, 0, this.srow_x[0]);
			mat.set(0, 1, this.srow_x[1]);
			mat.set(0, 2, this.srow_x[2]);
			mat.set(0, 3, this.srow_x[3]);
			mat.set(1, 0, this.srow_y[0]);
			mat.set(1, 1, this.srow_y[1]);
			mat.set(1, 2, this.srow_y[2]);
			mat.set(1, 3, this.srow_y[3]);
			mat.set(2, 0, this.srow_z[0]);
			mat.set(2, 1, this.srow_z[1]);
			mat.set(2, 2, this.srow_z[2]);
			mat.set(2, 3, this.srow_z[3]);
			sub2CoordMatrix = mat;
		}
		return sub2CoordMatrix;
	}
	public Matrix4 getCoordToSubMatrix() {
		if(coord2SubMatrix == null) {
			Matrix4 mat = this.getSubToCoordMatrix();
			coord2SubMatrix = mat.getInverse();			
		}
		return coord2SubMatrix;
	}



	public double getNearestValueOfCoordinates(double x, double y, double z, double tSeconds) {
		Matrix4 coord2Sub = this.getCoordToSubMatrix();
		double[] indices = coord2Sub.transformVertex(new double[] {x, y, z});
		double volumeNumber = tSeconds / this.getRepetitionTime();
		if(volumeNumber < 0) { volumeNumber = 0; }
		if(volumeNumber >= this.dim[4]) { volumeNumber = this.dim[4] - 1; }
		return getValueOfNearestSubindex(indices[0], indices[1], indices[2], volumeNumber);
	}
	public double getLinearInterpolationOfCoordinates(double x, double y, double z, double tSeconds) {
		Matrix4 coord2Sub = this.getCoordToSubMatrix();
		double[] indices = coord2Sub.transformVertex(new double[] {x, y, z});
		double volumeNumber = tSeconds / this.getRepetitionTime();
		if(volumeNumber < 0) { volumeNumber = 0; }
		if(volumeNumber >= this.dim[4]) { volumeNumber = this.dim[4] - 1; }
		return getLinearInterpolationOfSubindexes(indices[0], indices[1], indices[2], volumeNumber);
	}
	public double getCubicInterpolationOfCoordinates(double x, double y, double z, double tSeconds) {
		Matrix4 coord2Sub = this.getCoordToSubMatrix();
		double[] indices = coord2Sub.transformVertex(new double[] {x, y, z});
		double volumeNumber = tSeconds / this.getRepetitionTime();
		if(volumeNumber < 0) { volumeNumber = 0; }
		if(volumeNumber >= this.dim[4]) { volumeNumber = this.dim[4] - 1; }
		return getCubicInterpolationOfSubindexes(indices[0], indices[1], indices[2], volumeNumber);
	}


	public double[] getParallelNearestValueOfCoordinates(double[][] xyzt) {
		double[] result = new double[xyzt.length];
		IndexGenerator iGen = new IndexGenerator(xyzt.length);

		iGen.getStream().forEach(i -> {
			int ii = (int) i;
			double x = xyzt[ii][0];
			double y = xyzt[ii][1];
			double z = xyzt[ii][2];
			double t = xyzt[ii][3];
			result[ii] = getNearestValueOfCoordinates(x, y, z, t);
		});	
		return result;
	}
	public double[] getParallelLinearInterpolationOfCoordinates(double[][] xyzt) {
		double[] result = new double[xyzt.length];
		IndexGenerator iGen = new IndexGenerator(xyzt.length);

		iGen.getStream().forEach(i -> {
			int ii = (int) i;
			double x = xyzt[ii][0];
			double y = xyzt[ii][1];
			double z = xyzt[ii][2];
			double t = xyzt[ii][3];
			result[ii] = getLinearInterpolationOfCoordinates(x, y, z, t);
		});	
		return result;
	}
	public double[] getParallelCubicInterpolationOfCoordinates(double[][] xyzt) {
		double[] result = new double[xyzt.length];
		IndexGenerator iGen = new IndexGenerator(xyzt.length);

		iGen.getStream().forEach(i -> {
			int ii = (int) i;
			double x = xyzt[ii][0];
			double y = xyzt[ii][1];
			double z = xyzt[ii][2];
			double t = xyzt[ii][3];
			result[ii] = getCubicInterpolationOfCoordinates(x, y, z, t);
		});	
		return result;
	}
	
	public double[] getParallelNearestValueOfSubindexes(double[][] xyzt) {
		double[] result = new double[xyzt.length];
		IndexGenerator iGen = new IndexGenerator(xyzt.length);

		iGen.getStream().forEach(i -> {
			int ii = (int) i;
			double x = xyzt[ii][0];
			double y = xyzt[ii][1];
			double z = xyzt[ii][2];
			double t = xyzt[ii][3];
			result[ii] = getValueOfNearestSubindex(x, y, z, t);
		});	
		return result;
	}
	public double[] getParallelLinearInterpolationOfSubindexes(double[][] xyzt) {
		double[] result = new double[xyzt.length];
		IndexGenerator iGen = new IndexGenerator(xyzt.length);

		iGen.getStream().forEach(i -> {
			int ii = (int) i;
			double x = xyzt[ii][0];
			double y = xyzt[ii][1];
			double z = xyzt[ii][2];
			double t = xyzt[ii][3];
			result[ii] = getLinearInterpolationOfSubindexes(x, y, z, t);
		});	
		return result;
	}
	public double[] getParallelCubicInterpolationOfSubindexes(double[][] xyzt) {
		double[] result = new double[xyzt.length];
		IndexGenerator iGen = new IndexGenerator(xyzt.length);

		iGen.getStream().forEach(i -> {
			int ii = (int) i;
			double x = xyzt[ii][0];
			double y = xyzt[ii][1];
			double z = xyzt[ii][2];
			double t = xyzt[ii][3];
			result[ii] = getCubicInterpolationOfSubindexes(x, y, z, t);
		});	
		return result;
	}


	public double getValueOfNearestSubindex(double x, double y, double z, double volumeNumber) {

		// bounding box
		int xR = (int)Math.round(x);
		int yR = (int)Math.round(y);
		int zR = (int)Math.round(z);
		int tR = (int)Math.round(volumeNumber);

		// outside range => return 0
		if(xR < 0 || yR < 0 || zR < 0 || tR < 0) {        	
			return 0;
		}
		else if(xR >= this.dim[1] || yR >= this.dim[2] || zR >= this.dim[3] || tR >= this.dim[4]) {
			return 0;        	
		} else {
			return this.data[xR][yR][zR][tR];
		} // inbounds conditional

	} // method getLinearInterpolationOfSubindexes


	public double getLinearInterpolationOfSubindexes(double x, double y, double z, double volumeNumber) {

		// bounding box
		int xF = (int)Math.floor(x);
		int xC = (int)Math.ceil(x);
		int yF = (int)Math.floor(y);
		int yC = (int)Math.ceil(y);
		int zF = (int)Math.floor(z);
		int zC = (int)Math.ceil(z);
		int vF = (int)Math.floor(volumeNumber);
		int vC = (int)Math.ceil(volumeNumber);

		// outside range => return 0
		if(xF < 0 || yF < 0 || zF < 0 || vF < 0) {        	
			return 0;
		}
		else if(xC >= this.dim[1] || yC >= this.dim[2] || zC >= this.dim[3] || vC >= this.dim[4]) {
			return 0;        	
		} else {

			// read data from bounding box
			double v0000 = this.data[xF][yF][zF][vF];
			double v0001 = this.data[xF][yF][zF][vC];
			double v0010 = this.data[xF][yF][zC][vF];
			double v0011 = this.data[xF][yF][zC][vC];
			double v0100 = this.data[xF][yC][zF][vF];
			double v0101 = this.data[xF][yC][zF][vC];
			double v0110 = this.data[xF][yC][zC][vF];
			double v0111 = this.data[xF][yC][zC][vC];
			double v1000 = this.data[xC][yF][zF][vF];
			double v1001 = this.data[xC][yF][zF][vC];
			double v1010 = this.data[xC][yF][zC][vF];
			double v1011 = this.data[xC][yF][zC][vC];
			double v1100 = this.data[xC][yC][zF][vF];
			double v1101 = this.data[xC][yC][zF][vC];
			double v1110 = this.data[xC][yC][zC][vF];
			double v1111 = this.data[xC][yC][zC][vC];

			// interpolate
			double xFraction = x - xF;
			double yFraction = y - yF;
			double zFraction = z - zF;
			double tFraction = volumeNumber - vF;

			boolean useCorrectInterpolation = false;
			double v;
			if(useCorrectInterpolation) {

				double v000 = v0000 * (1 - xFraction) + v0001 * xFraction;
				double v001 = v0010 * (1 - xFraction) + v0011 * xFraction;
				double v010 = v0100 * (1 - xFraction) + v0101 * xFraction;
				double v011 = v0110 * (1 - xFraction) + v0111 * xFraction;
				double v100 = v1000 * (1 - xFraction) + v1001 * xFraction;
				double v101 = v1010 * (1 - xFraction) + v1011 * xFraction;
				double v110 = v1100 * (1 - xFraction) + v1101 * xFraction;
				double v111 = v1110 * (1 - xFraction) + v1111 * xFraction;

				double v00 = v000 * (1 - yFraction) + v001 * yFraction; 
				double v01 = v010 * (1 - yFraction) + v011 * yFraction;
				double v10 = v100 * (1 - yFraction) + v101 * yFraction;
				double v11 = v110 * (1 - yFraction) + v111 * yFraction;

				double v0 = v00 * (1 - zFraction) + v01 * zFraction;
				double v1 = v10 * (1 - zFraction) + v11 * zFraction;

				v = v0 * (1 - tFraction) + v1 * tFraction;
			} else {

				double v000 = v0001 * (1 - xFraction) + v0000 * xFraction;
				double v001 = v0011 * (1 - xFraction) + v0010 * xFraction;
				double v010 = v0101 * (1 - xFraction) + v0100 * xFraction;
				double v011 = v0111 * (1 - xFraction) + v0110 * xFraction;
				double v100 = v1001 * (1 - xFraction) + v1000 * xFraction;
				double v101 = v1011 * (1 - xFraction) + v1010 * xFraction;
				double v110 = v1101 * (1 - xFraction) + v1100 * xFraction;
				double v111 = v1111 * (1 - xFraction) + v1110 * xFraction;

				double v00 = v001 * (1 - yFraction) + v000 * yFraction;
				double v01 = v011 * (1 - yFraction) + v010 * yFraction;
				double v10 = v101 * (1 - yFraction) + v100 * yFraction;
				double v11 = v111 * (1 - yFraction) + v110 * yFraction;

				double v0 = v01 * (1 - zFraction) + v00 * zFraction;
				double v1 = v11 * (1 - zFraction) + v10 * zFraction;

				v = v1 * (1 - tFraction) + v0 * tFraction;
			}


			return v;
		} // inbounds conditional

	} // method getLinearInterpolationOfSubindexes


	public double getCubicInterpolationOfSubindexes(double xVoxel, double yVoxel, double zVoxel, double volumeNumber) {
		// bounding box
		int x0 = (int)Math.floor(xVoxel) - 1;
		int x1 = (int)Math.floor(xVoxel);
		int x2 = (int)Math.ceil(xVoxel);
		int x3 = (int)Math.ceil(xVoxel) + 1;
		int y0 = (int)Math.floor(yVoxel) - 1;
		int y1 = (int)Math.floor(yVoxel);
		int y2 = (int)Math.ceil(yVoxel);
		int y3 = (int)Math.ceil(yVoxel) + 1;
		int z0 = (int)Math.floor(zVoxel) - 1;
		int z1 = (int)Math.floor(zVoxel);
		int z2 = (int)Math.ceil(zVoxel);
		int z3 = (int)Math.ceil(zVoxel) + 1;
		int t0 = (int)Math.floor(volumeNumber) - 1;
		int t1 = (int)Math.floor(volumeNumber);
		int t2 = (int)Math.ceil(volumeNumber);
		int t3 = (int)Math.ceil(volumeNumber) + 1;

		// outside range => return 0
		if(x1 < 0 || y1 < 0 || z1 < 0 || t1 < 0) {        	
			return 0;
		}
		else if(x2 >= this.dim[1] || y2 >= this.dim[2] || z2 >= this.dim[3] || t2 >= this.dim[4]) {
			return 0;        	
		} else {
			if(x0 < 0) { x0 = 0; }
			if(x3 >= this.dim[1]) { x3 = this.dim[1] - 1; }
			if(y0 < 0) { y0 = 0; }
			if(y3 >= this.dim[2]) { y3 = this.dim[2] - 1; }
			if(z0 < 0) { z0 = 0; }
			if(z3 >= this.dim[3]) { z3 = this.dim[3] - 1; }
			if(t0 < 0) { t0 = 0; }
			if(t3 >= this.dim[4]) { t3 = this.dim[4] - 1; }

			// read data from 4x4x4x4 bounding box
			// x = 0
			double v0000 = this.data[x0][y0][z0][t0];
			double v0001 = this.data[x0][y0][z0][t1];
			double v0002 = this.data[x0][y0][z0][t2];
			double v0003 = this.data[x0][y0][z0][t3];
			double v0010 = this.data[x0][y0][z1][t0];
			double v0011 = this.data[x0][y0][z1][t1];
			double v0012 = this.data[x0][y0][z1][t2];
			double v0013 = this.data[x0][y0][z1][t3];
			double v0020 = this.data[x0][y0][z2][t0];
			double v0021 = this.data[x0][y0][z2][t1];
			double v0022 = this.data[x0][y0][z2][t2];
			double v0023 = this.data[x0][y0][z2][t3];
			double v0030 = this.data[x0][y0][z3][t0];
			double v0031 = this.data[x0][y0][z3][t1];
			double v0032 = this.data[x0][y0][z3][t2];
			double v0033 = this.data[x0][y0][z3][t3];

			double v0100 = this.data[x0][y1][z0][t0];
			double v0101 = this.data[x0][y1][z0][t1];
			double v0102 = this.data[x0][y1][z0][t2];
			double v0103 = this.data[x0][y1][z0][t3];
			double v0110 = this.data[x0][y1][z1][t0];
			double v0111 = this.data[x0][y1][z1][t1];
			double v0112 = this.data[x0][y1][z1][t2];
			double v0113 = this.data[x0][y1][z1][t3];
			double v0120 = this.data[x0][y1][z2][t0];
			double v0121 = this.data[x0][y1][z2][t1];
			double v0122 = this.data[x0][y1][z2][t2];
			double v0123 = this.data[x0][y1][z2][t3];
			double v0130 = this.data[x0][y1][z3][t0];
			double v0131 = this.data[x0][y1][z3][t1];
			double v0132 = this.data[x0][y1][z3][t2];
			double v0133 = this.data[x0][y1][z3][t3];

			double v0200 = this.data[x0][y2][z0][t0];
			double v0201 = this.data[x0][y2][z0][t1];
			double v0202 = this.data[x0][y2][z0][t2];
			double v0203 = this.data[x0][y2][z0][t3];
			double v0210 = this.data[x0][y2][z1][t0];
			double v0211 = this.data[x0][y2][z1][t1];
			double v0212 = this.data[x0][y2][z1][t2];
			double v0213 = this.data[x0][y2][z1][t3];
			double v0220 = this.data[x0][y2][z2][t0];
			double v0221 = this.data[x0][y2][z2][t1];
			double v0222 = this.data[x0][y2][z2][t2];
			double v0223 = this.data[x0][y2][z2][t3];
			double v0230 = this.data[x0][y2][z3][t0];
			double v0231 = this.data[x0][y2][z3][t1];
			double v0232 = this.data[x0][y2][z3][t2];
			double v0233 = this.data[x0][y2][z3][t3];

			double v0300 = this.data[x0][y3][z0][t0];
			double v0301 = this.data[x0][y3][z0][t1];
			double v0302 = this.data[x0][y3][z0][t2];
			double v0303 = this.data[x0][y3][z0][t3];
			double v0310 = this.data[x0][y3][z1][t0];
			double v0311 = this.data[x0][y3][z1][t1];
			double v0312 = this.data[x0][y3][z1][t2];
			double v0313 = this.data[x0][y3][z1][t3];
			double v0320 = this.data[x0][y3][z2][t0];
			double v0321 = this.data[x0][y3][z2][t1];
			double v0322 = this.data[x0][y3][z2][t2];
			double v0323 = this.data[x0][y3][z2][t3];
			double v0330 = this.data[x0][y3][z3][t0];
			double v0331 = this.data[x0][y3][z3][t1];
			double v0332 = this.data[x0][y3][z3][t2];
			double v0333 = this.data[x0][y3][z3][t3];

			// x = 1
			double v1000 = this.data[x1][y0][z0][t0];
			double v1001 = this.data[x1][y0][z0][t1];
			double v1002 = this.data[x1][y0][z0][t2];
			double v1003 = this.data[x1][y0][z0][t3];
			double v1010 = this.data[x1][y0][z1][t0];
			double v1011 = this.data[x1][y0][z1][t1];
			double v1012 = this.data[x1][y0][z1][t2];
			double v1013 = this.data[x1][y0][z1][t3];
			double v1020 = this.data[x1][y0][z2][t0];
			double v1021 = this.data[x1][y0][z2][t1];
			double v1022 = this.data[x1][y0][z2][t2];
			double v1023 = this.data[x1][y0][z2][t3];
			double v1030 = this.data[x1][y0][z3][t0];
			double v1031 = this.data[x1][y0][z3][t1];
			double v1032 = this.data[x1][y0][z3][t2];
			double v1033 = this.data[x1][y0][z3][t3];

			double v1100 = this.data[x1][y1][z0][t0];
			double v1101 = this.data[x1][y1][z0][t1];
			double v1102 = this.data[x1][y1][z0][t2];
			double v1103 = this.data[x1][y1][z0][t3];
			double v1110 = this.data[x1][y1][z1][t0];
			double v1111 = this.data[x1][y1][z1][t1];
			double v1112 = this.data[x1][y1][z1][t2];
			double v1113 = this.data[x1][y1][z1][t3];
			double v1120 = this.data[x1][y1][z2][t0];
			double v1121 = this.data[x1][y1][z2][t1];
			double v1122 = this.data[x1][y1][z2][t2];
			double v1123 = this.data[x1][y1][z2][t3];
			double v1130 = this.data[x1][y1][z3][t0];
			double v1131 = this.data[x1][y1][z3][t1];
			double v1132 = this.data[x1][y1][z3][t2];
			double v1133 = this.data[x1][y1][z3][t3];

			double v1200 = this.data[x1][y2][z0][t0];
			double v1201 = this.data[x1][y2][z0][t1];
			double v1202 = this.data[x1][y2][z0][t2];
			double v1203 = this.data[x1][y2][z0][t3];
			double v1210 = this.data[x1][y2][z1][t0];
			double v1211 = this.data[x1][y2][z1][t1];
			double v1212 = this.data[x1][y2][z1][t2];
			double v1213 = this.data[x1][y2][z1][t3];
			double v1220 = this.data[x1][y2][z2][t0];
			double v1221 = this.data[x1][y2][z2][t1];
			double v1222 = this.data[x1][y2][z2][t2];
			double v1223 = this.data[x1][y2][z2][t3];
			double v1230 = this.data[x1][y2][z3][t0];
			double v1231 = this.data[x1][y2][z3][t1];
			double v1232 = this.data[x1][y2][z3][t2];
			double v1233 = this.data[x1][y2][z3][t3];

			double v1300 = this.data[x1][y3][z0][t0];
			double v1301 = this.data[x1][y3][z0][t1];
			double v1302 = this.data[x1][y3][z0][t2];
			double v1303 = this.data[x1][y3][z0][t3];
			double v1310 = this.data[x1][y3][z1][t0];
			double v1311 = this.data[x1][y3][z1][t1];
			double v1312 = this.data[x1][y3][z1][t2];
			double v1313 = this.data[x1][y3][z1][t3];
			double v1320 = this.data[x1][y3][z2][t0];
			double v1321 = this.data[x1][y3][z2][t1];
			double v1322 = this.data[x1][y3][z2][t2];
			double v1323 = this.data[x1][y3][z2][t3];
			double v1330 = this.data[x1][y3][z3][t0];
			double v1331 = this.data[x1][y3][z3][t1];
			double v1332 = this.data[x1][y3][z3][t2];
			double v1333 = this.data[x1][y3][z3][t3];

			// x = 2
			double v2000 = this.data[x2][y0][z0][t0];
			double v2001 = this.data[x2][y0][z0][t1];
			double v2002 = this.data[x2][y0][z0][t2];
			double v2003 = this.data[x2][y0][z0][t3];
			double v2010 = this.data[x2][y0][z1][t0];
			double v2011 = this.data[x2][y0][z1][t1];
			double v2012 = this.data[x2][y0][z1][t2];
			double v2013 = this.data[x2][y0][z1][t3];
			double v2020 = this.data[x2][y0][z2][t0];
			double v2021 = this.data[x2][y0][z2][t1];
			double v2022 = this.data[x2][y0][z2][t2];
			double v2023 = this.data[x2][y0][z2][t3];
			double v2030 = this.data[x2][y0][z3][t0];
			double v2031 = this.data[x2][y0][z3][t1];
			double v2032 = this.data[x2][y0][z3][t2];
			double v2033 = this.data[x2][y0][z3][t3];

			double v2100 = this.data[x2][y1][z0][t0];
			double v2101 = this.data[x2][y1][z0][t1];
			double v2102 = this.data[x2][y1][z0][t2];
			double v2103 = this.data[x2][y1][z0][t3];
			double v2110 = this.data[x2][y1][z1][t0];
			double v2111 = this.data[x2][y1][z1][t1];
			double v2112 = this.data[x2][y1][z1][t2];
			double v2113 = this.data[x2][y1][z1][t3];
			double v2120 = this.data[x2][y1][z2][t0];
			double v2121 = this.data[x2][y1][z2][t1];
			double v2122 = this.data[x2][y1][z2][t2];
			double v2123 = this.data[x2][y1][z2][t3];
			double v2130 = this.data[x2][y1][z3][t0];
			double v2131 = this.data[x2][y1][z3][t1];
			double v2132 = this.data[x2][y1][z3][t2];
			double v2133 = this.data[x2][y1][z3][t3];

			double v2200 = this.data[x2][y2][z0][t0];
			double v2201 = this.data[x2][y2][z0][t1];
			double v2202 = this.data[x2][y2][z0][t2];
			double v2203 = this.data[x2][y2][z0][t3];
			double v2210 = this.data[x2][y2][z1][t0];
			double v2211 = this.data[x2][y2][z1][t1];
			double v2212 = this.data[x2][y2][z1][t2];
			double v2213 = this.data[x2][y2][z1][t3];
			double v2220 = this.data[x2][y2][z2][t0];
			double v2221 = this.data[x2][y2][z2][t1];
			double v2222 = this.data[x2][y2][z2][t2];
			double v2223 = this.data[x2][y2][z2][t3];
			double v2230 = this.data[x2][y2][z3][t0];
			double v2231 = this.data[x2][y2][z3][t1];
			double v2232 = this.data[x2][y2][z3][t2];
			double v2233 = this.data[x2][y2][z3][t3];

			double v2300 = this.data[x2][y3][z0][t0];
			double v2301 = this.data[x2][y3][z0][t1];
			double v2302 = this.data[x2][y3][z0][t2];
			double v2303 = this.data[x2][y3][z0][t3];
			double v2310 = this.data[x2][y3][z1][t0];
			double v2311 = this.data[x2][y3][z1][t1];
			double v2312 = this.data[x2][y3][z1][t2];
			double v2313 = this.data[x2][y3][z1][t3];
			double v2320 = this.data[x2][y3][z2][t0];
			double v2321 = this.data[x2][y3][z2][t1];
			double v2322 = this.data[x2][y3][z2][t2];
			double v2323 = this.data[x2][y3][z2][t3];
			double v2330 = this.data[x2][y3][z3][t0];
			double v2331 = this.data[x2][y3][z3][t1];
			double v2332 = this.data[x2][y3][z3][t2];
			double v2333 = this.data[x2][y3][z3][t3];

			// x = 3
			double v3000 = this.data[x3][y0][z0][t0];
			double v3001 = this.data[x3][y0][z0][t1];
			double v3002 = this.data[x3][y0][z0][t2];
			double v3003 = this.data[x3][y0][z0][t3];
			double v3010 = this.data[x3][y0][z1][t0];
			double v3011 = this.data[x3][y0][z1][t1];
			double v3012 = this.data[x3][y0][z1][t2];
			double v3013 = this.data[x3][y0][z1][t3];
			double v3020 = this.data[x3][y0][z2][t0];
			double v3021 = this.data[x3][y0][z2][t1];
			double v3022 = this.data[x3][y0][z2][t2];
			double v3023 = this.data[x3][y0][z2][t3];
			double v3030 = this.data[x3][y0][z3][t0];
			double v3031 = this.data[x3][y0][z3][t1];
			double v3032 = this.data[x3][y0][z3][t2];
			double v3033 = this.data[x3][y0][z3][t3];

			double v3100 = this.data[x3][y1][z0][t0];
			double v3101 = this.data[x3][y1][z0][t1];
			double v3102 = this.data[x3][y1][z0][t2];
			double v3103 = this.data[x3][y1][z0][t3];
			double v3110 = this.data[x3][y1][z1][t0];
			double v3111 = this.data[x3][y1][z1][t1];
			double v3112 = this.data[x3][y1][z1][t2];
			double v3113 = this.data[x3][y1][z1][t3];
			double v3120 = this.data[x3][y1][z2][t0];
			double v3121 = this.data[x3][y1][z2][t1];
			double v3122 = this.data[x3][y1][z2][t2];
			double v3123 = this.data[x3][y1][z2][t3];
			double v3130 = this.data[x3][y1][z3][t0];
			double v3131 = this.data[x3][y1][z3][t1];
			double v3132 = this.data[x3][y1][z3][t2];
			double v3133 = this.data[x3][y1][z3][t3];

			double v3200 = this.data[x3][y2][z0][t0];
			double v3201 = this.data[x3][y2][z0][t1];
			double v3202 = this.data[x3][y2][z0][t2];
			double v3203 = this.data[x3][y2][z0][t3];
			double v3210 = this.data[x3][y2][z1][t0];
			double v3211 = this.data[x3][y2][z1][t1];
			double v3212 = this.data[x3][y2][z1][t2];
			double v3213 = this.data[x3][y2][z1][t3];
			double v3220 = this.data[x3][y2][z2][t0];
			double v3221 = this.data[x3][y2][z2][t1];
			double v3222 = this.data[x3][y2][z2][t2];
			double v3223 = this.data[x3][y2][z2][t3];
			double v3230 = this.data[x3][y2][z3][t0];
			double v3231 = this.data[x3][y2][z3][t1];
			double v3232 = this.data[x3][y2][z3][t2];
			double v3233 = this.data[x3][y2][z3][t3];

			double v3300 = this.data[x3][y3][z0][t0];
			double v3301 = this.data[x3][y3][z0][t1];
			double v3302 = this.data[x3][y3][z0][t2];
			double v3303 = this.data[x3][y3][z0][t3];
			double v3310 = this.data[x3][y3][z1][t0];
			double v3311 = this.data[x3][y3][z1][t1];
			double v3312 = this.data[x3][y3][z1][t2];
			double v3313 = this.data[x3][y3][z1][t3];
			double v3320 = this.data[x3][y3][z2][t0];
			double v3321 = this.data[x3][y3][z2][t1];
			double v3322 = this.data[x3][y3][z2][t2];
			double v3323 = this.data[x3][y3][z2][t3];
			double v3330 = this.data[x3][y3][z3][t0];
			double v3331 = this.data[x3][y3][z3][t1];
			double v3332 = this.data[x3][y3][z3][t2];
			double v3333 = this.data[x3][y3][z3][t3];
			// end data read chunk

			// interpolate t
			double tFraction = volumeNumber - t1;
			double v000 = bezierCubic(v0000, v0001, v0002, v0003, tFraction);
			double v001 = bezierCubic(v0010, v0011, v0012, v0013, tFraction);
			double v002 = bezierCubic(v0020, v0021, v0022, v0023, tFraction);
			double v003 = bezierCubic(v0030, v0031, v0032, v0033, tFraction);
			double v010 = bezierCubic(v0100, v0101, v0102, v0103, tFraction);
			double v011 = bezierCubic(v0110, v0111, v0112, v0113, tFraction);
			double v012 = bezierCubic(v0120, v0121, v0122, v0123, tFraction);
			double v013 = bezierCubic(v0130, v0131, v0132, v0133, tFraction);
			double v020 = bezierCubic(v0200, v0201, v0202, v0203, tFraction);
			double v021 = bezierCubic(v0210, v0211, v0212, v0213, tFraction);
			double v022 = bezierCubic(v0220, v0221, v0222, v0223, tFraction);
			double v023 = bezierCubic(v0230, v0231, v0232, v0233, tFraction);
			double v030 = bezierCubic(v0300, v0301, v0302, v0303, tFraction);
			double v031 = bezierCubic(v0310, v0311, v0312, v0313, tFraction);
			double v032 = bezierCubic(v0320, v0321, v0322, v0323, tFraction);
			double v033 = bezierCubic(v0330, v0331, v0332, v0333, tFraction);

			double v100 = bezierCubic(v1000, v1001, v1002, v1003, tFraction);
			double v101 = bezierCubic(v1010, v1011, v1012, v1013, tFraction);
			double v102 = bezierCubic(v1020, v1021, v1022, v1023, tFraction);
			double v103 = bezierCubic(v1030, v1031, v1032, v1033, tFraction);
			double v110 = bezierCubic(v1100, v1101, v1102, v1103, tFraction);
			double v111 = bezierCubic(v1110, v1111, v1112, v1113, tFraction);
			double v112 = bezierCubic(v1120, v1121, v1122, v1123, tFraction);
			double v113 = bezierCubic(v1130, v1131, v1132, v1133, tFraction);
			double v120 = bezierCubic(v1200, v1201, v1202, v1203, tFraction);
			double v121 = bezierCubic(v1210, v1211, v1212, v1213, tFraction);
			double v122 = bezierCubic(v1220, v1221, v1222, v1223, tFraction);
			double v123 = bezierCubic(v1230, v1231, v1232, v1233, tFraction);
			double v130 = bezierCubic(v1300, v1301, v1302, v1303, tFraction);
			double v131 = bezierCubic(v1310, v1311, v1312, v1313, tFraction);
			double v132 = bezierCubic(v1320, v1321, v1322, v1323, tFraction);
			double v133 = bezierCubic(v1330, v1331, v1332, v1333, tFraction);

			double v200 = bezierCubic(v2000, v2001, v2002, v2003, tFraction);
			double v201 = bezierCubic(v2010, v2011, v2012, v2013, tFraction);
			double v202 = bezierCubic(v2020, v2021, v2022, v2023, tFraction);
			double v203 = bezierCubic(v2030, v2031, v2032, v2033, tFraction);
			double v210 = bezierCubic(v2100, v2101, v2102, v2103, tFraction);
			double v211 = bezierCubic(v2110, v2111, v2112, v2113, tFraction);
			double v212 = bezierCubic(v2120, v2121, v2122, v2123, tFraction);
			double v213 = bezierCubic(v2130, v2131, v2132, v2133, tFraction);
			double v220 = bezierCubic(v2200, v2201, v2202, v2203, tFraction);
			double v221 = bezierCubic(v2210, v2211, v2212, v2213, tFraction);
			double v222 = bezierCubic(v2220, v2221, v2222, v2223, tFraction);
			double v223 = bezierCubic(v2230, v2231, v2232, v2233, tFraction);
			double v230 = bezierCubic(v2300, v2301, v2302, v2303, tFraction);
			double v231 = bezierCubic(v2310, v2311, v2312, v2313, tFraction);
			double v232 = bezierCubic(v2320, v2321, v2322, v2323, tFraction);
			double v233 = bezierCubic(v2330, v2331, v2332, v2333, tFraction);

			double v300 = bezierCubic(v3000, v3001, v3002, v3003, tFraction);
			double v301 = bezierCubic(v3010, v3011, v3012, v3013, tFraction);
			double v302 = bezierCubic(v3020, v3021, v3022, v3023, tFraction);
			double v303 = bezierCubic(v3030, v3031, v3032, v3033, tFraction);
			double v310 = bezierCubic(v3100, v3101, v3102, v3103, tFraction);
			double v311 = bezierCubic(v3110, v3111, v3112, v3113, tFraction);
			double v312 = bezierCubic(v3120, v3121, v3122, v3123, tFraction);
			double v313 = bezierCubic(v3130, v3131, v3132, v3133, tFraction);
			double v320 = bezierCubic(v3200, v3201, v3202, v3203, tFraction);
			double v321 = bezierCubic(v3210, v3211, v3212, v3213, tFraction);
			double v322 = bezierCubic(v3220, v3221, v3222, v3223, tFraction);
			double v323 = bezierCubic(v3230, v3231, v3232, v3233, tFraction);
			double v330 = bezierCubic(v3300, v3301, v3302, v3303, tFraction);
			double v331 = bezierCubic(v3310, v3311, v3312, v3313, tFraction);
			double v332 = bezierCubic(v3320, v3321, v3322, v3323, tFraction);
			double v333 = bezierCubic(v3330, v3331, v3332, v3333, tFraction);

			// interpolate z
			double zFraction = zVoxel - z1;
			double v00 = bezierCubic(v000, v001, v002, v003, zFraction);
			double v01 = bezierCubic(v010, v011, v012, v013, zFraction);
			double v02 = bezierCubic(v020, v021, v022, v023, zFraction);
			double v03 = bezierCubic(v030, v031, v032, v033, zFraction);
			double v10 = bezierCubic(v100, v101, v102, v103, zFraction);
			double v11 = bezierCubic(v110, v111, v112, v113, zFraction);
			double v12 = bezierCubic(v120, v121, v122, v123, zFraction);
			double v13 = bezierCubic(v130, v131, v132, v133, zFraction);
			double v20 = bezierCubic(v200, v201, v202, v203, zFraction);
			double v21 = bezierCubic(v210, v211, v212, v213, zFraction);
			double v22 = bezierCubic(v220, v221, v222, v223, zFraction);
			double v23 = bezierCubic(v230, v231, v232, v233, zFraction);
			double v30 = bezierCubic(v300, v301, v302, v303, zFraction);
			double v31 = bezierCubic(v310, v311, v312, v313, zFraction);
			double v32 = bezierCubic(v320, v321, v322, v323, zFraction);
			double v33 = bezierCubic(v330, v331, v332, v333, zFraction);

			// interpolate y
			double yFraction = yVoxel - y1;
			double v0 = bezierCubic(v00, v01, v02, v03, yFraction);
			double v1 = bezierCubic(v10, v11, v12, v13, yFraction);
			double v2 = bezierCubic(v20, v21, v22, v23, yFraction);
			double v3 = bezierCubic(v30, v31, v32, v33, yFraction);

			// interpolate x
			double xFraction = xVoxel - x1;
			double v = bezierCubic(v0, v1, v2, v3, xFraction);
			return v;
		}

	} // method getCubicInterpolationOfSubindexes

	public static double bezierCubic(double p0, double p1, double p2, double p3, double t) {
		//		if(t < 0 || t > 1) {
		//			throw new Exception("t must be between 0 and 1 inclusive");
		//		}
		double  d1,d2;
		d1=0.5*(p2-p0);
		d2=0.5*(p3-p1);
		double a0 =p1;
		double a1 = d1;
		double a2 = (3.0*(p2-p1))-(2.0*d1)-d2;
		double a3 = d1+d2+(2.0*(-p2+p1));
		double result = a0+a1*t+a2*t*t+a3*t*t*t;
		return result;
	}




	public short getDimX() {return this.dim[1];}
	public short getDimY() {return this.dim[2];}
	public short getDimZ() {return this.dim[3];}
	public short getDimT() {return this.dim[4];}
	public void setDimX(short x) {
		this.dim[1] = x;
		this.allocateData();
	}
	public void setDimY(short y) {
		this.dim[2] = y;
		this.allocateData();
	}
	public void setDimZ(short z) {
		this.dim[3] = z;
		this.allocateData();
	}
	public void setDimT(short t) {
		this.dim[4] = t;
		this.allocateData();
	}

	public double[][][][] getData(){
		return this.data;
	}

	public double getRepetitionTime() {
		return this.pixdim[4];
	}
	public double getVoxelSizeX() {
		return this.pixdim[1];
	}
	public double getVoxelSizeY() {
		return this.pixdim[2];
	}
	public double getVoxelSizeZ() {
		return this.pixdim[3];
	}


	// The Private Methods

	// loads the header from file, returning the partially consumed InputStream
	// closes the stream by default, but the load function keeps it open.
	// advantage: by using GZIPInputStream we don't have to gunzip big files
	// to read the header
	protected InputStream loadHeader(String filePath, Boolean closeStream) throws FileNotFoundException, IOException {
		if(closeStream == null) {
			closeStream = true;
		}
		InputStream fileInputStream = getInputFileStreamBasedOnExtension(filePath);

		loadHeader(fileInputStream);
		if(closeStream) {
			fileInputStream.close();			
		}	
		return fileInputStream;
	}

	// loads the whole dang file
	public void load(String filePath) throws FileNotFoundException, IOException {

		// load the header and allocate memory
		InputStream fileStream = loadHeader(filePath, false);
		allocateData();
		int bytesPerVoxel = this.getVoxelBitLength() / 8;

		// double buffer for reading data
		byte[] fileBytes = new byte[bufferSize + bytesPerVoxel];
		byte[] nextBytes = new byte[bufferSize + bytesPerVoxel];
		ByteBuffer fileBuffer = ByteBuffer.wrap(fileBytes);
		ByteBuffer nextBuffer = ByteBuffer.wrap(nextBytes);
		fileBuffer.order(byteOrder());
		nextBuffer.order(byteOrder());

		int bytesRead = fileStream.read(fileBytes, 0, bufferSize);
		bytesRemaining -= bytesRead;
		
		// get ready to loop		
		boolean finished = false;
		int i = 0;
		int j = 0;
		int k = 0;
		int t = 0;
		int maxI = this.data.length;
		int maxJ = this.data[0].length;
		int maxK = this.data[0][0].length;
		int maxT = this.data[0][0][0].length;
		while(!finished) {
			//			System.out.println("bytes read: " + bytesRead);
			if(bytesRead == -1) {
				throw new IOException("unexpected end of file");
			}

			// bufferIndex will point to the next voxel to be read
			int nextVoxel = bytesPerVoxel;
			while(nextVoxel <= bytesRead && !finished) {

				// convert each voxel to a double
				double value = this.readVoxel(fileBuffer);
				if(value < this.globalMinimum) {
					this.globalMinimum = value;
				}
				if(value > this.globalMaximum) {
					this.globalMaximum = value;
				}
				this.data[i][j][k][t] = value;

				// step forward in the 4-D array
				i++;
				//				if(i >= this.dim[1]) {
				if(i >= maxI) {
					i = 0;
					j++;
					//					if(j >= this.dim[2]) {
					if(j >= maxJ) {
						j = 0;
						k++;
						//						if(k >= this.dim[3]) {
						if(k >= maxK) {
							k = 0;
							t++;
							//							if(t >= this.dim[4]) {
							if(t >= maxT) {
								finished = true;
							}
						}					
					}					
				}

				// step forward in the bytes from the file
				nextVoxel += bytesPerVoxel;

			} // voxel parse loop

			// copy any bytes that didn't add up to a full voxel to the next buffer
			int leftOverBytes = bytesRead - nextVoxel + bytesPerVoxel;
			for(int b = 0; b < leftOverBytes; b++) {
				nextBuffer.put(fileBuffer.get());
			}

			// read the next chunk of the file and copy to nextBytes
			bytesRead = fileStream.read(fileBytes, 0, bufferSize);
			if(bytesRead > -1) {
				System.arraycopy(fileBytes, 0, nextBytes, leftOverBytes, bytesRead);
				bytesRemaining -= bytesRead;

				// bytesRead should now reflect the number waiting to be converted into voxels
				bytesRead += leftOverBytes;
			}else {
				finished = true;
			}


			// finally swap the next and file arrays and reset the buffers
			byte[] tempArray = fileBytes;
			fileBytes = nextBytes;
			nextBytes = tempArray;
			fileBuffer = ByteBuffer.wrap(fileBytes);
			nextBuffer = ByteBuffer.wrap(nextBytes);
			fileBuffer.order(byteOrder());
			nextBuffer.order(byteOrder());

		} // data read loop

		// we cast everything to doubles, so change the header accordingly
		this.datatype = DataType.NIFTI_TYPE_FLOAT64.code;
		this.bitpix = 64;

		// cleanup
		fileStream.close();
	}

	private void setGlobalMinMax() {
		globalMinimum = Double.MAX_VALUE;
		globalMaximum = -Double.MAX_VALUE;
		int maxI = this.data.length;
		int maxJ = this.data[0].length;
		int maxK = this.data[0][0].length;
		int maxT = this.data[0][0][0].length;
		for(int i = 0; i < maxI; i++) {
			for(int j = 0; j < maxJ; j++) {
				for(int k = 0; k < maxK; k++) {
					for(int t = 0; t < maxT; t++) {
						double val = this.data[i][j][k][t];
						if(val < globalMinimum) {
							globalMinimum = val;
						}
						if(val > globalMaximum) {
							globalMaximum = val;
						}
					}
				}
			}
		}
		
	}

	// low-level logic for reading/parsing the header
	protected void loadHeader(InputStream input) throws IOException {
		// hk

		//TYPE	NAME	OFFSET	SIZE	DESCRIPTION
		//int	sizeof_hdr	0B	4B	Size of the header. Must be 348 (bytes).

		// determine endianness here
		byte[] headerSizeBytes = Nifti.readBytes(input, 4);
		ByteBuffer bigBuffer = ByteBuffer.wrap(headerSizeBytes);
		ByteBuffer littleBuffer = ByteBuffer.wrap(headerSizeBytes);
		bigBuffer.order(ByteOrder.BIG_ENDIAN);
		littleBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int bigInt = bigBuffer.getInt();
		int littleInt = littleBuffer.getInt();
		// set the field directly as the setByteOrder flag would throw an error at this point
		if(bigInt == 348) {
			_byteOrder = ByteOrder.BIG_ENDIAN;
		}else if(littleInt == 348) {
			_byteOrder = ByteOrder.LITTLE_ENDIAN;			
		}else {
			if(littleInt > 0 && bigInt > 0) {
				if(littleInt < bigInt) {
					_byteOrder = ByteOrder.LITTLE_ENDIAN;
				} else {
					_byteOrder = ByteOrder.BIG_ENDIAN;
				}
			} else {
				throw new IOException("invalid header size (bigEndian: " + Integer.toString(bigInt) + "; litteEndian: " + Integer.toString(littleInt) + ")");				
			}
		}
		this.sizeof_hdr = 348;
		//		this.sizeof_hdr = this.readInts(input, 1)[0];
		//char	data_type[10]	4B	10B	Not used; compatibility with analyze.
		this.data_type = this.readString(input, 10);
		//char	db_name[18]	14B	18B	Not used; compatibility with analyze.
		this.db_name = this.readString(input, 18);
		//int	extents	32B	4B	Not used; compatibility with analyze.
		this.extents = this.readInts(input, 1)[0];
		//short	session_error	36B	2B	Not used; compatibility with analyze.
		this.session_error = readShorts(input, 1)[0];
		//char	regular	38B	1B	Not used; compatibility with analyze.
		this.regular = readString(input, 1);
		//char	dim_info	39B	1B	Encoding directions (phase, frequency, slice).
		this.dim_info = readBytes(input, 1)[0];

		// dime

		//short	dim[8]	40B	16B	Data array dimensions.
		this.dim = readShorts(input, 8);
		this.intent_p1 = readFloats(input, 1)[0];
		//float	intent_p1	56B	4B	1st intent parameter.
		this.intent_p2 = readFloats(input, 1)[0];
		//float	intent_p2	60B	4B	2nd intent parameter.
		this.intent_p3 = readFloats(input, 1)[0];
		//float	intent_p3	64B	4B	3rd intent parameter.
		this.intent_code = readShorts(input, 1)[0];
		//short datatype;      /*!< Defines data type!    */  /* short datatype;      */
		this.datatype = readShorts(input, 1)[0];
		//short bitpix;        /*!< Number bits/voxel.    */  /* short bitpix;        */
		this.bitpix = readShorts(input, 1)[0];
		//short slice_start;   /*!< First slice index.    */  /* short dim_un0;       */
		this.slice_start = readShorts(input, 1)[0];
		//float pixdim[8];     /*!< Grid spacings.        */  /* float pixdim[8];     */
		this.pixdim = readFloats(input, 8);
		//float vox_offset;    /*!< Offset into .nii file */  /* float vox_offset;    */
		this.vox_offset = readFloats(input, 1)[0];
		//float scl_slope ;    /*!< Data scaling: slope.  */  /* float funused1;      */
		this.scl_slope = readFloats(input, 1)[0];
		//float scl_inter ;    /*!< Data scaling: offset. */  /* float funused2;      */
		this.scl_inter = readFloats(input, 1)[0];
		//short slice_end;     /*!< Last slice index.     */  /* float funused3;      */
		this.slice_end = readShorts(input, 1)[0];
		//char  slice_code ;   /*!< Slice timing order.   */
		this.slice_code = readBytes(input, 1)[0];
		//char  xyzt_units ;   /*!< Units of pixdim[1..4] */
		this.xyzt_units = readBytes(input, 1)[0];
		//float cal_max;       /*!< Max display intensity */  /* float cal_max;       */
		this.cal_max = readFloats(input, 1)[0];
		//float cal_min;       /*!< Min display intensity */  /* float cal_min;       */
		this.cal_min = readFloats(input, 1)[0];
		//float slice_duration;/*!< Time for 1 slice.     */  /* float compressed;    */
		this.slice_duration = readFloats(input, 1)[0];
		//float toffset;       /*!< Time axis shift.      */  /* float verified;      */
		this.toffset = readFloats(input, 1)[0];
		//int   glmax;         /*!< ++UNUSED++            */  /* int glmax;           */
		this.glmax = readInts(input, 1)[0];
		//int   glmin;         /*!< ++UNUSED++            */  /* int glmin;           */
		this.glmin = readInts(input, 1)[0];

		//
		//                         /*--- was data_history substruct ---*/
		//char  descrip[80];   /*!< any text you like.    */  /* char descrip[80];    */
		this.descrip = readString(input, 80);
		//char  aux_file[24];  /*!< auxiliary filename.   */  /* char aux_file[24];   */
		this.aux_file = readString(input, 24);
		//
		//short qform_code ;   /*!< NIFTI_XFORM_* code.   */  /*-- all ANALYZE 7.5 ---*/
		this.qform_code = readShorts(input, 1)[0];
		//short sform_code ;   /*!< NIFTI_XFORM_* code.   */  /*   fields below here  */
		this.sform_code = readShorts(input, 1)[0];
		//                                     /*   are replaced       */
		//float quatern_b ;    /*!< Quaternion b param.   */
		this.quatern_b = readFloats(input, 1)[0];
		//float quatern_c ;    /*!< Quaternion c param.   */
		this.quatern_c = readFloats(input, 1)[0];
		//float quatern_d ;    /*!< Quaternion d param.   */
		this.quatern_d = readFloats(input, 1)[0];
		//float qoffset_x ;    /*!< Quaternion x shift.   */
		this.qoffset_x = readFloats(input, 1)[0];
		//float qoffset_y ;    /*!< Quaternion y shift.   */
		this.qoffset_y = readFloats(input, 1)[0];
		//float qoffset_z ;    /*!< Quaternion z shift.   */
		this.qoffset_z = readFloats(input, 1)[0];
		//
		//float srow_x[4] ;    /*!< 1st row affine transform.   */
		this.srow_x = readFloats(input, 4);
		//float srow_y[4] ;    /*!< 2nd row affine transform.   */
		this.srow_y = readFloats(input, 4);
		//float srow_z[4] ;    /*!< 3rd row affine transform.   */
		this.srow_z = readFloats(input, 4);
		//
		//char intent_name[16];/*!< 'name' or meaning of data.  */
		this.intent_name = readString(input, 16);
		//
		//char magic[4] ;      /*!< MUST be "ni1\0" or "n+1\0". */
		this.magic = readString(input, 4);

		///*! \struct nifti1_extender
		//    \brief This structure represents a 4-byte string that should follow the
		//           binary nifti_1_header data in a NIFTI-1 header file.  If the char
		//           values are {1,0,0,0}, the file is expected to contain extensions,
		//           values of {0,0,0,0} imply the file does not contain extensions.
		//           Other sequences of values are not currently defined.
		// */
		//struct nifti1_extender { char extension[4] ; } ;
		//typedef struct nifti1_extender nifti1_extender ;
		//
		///*! \struct nifti1_extension
		//    \brief Data structure defining the fields of a header extension.	
		this.extension = readBytes(input, 4);
		int bytesPerVoxel = this.getVoxelBitLength() / 8;
		this.dataByteCount = bytesPerVoxel * this.dim[1] * this.dim[2] * this.dim[3] * this.dim[4];
		this.bytesRemaining = dataByteCount;
	}
	
	public long getDataByteCount() {
		return dataByteCount;
	}


	protected void saveHeader(OutputStream input) throws IOException {
		// hk
		writeInt(input, this.sizeof_hdr);
		writeString(input, this.data_type, 10);
		writeString(input, this.db_name, 18);
		writeInt(input, this.extents);
		writeShort(input, this.session_error);
		writeString(input, this.regular, 1);
		writeByte(input, this.dim_info);

		// dime

		// Data array dimensions.
		writeShorts(input, this.dim);
		writeFloat(input, this.intent_p1);
		writeFloat(input, this.intent_p2);
		writeFloat(input, this.intent_p3);
		writeShort(input, this.intent_code);
		writeShort(input, this.datatype);
		writeShort(input, this.bitpix);
		writeShort(input, this.slice_start);
		writeFloats(input, this.pixdim);
		writeFloat(input, this.vox_offset);
		writeFloat(input, this.scl_slope);
		writeFloat(input, this.scl_inter);
		writeShort(input, this.slice_end);
		//char  slice_code ;   /*!< Slice timing order.   */
		writeByte(input, this.slice_code);
		//char  xyzt_units ;   /*!< Units of pixdim[1..4] */
		writeByte(input, this.xyzt_units);
		//float cal_max;       /*!< Max display intensity */  /* float cal_max;       */
		writeFloat(input, this.cal_max);
		//float cal_min;       /*!< Min display intensity */  /* float cal_min;       */
		writeFloat(input, this.cal_min);
		//float slice_duration;/*!< Time for 1 slice.     */  /* float compressed;    */
		writeFloat(input, this.slice_duration);
		//float toffset;       /*!< Time axis shift.      */  /* float verified;      */
		writeFloat(input, this.toffset);
		//int   glmax;         /*!< ++UNUSED++            */  /* int glmax;           */
		writeInt(input, this.glmax);
		//int   glmin;         /*!< ++UNUSED++            */  /* int glmin;           */
		writeInt(input, this.glmin);

		// hist
		//char  descrip[80];   /*!< any text you like.    */  /* char descrip[80];    */
		writeString(input, this.descrip, 80);
		//char  aux_file[24];  /*!< auxiliary filename.   */  /* char aux_file[24];   */
		writeString(input, this.aux_file, 24);
		//short qform_code ;   /*!< NIFTI_XFORM_* code.   */  /*-- all ANALYZE 7.5 ---*/
		writeShort(input, this.qform_code);
		//short sform_code ;   /*!< NIFTI_XFORM_* code.   */  /*   fields below here  */
		writeShort(input, this.sform_code);
		//float quatern_b ;    /*!< Quaternion b param.   */
		writeFloat(input, this.quatern_b);
		//float quatern_c ;    /*!< Quaternion c param.   */
		writeFloat(input, this.quatern_c);
		//float quatern_d ;    /*!< Quaternion d param.   */
		writeFloat(input, this.quatern_d);
		//float qoffset_x ;    /*!< Quaternion x shift.   */
		writeFloat(input, this.qoffset_x);
		//float qoffset_y ;    /*!< Quaternion y shift.   */
		writeFloat(input, this.qoffset_y);
		//float qoffset_z ;    /*!< Quaternion z shift.   */
		writeFloat(input, this.qoffset_z);
		//float srow_x[4] ;    /*!< 1st row affine transform.   */
		writeFloats(input, this.srow_x);
		//float srow_y[4] ;    /*!< 2nd row affine transform.   */
		writeFloats(input, this.srow_y);
		//float srow_z[4] ;    /*!< 3rd row affine transform.   */
		writeFloats(input, this.srow_z);
		//char intent_name[16];/*!< 'name' or meaning of data.  */
		writeString(input, this.intent_name, 16);
		//char magic[4] ;      /*!< MUST be "ni1\0" or "n+1\0". */
		writeString(input, this.magic, 4);
		// header extension
		writeBytes(input, this.extension);
	}

	
	
	
	private void allocateData() {
	
		long[] dims = this.getDim();
		
		// the smallest a dimension can be is 1
		for(int i = 1; i < 8; i++) {
			if(dims[i] <= 0) {
				dims[i] = 1;
			}
		}
		
		// vertex niftis skip dimensions 1-4.
		// compress dimensions that are length 1
		int[] nonSingleDims = new int[4];
		int counter = 0;
		for(int i = 1; i < 8; i++) {
			if(dims[i] > 1) {
				nonSingleDims[counter] = (int)dims[i];
				counter++;
			}
		}
		while(counter < 4) {
			nonSingleDims[counter] = 1;
			counter++;
		}
		
		// allocate multi-dimensional array (for compatibility with matlab)
		//		this.data = new double[dims[1]][][][];
		this.data = new double[nonSingleDims[0]][][][];
		//		for(int i = 0; i < dims[1]; i++) {
		for(int i = 0; i < nonSingleDims[0]; i++) {
			//				this.data[i] = new double[dims[2]][][];
			this.data[i] = new double[nonSingleDims[1]][][];
			//				for(int j = 0; j < dims[2]; j++) {
			for(int j = 0; j < nonSingleDims[1]; j++) {
				//						this.data[i][j] = new double[dims[3]][];
				this.data[i][j] = new double[nonSingleDims[2]][];
				//						for(int k = 0; k < dims[3]; k++) {
				for(int k = 0; k < nonSingleDims[2]; k++) {
					//								this.data[i][j][k] = new double[dims[4]];
					this.data[i][j][k] = new double[nonSingleDims[3]];
				}
			}
		}		
	}

	// reads the voxel data appropriate to the datatype in the header
	// everything gets converted to a double, matlab-style
	protected double readVoxel(ByteBuffer buffer) throws IOException {
		double value = Double.NaN;
		short dType = this.getDataType();
		if(dType == DataType.NIFTI_TYPE_UINT8.code) {
			value = buffer.get();
			if(value < 0) {
				// Harness the power of two's complement
				// 1 => 0x01
				// -1 =(flip the bits)=> 0xFE =(add one)=> 0xFF
				value = 256.0 + value; // 2^8
			}
			return value;
		}else if(dType == DataType.NIFTI_TYPE_INT16.code) {
			return buffer.getShort();
		}else if(dType == DataType.NIFTI_TYPE_INT32.code) {
			return buffer.getInt();
		}else if(dType == DataType.NIFTI_TYPE_FLOAT32.code) {
			return buffer.getFloat();
		}else if(dType == DataType.NIFTI_TYPE_FLOAT64.code) {
			return buffer.getDouble();
		}else if(dType == DataType.NIFTI_TYPE_INT8.code) {
			return buffer.get();
		}else if(dType == DataType.NIFTI_TYPE_UINT16.code) {
			value = buffer.getShort();
			if(value < 0) {
				return 65536.0 + value; // 2^16
			}
			return value;
		}else if(dType == DataType.NIFTI_TYPE_UINT32.code) {
			value = buffer.getInt();
			if(value < 0) {
				return 4294967295.0 + value; // 2^32
			}
			return value;
		}else if(dType == DataType.NIFTI_TYPE_INT64.code) {
			// you lose some precision here. 				
			return buffer.getLong();
		}else if(dType == DataType.NIFTI_TYPE_UINT64.code) {
			value = buffer.getLong();
			if(value < 0) {
				// you lose some precision here too.
				return 18446744073709551616.0 + value; // 2^64
			}
			return value;
		}else {
			throw new IOException("unhandled data type: " + dType);
		}
	}


	private void saveData(OutputStream output) throws IOException {
		double[] saveBuffer = new double[1];
		int maxX = this.data.length;
		int maxY = this.data[0].length;
		int maxZ = this.data[0][0].length;
		int maxT = this.data[0][0][0].length;
		int x = 0;
		int y = 0;
		int z = 0;
		int t = 0;

		boolean finished = false;
		while(!finished) {
			saveBuffer[0] = this.data[x][y][z][t];
			writeDoubles(output, saveBuffer);
			x++;
			if(x >= maxX) {
				x = 0;
				y++;
				if(y >= maxY) {
					y = 0;
					z++;
					if(z >= maxZ) {
						z = 0;
						t++;
						if(t >= maxT) {
							finished = true;
						}
					}
				}
			}
		}

		//		for(int t = 0; t < this.data[i][j][k].length; t++) {
		//			for(int k = 0; k < this.data[i][j].length; k++) {
		//				for(int j = 0; j < this.data[i].length; j++) {
		//					for(int i = 0; i < this.data.length; i++) {
		//						writeDoubles(output, this.data[i][j][k]);						
		//					}
		//				}
		//			}
		//		}
	}




	// begin big block o' binary readers and writers
	private static byte[] readNBytes(InputStream input, int count) throws IOException {
		byte[] result = new byte[count];
		byte[] buffer = new byte[count];
		int totalBytesRead = 0;
		boolean finished = false;
		while(!finished) {
			int bytesRead = input.read(buffer, 0, count);
			if(bytesRead == count) {
				result = buffer;
				finished = true;
				totalBytesRead += bytesRead;
			}else if(bytesRead == -1) {
				if(totalBytesRead == count) {
					finished = true;
				}else {
					throw new IOException("unexpected end of stream");
				}
			}else {
				System.arraycopy(buffer, 0, result, totalBytesRead, bytesRead);
				totalBytesRead += bytesRead;
				if(totalBytesRead == count) {
					finished = true;
				}
			}
		}
		return result;
	}
	protected static byte[] readBytes(InputStream inputStream, int count) throws IOException {
		return readNBytes(inputStream, count);
	}
	protected static double[] readDoubles(InputStream inputStream, int count) throws IOException {
		double[] result = new double[count];		
		byte[] bytes = readNBytes(inputStream, count * 8);
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(byteOrder());
		for(int i = 0; i < count; i++) {
			result[i] = buffer.getDouble();
		}
		return result;
	}
	protected static long[] readLongs(InputStream inputStream, int count) throws IOException {
		long[] result = new long[count];		
		byte[] bytes = readNBytes(inputStream, count * 8);
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(byteOrder());
		for(int i = 0; i < count; i++) {
			result[i] = buffer.getLong();
		}
		return result;
	}
	protected static short[] readShorts(InputStream inputStream, int count) throws IOException {
		short[] result = new short[count];
		byte[] bytes = readNBytes(inputStream, count * 2);
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(byteOrder());
		for(int i = 0; i < count; i++) {
			result[i] = buffer.getShort();
		}
		return result;
	}
	protected static int[] readInts(InputStream inputStream, int count) throws IOException {
		int[] result = new int[count];
		byte[] bytes = readNBytes(inputStream, count * 4);
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(byteOrder());
		for(int i = 0; i < count; i++) {
			result[i] = buffer.getInt();
		}
		return result;
	}
	protected static float[] readFloats(InputStream inputStream, int count) throws IOException {
		float[] result = new float[count];
		byte[] bytes = readNBytes(inputStream, count * 4);
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(byteOrder());
		for(int i = 0; i < count; i++) {
			result[i] = buffer.getFloat();
		}
		return result;
	}
	protected static String readString(InputStream inputStream, int count) throws IOException {		
		byte[] bytes = readNBytes(inputStream, count);		
		String result = new String(bytes, StandardCharsets.UTF_8);
		return result;
	}
	protected static void writeBytes(OutputStream outputStream, byte[] toWrite) throws IOException {
		outputStream.write(toWrite);
	}
	protected static void writeString(OutputStream outputStream, String toWrite, int length) throws IOException {
		byte[] bytes = toWrite.getBytes(StandardCharsets.UTF_8);
		// truncate if longer than desired length
		int paddingLength = bytes.length - length;
		if(paddingLength < 0) {
			bytes = Arrays.copyOf(bytes, length);			
		}
		outputStream.write(bytes);
		if(paddingLength > 0) {
			byte[] padding = new byte[paddingLength];
			outputStream.write(padding);
		}
	}
	protected static void writeShorts(OutputStream outputStream, short[] toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(toWrite.length * 2);		
		buffer.order(byteOrder());
		for(int i = 0; i < toWrite.length; i++) {
			buffer.putShort(toWrite[i]);			
		}
		outputStream.write(buffer.array());
	}
	protected static void writeInts(OutputStream outputStream, int[] toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(toWrite.length * 4);
		buffer.order(byteOrder());
		for(int i = 0; i < toWrite.length; i++) {
			buffer.putInt(toWrite[i]);			
		}
		outputStream.write(buffer.array());
	}
	protected static void writeLongs(OutputStream outputStream, long[] toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(toWrite.length * 8);
		buffer.order(byteOrder());
		for(int i = 0; i < toWrite.length; i++) {
			buffer.putLong(toWrite[i]);			
		}
		outputStream.write(buffer.array());
	}
	protected static void writeFloats(OutputStream outputStream, float[] toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(toWrite.length * 4);
		buffer.order(byteOrder());
		for(int i = 0; i < toWrite.length; i++) {
			buffer.putFloat(toWrite[i]);			
		}
		outputStream.write(buffer.array());
	}
	protected static void writeDoubles(OutputStream outputStream, double[] toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(toWrite.length * 8);
		buffer.order(byteOrder());
		for(int i = 0; i < toWrite.length; i++) {
			buffer.putDouble(toWrite[i]);			
		}
		byte[] array = buffer.array();
		outputStream.write(array);
	}
	protected static void writeByte(OutputStream outputStream, byte toWrite) throws IOException {
		outputStream.write(toWrite);
	}
	protected static void writeShort(OutputStream outputStream, short toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(2);		
		buffer.order(byteOrder());
		buffer.putShort(toWrite);
		outputStream.write(buffer.array());
	}
	protected static void writeInt(OutputStream outputStream, int toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.order(byteOrder());
		buffer.putInt(toWrite);
		outputStream.write(buffer.array());
	}
	protected static void writeLong(OutputStream outputStream, long toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.order(byteOrder());
		buffer.putLong(toWrite);
		outputStream.write(buffer.array());
	}
	protected static void writeFloat(OutputStream outputStream, float toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.order(byteOrder());
		buffer.putFloat(toWrite);			
		outputStream.write(buffer.array());
	}
	protected static void writeDoubles(OutputStream outputStream, double toWrite) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.order(byteOrder());
		buffer.putDouble(toWrite);			
		outputStream.write(buffer.array());
	}	
	// end big block o' binary readers and writers
	
	public short getDataType() {
		return this.datatype;
	}
	public long[] getDim() {
		long[] result = new long[this.dim.length];
		for(int i = 0; i < this.dim.length; i++) {
			result[i] = this.dim[i];
		}
		return result;
	}

	
	// returns the number of bits per voxel for the supported
	// dataTypes
	protected int getVoxelBitLength() throws IOException {
		int bitsPerElement = 0;
		short dType = this.getDataType();
		if(dType == DataType.NIFTI_TYPE_UINT8.code) {
			bitsPerElement = 8;
		}else if(dType == DataType.NIFTI_TYPE_INT16.code) {
			bitsPerElement = 16;
		}else if(dType == DataType.NIFTI_TYPE_INT32.code) {
			bitsPerElement = 32;
		}else if(dType == DataType.NIFTI_TYPE_FLOAT32.code) {
			bitsPerElement = 32;
		}else if(dType == DataType.NIFTI_TYPE_FLOAT64.code) {
			bitsPerElement = 64;
		}else if(dType == DataType.NIFTI_TYPE_INT8.code) {
			bitsPerElement = 8;
		}else if(dType == DataType.NIFTI_TYPE_UINT16.code) {
			bitsPerElement = 16;
		}else if(dType == DataType.NIFTI_TYPE_UINT32.code) {
			bitsPerElement = 32;
		}else if(dType == DataType.NIFTI_TYPE_INT64.code) {
			bitsPerElement = 64;
		}else if(dType == DataType.NIFTI_TYPE_UINT64.code) {
			bitsPerElement = 64;
		}else {
			throw new IOException("unhandled data type: " + dType);
		}
		return bitsPerElement;

	}

	public Nifti scaleSpace(double scale) {
		int newDimX = (int)Math.ceil(this.getDimX() * scale);
		int newDimY = (int)Math.ceil(this.getDimY() * scale);
		int newDimZ = (int)Math.ceil(this.getDimZ() * scale);
		int dimT = this.getDimT();
		Nifti result = this.clone();
		result.pixdim[1] = (float)(result.pixdim[1] / scale);
		result.pixdim[2] = (float)(result.pixdim[2] / scale);
		result.pixdim[3] = (float)(result.pixdim[3] / scale);
		for(int i = 0; i < 3; i++) {
			result.srow_x[i] = (float)(this.srow_x[i] / scale);
			result.srow_y[i] = (float)(this.srow_y[i] / scale);
			result.srow_z[i] = (float)(this.srow_z[i] / scale);			
		}
		result.dim[1] = (short)newDimX;
		result.dim[2] = (short)newDimY;
		result.dim[3] = (short)newDimZ;
		result.resetData(newDimX, newDimY, newDimZ, dimT);
		for(int i = 0; i < newDimX; i++) {
			for(int j = 0; j < newDimY; j++) {
				for(int k = 0; k < newDimZ; k++) {
					for(int t = 0; t < dimT; t++) {
//						double value = this.getLinearInterpolationOfSubindexes(i / scale,  j / scale,  k / scale,  t);
						double value = this.getCubicInterpolationOfSubindexes(i / scale,  j / scale,  k / scale,  t);
						result.setData(i, j, k, t, value);
					}
				}
			}
		}
		return result;
	}
	public Nifti scaleSpaceAndTime(double spaceScale, double timeScale) {
		int newDimX = (int)Math.ceil(this.getDimX() * spaceScale);
		int newDimY = (int)Math.ceil(this.getDimY() * spaceScale);
		int newDimZ = (int)Math.ceil(this.getDimZ() * spaceScale);
		int newDimT = (int)Math.ceil(this.getDimT() * timeScale);
		Nifti result = this.clone();
		result.pixdim[1] = (float)(result.pixdim[1] / spaceScale);
		result.pixdim[2] = (float)(result.pixdim[2] / spaceScale);
		result.pixdim[3] = (float)(result.pixdim[3] / spaceScale);
		result.pixdim[4] = (float)(result.pixdim[4] / timeScale);
		for(int i = 0; i < 3; i++) {
			result.srow_x[i] = (float)(this.srow_x[i] / spaceScale);
			result.srow_y[i] = (float)(this.srow_y[i] / spaceScale);
			result.srow_z[i] = (float)(this.srow_z[i] / spaceScale);			
		}
		result.dim[1] = (short)newDimX;
		result.dim[2] = (short)newDimY;
		result.dim[3] = (short)newDimZ;
		result.dim[4] = (short)newDimT;
		result.resetData(newDimX, newDimY, newDimZ, newDimT);
		for(int i = 0; i < newDimX; i++) {
			for(int j = 0; j < newDimY; j++) {
				for(int k = 0; k < newDimZ; k++) {
					for(int t = 0; t < newDimT; t++) {
//						double value = this.getLinearInterpolationOfSubindexes(i / scale,  j / scale,  k / scale,  t);
						double value = this.getCubicInterpolationOfSubindexes(i / spaceScale,  j / spaceScale,  k / spaceScale,  t / timeScale);
						result.setData(i, j, k, t, value);
					}
				}
			}
		}
		return result;
	}

	public Nifti timecourseMean() {
		Nifti result = this.clone();
		result.dim[4] = 1;
		int maxI = this.data.length;
		int maxJ = this.data[0].length;
		int maxK = this.data[0][0].length;
		int maxT = this.data[0][0][0].length;
		for(int i = 0; i < maxI; i++) {
			for(int j = 0; j < maxJ; j++) {
				for(int k = 0; k < maxK; k++) {
					result.data[i][j][k] = new double[1];
					double sum = 0;
					for(int t = 0; t < maxT; t++) {
						sum += this.data[i][j][k][t];
					}
					double mean = sum / maxT;
					result.data[i][j][k][0] = mean;
				}
			}
		}
		result.setGlobalMinMax();
		return result;
	}

	public Nifti timecourseStdDev() {
		Nifti result = this.clone();
		result.dim[4] = 1;
		int maxI = this.data.length;
		int maxJ = this.data[0].length;
		int maxK = this.data[0][0].length;
		int maxT = this.data[0][0][0].length;
		for(int i = 0; i < maxI; i++) {
			for(int j = 0; j < maxJ; j++) {
				for(int k = 0; k < maxK; k++) {
					result.data[i][j][k] = new double[1];
					double sum = 0;
					for(int t = 0; t < maxT; t++) {
						sum += this.data[i][j][k][t];
					}
					double mean = sum / maxT;
					double totalDev = 0;
					for(int t = 0; t < maxT; t++) {
						double dev = sum - this.data[i][j][k][t];
						totalDev += dev * dev;
					}
					double std = Math.sqrt(totalDev) / maxT;
					result.data[i][j][k][0] = std;
				}
			}
		}
		result.setGlobalMinMax();
		return result;
	}

	public Nifti timecourseCoeffVar() {
		Nifti result = this.clone();
		result.dim[4] = 1;
		int maxI = this.data.length;
		int maxJ = this.data[0].length;
		int maxK = this.data[0][0].length;
		int maxT = this.data[0][0][0].length;
		for(int i = 0; i < maxI; i++) {
			for(int j = 0; j < maxJ; j++) {
				for(int k = 0; k < maxK; k++) {
					result.data[i][j][k] = new double[1];
					double sum = 0;
					for(int t = 0; t < maxT; t++) {
						sum += this.data[i][j][k][t];
					}
					double mean = sum / maxT;
					double totalDev = 0;
					for(int t = 0; t < maxT; t++) {
						double dev = sum - this.data[i][j][k][t];
						totalDev += dev * dev;
					}
					double std = Math.sqrt(totalDev) / maxT;
					double coefVar = std / mean;
					result.data[i][j][k][0] = coefVar;
				}
			}
		}
		result.setGlobalMinMax();
		return result;
	}

	// returns either a FileInputStream or a GZIPInputStream wrapping it, 
	// depending on the file extension of the given path
	protected InputStream getInputFileStreamBasedOnExtension(String filePath) throws FileNotFoundException, IOException{
		String lowerPath = filePath.toLowerCase();
		InputStream fileInputStream; 
		if(lowerPath.endsWith(".gz")) {
			fileInputStream = new GZIPInputStream(new FileInputStream(filePath));
		}else {
			fileInputStream = new FileInputStream(filePath);
		}
		return fileInputStream;
	}
	// returns either a FileOutputStream or a GZIPInputStream wrapping it, 
	// depending on the file extension of the given path
	private OutputStream getOutputFileStreamBasedOnExtension(String filePath) throws FileNotFoundException, IOException{
		String lowerPath = filePath.toLowerCase();
		OutputStream fileStream0;
		if(lowerPath.endsWith(".gz")) {
			fileStream0 = new GZIPOutputStream(new FileOutputStream(filePath));
		}else {
			fileStream0 = new FileOutputStream(filePath);
			
		}
		BufferedOutputStream bufferedStream = new BufferedOutputStream(fileStream0, this.bufferSize);
		return bufferedStream;
	}

	// attempts to get either the file size, or the unzipped file size if the path ends in .gz
	// note: this doesn't work if the gzip file is > 4Gb; the only way to find out is to gunzip the file
	// better to just figure out how much data there should be based on the header's dim and bytes per voxel
	// and throw if the unzipped file is actually the wrong length
	private long getFileLength(String filePath) throws IOException {
		long fileSize = Files.size(Paths.get(filePath));
		String lowerPath = filePath.toLowerCase();
		if(lowerPath.endsWith(".gz")) {
			RandomAccessFile file = new RandomAccessFile(filePath, "r");
			file.seek(fileSize - 4);
			byte[] sizeBytes = new byte[4];
			int bytesRead = file.read(sizeBytes);
			long tailSize2 = 0;
			for(int i = 3; i >= 0; i--) {
				int b = sizeBytes[i];
				if(b < 0) {
					b = 256 - b;
				}
				tailSize2 = tailSize2 * 256 + b;
			}
			file.close();
			return tailSize2;
		}else {
			return fileSize;
		}
	}

	// which endianness to serialize in (inferred from loaded files)
	public static ByteOrder byteOrder() {
		return _byteOrder;
	}	
	public void setByteOrder(boolean isLittleEndian) throws IOException {
		if(isReading || isWriting) {
			throw new IOException("Cannot change endianness whilst reading or writing files.");
		}
		if(isLittleEndian) {
			this._byteOrder = ByteOrder.LITTLE_ENDIAN;
		}else {			
			this._byteOrder = ByteOrder.BIG_ENDIAN;
		}
	}
	public void resetReadWriteFlags() throws IOException {
		this.isReading = false;
		this.isWriting = false;
		// always throwing an exception may be bad practice but I'm doing it to emphasize
		// this function should only be called in extenuating circumstances
		throw new IOException("Resetting read/write flags; open files may be corrupt.");
	}


	private static void testReadWrite() throws FileNotFoundException, IOException {

		//		String inputPath = "/source/neuro/+lesson/+docker/+node/snowpack1/public/data/sub-MINER116/anatomical/T1.nii.gz";
		//		String outputPath = "/source/neuro/+lesson/+docker/+node/snowpack1/public/data/sub-MINER116/anatomical/T1_test1.nii.gz";
		String outputFolder = "C:\\source\\neuro\\+lesson\\+docker\\+node\\snowpack1\\public\\data\\sub-710-01\\mri\\tuning\\";
		String inputFolder = "C:\\source\\neuro\\+lesson\\+docker\\+node\\snowpack1\\public\\data\\sub-710-01\\mri\\ses-01\\func\\";
		String inputFile = "sub-710-01_ses-01_run-01_RSFC_901.nii.gz";
		String outputFile = "original.nii.gz";
		String inputPath = inputFolder + inputFile;
		String outputPath = outputFolder + outputFile;

		Instant loadStart = Instant.now();
		Nifti nii = Nifti.loadFromFile(inputPath);
		Instant loadEnd = Instant.now();
		Duration loadElapsed = Duration.between(loadStart, loadEnd);
		Instant saveStart = Instant.now();
		nii.save(outputPath);
		Instant saveEnd = Instant.now();
		Duration saveElapsed = Duration.between(saveStart, saveEnd);

		// validation to be done in matlab
		int dummy = 1;
	}
	public double[][] imageLimits(){
		double[][] result = new double[4][];
		for(int i = 0; i < 4; i++) {
			result[i] = new double[2];
			result[i][0] = Double.MAX_VALUE;
			//			result[i][1] = Double.MIN_VALUE;
			result[i][1] = -Double.MAX_VALUE;
		}
		Matrix sub2Coord = this.getSubToCoordMatrix().getMatrix();

		// the fourth coordinate here needs to be one (not time min/max)
		// because it's used in matrix multiplication
		double[][] minSub = new double[][] {new double[] {0, 0, 0, 1}};
		double[][] maxSub = new double[][] {new double[] {this.dim[1], this.dim[2], this.dim[3], 1}};

		Matrix minSubMat = new Matrix(minSub);
		Matrix maxSubMat = new Matrix(maxSub);

		Matrix minCoord = minSubMat.times(sub2Coord);
		Matrix maxCoord = maxSubMat.times(sub2Coord);
		double[] mins = minCoord.getArray()[0]; 
		double[] maxs = maxCoord.getArray()[0]; 
		if(mins[0] < maxs[0]) {
			result[0][0] = mins[0];
			result[0][1] = maxs[0];
		} else {
			result[0][0] = maxs[0];
			result[0][1] = mins[0];
		}
		if(mins[1] < maxs[1]) {
			result[1][0] = mins[1];
			result[1][1] = maxs[1];
		} else {
			result[1][0] = maxs[1];
			result[1][1] = mins[1];
		}
		if(mins[2] < maxs[2]) {
			result[2][0] = mins[2];
			result[2][1] = maxs[2];
		} else {
			result[2][0] = maxs[2];
			result[2][1] = mins[2];
		}

		// set time min/max
		result[3][0] = 0;
		result[3][1] = (this.dim[4] - 1) * this.pixdim[4];

		return result;
	}
	public double[][] subIndex2Coord(double[] x, double[] y, double[] z){
		if(x == null) { return new double[0][]; }
		double[][] subIndices = new double[x.length][];
		for(int i = 0; i < x.length; i++) {
			subIndices[i] = new double[4];
			subIndices[i][0] = x[i];
			subIndices[i][1] = y[i];
			subIndices[i][2] = z[i];
			subIndices[i][3] = 1;
		}
		Matrix subMat = new Matrix(subIndices);
		Matrix sub2Coord = this.getSubToCoordMatrix().getMatrix();
		Matrix coordMat = subMat.times(sub2Coord);
		return coordMat.getArray();
	}
	public double[][] coord2SubIndex(double[] x, double[] y, double[] z){
		double[][] coords = new double[x.length][];
		for(int i = 0; i < x.length; i++) {
			coords[i] = new double[4];
			coords[i][0] = x[i];
			coords[i][1] = y[i];
			coords[i][2] = z[i];
			coords[i][3] = 1;
		}
		Matrix coordMat = new Matrix(coords);
		Matrix coord2Sub = this.getCoordToSubMatrix().getMatrix();
		Matrix subMat = coordMat.times(coord2Sub);
		return subMat.getArray();
	}
	
	public static boolean isNiftiPath(String path) {
		String lowPath = path.toLowerCase();
		if(lowPath.endsWith(".nii") || lowPath.endsWith(".nii.gz")) {
			return true;
		}
		return false;
	}

	public static Nifti[] testMeanStd() throws FileNotFoundException, IOException {
		String inputPath = "Z:\\data\\analyzed\\sub-710-01\\mri\\functional\\sliceCorrected\\sub-710-01_ses-01_run-01_RSFC_901_mcf_unwarp.nii.gz";
		Nifti nii = Nifti.loadFromFile(inputPath);
		Nifti mean = nii.timecourseMean();
		Nifti std = nii.timecourseStdDev();
		int dummy = 1;
		return new Nifti[] {mean, std};
	}

	private static void testInterpolation() throws FileNotFoundException, IOException {
	}
	private static void testCoords() throws FileNotFoundException, IOException {
		String inputPath = "/source/neuro/+lesson/+docker/+node/snowpack1/public/data/sub-MINER116/anatomical/T1.nii.gz";
		Nifti nii = Nifti.loadFromFile(inputPath);
		//		double[][] coords = nii.getVoxelCoordinates();
		int dummy = 1;

	}

	public String toString() {
		return Util.newGson().toJson(this);
	}
	
	private static void testLoad() throws FileNotFoundException, IOException {
		String folder = "Z:\\data\\analyzed\\sub-710-01\\mri\\functional\\sliceMark4\\";
		File folderFile = new File(folder);
		String[] contents = folderFile.list();
		for(int i = 0; i < contents.length; i++) {
			String file = contents[i];
			if(file.endsWith(".nii.gz")) {
				String filePath = folder + file;
				System.out.println("re-saving " + filePath);
				Nifti nii = Nifti.loadFromFile(filePath);
				nii.save(filePath);
			}
		}
	}


	public static void main(String[] args) throws IOException {
//		testMeanStd();
//		testLimits();
//		testReadWrite();
//		//		testReadWrite();
//		testCoords();
//		testLoad();
	}
}




