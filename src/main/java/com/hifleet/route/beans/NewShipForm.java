package com.hifleet.route.beans;


public class NewShipForm implements Comparable{

	String m;//mmsi;
    String n;//name;
    String sp;//speed;
    String co;//course;
    String ti;//time;
	Long timestamp;
    String la;//lat;
    String lo;//lon;
    String z;//zoom;
    String wp;//Waypoint,用于航线
    String dis;//distance计算航程
    String msgid;
    String displayzoom;
    String cmp;
    String src;
    String hdg;
    String navstatus;
  //是否是停船位置点
  	private String isstoppoint;
  	private	String portname;
  	private	String accumulatetime;
  	private	String stoptime;
  	private	String starttime;
  	private	String portcode;
  	private	String position;
  	private	String country;
  	private	String ancn;
  	private String eta;
  	private String draught;
  	private String destination;


  	// http://jira.hifleet.com/browse/NEWWEBNAPP-1115
	// 增加 修复后时间字段

	private String rev;


	// http://jira.hifleet.com/browse/NEWWEBNAPP-1258
	// 20211130 by kai
	// 表示类型用户有区别是插值点还是原来的点
	// 0 表示原始值
	private String frmt="0";

	// 原始的MMSI，如果变更过MMSI，为变更的MMSI
	private String mmsiOri;

	public String getMmsiOri() {
		return mmsiOri;
	}

	public void setMmsiOri(String mmsiOri) {
		this.mmsiOri = mmsiOri;
	}

	public String getEta() {
		return eta;
	}

	public void setEta(String eta) {
		this.eta = eta;
	}

	public String getDraught() {
		return draught;
	}

