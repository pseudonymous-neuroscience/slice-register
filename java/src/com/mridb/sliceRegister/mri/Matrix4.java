package com.mridb.sliceRegister.mri;


import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;

import Jama.Matrix;
import com.mridb.sliceRegister.Util;

public class Matrix4 {
	private Matrix matrix;
	public Matrix4() {
		this.matrix = Matrix.identity(4, 4);
	}
	public Matrix4(double[] values) throws Exception {
		this.matrix = new Matrix(4, 4);
		this.setValues(values);
	}
	public Matrix4(Matrix values) throws Exception {
		this();
		if(values.getColumnDimension() != 4 || values.getRowDimension() != 4) {
			throw new Exception("input matrix must have 4 rows and 4 columns");
		}
		this.matrix = values;
	}
	public Matrix4 clone() {
		Matrix matClone = this.matrix.copy();
		return new Matrix4();
	}
	public Matrix getMatrix() {
		return this.matrix;
	}
	public Matrix4 concatenate(Matrix4 subsequentTransform) {
//		Matrix m = subsequentTransform.matrix.inverse().times(this.matrix);
		Matrix m = subsequentTransform.matrix.times(this.matrix);
		Matrix4 result = null;
		try {
			result = new Matrix4(m);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;		
	}
	public void setValues(double[] values) throws Exception {
		if(values.length == 16 || values.length == 12) {
			this.matrix.set(0, 0, values[0]);
			this.matrix.set(1, 0, values[1]);
			this.matrix.set(2, 0, values[2]);
			this.matrix.set(3, 0, values[3]);
			this.matrix.set(0, 1, values[4]);
			this.matrix.set(1, 1, values[5]);
			this.matrix.set(2, 1, values[6]);
			this.matrix.set(3, 1, values[7]);
			this.matrix.set(0, 2, values[8]);
			this.matrix.set(1, 2, values[9]);
			this.matrix.set(2, 2, values[10]);
			this.matrix.set(3, 2, values[11]);
			if(values.length == 16) {
				this.matrix.set(0, 3, values[12]);
				this.matrix.set(1, 3, values[13]);
				this.matrix.set(2, 3, values[14]);
				this.matrix.set(3, 3, values[15]);				
			} else {
				this.matrix.set(0, 3, 0);
				this.matrix.set(1, 3, 0);
				this.matrix.set(2, 3, 0);
				this.matrix.set(3, 3, 1);				
			}	
		}else {
			throw new Exception("values must be of length 12 or 16");
		}
	}
	
	
	public double[] transformVertex(double[] input) {
		double[][] wrapped = new double[1][];
		wrapped[0] = input;
		double[][] result = transformVertices(wrapped);
		return result[0];	
	}

	public double[][] transformVertices(double[][] input) {
		double[][] result = new double[input.length][];
		double x0 = this.get(0, 0);
		double x1 = this.get(1, 0);
		double x2 = this.get(2, 0);
		double x3 = this.get(3, 0);
		double y0 = this.get(0, 1);
		double y1 = this.get(1, 1);
		double y2 = this.get(2, 1);
		double y3 = this.get(3, 1);
		double z0 = this.get(0, 2);
		double z1 = this.get(1, 2);
		double z2 = this.get(2, 2);
		double z3 = this.get(3, 2);
		
		
//		// arraylist for parallel processing
//		ConcurrentSkipListMap<Integer, double[]> map = new ConcurrentSkipListMap<Integer, double[]>();
//		for(Integer i = 0; i < result.length; i++) {
//			map.put(i,  input[i]);
//		}
//		
//		map.entrySet().parallelStream().forEach(e ->{
//			Integer key = e.getKey();
//			double[] value = e.getValue();
//		});
		
		for(int i = 0; i < result.length; i++) {
//			result[i] = new double[input[i].length];
			result[i] = new double[3];
			result[i][0] = x0 * input[i][0] + x1 * input[i][1] + x2 * input[i][2] + x3;
			result[i][1] = y0 * input[i][0] + y1 * input[i][1] + y2 * input[i][2] + y3;
			result[i][2] = z0 * input[i][0] + z1 * input[i][1] + z2 * input[i][2] + z3;
//			for(int j = 3; j < input[i].length; j++) {
//				result[i][j] = input[i][j];
//			}
		}
		return result;
	}
	
	public double get(int i, int j) {
		return this.matrix.get(i, j);
	}
	public void set(int i, int j, double value) {
		this.matrix.set(j, i, value);
	}
	public Matrix4 getInverse() {
//		// debug
//		System.out.println(this.toString());
//		// end debug
		
		boolean isIdentity = true;
		for(int i = 0; i < 4 && isIdentity; i++) {
			for(int j = 0; j < 4 && isIdentity; j++) {
				double val = this.get(i, j);
				if(i == j) {
					if(val != 1) {
						isIdentity = false;
					}
				} else {
					if(val != 0) {
						isIdentity = false;
					}
				}
			}
		}
		
		if(isIdentity) {
			return this.clone();
		} else {
			Matrix inv = this.matrix.inverse();
			Matrix4 result = new Matrix4();
			result.matrix = inv;
			//		Matrix4 result = new Matrix4(
			//				mat.get(0, 0), mat.get(0, 1), mat.get(0, 2), mat.get(0, 3), 
			//				mat.get(1, 0), mat.get(1, 1), mat.get(1, 2), mat.get(1, 3), 
			//				mat.get(2, 0), mat.get(2, 1), mat.get(2, 2), mat.get(2, 3), 
			//				0, 0, 0, 1
			//				);
			return result;
		}

	}
	public Matrix4 times(Matrix4 factor) {
		Matrix4 result = new Matrix4();
		result.matrix = matrix.times(factor.matrix);
		return result;
	}
	
	
	public static void main(String[] args) throws Exception {
		Matrix4 m = new Matrix4();
		String str = m.toString();
		Matrix4 m1 = new Matrix4();
		Matrix4 m2 = new Matrix4();
		m1.setValues(new double[] {1, 0, 0, 10, 0, 1, 0, 0, 0, 0, 1, 0});
		m2.setValues(new double[] {0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0});
		Matrix4 m3 = m1.times(m2);
		Matrix4 m4 = m2.times(m1);
		
		int vertCount = 10;
		double[][] verts = new double[vertCount][];
		for(int i = 0; i < vertCount; i++) {
			verts[i] = new double[4];
			for(int j = 0; j < 3; j++) {
				verts[i][j] = i + j * 10;
			}
			verts[i][3] = 1;
		}
		
		double[][] txed = m1.transformVertices(verts);
		int dummy = 1;
	}
	
//	public String toString() {
//		return Util.newGson().toJson(this);
//	}

	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < 4; i++) {
			for(int j = 0; j < 4; j++) {
				double val = this.matrix.get(i, j);
				sb.append(Double.toString(val));
				if(j < 3) {
					sb.append(", ");
				}else {
					sb.append("\n");					
				}
			}
		}
		return sb.toString();	
	}

}
