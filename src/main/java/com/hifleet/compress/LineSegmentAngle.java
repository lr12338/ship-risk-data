package com.hifleet.compress;


import com.hifleet.route.beans.NewShipForm;

public class LineSegmentAngle {
	private static double limit=30;
	private NewShipForm pa,pb,pc;
	
	private double modeab,modebc;
	
	public LineSegmentAngle(NewShipForm pA,NewShipForm pB,NewShipForm pC){
		this.pa=pA;
		this.pb=pB;
		this.pc=pC;
	}
	
	public void calculateModeAB(){
		double x1,y1,x2,y2;
		x1 = Double.parseDouble(pa.getLo());
		y1 = Double.parseDouble(pa.getLa());
		
		x2 = Double.parseDouble(pb.getLo());
		y2 = Double.parseDouble(pb.getLa());
		
		modeab = Math.sqrt((x1-x2)*(x1-x2)+(y1-y2)*(y1-y2));
		
	}
	
	public void calculateModeBC(){
		double x1,y1,x2,y2;
		x1 = Double.parseDouble(pc.getLo());
		y1 = Double.parseDouble(pc.getLa());
		
		x2 = Double.parseDouble(pb.getLo());
		y2 = Double.parseDouble(pb.getLa());
		
		modebc = Math.sqrt((x1-x2)*(x1-x2)+(y1-y2)*(y1-y2));
	}
	
	
	public boolean isClosedAngle(){		
		return getAngleABC()<=limit?true:false;
	}
	
	public double getAngleABC(){
		
		calculateModeAB();
		calculateModeBC();
		
		double xa,ya,xb,yb,xc,yc;
		
		xa = Double.parseDouble(pa.getLo());
		ya = Double.parseDouble(pa.getLa());
		xb = Double.parseDouble(pb.getLo());
		yb = Double.parseDouble(pb.getLa());
		xc = Double.parseDouble(pc.getLo());
		yc = Double.parseDouble(pc.getLa());
		
		double cosAngle = ((xa-xb)*(xc-xb)+(ya-yb)*(yc-yb))/(modeab*modebc);
		double angle = Math.acos(cosAngle)*180/Math.PI;
		return angle;
	}
	
//	//private boolean 
//	
//	public static void main(String []args){
//		ShipForm pa=new ShipForm();
//		ShipForm pb = new ShipForm();
//		ShipForm pc = new ShipForm();
//		
//		pa.setLo("1");
//		pa.setLa("0");
//		
//		pb.setLo("0");
//		pb.setLa("0");
//		
//		pc.setLo("1");
//		pc.setLa("0.001");
//		
//		LineSegmentAngle la = new LineSegmentAngle(pa,pb,pc);
//		double a =la.getAngleABC();
//		
//		System.out.println(a);
//	}
	
}
