package com.hifleet.utils;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import org.geotools.geometry.jts.JTSFactoryFinder;

/**
 * 类说明:
 * @author yang chun
 * @创建时间：2019年11月8日 下午5:09:01
 * 
 */
public class GeometryUtils {

	private final static double EARTH_RADIUS_KM=6371;
	private static GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);;

	public static Geometry getGeo(String geo) throws ParseException {
		WKTReader reader = new WKTReader(geometryFactory);
		Geometry geometry1 = (Geometry) reader.read(geo);
		return geometry1;
	}
	
	//计算两个点之间的海里数
		public static double getNauticalMileDistance(double lon1, double lat1, double lon2, double lat2) {
			if (lon1 == lon2 && lat1 == lat2){
				return 0.0;
			}

			double distance =0;		
			double dlon = lon2 - lon1, dlat = lat2 - lat1;
			
			//System.out.println("经差: "+dlon+", 纬差: "+dlat);
			
			if (dlon < -180) {
				dlon += 360;
			}
			if (dlon > 180) {
				dlon -= 360;
			}

			boolean north = dlat > 0 ? true : false;
			boolean east = dlon > 0 ? true : false;

			if (Math.abs(dlat) <= 0.00001) {			
				distance = Math.abs(dlon) * 60.0 * Math.cos(Math.toRadians(Math.abs(lat1)));
				//System.out.println("沿着等纬圈航行: "+(east?90:270)+", 航程： "+distance+" 海里");
				return distance;
			}

			//System.out.println("计算纬度渐长率：MP(lat1)="+calculateMP(lat1)+" --> MP(lat2)="+calculateMP(lat2));
			
			double dmp = calculateMP(lat2) - calculateMP(lat1);
			//System.out.println("纬度渐长率之差： "+dmp);
			
			double tanc = dlon * 60.0 / dmp;

			double c = Math.atan(tanc);
			
			

			c = arad(Math.atan(tanc));
			
			//System.out.println("半圆法航向： "+c);
			
			if (east && !north) {
				c = 180 + c;
			} else if (!north && !east) {
				c += 180;
			} else if (north && !east) {
				c = 360 + c;
			}
			//System.out.println("圆周法航向: " + c);
			
			distance =Math.abs(dlat * 60.0 / Math.cos(Math.toRadians(c)));
			//System.out.println("恒向线航程： "+distance+" 海里");
			return distance;

		}
		
		private static double calculateMP(double lat) {
			boolean isNorth = lat > 0 ? true : false;
			int sign = 1;
			if (!isNorth) {
				sign = -1;
			}
			return (sign * 7915.70447 * Math.log10(Math.tan(Math.toRadians(45 + Math.abs(lat) / 2))));
		}
		
		private static double arad(double rad) {
			return rad * 180 / Math.PI;
		}

		public static double convertCenterAngleDegreeToKiloMeters(double centerAngleDegree){
			return (centerAngleDegree/180)*Math.PI*EARTH_RADIUS_KM;
		}
		
		public static Geometry  getGeoByEnvelope(Envelope env) throws ParseException {
			
			String polygonString = String.format("POLYGON((%s %s,%s %s,%s %s,%s %s,%s %s))", 
					env.getMinX(),env.getMinY(),
					env.getMinX(),env.getMaxY(),
					env.getMaxX(),env.getMaxY(),
					env.getMaxX(),env.getMinY(),
					env.getMinX(),env.getMinY());
			
			WKTReader reader = new WKTReader(geometryFactory);
			Geometry geometry1 = (Geometry) reader.read(polygonString);
			return geometry1;
		}

}
