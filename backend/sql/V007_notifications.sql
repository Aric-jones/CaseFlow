-- 通知表
CREATE TABLE IF NOT EXISTS `notifications` (
  `id` varchar(64) NOT NULL,
  `user_id` varchar(64) NOT NULL COMMENT '接收人',
  `type` varchar(32) NOT NULL COMMENT '通知类型',
  `title` varchar(200) NOT NULL,
  `content` varchar(500) DEFAULT NULL,
  `link` varchar(500) DEFAULT NULL COMMENT '跳转链接',
  `is_read` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_user_time` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
