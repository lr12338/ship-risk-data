package com.hifleet.repository;


import com.hifleet.beans.ShipForm;
import org.apache.log4j.Logger;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.*;

import org.springframework.stereotype.Repository;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 类说明:
 *
 * @author yang chun
 * @创建时间：2019年1月25日 下午2:17:40
 *
 */
@Repository
public class AIS41GeneralQueryRepository {

	Logger log = Logger.getLogger(AIS41GeneralQueryRepository.class);

	org.slf4j.Logger logger = LoggerFactory.getLogger(AIS41GeneralQueryRepository.class);

	SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

	@Autowired
	@Qualifier("aid41JdbcTemplate")
	private JdbcTemplate ais41JdbcTemplate;





	public List<ShipForm> selectRecentShips() {

		String sql="SELECT mmsi,imonumber,dwt,type FROM ais41.recentships\n" +
				"                 WHERE TYPE!='渔船' AND TYPE!='-' AND TYPE!='游艇' \n" +
				"                   AND TYPE!='工程及服务船' AND TYPE!='客船' AND TYPE!='拖带船' \n" +
				"                    AND TYPE!='拖轮' AND TYPE!='专用船' AND TYPE!='其他' AND TYPE!='未知类型船舶'\n" +
				"                   AND TYPE!='近海作业船' and length>70 and updatetime>'2025-01-01 00:00:00'; ";



		Object[] params = new Object[]{};
		return ais41JdbcTemplate.query(sql, params, new RowMapper<ShipForm>() {
			@Override
			public ShipForm mapRow(ResultSet arg0, int arg1) throws SQLException {
				ShipForm shipForm = new ShipForm();
				int mmsi= arg0.getInt("mmsi");
				shipForm.setMmsi(mmsi+"");
				return shipForm;
			}
		});
	}

	public List<ShipForm> selectRecentShips2() {

//		String sql="SELECT mmsi,imonumber,dwt,type FROM ais41.recentships\n" +
//				"                 WHERE TYPE!='渔船' AND TYPE!='-' AND TYPE!='游艇' \n" +
//				"                   AND TYPE!='工程及服务船' AND TYPE!='客船' AND TYPE!='拖带船' \n" +
//				"                    AND TYPE!='拖轮' AND TYPE!='专用船' AND TYPE!='其他' AND TYPE!='未知类型船舶'\n" +
//				"                   AND TYPE!='近海作业船' and dwt>1000 and updatetime>DATE_SUB(CURDATE(), INTERVAL 1 YEAR); ";

		String sql="SELECT mmsi,imonumber,dwt,type FROM ais41.recentships\n" +
				"                 WHERE TYPE!='渔船' AND TYPE!='-' AND TYPE!='游艇' \n" +
				"                   AND TYPE!='工程及服务船' AND TYPE!='客船' AND TYPE!='拖带船' \n" +
				"                    AND TYPE!='拖轮' AND TYPE!='专用船' AND TYPE!='其他' AND TYPE!='未知类型船舶'\n" +
				"                   AND TYPE!='近海作业船' and length>70 and updatetime>'2025-01-01 00:00:00'; ";
//mmsi<413827957
		Object[] params = new Object[]{};
		return ais41JdbcTemplate.query(sql, params, new RowMapper<ShipForm>() {
			@Override
			public ShipForm mapRow(ResultSet arg0, int arg1) throws SQLException {
				ShipForm shipForm = new ShipForm();
				int mmsi= arg0.getInt("mmsi");
				shipForm.setMmsi(mmsi+"");
				return shipForm;
			}
		});
	}

	public Map<String, String> selectShipTypes(Set<String> mmsis) {
		if (mmsis == null || mmsis.isEmpty()) {
			return Collections.emptyMap();
		}
		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < mmsis.size(); i++) {
			if (i > 0) {
				placeholders.append(',');
			}
			placeholders.append('?');
		}
		String sql = "SELECT mmsi, `type` FROM ais41.recentships WHERE mmsi IN ("
				+ placeholders + ")";
		List<Map.Entry<String, String>> rows = ais41JdbcTemplate.query(sql, mmsis.toArray(),
				(rs, rowNum) -> new java.util.AbstractMap.SimpleEntry<String, String>(
						rs.getString("mmsi"), rs.getString("type")));
		Map<String, String> result = new HashMap<String, String>();
		for (Map.Entry<String, String> row : rows) {
			result.put(row.getKey(), row.getValue());
		}
		return result;
	}

}
