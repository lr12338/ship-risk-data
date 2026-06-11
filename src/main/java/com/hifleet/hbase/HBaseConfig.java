package com.hifleet.hbase;

/**
 * 类说明:
 * @author yang chun
 * @创建时间：2019年6月18日 下午1:02:57
 * 
 */

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class HBaseConfig {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	private static Configuration conf = HBaseConfiguration.create();
	private static ExecutorService pool = Executors.newScheduledThreadPool(200); // 设置连接池

	private static String ganos_address="hb-2ze7re5fou1zr2402-master1-001.ganos.rds.aliyuncs.com:2181,hb-2ze7re5fou1zr2402-master2-001.ganos.rds.aliyuncs.com:2181,hb-2ze7re5fou1zr2402-master3-001.ganos.rds.aliyuncs.com:2181";
	private static HBaseConfig instance;
	
	public static HBaseConfig getInstance(){
		HBaseConfig inst = instance;
		if(inst==null){
			synchronized(HBaseConfig.class){
				inst = instance;
				if(null == inst){
					inst = new HBaseConfig();
					instance=inst;
				}
			}
		}
		return inst;
	}
	
	
	 private Connection connection = null;
	    private Admin admin = null;
	    
	    private void init(){
	        if(connection == null){
	            try {
	            	//System.out.println(ganos_address);
	            	conf.set(HConstants.ZOOKEEPER_QUORUM, ganos_address);
	                connection = ConnectionFactory.createConnection(conf,pool);
	                admin = connection.getAdmin();
	            } catch (IOException e) {
	                logger.error("HBaseConfig实例初始化失败！错误信息为：" + e.getMessage(), e);
	            }
	        }
	    }
	    
	    public Table getTable(String tablename) throws IOException {
	    	this.init();
	        TableName name=TableName.valueOf(tablename);
	        Table table = connection.getTable(name);
	        return table;
	    }
	    
	    public Admin getAdmin(){
	    	this.init();
	        return this.admin;
	    }
}
