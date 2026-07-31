-- 液货船/集装箱船测试来源与名单预检。
-- 全部为只读 SQL；事件库和 AIS 库必须使用各自的只读/运行连接。

-- 1. 在事件数据库 dataanalysis 执行：五个来源都必须大于0。
SELECT 'zones_with_port_id' source_name, COUNT(*) row_count
FROM dataanalysis.zones_with_port_id
WHERE is_delete=0 AND bound IS NOT NULL
UNION ALL
SELECT 'portsboundary', COUNT(*)
FROM dataanalysis.portsboundary WHERE bound IS NOT NULL
UNION ALL
SELECT 'port_anchorages', COUNT(*)
FROM dataanalysis.port_anchorages WHERE bound IS NOT NULL
UNION ALL
SELECT 'berth_sts_with_port_id', COUNT(*)
FROM dataanalysis.berth_sts_with_port_id WHERE bound IS NOT NULL
UNION ALL
SELECT 'berth_with_port_id', COUNT(*)
FROM dataanalysis.berth_with_port_id WHERE bound IS NOT NULL;

-- 2. 在 AIS 只读连接执行：两个来源都必须大于0。
SELECT 'anchor_boundary' source_name, COUNT(*) row_count
FROM ais41.anchor_boundary
WHERE bound IS NOT NULL
UNION ALL
SELECT 'alertzones_anchor', COUNT(*)
FROM ais41.alertzones
WHERE bound IS NOT NULL
  AND (LOWER(name) LIKE '%anchor%'
       OR name LIKE '%锚地%'
       OR name LIKE '%锚泊%');

-- 3. 在事件数据库执行：必须为 total=200、unique_mmsi=200、seq=1..200、
-- containers=100、liquid_cargo=100。
SET @list_batch='liquid-container-2026h1-v1';

SELECT COUNT(*) total,
       COUNT(DISTINCT mmsi) unique_mmsi,
       MIN(seq_no) min_seq,
       MAX(seq_no) max_seq,
       SUM(standard_ship_type='CONTAINER') containers,
       SUM(standard_ship_type LIKE 'TANKER_%') liquid_cargo
FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship
WHERE list_batch_no=@list_batch;

SELECT standard_ship_type,COUNT(*) row_count
FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship
WHERE list_batch_no=@list_batch
GROUP BY standard_ship_type
ORDER BY standard_ship_type;

-- 必须为0：奇数应为液货船，偶数应为集装箱船。
SELECT COUNT(*) invalid_alternation
FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship
WHERE list_batch_no=@list_batch
  AND (
    (MOD(seq_no,2)=1 AND standard_ship_type NOT LIKE 'TANKER_%')
    OR
    (MOD(seq_no,2)=0 AND standard_ship_type<>'CONTAINER')
  );

-- 4. 复核表必须存在；只检查结构，不插入测试数据。
SHOW CREATE TABLE dataanalysis.ship_behavior_event_degraded_candidate;
