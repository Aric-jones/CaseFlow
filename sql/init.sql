-- CaseFlow 测试用例管理平台 - 数据库初始化脚本
-- 默认数据(管理员/项目/目录/自定义属性)由应用启动时 DataInitializer 自动创建
CREATE DATABASE IF NOT EXISTS caseflow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE caseflow;

-- ========================================
-- 用户表 (不加审计字段)
-- ========================================
CREATE TABLE users
(
    id           VARCHAR(32)  NOT NULL COMMENT '用户ID(UUID)',
    username     VARCHAR(50)  NOT NULL COMMENT '登录用户名(唯一)',
    display_name VARCHAR(100) NOT NULL COMMENT '显示名称',
    password     VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    role         ENUM ('SUPER_ADMIN','ADMIN','MEMBER')
                              NOT NULL DEFAULT 'MEMBER' COMMENT '角色: 超管/管理员/成员',
    identity     ENUM ('TEST','DEV','PRODUCT')
                              NOT NULL DEFAULT 'TEST' COMMENT '身份: 测试/研发/产品',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1=启用 0=禁用',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ========================================
-- 项目空间表
-- ========================================
CREATE TABLE projects
(
    id              VARCHAR(32)  NOT NULL COMMENT '项目ID(UUID)',
    name            VARCHAR(100) NOT NULL COMMENT '项目名称',
    description     VARCHAR(500)          DEFAULT '' COMMENT '项目描述',
    created_by      VARCHAR(32)  NOT NULL COMMENT '创建人编号',
    created_by_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '创建人',
    updated_by      VARCHAR(32)           DEFAULT NULL COMMENT '修改人编号',
    updated_by_name VARCHAR(100)          DEFAULT NULL COMMENT '修改人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='项目空间表';

-- ========================================
-- 项目成员关联表 (关联表不加审计字段)
-- ========================================
CREATE TABLE project_members
(
    id         VARCHAR(32) NOT NULL COMMENT '记录ID(UUID)',
    project_id VARCHAR(32) NOT NULL COMMENT '项目ID',
    user_id    VARCHAR(32) NOT NULL COMMENT '用户ID',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_user (project_id, user_id),
    INDEX idx_project (project_id),
    INDEX idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='项目成员关联表';

-- ========================================
-- 目录树 (用例目录 & 测试计划目录 共用)
-- ========================================
CREATE TABLE directories
(
    id              VARCHAR(32)  NOT NULL COMMENT '目录ID(UUID)',
    name            VARCHAR(255) NOT NULL COMMENT '目录名称',
    parent_id       VARCHAR(32)           DEFAULT NULL COMMENT '父目录ID, NULL表示顶级目录',
    project_id      VARCHAR(32)  NOT NULL COMMENT '所属项目ID',
    dir_type        ENUM ('CASE','TEST_PLAN')
                                 NOT NULL COMMENT '目录类型: 用例/测试计划',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    created_by      VARCHAR(32)           DEFAULT NULL COMMENT '创建人编号',
    created_by_name VARCHAR(100)          DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(32)           DEFAULT NULL COMMENT '修改人编号',
    updated_by_name VARCHAR(100)          DEFAULT NULL COMMENT '修改人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_project_type (project_id, dir_type),
    INDEX idx_parent (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='目录树表(用例/测试计划共用)';

-- ========================================
-- 用例集
-- ========================================
CREATE TABLE case_sets
(
    id               VARCHAR(32)  NOT NULL COMMENT '用例集ID(UUID)',
    name             VARCHAR(255) NOT NULL COMMENT '用例集名称',
    directory_id     VARCHAR(32)  NOT NULL COMMENT '所属目录ID',
    project_id       VARCHAR(32)  NOT NULL COMMENT '所属项目ID',
    status           ENUM ('WRITING','PENDING_REVIEW','NO_REVIEW','APPROVED')
                                  NOT NULL DEFAULT 'WRITING' COMMENT '状态: 编写中/待评审/无需评审/审核通过',
    requirement_link VARCHAR(500)          DEFAULT '' COMMENT '关联需求链接',
    case_count       INT          NOT NULL DEFAULT 0 COMMENT '用例数量(统计缓存)',
    data_version     INT          NOT NULL DEFAULT 0 COMMENT '数据版本号(乐观锁)',
    created_by       VARCHAR(32)  NOT NULL COMMENT '创建人编号',
    created_by_name  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '创建人',
    updated_by       VARCHAR(32)           DEFAULT NULL COMMENT '修改人编号',
    updated_by_name  VARCHAR(100)          DEFAULT NULL COMMENT '修改人',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=正常 1=已删除',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_directory (directory_id),
    INDEX idx_project_deleted (project_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用例集表';

-- ========================================
-- 思维导图节点
-- ========================================
CREATE TABLE mind_nodes
(
    id          VARCHAR(64)  NOT NULL COMMENT '节点ID(前端生成)',
    case_set_id VARCHAR(32)  NOT NULL COMMENT '所属用例集ID',
    parent_id   VARCHAR(64)           DEFAULT NULL COMMENT '父节点ID, NULL表示根节点',
    text        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '节点文本内容',
    node_type   ENUM ('TITLE','PRECONDITION','STEP','EXPECTED')
                                      DEFAULT NULL COMMENT '节点类型: 用例标题/前置条件/步骤/预期结果, 可为空',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '同级排序序号',
    is_root     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否根节点: 1=是 0=否, 每个用例集仅一个',
    properties      JSON                  DEFAULT NULL COMMENT '动态属性JSON, 键名=属性名 值=属性值或数组',
    updated_by      VARCHAR(32)           DEFAULT NULL COMMENT '最后修改人ID',
    updated_by_name VARCHAR(100)          DEFAULT NULL COMMENT '最后修改人名称',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_case_set (case_set_id),
    INDEX idx_parent (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='思维导图节点表';

-- ========================================
-- 评论 (支持回复, parent_id指向父评论)
-- ========================================
CREATE TABLE comments
(
    id           VARCHAR(32)  NOT NULL COMMENT '评论ID(UUID)',
    node_id      VARCHAR(64)  NOT NULL COMMENT '关联节点ID',
    case_set_id  VARCHAR(32)  NOT NULL COMMENT '关联用例集ID',
    parent_id    VARCHAR(32)           DEFAULT NULL COMMENT '父评论ID(回复时填写)',
    user_id      VARCHAR(32)  NOT NULL COMMENT '评论人用户ID',
    display_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '评论人显示名称(冗余)',
    content      TEXT         NOT NULL COMMENT '评论内容',
    resolved     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已解决: 0=未解决 1=已解决(仅根评论有效)',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_node (node_id),
    INDEX idx_case_set (case_set_id),
    INDEX idx_parent (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='评论表(支持一级回复)';

-- ========================================
-- 用例集历史版本 (15分钟间隔快照)
-- ========================================
CREATE TABLE case_history
(
    id              VARCHAR(32)  NOT NULL COMMENT '历史ID(UUID)',
    case_set_id     VARCHAR(32)  NOT NULL COMMENT '用例集ID',
    snapshot        JSON         NOT NULL COMMENT '节点树完整快照(JSON)',
    created_by      VARCHAR(32)  NOT NULL COMMENT '操作人编号',
    created_by_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '操作人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    PRIMARY KEY (id),
    INDEX idx_case_set_time (case_set_id, created_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用例集历史版本表';

-- ========================================
-- 评审分配
-- ========================================
CREATE TABLE review_assignments
(
    id            VARCHAR(32)  NOT NULL COMMENT '评审ID(UUID)',
    case_set_id   VARCHAR(32)  NOT NULL COMMENT '用例集ID',
    reviewer_id   VARCHAR(32)  NOT NULL COMMENT '评审人用户ID',
    reviewer_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '评审人显示名称(冗余)',
    status        ENUM ('PENDING','APPROVED','REJECTED','NEED_MODIFY')
                               NOT NULL DEFAULT 'PENDING' COMMENT '评审状态: 待审/通过/拒绝/待修改',
    remark        TEXT                  DEFAULT NULL COMMENT '评审备注',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_case_set (case_set_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='评审分配表';

-- ========================================
-- 测试计划
-- ========================================
CREATE TABLE test_plans
(
    id              VARCHAR(32)  NOT NULL COMMENT '计划ID(UUID)',
    name            VARCHAR(255) NOT NULL COMMENT '计划名称',
    directory_id    VARCHAR(32)           DEFAULT NULL COMMENT '所属目录ID',
    project_id      VARCHAR(32)  NOT NULL COMMENT '所属项目ID',
    status          ENUM ('NOT_STARTED','IN_PROGRESS','COMPLETED')
                                 NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态: 未开始/进行中/已完成',
    executor_id     VARCHAR(32)           DEFAULT NULL COMMENT '执行人用户ID',
    created_by      VARCHAR(32)  NOT NULL COMMENT '创建人编号',
    created_by_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '创建人',
    updated_by      VARCHAR(32)           DEFAULT NULL COMMENT '修改人编号',
    updated_by_name VARCHAR(100)          DEFAULT NULL COMMENT '修改人',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=正常 1=已删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_project_deleted (project_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='测试计划表';

-- 测试计划新增字段：筛选条件和用例集ID
ALTER TABLE test_plans ADD COLUMN IF NOT EXISTS filters JSON DEFAULT NULL COMMENT '用例筛选条件 {caseSetId -> {attrName -> [values]}}';
ALTER TABLE test_plans ADD COLUMN IF NOT EXISTS case_set_ids JSON DEFAULT NULL COMMENT '选中的用例集ID列表';

-- ========================================
-- 测试计划执行人 (关联表不加审计字段)
-- ========================================
CREATE TABLE test_plan_executors
(
    id      VARCHAR(32) NOT NULL COMMENT '记录ID(UUID)',
    plan_id VARCHAR(32) NOT NULL COMMENT '计划ID',
    user_id VARCHAR(32) NOT NULL COMMENT '执行人用户ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_user (plan_id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='测试计划执行人表';

-- ========================================
-- 测试计划用例 (关联表不加审计字段)
-- ========================================
CREATE TABLE test_plan_cases
(
    id            VARCHAR(32) NOT NULL COMMENT '记录ID',
    plan_id       VARCHAR(32) NOT NULL COMMENT '计划ID',
    node_id       VARCHAR(64) NOT NULL COMMENT '源TITLE节点ID(用于刷新时回源)',
    case_set_id   VARCHAR(32) NOT NULL COMMENT '来源用例集ID',
    path_snapshot JSON                 DEFAULT NULL COMMENT '完整路径快照JSON,从root到EXPECTED的所有节点',
    executor_id   VARCHAR(32)          DEFAULT NULL COMMENT '分配的执行人ID',
    result        ENUM ('PENDING','PASS','FAIL','SKIP')
                              NOT NULL DEFAULT 'PENDING' COMMENT '执行结果: 待执行/通过/失败/跳过',
    reason        TEXT                 DEFAULT NULL COMMENT '不通过/跳过原因',
    executed_at       DATETIME             DEFAULT NULL COMMENT '执行时间',
    executed_by_name  VARCHAR(100)         DEFAULT NULL COMMENT '执行人名称',
    PRIMARY KEY (id),
    INDEX idx_plan (plan_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='测试计划用例表';

-- ========================================
-- 自定义属性定义 (项目级)
-- ========================================
CREATE TABLE custom_attributes
(
    id              VARCHAR(32)  NOT NULL COMMENT '属性ID(UUID)',
    project_id      VARCHAR(32)  NOT NULL COMMENT '所属项目ID',
    name            VARCHAR(100) NOT NULL COMMENT '属性名称(如 优先级/标签)',
    options         JSON         NOT NULL COMMENT '可选值列表, 如 ["P0","P1","P2","P3"]',
    multi_select    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否多选: 0=单选 1=多选',
    required        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必填: 0=非必填 1=必填',
    node_type_limit VARCHAR(100)          DEFAULT NULL COMMENT '限制节点类型, NULL=不限, 逗号分隔如 TITLE,EXPECTED',
    display_type    ENUM ('DROPDOWN','TILE')
                                 NOT NULL DEFAULT 'DROPDOWN' COMMENT '展示形式: 下拉框/平铺',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    created_by      VARCHAR(32)           DEFAULT NULL COMMENT '创建人编号',
    created_by_name VARCHAR(100)          DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_project (project_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='自定义属性定义表';

-- ========================================
-- 回收站
-- ========================================
CREATE TABLE recycle_bin
(
    id                    VARCHAR(32)  NOT NULL COMMENT '记录ID(UUID)',
    item_type             VARCHAR(20)  NOT NULL DEFAULT 'CASE_SET' COMMENT '类型: CASE_SET / TEST_PLAN',
    item_id               VARCHAR(32)  NOT NULL COMMENT '业务ID（用例集ID或测试计划ID）',
    item_name             VARCHAR(200)          DEFAULT NULL COMMENT '删除时的名称',
    project_id            VARCHAR(32)           DEFAULT NULL COMMENT '所属项目ID',
    original_directory_id VARCHAR(32)           DEFAULT NULL COMMENT '原目录ID',
    deleted_by            VARCHAR(32)  NOT NULL COMMENT '删除人编号',
    deleted_by_name       VARCHAR(100) NOT NULL DEFAULT '' COMMENT '删除人',
    deleted_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '删除时间',
    PRIMARY KEY (id),
    INDEX idx_project_type (project_id, item_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='回收站表';

CREATE TABLE IF NOT EXISTS `notifications`
(
    `id`         varchar(64)  NOT NULL,
    `user_id`    varchar(64)  NOT NULL COMMENT '接收人',
    `type`       varchar(32)  NOT NULL COMMENT '通知类型',
    `title`      varchar(200) NOT NULL,
    `content`    varchar(500)          DEFAULT NULL,
    `link`       varchar(500)          DEFAULT NULL COMMENT '跳转链接',
    `is_read`    tinyint      NOT NULL DEFAULT 0,
    `created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`),
    KEY `idx_user_time` (`user_id`, `created_at` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


CREATE TABLE IF NOT EXISTS `sys_job`
(
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
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='定时任务';

CREATE TABLE IF NOT EXISTS `sys_job_log`
(
    `id`            VARCHAR(32) NOT NULL,
    `job_id`        VARCHAR(32) NOT NULL COMMENT '任务ID',
    `job_name`      VARCHAR(100) DEFAULT NULL,
    `invoke_target` VARCHAR(200) DEFAULT NULL,
    `message`       VARCHAR(500) DEFAULT NULL COMMENT '执行结果消息',
    `status`        TINYINT      DEFAULT 0 COMMENT '0成功 1失败',
    `exception`     TEXT         DEFAULT NULL,
    `start_time`    DATETIME     DEFAULT NULL,
    `end_time`      DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='定时任务日志';

INSERT IGNORE INTO sys_job (id, job_name, job_group, invoke_target, cron_expression, status, remark)
VALUES ('1', '清理过期消息', 'SYSTEM', 'notificationCleanTask.execute', '0 0 2 * * ?', 0,
        '每天凌晨2点清理30天前的消息通知');

