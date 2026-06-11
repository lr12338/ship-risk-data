/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.hifleet.compress;

import java.util.Date;
import java.util.StringTokenizer;

import com.hifleet.route.beans.NewShipForm;
import org.apache.commons.lang.NumberUtils;




public class LineSegment {

	public NewShipForm p0, p1;

	public LineSegment(NewShipForm p0, NewShipForm p1) {
		this.p0 = p0;
		this.p1 = p1;
	}

	public LineSegment(LineSegment ls) {
		this(ls.p0, ls.p1);
	}

	/**
	 * Computes the distance between this line segment and a given point.
	 *
	 * @return the distance from this segment to the given point
	 */
	public double distance(NewShipForm p) {
		double x0 = Double.parseDouble(p0.getLo());
		double y0 = Double.parseDouble(p0.getLa());
		double x1 = Double.parseDouble(p1.getLo());
		double y1 = Double.parseDouble(p1.getLa());
		double x = Double.parseDouble(p.getLo());
		double y = Double.parseDouble(p.getLa());
		double dist;
		if (x0 == x1 && y0 == y1) {
			dist = Math.sqrt((y - y0) * (y - y0) + (x - x0) * (x - x0));
		} else {
			// 点到直线距离公式
			dist = Math
					.abs((y0 - y1) * x + (x1 - x0) * y + (x0 * y1 - y0 * x1))
					/ Math.sqrt((y1 - y0) * (y1 - y0) + (x0 - x1) * (x0 - x1));
		}
		return dist;
	}

	public double distance(NewShipForm p0, NewShipForm p1) {
		double x0 = Double.parseDouble(p0.getLo());
		double y0 = Double.parseDouble(p0.getLa());
		double x1 = Double.parseDouble(p1.getLo());
		double y1 = Double.parseDouble(p1.getLa());
		double dist = Math.sqrt((y1 - y0) * (y1 - y0) + (x0 - x1) * (x0 - x1)) * 60;
		return dist;
	}

	@SuppressWarnings("deprecation")
	public boolean isOverSpeed() {

		double p0Speed = Double.parseDouble(p0.getSp());
		double p1Speed = Double.parseDouble(p1.getSp());
		double p1Course = NumberUtils.isNumber(p1.getCo()) ? Double.parseDouble(p1.getCo()) : 511;
		double p0Course =NumberUtils.isNumber(p0.getCo())? Double.parseDouble(p0.getCo()): 511;

		// 不用欧式距离
		// double dist = Math.sqrt((y1 - y0) * (y1 - y0) + (x0 - x1) * (x0 -
		// x1))*60;
		// 改用墨卡托计算法
		double dist = getMecatorDistance();
		Date t0 = str2Date(p0.getTi());
		Date t1 = str2Date(p1.getTi());
		double deltaT = (t1.getTime() - t0.getTime()) / 3600000.0;

		if (deltaT == 0) {
			if (p1Speed <= 1 && p0Speed <= 1 && dist < 0.1) {
				return false;
			}
			else if (p1Speed >= 1) {
				double direction = getP0ToP1Direction();
				if (511!= p1Course && Math.cos(Math.toRadians(direction - p1Course)) <= 0) {
					System.out.println(p0.getTi()+" --> "+p1.getTi()+", direction: "+direction+", "+p1Course);
					return true;
				}
			}
			return false;
		}

		// System.out.println("compress2: "+p0.getTi()+" --> "+p1.getTi()+", spd: "+(dist/deltaT));
		int spd_cal = (int) (dist / deltaT);
		double p2spd = Double.parseDouble(p1.getSp());
		double speedlimit;
		if (p2spd <= 0.5) {
			speedlimit = 5;
		} else {
			speedlimit = p2spd * 1.2;
		}
		// 单位是节
		if (spd_cal > speedlimit) {

//			if (p1Speed >= 2 && p0Speed>=2)
//				//354092000，2025.5.17.11 - 2025.5.18 11轨迹异常，所以增加这段代码。
//			{
//				double direction = getP0ToP1Direction();
//				if (511!= p1Course && 511!= p0Course && Math.cos(Math.toRadians(direction - p1Course)) > 0 &&
//						Math.cos(Math.toRadians(direction - p0Course))>0){
//					return false;
//				}
//
//			}
			return true;
		}

		return false;
	}

	private double p0ToP1Direction;

	private double getP0ToP1Direction() {
		return p0ToP1Direction;
	}

	public double getMecatorDistance() {
		double x0 = Double.parseDouble(p0.getLo());
		double y0 = Double.parseDouble(p0.getLa());
		double x1 = Double.parseDouble(p1.getLo());
		double y1 = Double.parseDouble(p1.getLa());

		if (x0 == x1 && y1 == y0) {
			return 0.0;
		}

		double dlon = x1 - x0, dlat = y1 - y0;

		if (dlon < -180) {
			dlon += 360;
		}
		if (dlon > 180) {
			dlon -= 360;
		}

		boolean north = dlat > 0 ? true : false;
		boolean east = dlon > 0 ? true : false;

		if (Math.abs(dlat) <= 0.005) {
			// 沿着等纬圈航行
			if (east) {
				p0ToP1Direction = 90;
			} else {
				p0ToP1Direction = 270;
			}
			return Math.abs(dlon) * 60.0
					* Math.cos(Math.toRadians(Math.abs(y0)));
		}

		double dmp = calculateMP(y1) - calculateMP(y0);

		double tanc = dlon * 60.0 / dmp;

		double c = Math.atan(tanc);

		c = arad(Math.atan(tanc));
		// System.out.println("c: "+c);

		if (east && !north) {
			c = 180 + c;
		} else if (!north && !east) {
			c += 180;
		} else if (north && !east) {
			c = 360 + c;
		}
		p0ToP1Direction = c;

		return Math.abs(dlat * 60 / Math.cos(Math.toRadians(c)));
	}

	public double calculateMP(double lat) {
		boolean isNorth = lat > 0 ? true : false;
		int sign = 1;
		if (!isNorth) {
			sign = -1;
		}
		return (sign * 7915.70447 * Math.log10(Math.tan(Math
				.toRadians(45 + Math.abs(lat) / 2))));
	}

	private double arad(double rad) {
		return rad * 180 / Math.PI;
	}

	public boolean isCrossedDateLine() {
		double x0 = Double.parseDouble(p0.getLo());
		double x1 = Double.parseDouble(p1.getLo());
		if (Math.abs(x0 - x1) > 180) {
			return true;
		} else {
			return false;
		}
	}

	public Date str2Date(String strDate) {
		strDate = strDate.substring(0, 19);
		strDate.replaceAll(".", "-");
		StringTokenizer tokenizer = new StringTokenizer(strDate, "- :");
		String year = tokenizer.nextToken();
		int iYear = Integer.parseInt(year);
		String month = tokenizer.nextToken();
		int iMonth = Integer.parseInt(month);
		String day = tokenizer.nextToken();
		int iDay = Integer.parseInt(day);
		String hour = tokenizer.nextToken();
		int iHour = Integer.parseInt(hour);
		String minute = tokenizer.nextToken();
		int iMinute = Integer.parseInt(minute);
		String second = tokenizer.nextToken();
		int iSecond = Integer.parseInt(second);
		Date time = new Date(iYear - 1900, iMonth - 1,
				iDay, iHour, iMinute, iSecond);
		java.util.GregorianCalendar calendar = new java.util.GregorianCalendar();
		calendar.setTime(time);
		calendar.add(java.util.Calendar.HOUR, -12);
		return calendar.getTime();
	}
}

 
