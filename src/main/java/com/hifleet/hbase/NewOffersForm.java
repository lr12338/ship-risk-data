package com.hifleet.hbase;


import com.hifleet.route.beans.NewShipForm;

import java.util.Vector;

public class NewOffersForm {

    private Vector<NewShipForm> offers = new Vector<NewShipForm>();

    // http://jira.hifleet.com/browse/NEWWEBNAPP-1711
    // 查询轨迹的时候显示平均航速
    String speedavg;//平均航速
    private Long maxDiffSeconds;
    // 变量
    private int d_ais_size;

    public int getD_ais_size() {
        return d_ais_size;
    }

    public void setD_ais_size(int d_ais_size) {
        this.d_ais_size = d_ais_size;
    }

    public Long getMaxDiffSeconds() {
        return maxDiffSeconds;
    }

    public void setMaxDiffSeconds(Long maxDiffSeconds) {
        this.maxDiffSeconds = maxDiffSeconds;
    }

    public void setOffers(Vector<NewShipForm> offers) {
        this.offers = offers;
    }

    public String getSpeedavg() {
        return speedavg;
    }

    public void setSpeedavg(String speedavg) {
        this.speedavg = speedavg;
    }

    //
    public Vector<NewShipForm> getOffers(){
        return offers;
    }
    //
    public void addShip(NewShipForm offer){
        this.offers.addElement(offer);
    }

//	private Vector<NewShipForm> offers = new Vector<NewShipForm>();
////
//    public Vector<NewShipForm> getOffers(){
//        return offers;
//    }
////
//    public void addShip(NewShipForm offer){
//        this.offers.addElement(offer);
//    }
}

