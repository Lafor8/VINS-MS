package motionestimation;

import Jama.Matrix;

public class DevicePose {
	private double xPos;
	private double yPos;
	private double zPos;
	private Matrix rotWorld;

	private double heading;

	public DevicePose(double x, double y, double z, double h) {
		xPos = x;
		yPos = y;
		zPos = z;
		heading = h;
	}

	public String toString() {
		return "(" + xPos + "," + yPos + "," + zPos + ", " + heading + ")";
	}

	public double getXYDistance() {
		return Math.sqrt(Math.pow(xPos, 2) + Math.pow(yPos, 2));
	}
	
	public Matrix getRotWorld(){
		return rotWorld;
	}
	
	public void setRotWorld(Matrix rotWorld){
		this.rotWorld = rotWorld;
	}

	public double get_xPos() {
		return xPos;
	}

	public void set_xPos(double xPos) {
		this.xPos = xPos;
	}

	public double get_yPos() {
		return yPos;
	}

	public void set_yPos(double yPos) {
		this.yPos = yPos;
	}

	public double get_zPos() {
		return zPos;
	}

	public void set_zPos(double zPos) {
		this.zPos = zPos;
	}

	// returns in radians
	public double getHeading() {
		return Math.toRadians(heading);
	}

	public void setHeading(double heading) {
		this.heading = heading;
	}
}
