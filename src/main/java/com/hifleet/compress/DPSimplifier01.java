/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.hifleet.compress;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.sql.DataSource;


//import com.hifleet.util.TrajectoryUtil;
//import com.hifleet.util.TrajectoryUtil;
import com.hifleet.hbase.NewOffersForm;
import com.hifleet.route.beans.NewShipForm;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

public class DPSimplifier01 {
	double[] thresholds;
	double[] dthresholds;

	
	
	
	public NewOffersForm simplify(NewOffersForm pts, int zoom) {
		this.pts = pts;
		setDistanceTolerance(thresholds[zoom]);
		setDistanceTolerance1(dthresholds[zoom]);
		return simplify();
	}

	private NewOffersForm pts;
	private boolean[] usePt;
	private Vector<Boolean> overSpeeds;
	private double distanceTolerance;
	private double distanceTolerance1;

	public DPSimplifier01() {
		thresholds = new double[18];
		thresholds[0] = 10.0;
		thresholds[1] = 5.0;
		thresholds[2] = 1.0;
		thresholds[3] = 0.3;
		thresholds[4] = 0.16;
		thresholds[5] = 0.14;
		thresholds[6] = 0.10;
		thresholds[7] = 0.06;
		thresholds[8] = 0.03;
		thresholds[9] = 0.02;
		thresholds[10] = 0.008;// 0.01;
		thresholds[11] = 0.004;// 0.008;
		thresholds[12] = 0.003;// 0.004;
		thresholds[13] = 0.002;// 0.003;
		thresholds[14] = 0.001;// 0.002;
		thresholds[15] = 0.0005;// 0.001;
		thresholds[16] = 0.0002;
		thresholds[17] = 0.0;
		dthresholds = new double[18];
		dthresholds[0] = 1000.0 * 5;
		dthresholds[1] = 600.0 * 5;
		dthresholds[2] = 300.0 * 5;
		dthresholds[3] = 300.0 * 5;
		dthresholds[4] = 300.0 * 5;
		dthresholds[5] = 300.0 * 5;//81.0 * 5;
		dthresholds[6] = 300.0 * 5;//25.0 * 5;
		dthresholds[7] = 300.0 * 5;//12.5 * 5;
		dthresholds[8] = 300.0 * 5;//6.2 * 5;
		dthresholds[9] = 300.0 * 5;//3.1 * 5;
		dthresholds[10] = 300.0 * 5;//1.5 * 5;
		dthresholds[11] = 300.0 * 5;//0.75 * 5;
		dthresholds[12] = 300.0 * 5;;//0.38 * 5;
		dthresholds[13] =300.0 * 5;// 0.19 * 5;
		dthresholds[14] = 300.0 * 5;//0.085 * 5;
		dthresholds[15] = 300.0 * 5;//0.042 * 1;
		dthresholds[16] =300.0 * 5;// 0.021 * 1;
		dthresholds[17] =300.0 * 5;// 0.01 * 2;
//		thresholds = new double[26];
//		thresholds[0] = 10.0;
//		thresholds[1] = 5.0;
//		thresholds[2] = 1.0;
//		thresholds[3] = 0.3;
//		thresholds[4] = 0.16;
//		thresholds[5] = 0.14;
//		thresholds[6] = 0.10;
//		thresholds[7] = 0.06;
//		thresholds[8] = 0.03;
//		thresholds[9] = 0.02;
//		thresholds[10] = 0.008;// 0.01;
//		thresholds[11] = 0.004;// 0.008;
//		thresholds[12] = 0.003;// 0.004;
//		thresholds[13] = 0.002;// 0.003;
//		thresholds[14] = 0.001;// 0.002;
//		thresholds[15] = 0.0005;// 0.001;
//		thresholds[16] = 0.0002;
//		thresholds[17] = 0.0;
//		thresholds[18] = 0.0;
//		thresholds[19] = 0.0;
//		thresholds[20] = 0.0;
//		thresholds[21] = 0.0;
//		thresholds[22] = 0.0;
//		thresholds[23] = 0.0;
//		thresholds[24] = 0.0;
//		thresholds[25] = 0.0;
//		dthresholds = new double[26];
//		dthresholds[0] = 1000.0 * 5;
//		dthresholds[1] = 600.0 * 5;
//		dthresholds[2] = 300.0 * 5;
//		dthresholds[3] = 160.0 * 5;
//		dthresholds[4] = 120.0 * 5;
//		dthresholds[5] = 81.0 * 5;
//		dthresholds[6] = 25.0 * 5;
//		dthresholds[7] = 12.5 * 5;
//		dthresholds[8] = 6.2 * 5;
//		dthresholds[9] = 3.1 * 5;
//		dthresholds[10] = 1.5 * 5;
//		dthresholds[11] = 0.75 * 5;
//		dthresholds[12] = 0.38 * 5;
//		dthresholds[13] = 0.19 * 5;
//		dthresholds[14] = 0.085 * 5;
//		dthresholds[15] = 0.042 * 1;
//		dthresholds[16] = 0.021 * 1;
//		dthresholds[17] = 0.01 * 2;
//		dthresholds[18] = 0.0;
//		dthresholds[19] = 0.0;
//		dthresholds[20] = 0.0;
//		dthresholds[21] = 0.0;
//		dthresholds[22] = 0.0;
//		dthresholds[23] = 0.0;
//		dthresholds[24] = 0.0;
//		dthresholds[25] = 0.0;

	}

