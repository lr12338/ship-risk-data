package com.hifleet.job.services;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.beans.ShipForm;
import com.hifleet.hbase.GetTrajectoryFromHbase;
import com.hifleet.hbase.NewOffersForm;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.AisAnalysisRepositiory;
import com.hifleet.route.beans.NewShipForm;
import com.hifleet.utils.GeometryUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AisAnalysisService {

    /** 航速大于该阈值（节）视为在航，否则不计入航行时间与距离 */
    private static final double SAILING_SPEED_THRESHOLD = 2.0;

    /** 相邻两天末首点间隔小于该值（毫秒）时，按日界 23:59:59 / 00:00:00 拆分航行时间 */
    private static final long CROSS_DAY_GAP_THRESHOLD_MS = 6 * 3600 * 1000L;

    /** 相邻两点时间差超过该值（毫秒）时不参与航行时间与距离计算 */
    private static final long MAX_SEGMENT_GAP_MS = 18 * 3600 * 1000L;

    private static final String ABNORMAL_DATA_URL =
            "https://www.hifleet.com/shipdetail/getAbnormalDataByMmsiAndTime";

    private static final Pattern POINT_PATTERN =
            Pattern.compile("POINT\\s*\\(\\s*([-\\d.]+)\\s+([-\\d.]+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    /** 同一 alerttype 间隔小于该值（毫秒）时只保留第一条 */
    private static final long ABNORMAL_DEDUP_INTERVAL_MS = 3600 * 1000L;

    private static final ZoneId ANALYSIS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 单日航行统计结果
     */
    public static class AisDailySailingResult {
        private LocalDate date;
        /** 航行时间，单位：小时 */
        private double sailingHours;
        /** 航行距离，单位：海里 */
        private double sailingDistanceNm;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public double getSailingHours() {
            return sailingHours;
        }

        public void setSailingHours(double sailingHours) {
            this.sailingHours = sailingHours;
        }

        public double getSailingDistanceNm() {
            return sailingDistanceNm;
        }

        public void setSailingDistanceNm(double sailingDistanceNm) {
            this.sailingDistanceNm = sailingDistanceNm;
        }
    }

    @Autowired
    private AIS41GeneralQueryRepository ais41GeneralQueryRepository;

    @Autowired
    private AisAnalysisRepositiory aisAnalysisRepositiory;

    Logger logger = LoggerFactory.getLogger(AisAnalysisService.class);
    //读取所有需要计算的船舶

    public boolean analysisAbbormalShip(){
        ArrayList<ShipForm> ships = (ArrayList<ShipForm>) ais41GeneralQueryRepository.selectRecentShips();
        String starttime = "2025-06-10 00:00:00";
        String endtime = "2026-06-10 00:00:00";
        ArrayList<Integer> readyMmsi = (ArrayList<Integer>) aisAnalysisRepositiory.getAbnormalReadyMmsi();
        HashMap<Integer, Integer> readMmsiMap = new HashMap<>();
        for(int i=0;i<readyMmsi.size();i++){
            int mmsi = readyMmsi.get(i);
            readMmsiMap.put(mmsi,mmsi);
        }
        for (int i = 0; i < ships.size(); i++) {
            if(readMmsiMap.containsKey(Integer.parseInt(ships.get(i).getMmsi()))){
                continue;
            }
            getAbnormalShip(Integer.parseInt(ships.get(i).getMmsi()), starttime, endtime);
        }
        return true;
    }

    /**
     * 读取船舶异常行为数据并写入 dataanalysis.ais_analysis_abnormal。
     */
    public void getAbnormalShip(int mmsi, String starttime, String endtime) {
        try {
            String url = ABNORMAL_DATA_URL
                    + "?mmsi=" + mmsi
                    + "&starttime=" + URLEncoder.encode(starttime, StandardCharsets.UTF_8.name())
                    + "&endtime=" + URLEncoder.encode(endtime, StandardCharsets.UTF_8.name());

            String responseBody = httpGet(url);
            if (responseBody == null || responseBody.isEmpty()) {
                logger.warn("getAbnormalShip empty response, mmsi={}", mmsi);
                return;
            }

            JSONObject response = JSONObject.parseObject(responseBody);
            JSONArray list = response.getJSONArray("list");
            if (list == null || list.isEmpty()) {
                return;
            }

            List<Object[]> rows = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                JSONObject item = list.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String alerttype = item.getString("alerttype");
                String updatetime = item.getString("triggertime");
                double[] lonLat = parsePointPosition(item.getString("position"));
                if (alerttype == null || updatetime == null || lonLat == null) {
                    continue;
                }
                rows.add(new Object[]{
                        mmsi,
                        alerttype,
                        updatetime,
                        round6(lonLat[0]),
                        round6(lonLat[1])
                });
            }

            if (rows.isEmpty()) {
                return;
            }
            rows = deduplicateAbnormalRows(rows);
            if (rows.isEmpty()) {
                return;
            }
          //  aisAnalysisRepositiory.deleteAisAnalysisAbnormalByMmsi(mmsi);
            aisAnalysisRepositiory.batchInsertAisAnalysisAbnormal(rows);
        } catch (Exception e) {
            logger.error("getAbnormalShip failed, mmsi={}", mmsi, e);
        }
    }

    private String httpGet(String url) throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } finally {
            httpClient.close();
        }
    }

    /** 解析 WKT POINT(lon lat) */
    private double[] parsePointPosition(String position) {
        if (position == null || position.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = POINT_PATTERN.matcher(position.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            return new double[]{
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2))
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double round6(double value) {
        return Math.round(value * 1000000.0) / 1000000.0;
    }

    /**
     * 同一 alerttype 在 1 小时内的重复报警只保留最早一条。
     */
    private List<Object[]> deduplicateAbnormalRows(List<Object[]> rows) {
        rows.sort((a, b) -> {
            Date t1 = parseTime((String) a[2]);
            Date t2 = parseTime((String) b[2]);
            if (t1 == null && t2 == null) {
                return 0;
            }
            if (t1 == null) {
                return 1;
            }
            if (t2 == null) {
                return -1;
            }
            return t1.compareTo(t2);
        });

        List<Object[]> result = new ArrayList<>();
        Map<String, Date> lastKeptByType = new HashMap<>();
        for (Object[] row : rows) {
            String alerttype = (String) row[1];
            Date triggerTime = parseTime((String) row[2]);
            if (triggerTime == null) {
                continue;
            }
            Date lastKept = lastKeptByType.get(alerttype);
            if (lastKept != null
                    && triggerTime.getTime() - lastKept.getTime() < ABNORMAL_DEDUP_INTERVAL_MS) {
                continue;
            }
            result.add(row);
            lastKeptByType.put(alerttype, triggerTime);
        }
        return result;
    }


    public boolean analysisShip(){
        ArrayList<ShipForm> ships = (ArrayList<ShipForm>) ais41GeneralQueryRepository.selectRecentShips();
        ArrayList<Integer> readyMmsi = (ArrayList<Integer>) aisAnalysisRepositiory.getReadyMmsi();
        HashMap<Integer, Integer> readMmsiMap = new HashMap<>();
        for(int i=0;i<readyMmsi.size();i++){
            int mmsi = readyMmsi.get(i);
            readMmsiMap.put(mmsi,mmsi);
        }
        GetTrajectoryFromHbase getTrajectoryFromHbase = new GetTrajectoryFromHbase();
        for(int i=0;i<ships.size();i++){


            String mmsi = ships.get(i).getMmsi();
//            if(!mmsi.equals("253242499")){
//                continue;
//            }

            if(readMmsiMap.containsKey(Integer.parseInt(mmsi))){

                continue;
            }



           ArrayList<LocalDate> days =generateDateList();

            for(int j=0;j<days.size()-1;j++){

                String starttime =days.get(j).toString() +" 00:00:00";//"2026-05-25 00:00:00";//"2025-06-10 00:00:00";//
                String endtime = days.get(j+1).toString()+" 00:00:00";;//"2026-05-29 00:00:00";//"2026-06-10 00:00:00";//
                NewOffersForm offersForm = getTrajectoryFromHbase.getVoffersFromHbase(mmsi,starttime,endtime,16);

                if(offersForm.getOffers().size()>10){
                    Map<LocalDate, AisDailySailingResult> dailyStats =
                            AnalysisAisDistanceAndTime(offersForm.getOffers());
                    saveAisAnalysisResult(Integer.parseInt(mmsi), dailyStats);
                }

            getAbnormalShip(Integer.parseInt(mmsi),starttime,endtime);

            }
            aisAnalysisRepositiory.insertPortofCall(Integer.parseInt(ships.get(i).getMmsi()));
        }
        //全部生成完 清空状态
        aisAnalysisRepositiory.removeAisAnalysisStatus();

        return true;
    }



    public Map<LocalDate, AisDailySailingResult> AnalysisAisDistanceAndTime(Vector<NewShipForm> offers) {
        Map<LocalDate, AisDailySailingResult> result = new TreeMap<>();
        if (offers == null || offers.size() < 2) {
            return result;
        }

        List<NewShipForm> sorted = new ArrayList<>(offers);
        Collections.sort(sorted);

        for (int i = 0; i < sorted.size() - 1; i++) {
            NewShipForm prev = sorted.get(i);
            NewShipForm next = sorted.get(i + 1);

           // logger.info(isBothNotSailing(prev, next)+",==,"+prev.getTi()+","+prev.getSp()+","+next.getSp()+","+prev.getTi());

//            if (isBothNotSailing(prev, next)) {
//                continue;
//            }

            if(!isSailing(prev) || !isSailing(next)){
                continue;
            }

            Date startTime = parseTime(prev.getTi());
            Date endTime = parseTime(next.getTi());
            if (startTime == null || endTime == null || !endTime.after(startTime)) {
                continue;
            }

            Double lon1 = parseCoordinate(prev.getLo());
            Double lat1 = parseCoordinate(prev.getLa());
            Double lon2 = parseCoordinate(next.getLo());
            Double lat2 = parseCoordinate(next.getLa());
            if (lon1 == null || lat1 == null || lon2 == null || lat2 == null) {
                continue;
            }

            double distanceNm = GeometryUtils.getNauticalMileDistance(lon1, lat1, lon2, lat2);
            long gapMs = endTime.getTime() - startTime.getTime();
            //logger.info(prev.getTi()+""+distanceNm+"");
            //logger.info(gapMs/(1000*60)+"");
            if (gapMs > MAX_SEGMENT_GAP_MS) {

                aisAnalysisRepositiory.insertAisGap(Integer.parseInt(prev.getM()),prev.getTi(),next.getTi());
                continue;
            }
            boolean dayBoundaryBridge = isSailing(prev) && isSailing(next)
                    && gapMs < CROSS_DAY_GAP_THRESHOLD_MS
                    && !toLocalDate(startTime).equals(toLocalDate(endTime))
                    && isLastPointOfDay(sorted, i)
                    && isFirstPointOfDay(sorted, i + 1);

            addSailingSegment(result, startTime, endTime, distanceNm, dayBoundaryBridge);
        }
        return result;
    }

    private void saveAisAnalysisResult(int mmsi, Map<LocalDate, AisDailySailingResult> dailyStats) {
        if (dailyStats == null || dailyStats.isEmpty()) {
            return;
        }
       // aisAnalysisRepositiory.deleteAisAnalysisResultByMmsi(mmsi);
        Map<String, double[]> rows = new TreeMap<>();
        for (Map.Entry<LocalDate, AisDailySailingResult> entry : dailyStats.entrySet()) {
            AisDailySailingResult stat = entry.getValue();
            rows.put(entry.getKey().toString(), new double[]{
                    round1(stat.getSailingDistanceNm()),
                    round1(stat.getSailingHours())
            });
        }
        aisAnalysisRepositiory.batchInsertAisAnalysisResult(mmsi, rows);
    }




    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private boolean isSailing(NewShipForm point) {
        Double speed = parseCoordinate(point.getSp());
        return speed != null && speed > SAILING_SPEED_THRESHOLD;
    }

    /** 相邻两点航速均不超过阈值时，该航段不参与航行时间与距离计算 */
    private boolean isBothNotSailing(NewShipForm prev, NewShipForm next) {

        return !isSailing(prev) && !isSailing(next);
    }

    private void addSailingSegment(Map<LocalDate, AisDailySailingResult> result,
                                   Date startTime, Date endTime, double distanceNm,
                                   boolean dayBoundaryBridge) {
        long totalMs = endTime.getTime() - startTime.getTime();
        if (totalMs <= 0) {
            return;
        }

        LocalDate startDate = toLocalDate(startTime);
        LocalDate endDate = toLocalDate(endTime);
        if (startDate.equals(endDate)) {
            accumulate(result, startDate, totalMs / 3600000.0, distanceNm);
            return;
        }

        if (dayBoundaryBridge) {
            // 前一天末点 -> 后一天首点：前一日计到 23:59:59，后一日从 00:00:00 起算
            Date dayEnd = endOfDay(startDate);
            Date dayStart = startOfDay(endDate);
            long day1Ms = dayEnd.getTime() - startTime.getTime();
            long day2Ms = endTime.getTime() - dayStart.getTime();
            if (day1Ms > 0) {
                accumulate(result, startDate, day1Ms / 3600000.0, distanceNm * day1Ms / totalMs);
            }
            if (day2Ms > 0) {
                accumulate(result, endDate, day2Ms / 3600000.0, distanceNm * day2Ms / totalMs);
            }
            return;
        }

        // 普通跨天航段：按时间比例拆分
        Date cursor = startTime;
        while (cursor.before(endTime)) {
            LocalDate day = toLocalDate(cursor);
            Date nextDayStart = startOfDay(day.plusDays(1));
            Date segmentEnd = nextDayStart.before(endTime) ? nextDayStart : endTime;
            long segmentMs = segmentEnd.getTime() - cursor.getTime();
            double ratio = (double) segmentMs / totalMs;
            accumulate(result, day, segmentMs / 3600000.0, distanceNm * ratio);
            cursor = segmentEnd;
        }
    }

    private boolean isLastPointOfDay(List<NewShipForm> sorted, int index) {
        LocalDate day = toLocalDate(parseTime(sorted.get(index).getTi()));
        if (day == null) {
            return false;
        }
        for (int j = index + 1; j < sorted.size(); j++) {
            LocalDate nextDay = toLocalDate(parseTime(sorted.get(j).getTi()));
            if (nextDay == null) {
                continue;
            }
            if (nextDay.equals(day)) {
                return false;
            }
            return nextDay.isAfter(day);
        }
        return true;
    }

    private boolean isFirstPointOfDay(List<NewShipForm> sorted, int index) {
        LocalDate day = toLocalDate(parseTime(sorted.get(index).getTi()));
        if (day == null) {
            return false;
        }
        for (int j = index - 1; j >= 0; j--) {
            LocalDate prevDay = toLocalDate(parseTime(sorted.get(j).getTi()));
            if (prevDay == null) {
                continue;
            }
            if (prevDay.equals(day)) {
                return false;
            }
            return prevDay.isBefore(day);
        }
        return true;
    }

    private Date startOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay(ANALYSIS_ZONE).toInstant());
    }

    private Date endOfDay(LocalDate date) {
        return Date.from(date.atTime(23, 59, 59).atZone(ANALYSIS_ZONE).toInstant());
    }

    private void accumulate(Map<LocalDate, AisDailySailingResult> result,
                            LocalDate date, double hours, double distanceNm) {
        AisDailySailingResult daily = result.computeIfAbsent(date, d -> {
            AisDailySailingResult stat = new AisDailySailingResult();
            stat.setDate(d);
            return stat;
        });
        daily.setSailingHours(daily.getSailingHours() + hours);
        daily.setSailingDistanceNm(daily.getSailingDistanceNm() + distanceNm);
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ANALYSIS_ZONE).toLocalDate();
    }

    private Date parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        String normalized = timeStr.trim();
        if (normalized.length() == 16) {
            normalized += ":00";
        }
        try {
            return TIME_FORMAT.parse(normalized);
        } catch (ParseException e) {
            return null;
        }
    }

    private Double parseCoordinate(String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }


    public static ArrayList<LocalDate> generateDateList() {
        LocalDate endDate = LocalDate.of(2026, 6, 10);          // 结束日期：今天
        LocalDate startDate = endDate.minusYears(1); // 开始日期：N年前的今天

        ArrayList<LocalDate> result = new ArrayList<>();

        // 从开始日期开始，步长 intervalDays 天
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            result.add(current);
            current = current.plusDays(15);
        }

        return result;
    }
}
