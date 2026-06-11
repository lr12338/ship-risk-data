package com.hifleet.configs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class HiFleetHBaseConfiguration {

    @Value("${hifleet.aliyun.hbase.zkserver}")
    private String zkServer;

    @Value("${hifleet.aliyun.hbase.tablename}")
    private String tableName;

    @Bean(name = "HiFleetHbaseConnection")
    public Connection getHbaseConnection() throws Exception {
        Configuration config = HBaseConfiguration.create();
        config.set(HConstants.ZOOKEEPER_QUORUM, zkServer);
        return ConnectionFactory.createConnection(config);
    }

    @Bean
    public Admin getHbaseAdmin(@Qualifier("HiFleetHbaseConnection") Connection connection) throws Exception {
        return connection.getAdmin();
    }

    @Bean(name = "HiFleetHbaseTable")
    public Table getTable(@Qualifier("HiFleetHbaseConnection") Connection connection) throws Exception {
        return connection.getTable(TableName.valueOf(tableName));
    }
}
