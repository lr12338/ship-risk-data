package com.hifleet.analysis.portcall.consumer;

import com.hifleet.analysis.portcall.model.AisPoint;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class AisMessageParserTest {

    @Test
    public void shouldParsePreferredBusinessPayload() {
        AisMessageParser parser = new AisMessageParser();

        AisPoint point = parser.parse(
            "413000001,2026-03-21T00:00:00Z,2026-03-21T00:00:05Z,121.50,31.20,"
                + "7.5,90.0,88.0,5,source-a,msg-1,8.2,2026-03-22T12:00:00Z,"
                + "CNSHA,TEST SHIP,ABC123,IMO1234567,180.0,30.0,1"
        );

        Assert.assertNotNull(point);
        Assert.assertEquals(Long.valueOf(413000001L), point.getMmsi());
        Assert.assertEquals(Integer.valueOf(5), point.getNavigationStatus());
        Assert.assertEquals("source-a", point.getDataSourceId());
        Assert.assertEquals("msg-1", point.getMessageId());
        Assert.assertEquals(Double.valueOf(8.2), point.getDraught());
        Assert.assertEquals(Instant.parse("2026-03-22T12:00:00Z"), point.getEta());
        Assert.assertEquals("CNSHA", point.getDestination());
        Assert.assertEquals("TEST SHIP", point.getShipName());
        Assert.assertEquals("ABC123", point.getCallSign());
        Assert.assertEquals("IMO1234567", point.getImoNumber());
        Assert.assertEquals(Double.valueOf(180.0), point.getShipLength());
        Assert.assertEquals(Double.valueOf(30.0), point.getShipWidth());
        Assert.assertEquals("1", point.getDataState());
    }

    @Test
    public void shouldKeepLegacyTenColumnPayloadCompatible() {
        AisMessageParser parser = new AisMessageParser();

        AisPoint point = parser.parse(
            "413000001,2026-03-21T00:00:00Z,2026-03-21T00:00:05Z,121.50,31.20,"
                + "7.5,90.0,88.0,source-a,msg-1"
        );

        Assert.assertNotNull(point);
        Assert.assertEquals("source-a", point.getDataSourceId());
        Assert.assertEquals("msg-1", point.getMessageId());
        Assert.assertNull(point.getNavigationStatus());
    }

    @Test
    public void shouldParseFlinkedAisMessageStructJsonObject() {
        AisMessageParser parser = new AisMessageParser();

        AisPoint point = parser.parse(
            "{\"mmsi\":413000001,\"messageid\":1,\"status\":\"Under way\",\"rot\":0.0,"
                + "\"speed\":0.2,\"lon\":121.50,\"lat\":31.20,\"course\":90.0,"
                + "\"heading\":88.0,\"imonumber\":1234567,\"callsign\":\"ABC123\","
                + "\"name\":\"TEST SHIP\",\"eta\":\"2026-03-22 12:00:00\","
                + "\"width\":30,\"length\":180,\"draught\":8.2,\"destination\":\"CNSHA\","
                + "\"sourceid\":3,\"ais_shiptype\":70,\"updatetime\":\"2026-03-21 00:00:00\","
                + "\"servertime\":1774051205000,\"data_state\":\"normal\",\"topic\":\"AISTOPIC\"}"
        );

        Assert.assertNotNull(point);
        Assert.assertEquals(Long.valueOf(413000001L), point.getMmsi());
        Assert.assertEquals(Instant.parse("2026-03-20T16:00:00Z"), point.getUpdateTime());
        Assert.assertEquals(Instant.parse("2026-03-21T00:00:05Z"), point.getServerReceiveTime());
        Assert.assertEquals("normal", point.getDataState());
        Assert.assertEquals("TEST SHIP", point.getShipName());
        Assert.assertEquals("1234567", point.getImoNumber());
        Assert.assertEquals(Integer.valueOf(70), point.getShipType());
    }

    @Test
    public void shouldParseLiveRecentShipsJsonObjectBatch() {
        AisMessageParser parser = new AisMessageParser();

        List<AisPoint> points = parser.parseMany(
            "[{\"mmsi\":413000001,\"messageid\":1,\"status\":\"5\",\"rot\":0.0,"
                + "\"speed\":7.5,\"lon\":121.50,\"lat\":31.20,\"course\":90.0,"
                + "\"heading\":88.0,\"imonumber\":1234567,\"callsign\":\"ABC123\","
                + "\"name\":\"TEST SHIP\",\"eta\":\"2026-03-22 12:00:00\","
                + "\"type\":\"Cargo\",\"minotype\":\"\",\"width\":30,\"length\":180,"
                + "\"draught\":8.2,\"destination\":\"CNSHA\",\"sourceid\":3,"
                + "\"data_state\":1,"
                + "\"updatetime\":\"2026-03-21 00:00:00\","
                + "\"staticInfoUpdatetime\":\"2026-03-20 23:00:00\","
                + "\"ais_shiptype\":70},"
                + "{\"mmsi\":413000002,\"messageid\":2,\"speed\":6.5,\"lon\":121.51,"
                + "\"lat\":31.21,\"course\":91.0,\"heading\":89.0,\"imonumber\":0,"
                + "\"name\":\"TEST SHIP 2\",\"destination\":\"CNSHA\",\"sourceid\":3,"
                + "\"updatetime\":\"2026-03-21 00:01:00\"}]"
        );

        Assert.assertEquals(2, points.size());
        Assert.assertEquals(Long.valueOf(413000001L), points.get(0).getMmsi());
        Assert.assertEquals(Instant.parse("2026-03-20T16:00:00Z"), points.get(0).getUpdateTime());
        Assert.assertEquals(Integer.valueOf(5), points.get(0).getNavigationStatus());
        Assert.assertEquals("3", points.get(0).getDataSourceId());
        Assert.assertEquals("1", points.get(0).getMessageId());
        Assert.assertEquals("TEST SHIP", points.get(0).getShipName());
        Assert.assertEquals("1234567", points.get(0).getImoNumber());
        Assert.assertEquals(Integer.valueOf(70), points.get(0).getShipType());
        Assert.assertEquals(Double.valueOf(180.0), points.get(0).getShipLength());
        Assert.assertEquals(Double.valueOf(30.0), points.get(0).getShipWidth());
        Assert.assertEquals("1", points.get(0).getDataState());
        Assert.assertNull(points.get(1).getImoNumber());
    }

    @Test
    public void shouldParseSouthChinaSeaKafkaOutputBatch() {
        AisMessageParser parser = new AisMessageParser();

        List<AisPoint> points = parser.parseMany(
            "[\"413000001;2026-03-21 00:00:00;121.50;31.20;90.0;7.5;3;88.0;0.0;1;Under way;"
                + "2026-03-20 23:00:00;2026-03-22 12:00:00;CNSHA;8.2;TEST SHIP;1234567;ABC123;"
                + "Cargo;;180;30;0;0;0;0;China;70\","
                + "\"413000002;2026-03-21 00:01:00;121.51;31.21;91.0;6.5;3;89.0;0.0;2;Under way;"
                + "2026-03-20 23:00:00;;CNSHA;7.8;TEST SHIP 2;0;DEF456;"
                + "Cargo;;190;32;0;0;0;0;China;70\"]"
        );

        Assert.assertEquals(2, points.size());
        Assert.assertEquals(Long.valueOf(413000001L), points.get(0).getMmsi());
        Assert.assertEquals(Instant.parse("2026-03-20T16:00:00Z"), points.get(0).getUpdateTime());
        Assert.assertEquals("3", points.get(0).getDataSourceId());
        Assert.assertEquals("1", points.get(0).getMessageId());
        Assert.assertEquals("TEST SHIP", points.get(0).getShipName());
        Assert.assertEquals("1234567", points.get(0).getImoNumber());
        Assert.assertEquals(Double.valueOf(180.0), points.get(0).getShipLength());
        Assert.assertEquals(Double.valueOf(30.0), points.get(0).getShipWidth());
        Assert.assertNull(points.get(1).getImoNumber());
    }

    @Test
    public void shouldParseNaiveSqlTimestampAsBeijingTime() {
        AisMessageParser parser = new AisMessageParser();

        AisPoint point = parser.parse("{\"mmsi\":244150076,\"updatetime\":\"2026-07-07 19:10:51\","
            + "\"lon\":113.1,\"lat\":22.2,\"speed\":1.0,\"course\":90.0,\"sourceid\":3}");

        Assert.assertNotNull(point);
        Assert.assertEquals(Instant.parse("2026-07-07T11:10:51Z"), point.getUpdateTime());
    }

    @Test
    public void shouldKeepTimestampWithZoneUnchanged() {
        AisMessageParser parser = new AisMessageParser();

        AisPoint utc = parser.parse("{\"mmsi\":244150076,\"updatetime\":\"2026-07-07T11:10:51Z\","
            + "\"lon\":113.1,\"lat\":22.2,\"speed\":1.0,\"course\":90.0,\"sourceid\":3}");
        AisPoint beijing = parser.parse("{\"mmsi\":244150076,\"updatetime\":\"2026-07-07T19:10:51+08:00\","
            + "\"lon\":113.1,\"lat\":22.2,\"speed\":1.0,\"course\":90.0,\"sourceid\":3}");

        Assert.assertNotNull(utc);
        Assert.assertNotNull(beijing);
        Assert.assertEquals(Instant.parse("2026-07-07T11:10:51Z"), utc.getUpdateTime());
        Assert.assertEquals(Instant.parse("2026-07-07T11:10:51Z"), beijing.getUpdateTime());
    }
}
