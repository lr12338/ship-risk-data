package com.hifleet.hbase;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;


import com.hifleet.compress.DPSimplifier01;
import com.hifleet.route.beans.NewShipForm;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;

//import com.hifleet.form.NewOffersForm;
//import com.hifleet.form.NewShipForm;
//import com.hifleet.route.AppTrajectory;
//import com.hifleet.util.HiFleetStringUtils;
//import com.hifleet.webapp.admin.compress2.DPSimplifier01;

public class GetTrajectoryFromHbase {
	
	
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	

	
	public NewOffersForm getVoffersFromHbase(String mmsi,
											 String startTime, String endTime, int zoom) {

		ArrayList<NewShipForm> list = new ArrayList<NewShipForm>();
		NewShipForm offer;
		
		
		NewOffersForm rightforms= new NewOffersForm();
		try {
		String timestamp, ais;
		String lon_str, lat_str, course_str, speed_str;
		String reportsrc;
		double lon, lat, course, speed;
		String msgid;

		Table table = null;
		Scan scan = null;

		try {

			NewOffersForm offers = new NewOffersForm();
			
			HBaseConfig hbaseConfig = HBaseConfig.getInstance();
			
			table = hbaseConfig.getTable(HiFleetStringUtils.HBASE_TABLE_NAME);

			scan = new Scan();
			scan.addColumn(HiFleetStringUtils.HBASE_CF_DEFAULT.getBytes(), HiFleetStringUtils.HBASE_QUALIFIER);
			
			String start_rowkey = mmsi + "-" + sdf.parse(startTime).getTime()
					/ 1000;
			
			String end_rowkey = mmsi + "-" + sdf.parse(endTime).getTime()
					/ 1000;

			scan.setStartRow(start_rowkey.getBytes());
			scan.setStopRow(end_rowkey.getBytes());
			ResultScanner hbasers = table.getScanner(scan);
			String mmsi_timestamp[];
			//int datacount=0;

			for (Result rt = hbasers.next(); rt != null; rt = hbasers.next()) {
			//	datacount++;
				//System.out.println(rt.getRow());
				mmsi_timestamp = new String(rt.getRow()).split("-");
				timestamp = sdf.format(new Date(
						Long.valueOf(mmsi_timestamp[1]) * 1000));
				ais = new String(rt.getValue(HiFleetStringUtils.HBASE_CF_DEFAULT.getBytes(), HiFleetStringUtils.HBASE_QUALIFIER));
				//System.out.println(ais);
				String aiss[] = ais.split(";");
				if (aiss.length < 4) {
					continue;
				}

				lon_str = aiss[0];

				lat_str = aiss[1];
				if (lon_str.trim().length() < 1 || lat_str.trim().length() < 1) {
					continue;
				}
				course_str = aiss[2];
				speed_str = aiss[3];

				if (aiss.length >= 5) {
					reportsrc = aiss[4];
				} else {
					reportsrc = "1";
				}


				if (aiss.length >= 8) {
					msgid = aiss[7];
				} else {
					msgid = "";
				}

				if ("19".compareTo(reportsrc) == 0 && speed_str != null
						&& speed_str.compareTo("null") != 0
						&& Double.parseDouble(speed_str) < 1) {
					continue;
				}

				if (lon_str.trim().length() > 9) {

					lon_str = lon_str.trim().substring(0, 8);
				}
				if (lat_str.trim().length() > 9) {
					lat_str = lat_str.trim().substring(0, 8);
				}

				lon = Double.parseDouble(lon_str);
				lat = Double.parseDouble(lat_str);
				if (course_str != null && course_str.trim().length() > 0
						&& course_str.compareTo("null") != 0) {
					course = Double.parseDouble(course_str);
				} else {
					course = 0;
				}
				if (speed_str != null && speed_str.compareTo("null") != 0
						&& speed_str.trim().length() > 0) {
					speed = Double.parseDouble(speed_str);
				} else {
					speed = 0;
				}
				
				String nav_status = "";
				if (aiss.length >= 9 && null != aiss[8]) {
//					if(aiss[8].equals("5")) {//||aiss[8].equals("1")
//						System.out.println("ddd"+aiss[8]+"ss"+timestamp);
//					}
					nav_status = getNav_Status_By_Id(aiss[8]);
					
				}

				offer = new NewShipForm();
			
				offer.setM(mmsi);
				offer.setSp("" + speed);
				offer.setCo("" + course);
				offer.setTi(timestamp);
				offer.setLa(("" + (lat)).trim());
				offer.setLo(("" + (lon)).trim());
				offer.setNavstatus(nav_status);

				
				offers.addShip(offer);
				
			}
			int size =offers.getOffers().size()-1;

			DPSimplifier01 simplifier = new DPSimplifier01();

			NewOffersForm noufosimpRoute =simplifier.findRightPoints(offers) ;

			NewOffersForm noclosedangleSimpRoute = simplifier
					.deleteUFOPoint(noufosimpRoute);

			NewOffersForm deleteClosedrightforms = simplifier
					.deleteClosedAnglePoint(noclosedangleSimpRoute);


			 rightforms= simplifier.simplify(deleteClosedrightforms, zoom);

			 hbasers.close();
			
		
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return rightforms;
	

	}

	public NewOffersForm getVoffersFromHbase1(String mmsi,
			String startTime, String endTime,int zoom) {

		ArrayList<NewShipForm> list = new ArrayList<NewShipForm>();
		NewShipForm offer;
		
		
		NewOffersForm rightforms= new NewOffersForm();
		NewOffersForm newrightforms= new NewOffersForm();
		String timestamp, ais;
		String lon_str, lat_str, course_str, speed_str;
		String reportsrc;// ������Դ�����ļ�ͷ
		double lon, lat, course, speed;
		String msgid;

		Table table = null;
		Scan scan = null;

		try {
			//.out.println("��ʼ����");
			NewOffersForm offers = new NewOffersForm();
			
			HBaseConfig hbaseConfig = HBaseConfig.getInstance();
			
			table = hbaseConfig.getTable(HiFleetStringUtils.HBASE_TABLE_NAME);
			//System.out.println("���ӳɹ�");
			scan = new Scan();
			scan.addColumn(HiFleetStringUtils.HBASE_CF_DEFAULT.getBytes(), HiFleetStringUtils.HBASE_QUALIFIER);
			
			String start_rowkey = mmsi + "-" + sdf.parse(startTime).getTime()
					/ 1000;
			
			String end_rowkey = mmsi + "-" + sdf.parse(endTime).getTime()
					/ 1000;

			scan.setStartRow(start_rowkey.getBytes());
			scan.setStopRow(end_rowkey.getBytes());
			ResultScanner hbasers = table.getScanner(scan);
			String mmsi_timestamp[];
			//int datacount=0;
			for (Result rt = hbasers.next(); rt != null; rt = hbasers.next()) {
			//	datacount++;
				//System.out.println(rt.getRow());
				mmsi_timestamp = new String(rt.getRow()).split("-");
				timestamp = sdf.format(new Date(
						Long.valueOf(mmsi_timestamp[1]) * 1000));
				ais = new String(rt.getValue(HiFleetStringUtils.HBASE_CF_DEFAULT.getBytes(), HiFleetStringUtils.HBASE_QUALIFIER));
				//System.out.println(ais);
				String aiss[] = ais.split(";");
				if (aiss.length < 4) {
					continue;
				}

				lon_str = aiss[0];

				lat_str = aiss[1];
				if (lon_str.trim().length() < 1 || lat_str.trim().length() < 1) {
					continue;
				}
				course_str = aiss[2];
				speed_str = aiss[3];

				// �˴���reportsrcΪ������Դ����������Ӧ��Ҫ���жϣ���ֹ�е�������û��д��������Դ��
				if (aiss.length >= 5) {
					reportsrc = aiss[4];
				} else {
					reportsrc = "1";
				}

//				// �����л�ú���ͨ��λ���ݵ��ʸ�
//				if ( reportsrc.compareTo("19") == 0) {
//					continue;
//				}
				if (aiss.length >= 8) {
					msgid = aiss[7];
				} else {
					msgid = "";
				}

				if ("19".compareTo(reportsrc) == 0 && speed_str != null
						&& speed_str.compareTo("null") != 0
						&& Double.parseDouble(speed_str) < 1) {
					continue;
				}

				if (lon_str.trim().length() > 9) {

					lon_str = lon_str.trim().substring(0, 8);
				}
				if (lat_str.trim().length() > 9) {
					lat_str = lat_str.trim().substring(0, 8);
				}

				lon = Double.parseDouble(lon_str);
				lat = Double.parseDouble(lat_str);
				if (course_str != null && course_str.trim().length() > 0
						&& course_str.compareTo("null") != 0) {
					course = Double.parseDouble(course_str);
				} else {
					course = 0;
				}
				if (speed_str != null && speed_str.compareTo("null") != 0
						&& speed_str.trim().length() > 0) {
					speed = Double.parseDouble(speed_str);
				} else {
					speed = 0;
				}
				
				String nav_status = "";
				if (aiss.length >= 9 && null != aiss[8]) {
//					if(aiss[8].equals("5")) {//||aiss[8].equals("1")
//						System.out.println("ddd"+aiss[8]+"ss"+timestamp);
//					}
					nav_status = getNav_Status_By_Id(aiss[8]);
					
				}

				offer = new NewShipForm();
			
				offer.setM(mmsi);
				offer.setSp("" + speed);
				offer.setCo("" + course);
				offer.setTi(timestamp);
				offer.setLa(("" + (lat)).trim());
				offer.setLo(("" + (lon)).trim());
				offer.setNavstatus(nav_status);
			
				offers.addShip(offer);
			}
		
			
			DPSimplifier01 simplifier = new DPSimplifier01();

			NewOffersForm noufosimpRoute =simplifier.findRightPoints(offers) ;//simplifier.findRightPoints(offers);
			// ɾ���ɵ�
			NewOffersForm noclosedangleSimpRoute = simplifier
					.deleteUFOPoint(noufosimpRoute);
			// ɾ����ǵ�
			 rightforms = simplifier
					.deleteClosedAnglePoint(noclosedangleSimpRoute);
			 newrightforms =simplifier.simplify(rightforms, zoom);
		 	hbasers.close();
			
		
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return newrightforms;
	

	}

	

	private String getNav_Status_By_Id(String id) {

		if (id.equals("0")) {
			return "UNDERWAY USING ENGINE".toLowerCase();
		}

		if (id.equals("1")) {
			return "at anchor";
		}
		if (id.equals("2")) {
			return "not under command";
		}
		if (id.equals("3")) {
			return "RESTRICTED MANEUVERABILITY".toLowerCase();
		}

		if (id.equals("4")) {
			return "restricted by draught";
		}
		if (id.equals("5")) {
			return "moored";
		}
		if (id.equals("6")) {
			return "aground";
		}
		if (id.equals("7")) {
			return "fishing";
		}
		if (id.equals("8")) {
			return "sailing";
		}
		if (id.equals("9")) {
			return "reserved for HSC";
		}
		if (id.equals("10")) {
			return "reserved for HSC";
		}
		if (id.equals("15")) {
			return "not defined";
		}

		return "not defined";

	}
	
	
	public NewOffersForm getOriginalVoffersFromHbase(String mmsi,
			String startTime, String endTime) {

		ArrayList<NewShipForm> list = new ArrayList<NewShipForm>();
		NewShipForm offer;
		
		
		NewOffersForm rightforms= new NewOffersForm();
		String timestamp, ais;
		String lon_str, lat_str, course_str, speed_str;
		String reportsrc;// ������Դ�����ļ�ͷ
		double lon, lat, course, speed;
		String msgid;

		Table table = null;
		Scan scan = null;
		NewOffersForm offers = new NewOffersForm();
		try {
			//.out.println("��ʼ����");
			
			
			HBaseConfig hbaseConfig = HBaseConfig.getInstance();
			
			table = hbaseConfig.getTable(HiFleetStringUtils.HBASE_TABLE_NAME);
			//System.out.println("���ӳɹ�");
			scan = new Scan();
			scan.addColumn(HiFleetStringUtils.HBASE_CF_DEFAULT.getBytes(), HiFleetStringUtils.HBASE_QUALIFIER);
			
			String start_rowkey = mmsi + "-" + sdf.parse(startTime).getTime()
					/ 1000;
			
			String end_rowkey = mmsi + "-" + sdf.parse(endTime).getTime()
					/ 1000;

			scan.setStartRow(start_rowkey.getBytes());
			scan.setStopRow(end_rowkey.getBytes());
			ResultScanner hbasers = table.getScanner(scan);
			String mmsi_timestamp[];
			//int datacount=0;
			for (Result rt = hbasers.next(); rt != null; rt = hbasers.next()) {
			//	datacount++;
				//System.out.println(rt.getRow());
				mmsi_timestamp = new String(rt.getRow()).split("-");
				timestamp = sdf.format(new Date(
						Long.valueOf(mmsi_timestamp[1]) * 1000));
				ais = new String(rt.getValue(HiFleetStringUtils.HBASE_CF_DEFAULT.getBytes(), HiFleetStringUtils.HBASE_QUALIFIER));
				//System.out.println(ais);
				String aiss[] = ais.split(";");
				if (aiss.length < 4) {
					continue;
				}

				lon_str = aiss[0];

				lat_str = aiss[1];
				if (lon_str.trim().length() < 1 || lat_str.trim().length() < 1) {
					continue;
				}
				course_str = aiss[2];
				speed_str = aiss[3];

				// �˴���reportsrcΪ������Դ����������Ӧ��Ҫ���жϣ���ֹ�е�������û��д��������Դ��
				if (aiss.length >= 5) {
					reportsrc = aiss[4];
				} else {
					reportsrc = "1";
				}

//				// �����л�ú���ͨ��λ���ݵ��ʸ�
//				if ( reportsrc.compareTo("19") == 0) {
//					continue;
//				}
				if (aiss.length >= 8) {
					msgid = aiss[7];
				} else {
					msgid = "";
				}

				if ("19".compareTo(reportsrc) == 0 && speed_str != null
						&& speed_str.compareTo("null") != 0
						&& Double.parseDouble(speed_str) < 1) {
					continue;
				}

				if (lon_str.trim().length() > 9) {

					lon_str = lon_str.trim().substring(0, 8);
				}
				if (lat_str.trim().length() > 9) {
					lat_str = lat_str.trim().substring(0, 8);
				}

				lon = Double.parseDouble(lon_str);
				lat = Double.parseDouble(lat_str);
				if (course_str != null && course_str.trim().length() > 0
						&& course_str.compareTo("null") != 0) {
					course = Double.parseDouble(course_str);
				} else {
					course = 0;
				}
				if (speed_str != null && speed_str.compareTo("null") != 0
						&& speed_str.trim().length() > 0) {
					speed = Double.parseDouble(speed_str);
				} else {
					speed = 0;
				}
				
				String nav_status = "";
				if (aiss.length >= 9 && null != aiss[8]) {
//					if(aiss[8].equals("5")) {//||aiss[8].equals("1")
//						System.out.println("ddd"+aiss[8]+"ss"+timestamp);
//					}
					nav_status = getNav_Status_By_Id(aiss[8]);
					
				}

				offer = new NewShipForm();
			
				offer.setM(mmsi);
				offer.setSp("" + speed);
				offer.setCo("" + course);
				offer.setTi(timestamp);
				offer.setLa(("" + (lat)).trim());
				offer.setLo(("" + (lon)).trim());
				offer.setNavstatus(nav_status);
					if(speed>3) {
				offers.addShip(offer);
					}
			}
		
			
			//DPSimplifier simplifier = new DPSimplifier();

			//NewOffersForm noufosimpRoute =simplifier.findRightPoints(offers) ;//simplifier.findRightPoints(offers);
			// ɾ���ɵ�
//			NewOffersForm noclosedangleSimpRoute = simplifier
//					.deleteUFOPoint(noufosimpRoute);
			// ɾ����ǵ�
//			 rightforms = simplifier
//					.deleteClosedAnglePoint(noclosedangleSimpRoute);
			 
		 	hbasers.close();
			
		
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return offers;
	

	}

}