	/**
	 * Sets the distance tolerance for the simplification. All vertices in the
	 * simplified linestring will be within this distance of the original
	 * linestring.
	 * 
	 * @param distanceTolerance
	 *            the approximation tolerance to use
	 */
	public void setDistanceTolerance(double distanceTolerance) {
		this.distanceTolerance = distanceTolerance;
	}

	public void setDistanceTolerance1(double distanceTolerance) {
		distanceTolerance1 = distanceTolerance;
	}

	public NewOffersForm simplify() {
		if(pts.getOffers().size()<=3) {return pts;}
		usePt = new boolean[pts.getOffers().size()];
		// for (int i = 0; i < pts.getOffers().size(); i++) {
		// usePt[i] = true;
		// }
		simplifySection(0, pts.getOffers().size() - 1);
		NewOffersForm shippoints = new NewOffersForm();
		for (int i = 0; i < pts.getOffers().size(); i++) {
			NewShipForm nsf = (NewShipForm) pts.getOffers().elementAt(i);
			if(nsf!=null && nsf.getCmp()!=null && nsf.getCmp().compareTo("1")==0){
				shippoints.addShip(nsf);
				continue;
			}
			if (!usePt[i]) {
				shippoints.addShip((NewShipForm) pts.getOffers().elementAt(i));
			}
		}
		
		return shippoints;
	}

	private LineSegment seg;