	public void setDraught(String draught) {
		this.draught = draught;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public Long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	public String getFrmt() {
		return frmt;
	}

	public void setFrmt(String frmt) {
		this.frmt = frmt;
	}

	public String getRev() {
		return rev;
	}


	public void setRev(String rev) {
		this.rev = rev;
	}

	public String getNavstatus() {
		return navstatus;
	}



	public void setNavstatus(String navstatus) {
		this.navstatus = navstatus;
	}



	public String getHdg() {
		return hdg;
	}



	public void setHdg(String hdg) {
		this.hdg = hdg;
	}



	public String getSrc() {
		return src;
	}



	public void setSrc(String src) {
		this.src = src;
	}



	public String getCmp() {
		return cmp;
	}



	public void setCmp(String cmp) {
		this.cmp = cmp;
	}



	public String getDisplayzoom() {
		return displayzoom;
	}



	public void setDisplayzoom(String displayzoom) {
		this.displayzoom = displayzoom;
	}

// 在缓存中占用大量内存，比bo本身占用的还要多,先放到方法里面
//	private java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//
	@Override
	public int compareTo(Object obj) {
		// 还有更好的方式来处理，但这个方法还在使用
		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		NewShipForm p = (NewShipForm) obj;
		try{
		String t1 = p.getTi();
		String t2 = this.getTi();
		if(t1.trim().length()==16){
			t1+=":00";
		}
		if(t2.trim().length()==16){
			t2+=":00";
		}
		java.util.Date costtimew = sdf.parse(t1);
		java.util.Date costtimet = sdf.parse(t2);

		/*if (costtimew.before(costtimet)) {
			//System.out.println("return 1---> t1:"+t1+" before t2:"+t2);
			return 1;
		} else{
			//System.out.println("return -1, t1:"+t1+" after t2:"+t2);
			return -1;
		}解决排序报错*/
		if(costtimew == null && costtimew == null) {
		  return 0;
		}
		if(costtimet == null) {
			  return -1;
		}
		if(costtimew == null) {
			  return 1;
		}
		if(costtimet.getTime()>costtimew.getTime()) {
			  return 1;
		}
		if(costtimet.getTime() < costtimew.getTime()) {
			  return -1;
		}
	  return 0;
		}catch(Exception ex){
			//ex.printStackTrace();
			return 0;
		}
	}



	public String getM() {
		return m;
	}
	public void setM(String m) {
		this.m = m;
	}
	public String getN() {
		return n;
	}
	public void setN(String n) {
		this.n = n;
	}
	public String getSp() {
		return sp;
	}
	public void setSp(String sp) {
		this.sp = sp;
	}
	public String getCo() {
		return co;
	}
	public void setCo(String co) {
		this.co = co;
	}
	public String getTi() {
		return ti;
	}
	public void setTi(String ti) {
		this.ti = ti;
	}
	public String getLa() {
		return la;
	}
	public void setLa(String la) {
		this.la = la;
	}
	public String getLo() {
		return lo;
	}
	public void setLo(String lo) {
		this.lo = lo;
	}
	public String getZ() {
		return z;
	}
	public void setZ(String z) {
		this.z = z;
	}
	public String getWp() {
		return wp;
	}
	public void setWp(String wp) {
		this.wp = wp;
	}
	public String getDis() {
		return dis;
	}
	public void setDis(String dis) {
		this.dis = dis;
	}


	public String getIsstoppoint() {
		return isstoppoint;
	}
	public void setIsstoppoint(String isstoppoint) {
		this.isstoppoint = isstoppoint;
	}
	public String getPortname() {
		return portname;
	}
	public void setPortname(String portname) {
		this.portname = portname;
	}
	public String getAccumulatetime() {
		return accumulatetime;
	}
	public void setAccumulatetime(String accumulatetime) {
		this.accumulatetime = accumulatetime;
	}
	public String getStoptime() {
		return stoptime;
	}
	public void setStoptime(String stoptime) {
		this.stoptime = stoptime;
	}
	public String getStarttime() {
		return starttime;
	}
	public void setStarttime(String starttime) {
		this.starttime = starttime;
	}
	public String getPortcode() {
		return portcode;
	}
	public void setPortcode(String portcode) {
		this.portcode = portcode;
	}
	public String getPosition() {
		return position;
	}
	public void setPosition(String position) {
		this.position = position;
	}
	public String getCountry() {
		return country;
	}
	public void setCountry(String country) {
		this.country = country;
	}
	public String getAncn() {
		return ancn;
	}
	public void setAncn(String ancn) {
		this.ancn = ancn;
	}



	public String getMsgid() {
		return msgid;
	}



	public void setMsgid(String msgid) {
		this.msgid = msgid;
	}

	@Override
	public String toString() {
		return "{\"NewShipForm\":{"
				+ "\"m\":\""
				+ m + '\"'
				+ ",\"n\":\""
				+ n + '\"'
				+ ",\"sp\":\""
				+ sp + '\"'
				+ ",\"co\":\""
				+ co + '\"'
				+ ",\"ti\":\""
				+ ti + '\"'
				+ ",\"la\":\""
				+ la + '\"'
				+ ",\"lo\":\""
				+ lo + '\"'
				+ ",\"z\":\""
				+ z + '\"'
				+ ",\"wp\":\""
				+ wp + '\"'
				+ ",\"dis\":\""
				+ dis + '\"'
				+ ",\"msgid\":\""
				+ msgid + '\"'
				+ ",\"displayzoom\":\""
				+ displayzoom + '\"'
				+ ",\"cmp\":\""
				+ cmp + '\"'
				+ ",\"src\":\""
				+ src + '\"'
				+ ",\"hdg\":\""
				+ hdg + '\"'
				+ ",\"navstatus\":\""
				+ navstatus + '\"'
				+ ",\"isstoppoint\":\""
				+ isstoppoint + '\"'
				+ ",\"portname\":\""
				+ portname + '\"'
				+ ",\"accumulatetime\":\""
				+ accumulatetime + '\"'
				+ ",\"stoptime\":\""
				+ stoptime + '\"'
				+ ",\"starttime\":\""
				+ starttime + '\"'
				+ ",\"portcode\":\""
				+ portcode + '\"'
				+ ",\"position\":\""
				+ position + '\"'
				+ ",\"country\":\""
				+ country + '\"'
				+ ",\"ancn\":\""
				+ ancn + '\"'
				+ ",\"rev\":\""
				+ rev + '\"'
				+ "}}";

	}
}
