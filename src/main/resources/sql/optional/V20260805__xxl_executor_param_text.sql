-- Optional only; Profile references remain the primary solution.
-- Verify the current definition before considering this migration.
ALTER TABLE xxl_job.xxl_job_info MODIFY COLUMN executor_param TEXT NULL COMMENT '执行器任务参数';