	private void simplifySection(int i, int j) {
		if(i==0 && j==0){
			return;
		}
		if ((i + 1) == j) {
			// 如果仅有两个点，就return，不做压缩。
			return;
		}
		seg = new LineSegment((NewShipForm) pts.getOffers().elementAt(i),
				(NewShipForm) pts.getOffers().elementAt(j));
		double maxDistance = -1.0;
		int maxIndex = i;

		// 除了起始点外，其他各点到起始点构成的连线的最大的距离和该点的序号。
		for (int k = i + 1; k < j; k++) {
			double distance = seg.distance((NewShipForm) pts.getOffers()
					.elementAt(k));
			if (distance > maxDistance) {
				maxDistance = distance;
				maxIndex = k;
			}
		}

		if (maxDistance <= distanceTolerance) {
			// 如果上述计算获得的最大的距离小于点到直线的距离阈值。
			for (int k = i + 1; k < j; k++) {
				
				double d = seg.distance((NewShipForm) pts.getOffers()
						.elementAt(getLastPointIndex(k - 1)), (NewShipForm) pts
						.getOffers().elementAt(k));
				if (d < distanceTolerance1) {
					usePt[k] = true;
				}
				if (!usePt[k]
						&& seg.distance((NewShipForm) pts.getOffers()
								.elementAt(k), (NewShipForm) pts.getOffers()
								.elementAt(pts.getOffers().size() - 1)) < (0.4 * distanceTolerance1)){
					usePt[k] = true;}
			}
		} else {
			try {
			simplifySection(i, maxIndex);
			simplifySection(maxIndex, j);
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	private int getLastPointIndex(int k) {
		for (int i = k; i > 0; i--) {
			if (!usePt[i]) {
				return i;
			}
		}
		return 0;
	}

	private NewOffersForm deleteMsg18IfExistsMsg1(NewOffersForm pts) {

		int i = 0, j = pts.getOffers().size();
		NewOffersForm rights = new NewOffersForm();
		boolean hasMsg1 = false;
		for (i = 0; i < j; i++) {
			NewShipForm sf_i = (NewShipForm) pts.getOffers().elementAt(i);
			if (sf_i != null && sf_i.getMsgid() != null
					&& sf_i.getMsgid().compareTo("1") == 0) {
				hasMsg1 = true;
				break;
			}
		}
		for (i = 0; i < j; i++) {
			NewShipForm sf_i = (NewShipForm) pts.getOffers().elementAt(i);
			if (sf_i != null && sf_i.getMsgid() != null
					&& sf_i.getMsgid().compareTo("18") == 0 && hasMsg1) {
				continue;
			}
			rights.addShip(sf_i);

		}
		return rights;

	}

	public NewOffersForm findRightPoints(NewOffersForm pts) {

		NewOffersForm rights0 = deleteMsg18IfExistsMsg1(pts);

		int i = 0, j = rights0.getOffers().size();
		NewOffersForm rights = new NewOffersForm();
		for (i = 0; i < j; i++) {
			NewShipForm sf_i = (NewShipForm) rights0.getOffers().elementAt(i);

			boolean overspeedwithpre = false, overspeedwithnex = false;
			int k = i;
			if (k - 1 >= 0) {
				NewShipForm sf_i_minus = (NewShipForm) rights0.getOffers()
						.elementAt(k - 1);
				LineSegment seg_minus_check = new LineSegment(sf_i_minus, sf_i);
				if (seg_minus_check.isOverSpeed()) {
					overspeedwithpre = true;
				} else {
					overspeedwithpre = false;
				}
			}

			if (k + 1 < j) {
				NewShipForm sf_i_plus = (NewShipForm) rights0.getOffers()
						.elementAt(k + 1);
				LineSegment seg_plus_check = new LineSegment(sf_i, sf_i_plus);
				if (seg_plus_check.isOverSpeed()) {
					overspeedwithnex = true;
				} else {
					overspeedwithnex = false;
				}
			}

			if (!overspeedwithpre && !overspeedwithnex) {
				rights.addShip(sf_i);
			}
		}
		return rights;

	}

	private TheRightOneInFirstTen findTheRightOneInFirstTen(NewOffersForm pts10) {
		// ShipForm rightOne=null;
		TheRightOneInFirstTen rightOne = new TheRightOneInFirstTen();
		int maxright = 0;
		int i = 0, j = pts10.getOffers().size();
		for (i = 0; i < j; i++) {
			int right = 0, wrong = 0;
			NewShipForm sf_i = (NewShipForm) pts10.getOffers().elementAt(i);

			int k = i;

			while ((k - 1) >= 0) {
				NewShipForm sf_i_minus = (NewShipForm) pts10.getOffers()
						.elementAt(k - 1);
				LineSegment seg_minus_check = new LineSegment(sf_i_minus, sf_i);
				if (seg_minus_check.isOverSpeed()) {
					wrong++;
				} else {
					right++;
				}
				k--;
			}

			k = i;

			while ((k + 1) < j) {

				NewShipForm sf_i_plus = (NewShipForm) pts10.getOffers()
						.elementAt(k + 1);
				LineSegment seg_plus_check = new LineSegment(sf_i, sf_i_plus);
				if (seg_plus_check.isOverSpeed()) {
					wrong++;
				} else {
					right++;
				}
				k++;
			}
			// System.out.println(sf_i.getTi() + ", right: " + right +
			// ", wrong: " + wrong);
			if (right > maxright) {
				maxright = right;
				// rightOne = sf_i;
				rightOne.setSf(sf_i);
				rightOne.setIndex(i);
			}
		}
		return rightOne;
	}

	public NewOffersForm deleteUFOPoint(NewOffersForm pts) {

		if (pts.getOffers().size() < 3) {
			return pts;
		}

		TheRightOneInFirstTen rightone;
		if (pts.getOffers().size() <= 10) {
			rightone = findTheRightOneInFirstTen(pts);
		} else {
			Vector vector = pts.getOffers();
			int i = 0;
			NewOffersForm pts10 = new NewOffersForm();
			for (i = 0; i < 10; i++) {
				pts10.addShip((NewShipForm) vector.get(i));
			}
			rightone = findTheRightOneInFirstTen(pts10);
		}

		if (rightone != null) {

			NewOffersForm returnpts = new NewOffersForm();
			NewOffersForm originalpts = pts;
			int i = 0, j = originalpts.getOffers().size();
			int rightindex = rightone.getIndex();
			NewShipForm rightsf = rightone.getSf();
			NewShipForm lastrightone = null;
			int lastrightindex = 0;

			// 开始迭代计算
			for (i = 0; i < j; i++) {

				NewShipForm sf_i = (NewShipForm) originalpts.getOffers()
						.elementAt(i);
				LineSegment seg;

				if (rightindex == i) {
					returnpts.addShip(sf_i);
					continue;
				}

				if (lastrightone == null) {

					if (rightindex < i) {
						seg = new LineSegment(rightsf, sf_i);
					} else {
						seg = new LineSegment(sf_i, rightsf);
					}

					if (!seg.isOverSpeed()) {
						returnpts.addShip(sf_i);
						lastrightone = sf_i;
						lastrightindex = i;
					}

				} else {
					if (lastrightindex < i) {
						seg = new LineSegment(lastrightone, sf_i);
					} else {
						seg = new LineSegment(sf_i, lastrightone);
					}

					if (!seg.isOverSpeed()) {
						returnpts.addShip(sf_i);
						lastrightone = sf_i;
						lastrightindex = i;
					}
				}
			}

			return returnpts;

		} else {
			System.out.println("没有找到正确的点。全部返回");
			return pts;
		}
	}

	public NewOffersForm deleteUFOPoint_(NewOffersForm pts) {

		if (pts.getOffers().size() < 3) {
			return pts;
		}

		// 飞点的嫌疑点集合
		Vector<NewShipForm> suspiciousPoints = new Vector<NewShipForm>();

		NewOffersForm returnpts = new NewOffersForm();
		NewOffersForm originalpts = pts;
		int i = 0, j = originalpts.getOffers().size();

		// returnpts.addShip((ShipForm) originalpts.getOffers().elementAt(0));

		for (i = 0; i < j; i++) {
			NewShipForm point_i_plus = null;

			NewShipForm point_i = (NewShipForm) originalpts.getOffers()
					.elementAt(i);

			if ((i + 1) < j) {
				point_i_plus = (NewShipForm) originalpts.getOffers().elementAt(
						i + 1);
			}

			if (point_i_plus != null) {
				// 前一个点不为空。
				LineSegment seg = new LineSegment(point_i, point_i_plus);
				if (seg.isOverSpeed()) {

					// System.out.println("发现飞点!");

					// 向前取一个点或者向后取一个点
					// 1,倒取一个点试试先
					if ((i - 1) >= 0) {
						// 倒取一个点
						// System.out.println("倒取一个点。");
						NewShipForm point_i_minus = (NewShipForm) originalpts
								.getOffers().elementAt(i - 1);
						LineSegment seg_minus = new LineSegment(point_i_minus,
								point_i);
						if (!seg_minus.isOverSpeed()) {
							// System.out.println("非飞点："+point_i.getTi());
							returnpts.addShip(point_i);
						} else {
							// System.out.println("飞点："+point_i.getTi());
						}
					} else if ((i + 2) < j) {
						// 进取一个点
						// System.out.println("进取一个点。");
						NewShipForm point_i_pluss = (NewShipForm) originalpts
								.getOffers().elementAt(i + 2);
						LineSegment seg_plus = new LineSegment(point_i,
								point_i_pluss);
						if (!seg_plus.isOverSpeed()) {
							// System.out.println("非飞点："+point_i.getTi());
							returnpts.addShip(point_i);
						} else {
							// System.out.println("飞点："+point_i.getTi());
						}
					}

				} else {
					// 该点不是飞点
					// System.out.println("非飞点："+point_i.getTi());
					returnpts.addShip(point_i);
				}

			} else {
				// 前 一个点为空,那就往后倒一个点

				// System.out.println("前一个点为空。准备后倒. "+point_i.getTi());
				NewShipForm point_i_minus = (NewShipForm) originalpts
						.getOffers().elementAt(i - 1);
				LineSegment seg_minus = new LineSegment(point_i_minus, point_i);
				if (!seg_minus.isOverSpeed()) {
					// System.out.println("非飞点："+point_i.getTi());
					returnpts.addShip(point_i);
				} else {
					// System.out.println("飞点："+point_i.getTi());
					NewShipForm point_i_minus_minus = (NewShipForm) originalpts
							.getOffers().elementAt(i - 2);
					if (point_i_minus_minus != null) {
						LineSegment seg_minus_minus = new LineSegment(
								point_i_minus_minus, point_i);
						if (!seg_minus_minus.isOverSpeed()) {
							returnpts.addShip(point_i);
						}
					}
				}
				// returnpts.addShip(point_i);
			}
		}
		return returnpts;
	}

	// 删除锐角点
	public NewOffersForm deleteClosedAnglePoint(NewOffersForm pts) {
		if (pts.getOffers().size() < 3) {
			return pts;
		}

		NewOffersForm returnpts = new NewOffersForm();

		Vector vector = pts.getOffers();
		int i = 0, j = vector.size();
		for (i = 0; i < j; i++) {
			NewShipForm sfa = (NewShipForm) vector.get(i);
			if ((i + 1) < j) {
				NewShipForm sfb = (NewShipForm) vector.get(i + 1);
				if ((i + 2) < j) {
					NewShipForm sfc = (NewShipForm) vector.get(i + 2);

					LineSegmentAngle lsa = new LineSegmentAngle(sfa, sfb, sfc);

					if (lsa.isClosedAngle()) {
						// System.out.println("发现锐角： "+sfb.getTi());
						pts.getOffers().remove(i + 1);
						i++;
						j--;
						continue;
					}
				}
			}
		}
		return pts;
	}

	//private static volatile List<LinesMarker> listLine = null;
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	// 补全缺失点
	// 距离为3度，两点间缺失点距离超过3度
	private static final float DIFF_DISTANCE = 1;
	// 时间差为12小时
	private static final float DIFF_HOURS = 12;

	private static final float SELECT_LINE_DISTANCE = 1f;

	private static GeometryFactory geometryFactory = new GeometryFactory();

//	public NewOffersForm analysisMultitrajectoryOL(DataSource ds,
//			NewOffersForm pts) throws Exception {
//
//		if (pts.getOffers().size() < 3) {
//			return pts;
//		}
//
//		if (listLine == null) {
//			Connection conn = null;
//			Statement stmt = null;
//			ResultSet rst = null;
//			try {
//				String sql = "select  Id, email, line, name from ais41.linesmarker_traj_complet";
//				conn = ds.getConnection();
//				stmt = conn.createStatement();
//				rst = stmt.executeQuery(sql);
//				List<LinesMarker> listLs = new ArrayList<LinesMarker>();
//				while (rst.next()) {
//					LinesMarker maker = new LinesMarker();
//					maker.setId(rst.getInt("Id"));
//					maker.setEmail(rst.getString("email"));
//					maker.setLine(rst.getString("line"));
//					maker.setName(rst.getString("name"));
//					listLs.add(maker);
//				}
//				listLine = listLs;
//			} catch (Exception ex) {
//				ex.printStackTrace();
//				return pts;
//			} finally {
//
//				if (rst != null) {
//					try {
//						rst.close();
//					} catch (SQLException sqlex) {
//					}
//					rst = null;
//				}
//				if (stmt != null) {
//					try {
//						stmt.close();
//					} catch (SQLException sqlex) {
//					}
//					stmt = null;
//				}
//				if (conn != null) {
//					try {
//						conn.close();
//					} catch (SQLException sqlex) {
//					}
//					conn = null;
//				}
//			}
//		}
//
//		NewOffersForm returnpts = new NewOffersForm();
//		Vector vector = pts.getOffers();
//		int i = 0, j = vector.size();
//
//		for (i = 0; i < j; i++) {
//			NewShipForm sfa = (NewShipForm) vector.get(i);
//			returnpts.getOffers().add(sfa);
//			if ((i + 1) < j) {
//				NewShipForm sfb = (NewShipForm) vector.get(i + 1);
//
//				String t1 = sfa.getTi();
//				String t2 = sfb.getTi();
//				if (t1.trim().length() == 16) {
//					t1 += ":00";
//				}
//				if (t2.trim().length() == 16) {
//					t2 += ":00";
//				}
//				java.util.Date costtimew = sdf.parse(t1);
//				java.util.Date costtimet = sdf.parse(t2);
//				Long start = costtimew.getTime();
//				Long end = costtimet.getTime();
//				int hours = (int) ((end - start) / (1000 * 60 * 60));
//				// 存在时间差大于12小时
//				if (hours > DIFF_HOURS) {
//					// 记录时间差大于12小时的两个点
//
//					// 距离大于3度
//
//					Double x1 = Double.valueOf(sfa.getLo());
//					Double y1 = Double.valueOf(sfa.getLa());
//					Double x2 = Double.valueOf(sfb.getLo());
//					Double y2 = Double.valueOf(sfb.getLa());
//
//					Point p1 = geometryFactory.createPoint(new Coordinate(x1,
//							y1));
//					Point p2 = geometryFactory.createPoint(new Coordinate(x2,
//							y2));
//					Double p12p2distance = p1.distance(p2);
//					// Double p12p2distance =
//					// TrajectoryUtil.getDistance(x1,y1,x2,y2);
//					if (p12p2distance > DIFF_DISTANCE) {
//						// 补全两点间的点；
//						//System.out.println("开始补全轨迹点+++++++++++++++++");
//						Vector<NewShipForm> offers = interpolation2Point(sfa,
//								sfb, p1, p2);
//						if(offers!=null && offers.size()>0){
//							System.out.println("完成本段补全轨迹点+++++++++++++++++数量： "+offers.size());
//							for (NewShipForm form : offers) {
//								returnpts.getOffers().add(form);
//							}
//						}						
//					}
//				}
//			}
//		}
//		return returnpts;
//	}

//	// 补全两点间的轨迹点
//	private Vector<NewShipForm> interpolation2Point(NewShipForm sfa,
//			NewShipForm sfb, Point p1, Point p2) throws ParseException {
//		long starttimestamp = sdf.parse(sfa.getTi()).getTime();
//		long endtimestamp = sdf.parse(sfb.getTi()).getTime();
//		// 获取惯常轨迹
//		List<LineString> lineList = new ArrayList<LineString>();
//		for (LinesMarker linesMarker : listLine) {
//
//			String[] lines = linesMarker.getLine().split(",");
//			int linesLength = lines.length;
//			Coordinate[] coords = new Coordinate[linesLength / 2];
//
//			for (int i = 0; i < linesLength; i += 2) {
//				coords[i / 2] = new Coordinate(Double.valueOf(lines[i]),
//						Double.valueOf(lines[i + 1]));
//			}
//			LineString line = geometryFactory.createLineString(coords);
//			lineList.add(line);
//		}
//		Map<Integer, Double> line1 = new HashMap<Integer, Double>();
//		Map<Integer, Double> line2 = new HashMap<Integer, Double>();
//		
//		for(int i=0;i<lineList.size();i++){
//			Double distance1 = lineList.get(i).distance(p1);
//			Double distance2 = lineList.get(i).distance(p2);
//			// 计算轨迹到点1距离小于distance的轨迹有哪些
//			if(distance1<SELECT_LINE_DISTANCE){
//				line1.put(i, distance1);
//			}
//			// 计算轨迹到点2距离小于distance的轨迹有哪些
//			if(distance2<SELECT_LINE_DISTANCE){
//				line2.put(i, distance2);
//			}
//		}
//		Set<Integer> keyset1 = line1.keySet();
//		Set<Integer> keyset2 = line2.keySet();
//		keyset1.retainAll(keyset2);
//		
//		// 获取惯常轨迹
//		if(keyset1.size()>0){
//			// 若交集的个数大于1，先取第一个，后面再改，可以考虑线的方向
//			Double minDist = 999999.0;
//			Integer minkey = 0;
//			
////			if(keyset1.size()>1){
//				Iterator<Integer> iterator = keyset1.iterator();
//				while(iterator.hasNext()){
//					Integer key = iterator.next();
//					Double dist1 = line1.get(key);
//					Double dist2 = line2.get(key);
//					Double dist = dist1 + dist2;
//					if(dist<minDist){
//						minkey = key;
//						minDist = dist;
//					}
//				}
////			}
////			System.out.println("最近线ID"+minkey);
//			LineString nearstLine = lineList.get(minkey);
//			
//			Vector list = new Vector();
//			// 起始点的course
//			Double stratPointCourse = Double.valueOf(sfa.getCo());
//			// 结束点的course
//			Double endPointCourse = Double.valueOf(sfb.getCo());
//			Coordinate[] coordinates = nearstLine.getCoordinates();
////			List<Coordinate> coordinates = TrajectoryUtil.findEchidistantPoints(nearstLine,STEP_SIZE);
//			
//			// 最近距离的索引
//			Integer nearStartIndex = null;
//			Integer nearEndIndex = null;
//			// 最近距离
//			Double nearstartd1 = 9999d;
//			Double nearstartd2 = 9999d;
//			Coordinate startcoord = p1.getCoordinate();
//			Coordinate endcoord = p2.getCoordinate();
//			// 寻找距离最近点
//			for(int i=0;i< coordinates.length;i++){
//				
////				Double d1 = coordinates.get(i).distance(startcoord);
//				Double d1 = TrajectoryUtil.getDistance(coordinates[i], startcoord);
//				//System.out.println("d1:"+d1+"   nearstartd1:"+nearstartd1);
//				if(d1<nearstartd1){
//					nearstartd1 = d1;
//					// 最近点夹角成锐角
//					Double start2lineCourse = TrajectoryUtil.computeCourse(startcoord, coordinates[i]);
//					
//					// 起始点的方向和起始点到最近点的方向的夹角
//					Double startDiffCourse = Math.abs(stratPointCourse-start2lineCourse);
//					if(startDiffCourse>180){
//						startDiffCourse = 360-startDiffCourse;
//					}
//					//System.out.println("startDiffCourse: "+startDiffCourse);
//					if(startDiffCourse<45){
//						nearStartIndex = i; 
//						//System.out.println("nearStartIndex :"+nearStartIndex);
//					}
//				}
////				Double d2 = coordinates.get(i).distance(endcoord);
//				Double d2 = TrajectoryUtil.getDistance(coordinates[i], endcoord);
//				// 寻找锐角最近
//				//System.out.println("d2:"+d2+"   nearstartd2:"+nearstartd2);
//				if(d2<nearstartd2){
//					nearstartd2 = d2;
//					Double line2endCourse = TrajectoryUtil.computeCourse(coordinates[i],endcoord);
//					Double endDiffCourse = Math.abs(endPointCourse-line2endCourse);
//					if(endDiffCourse>180){
//						endDiffCourse = 360-endDiffCourse;
//					}
//					//System.out.println("endDiffCourse: "+endDiffCourse);
//					if(endDiffCourse<45){
//						nearEndIndex = i;
//						//System.out.println("nearEndIndex: "+nearEndIndex);
//					}
//				}
//			}
//			if(nearStartIndex == null && nearEndIndex == null){
//				return new Vector();
//			}else if(nearStartIndex != null && nearEndIndex == null ){
//				NewShipForm bo = new NewShipForm();
//				bo.setLo(String.valueOf(coordinates[nearStartIndex].x));
//				bo.setLa(String.valueOf(coordinates[nearStartIndex].y));
//				bo.setN(sfa.getN());
//				bo.setM(sfa.getM());
//				bo.setSp(sfa.getSp());
//				bo.setCo(sfa.getCo());
//				bo.setIsstoppoint("0");
//				bo.setZ("0");
//				bo.setCmp("1");
//				String timestamp = TrajectoryUtil.getNextTime(p1.getX(), p1.getY(), coordinates[nearStartIndex].x,  coordinates[nearStartIndex].y, sfa.getTi(),sfa.getSp());
//				bo.setTi(timestamp);
//				long timestamptemp = sdf.parse(timestamp).getTime();
//				if(timestamptemp > starttimestamp){
//				//	list.add(bo);
//				}
//			}else if(nearStartIndex == null && nearEndIndex != null ){
//				NewShipForm bo = new NewShipForm();
//				bo.setLo(String.valueOf(coordinates[nearEndIndex].x));
//				bo.setLa(String.valueOf(coordinates[nearEndIndex].y));
//				bo.setN(sfa.getN());
//				bo.setM(sfa.getM());
//				bo.setSp(sfa.getSp());
//				bo.setCo(sfa.getCo());
//				bo.setIsstoppoint("0");
//				bo.setZ("0");
//				bo.setCmp("1");
//				String timestamp = TrajectoryUtil.getNextTime(p1.getX(), p1.getY(), coordinates[nearEndIndex].x,  coordinates[nearEndIndex].y, sfa.getTi(),sfa.getSp());
//				bo.setTi(timestamp);
//				long timestamptemp = sdf.parse(timestamp).getTime();
//				if(timestamptemp < endtimestamp){
//				//	list.add(bo);
//				}
//			}else{
//				for(int k=nearStartIndex;k<=nearEndIndex;k++){
//					NewShipForm bo = new NewShipForm();
//					bo.setLo(String.valueOf(coordinates[k].x));
//					bo.setLa(String.valueOf(coordinates[k].y));
//					bo.setN(sfa.getN());
//					bo.setM(sfa.getM());
//					bo.setSp(sfa.getSp());
//					bo.setCo(sfa.getCo());
//					bo.setIsstoppoint("0");
//					bo.setZ("0");
//					bo.setCmp("1");
//					String timestamp = TrajectoryUtil.getNextTime(p1.getX(), p1.getY(), coordinates[k].x,  coordinates[k].y, sfa.getTi(),sfa.getSp());
//					bo.setTi(timestamp);
//					long timestamptemp = sdf.parse(timestamp).getTime();
//					if(timestamptemp<starttimestamp || timestamptemp >endtimestamp){
//						continue;
//					}
//					
//					list.add(bo);
//				}
//			}
//			return list;
//			
//		}else{
//			// 没有匹配的惯常轨迹。
//			// 计算起点终点的角度，生成轨迹点
//			return new Vector();
//		}
//	}
}
