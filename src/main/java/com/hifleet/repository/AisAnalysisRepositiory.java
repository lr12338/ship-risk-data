package com.hifleet.repository;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AisAnalysisRepositiory {

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;



    @Autowired
    @Qualifier("aid41JdbcTemplate")
    private JdbcTemplate ais41JdbcTemplate;

    public List<Integer> getReadyMmsi(){


        String sql="select DISTINCT(mmsi) from dataanalysis.ais_analysis_status;";
        return newDataanalysisJdbcTemplate.query(sql,new RowMapper<Integer>() {
            @Override
            public Integer mapRow(ResultSet rs, int rowNum) throws SQLException {

               int mmsi =rs.getInt("mmsi");

                return mmsi;
            }
        });
    }





    public void insertPortofCall(int  mmsi){
        String sql="insert into dataanalysis.ais_analysis_status (mmsi ) values (?)";

        newDataanalysisJdbcTemplate.update(sql, new PreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement stmt) throws SQLException {


                stmt.setInt(1,mmsi);

            }
        });
    }

    public void insertAisGap(int  mmsi,String startday,String endday){
        String sql="insert into dataanalysis.ais_analysis_gap (mmsi,startday,endday ) values (?,?,?)";

        newDataanalysisJdbcTemplate.update(sql, new PreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement stmt) throws SQLException {


                stmt.setInt(1,mmsi);
                stmt.setString(2,startday);
                stmt.setString(3,endday);

            }
        });
    }

    public void removeAisAnalysisStatus() {

        String sql = "delete from dataanalysis.ais_analysis_status";
        newDataanalysisJdbcTemplate.execute(sql);
    }

    public void deleteAisAnalysisResultByMmsi(int mmsi) {
        String sql = "delete from dataanalysis.ais_analysis_result where mmsi = ?";
        newDataanalysisJdbcTemplate.update(sql, mmsi);
    }

    public void batchInsertAisAnalysisResult(int mmsi, Map<String, double[]> dailyStats) {
        if (dailyStats == null || dailyStats.isEmpty()) {
            return;
        }
        String sql = "insert into dataanalysis.ais_analysis_result (mmsi, day, distance, hours) values (?, ?, ?, ?)";
        List<Map.Entry<String, double[]>> entries = new ArrayList<>(dailyStats.entrySet());
        newDataanalysisJdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int i) throws SQLException {
                Map.Entry<String, double[]> entry = entries.get(i);
                double[] values = entry.getValue();
                stmt.setInt(1, mmsi);
                stmt.setString(2, entry.getKey());
                stmt.setDouble(3, values[0]);
                stmt.setDouble(4, values[1]);
            }

            @Override
            public int getBatchSize() {
                return entries.size();
            }
        });
    }

    public void deleteAisAnalysisAbnormalByMmsi(int mmsi) {
        String sql = "delete from dataanalysis.ais_analysis_abnormal where mmsi = ?";
        newDataanalysisJdbcTemplate.update(sql, mmsi);
    }

    public void batchInsertAisAnalysisAbnormal(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String sql = "insert into dataanalysis.ais_analysis_abnormal (mmsi, alerttype, updatetime, lon, lat) values (?, ?, ?, ?, ?)";
        newDataanalysisJdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int i) throws SQLException {
                Object[] row = rows.get(i);
                stmt.setInt(1, (Integer) row[0]);
                stmt.setString(2, (String) row[1]);
                stmt.setString(3, (String) row[2]);
                stmt.setDouble(4, (Double) row[3]);
                stmt.setDouble(5, (Double) row[4]);
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

}
