CREATE TABLE IF NOT EXISTS `sys_job` (
    `id`              VARCHAR(32)  NOT NULL,
    `job_name`        VARCHAR(100) NOT NULL COMMENT '任务名称',
    `job_group`       VARCHAR(50)  DEFAULT 'DEFAULT' COMMENT '任务分组',
    `invoke_target`   VARCHAR(200) NOT NULL COMMENT '调用目标（Bean名称.方法名）',
    `cron_expression` VARCHAR(100) NOT NULL COMMENT 'cron表达式',
    `status`          TINYINT      DEFAULT 0 COMMENT '状态（0正常 1暂停）',
    `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务';

CREATE TABLE IF NOT EXISTS `sys_job_log` (
    `id`           VARCHAR(32)  NOT NULL,
    `job_id`       VARCHAR(32)  NOT NULL COMMENT '任务ID',
    `job_name`     VARCHAR(100) DEFAULT NULL,
    `invoke_target` VARCHAR(200) DEFAULT NULL,
    `message`      VARCHAR(500) DEFAULT NULL COMMENT '执行结果消息',
    `status`       TINYINT      DEFAULT 0 COMMENT '0成功 1失败',
    `exception`    TEXT         DEFAULT NULL,
    `start_time`   DATETIME     DEFAULT NULL,
    `end_time`     DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务日志';

INSERT IGNORE INTO sys_job (id, job_name, job_group, invoke_target, cron_expression, status, remark)
VALUES ('1', '清理过期消息', 'SYSTEM', 'notificationCleanTask.execute', '0 0 2 * * ?', 0, '每天凌晨2点清理30天前的消息通知');
