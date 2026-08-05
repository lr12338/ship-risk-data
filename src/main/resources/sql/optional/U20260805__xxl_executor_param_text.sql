-- Replace 512 with the verified original length before executing this rollback.
ALTER TABLE xxl_job.xxl_job_info MODIFY COLUMN executor_param VARCHAR(512) NULL COMMENT '执行器任务参数';
