package com.mridb.sliceRegister;

// parabola of the form y = A * x ^ 2 + B * x + C
// computes the vertex from three points.
// if the points are collinear, vertex X and Y will be Infinite.
// if two points have the same X value, vertex X and Y will be NaN.
public class Parabola {


	double p1x;
	double p2x;
	double p3x;
	double p1y;
	double p2y;
	double p3y;

	Double vertexX;
	Double vertexY;

	private Parabola() {
		this.vertexX = null;
		this.vertexY = null;
	}

	public static Parabola create(double point1x, double point1y, double point2x, double point2y, double point3x, double point3y) {
		Parabola prab = new Parabola();
		prab.p1x = point1x;
		prab.p1y = point1y;
		prab.p2x = point2x;
		prab.p2y = point2y;
		prab.p3x = point3x;
		prab.p3y = point3y;
		prab.solveForVertex();
		return prab;
	}

	public static double[] getVertexXandY(double point1x, double point1y, double point2x, double point2y, double point3x, double point3y) {
		Parabola prab = Parabola.create(point1x, point1y, point2x, point2y, point3x, point3y);
		return new double[] {prab.getVertexX(), prab.getVertexY()};
	}
	public static double getVertexX(double point1x, double point1y, double point2x, double point2y, double point3x, double point3y) {
		Parabola prab = Parabola.create(point1x, point1y, point2x, point2y, point3x, point3y);
		return prab.getVertexX();
	}
	public static double getVertexY(double point1x, double point1y, double point2x, double point2y, double point3x, double point3y) {
		Parabola prab = Parabola.create(point1x, point1y, point2x, point2y, point3x, point3y);
		return prab.getVertexY();
	}
	public static boolean isValidMinimum(double point1x, double point1y, double point2x, double point2y, double point3x, double point3y) {
		Parabola prab = Parabola.create(point1x, point1y, point2x, point2y, point3x, point3y);
		return prab.isValidMinimum();
	}

	private void solveForVertex()
	{
		double denomRecip = 1 / ((p1x - p2x) * (p1x - p3x) * (p2x - p3x));
		double A     = (p3x * (p2y - p1y) + p2x * (p1y - p3y) + p1x * (p3y - p2y)) * denomRecip;
		double B     = (p3x*p3x * (p1y - p2y) + p2x*p2x * (p3y - p1y) + p1x*p1x * (p2y - p3y)) * denomRecip;
		double C     = (p2x * p3x * (p2x - p3x) * p1y + p3x * p1x * (p3x - p1x) * p2y + p1x * p2x * (p1x - p2x) * p3y) * denomRecip;

		vertexX = -B / (2*A);
		vertexY = C - B*B / (4*A);	    	
		int dummy = 1;
	}

	public double getVertexX() {
		if(this.vertexX == null) {
			this.solveForVertex();
		}
		return this.vertexX;
	}
	public double getVertexY() {
		if(this.vertexY == null) {
			this.solveForVertex();
		}
		return this.vertexY;
	}
	public boolean isValidMinimum() {
		if(this.vertexX == null) {
			this.solveForVertex();
		}
		if(Double.isFinite(this.vertexX)) {
			if(this.vertexY <= this.p1y) {
				return true;
			} else if(this.vertexY <= this.p2y) {
				return true;
			} else if(this.vertexY <= this.p3y) {
				return true;
			}
			return false;
		} else {
			return false;
		}
	}
	public static void main(String[] args) {
		// collinear
		double [] xy1 = Parabola.getVertexXandY(1, 1, 0, 0, -1, -1);
		// vertex = minimum
		double [] xy2 = Parabola.getVertexXandY(1, 1, 0, 0, -1, 1);
		boolean isMin2 = Parabola.isValidMinimum(1, 1, 0, 0, -1, 1);
		double [] xy20 = Parabola.getVertexXandY(1, 1, 0, 2, -1, 1);
		boolean isMin20 = Parabola.isValidMinimum(1, 1, 0, 2, -1, 1);
		double [] xy3 = Parabola.getVertexXandY(10, 5, 10, 4, 11, 5);
		double [] xy4 = Parabola.getVertexXandY(10, 11, 12, -10, 20, 20);
		boolean pos = Double.isInfinite(Double.POSITIVE_INFINITY);
		boolean neg = Double.isInfinite(Double.NEGATIVE_INFINITY);
		int dummy = 1;
	}

}
