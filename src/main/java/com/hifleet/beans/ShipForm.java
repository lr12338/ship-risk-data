package com.hifleet.beans;
/**
 * 类说明:
 * @author yang chun
 * @创建时间：2019年1月30日 上午9:55:30
 * 
 */
public class ShipForm {
	private String name;
	private Double lon;
	private Double lat;
	private String mmsi;
	private String heading;
	private String speed;
	private String updatetime;
	private String an;
	private String dn;
	private String eta;
	private String draught;
	private String destination;
	private String width;
	private String length;
	private String type;
	private String minotype;
	private String callsign;
	private String imonumber;
	private String course;
	private String turnrate;
	private String status;
	private int offseta;
	private int offsetb;
	private int offsetc;
	private int offsetd;
	private int rs;
	private String fn;
	private String minudiff;
	private String dis;//目标船舶的距离
	private int dwt;
	
	private String reportsrc;
	

	public String getReportsrc() {
    return reportsrc;
  }
  public void setReportsrc(String reportsrc) {
    this.reportsrc = reportsrc;
  }
  public int getDwt() {
		return dwt;
	}
	public void setDwt(int dwt) {
		this.dwt = dwt;
	}
	public String getDis() {
		return dis;
	}
	public void setDis(String dis) {
		this.dis = dis;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Double getLon() {
		return lon;
	}
	public void setLon(Double lon) {
		this.lon = lon;
	}
	public Double getLat() {
		return lat;
	}
	public void setLat(Double lat) {
		this.lat = lat;
	}
	public String getMmsi() {
		return mmsi;
	}
	public void setMmsi(String mmsi) {
		this.mmsi = mmsi;
	}
	public String getHeading() {
		return heading;
	}
	public void setHeading(String heading) {
		this.heading = heading;
	}
	public String getSpeed() {
		return speed;
	}
	public void setSpeed(String speed) {
		this.speed = speed;
	}
	public String getUpdatetime() {
		return updatetime;
	}
	public void setUpdatetime(String updatetime) {
		this.updatetime = updatetime;
	}
	public String getAn() {
		return an;
	}
	public void setAn(String an) {
		this.an = an;
	}
	public String getDn() {
		return dn;
	}
	public void setDn(String dn) {
		this.dn = dn;
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


	public String getWidth() {
		return width;
	}
	public void setWidth(String width) {
		this.width = width;
	}
	public String getLength() {
		return length;
	}
	public void setLength(String length) {
		this.length = length;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getCallsign() {
		return callsign;
	}
	public void setCallsign(String callsign) {
		this.callsign = callsign;
	}
	public String getImonumber() {
		return imonumber;
	}
	public void setImonumber(String imonumber) {
		this.imonumber = imonumber;
	}
	public String getCourse() {
		return course;
	}
	public void setCourse(String course) {
		this.course = course;
	}
	public String getTurnrate() {
		return turnrate;
	}
	public void setTurnrate(String turnrate) {
		this.turnrate = turnrate;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getDestination() {
		return destination;
	}
	public void setDestination(String destination) {
		this.destination = destination;
	}
	public int getOffseta() {
		return offseta;
	}
	public void setOffseta(int offseta) {
		this.offseta = offseta;
	}
	public int getOffsetb() {
		return offsetb;
	}
	public void setOffsetb(int offsetb) {
		this.offsetb = offsetb;
	}
	public int getOffsetc() {
		return offsetc;
	}
	public void setOffsetc(int offsetc) {
		this.offsetc = offsetc;
	}
	public int getOffsetd() {
		return offsetd;
	}
	public void setOffsetd(int offsetd) {
		this.offsetd = offsetd;
	}
	public int getRs() {
		return rs;
	}
	public void setRs(int rs) {
		this.rs = rs;
	}
	public String getMinotype() {
		return minotype;
	}
	public void setMinotype(String minotype) {
		this.minotype = minotype;
	}
	public String getFn() {
		return fn;
	}
	public void setFn(String fn) {
		this.fn = fn;
	}
	public String getMinudiff() {
		return minudiff;
	}
	public void setMinudiff(String minudiff) {
		this.minudiff = minudiff;
	}

}
