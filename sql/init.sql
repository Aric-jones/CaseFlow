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
    dir_type        ENUM ('CASE','TEST_PLAN','API','API_SCENARIO','API_PLAN')
                                 NOT NULL COMMENT '目录类型: 用例/测试计划/接口定义/测试场景/自动化计划',
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

-- ============================================================
-- UI 自动化模块（整合版）
-- ============================================================

-- 扩展目录类型
ALTER TABLE directories MODIFY COLUMN dir_type
    ENUM ('CASE','TEST_PLAN','API','API_SCENARIO','API_PLAN','UI_PAGE','UI_CASE','UI_SCENARIO','UI_PLAN')
        NOT NULL COMMENT '目录类型';

-- 1) 页面对象
CREATE TABLE IF NOT EXISTS ui_pages
(
    id              VARCHAR(32)  NOT NULL COMMENT '页面ID',
    project_id      VARCHAR(32)  NOT NULL COMMENT '所属项目ID',
    directory_id    VARCHAR(32)           DEFAULT NULL COMMENT '所属目录ID',
    name            VARCHAR(200) NOT NULL COMMENT '页面名称',
    url             VARCHAR(500)          DEFAULT '' COMMENT '页面URL',
    description     VARCHAR(500)          DEFAULT '' COMMENT '描述',
    tags            JSON                  DEFAULT NULL COMMENT '标签列表',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_by      VARCHAR(32)           DEFAULT NULL COMMENT '创建人ID',
    created_by_name VARCHAR(100)          DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project_deleted (project_id, deleted),
    INDEX idx_directory (directory_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI页面对象表';

-- 2) 页面元素
CREATE TABLE IF NOT EXISTS ui_elements
(
    id              VARCHAR(32)  NOT NULL COMMENT '元素ID',
    page_id         VARCHAR(32)  NOT NULL COMMENT '所属页面ID',
    name            VARCHAR(200) NOT NULL COMMENT '元素名称',
    locator_type    VARCHAR(30)  NOT NULL DEFAULT 'CSS_SELECTOR' COMMENT '定位方式',
    locator_value   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '定位值',
    description     VARCHAR(500)          DEFAULT '' COMMENT '描述',
    screenshot_path VARCHAR(500)          DEFAULT NULL COMMENT '元素截图路径',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_by      VARCHAR(32)           DEFAULT NULL COMMENT '创建人ID',
    created_by_name VARCHAR(100)          DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_page (page_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI页面元素表';

-- 3) UI 测试用例
CREATE TABLE IF NOT EXISTS ui_test_cases
(
    id              VARCHAR(32)  NOT NULL COMMENT '用例ID',
    project_id      VARCHAR(32)  NOT NULL COMMENT '所属项目ID',
    directory_id    VARCHAR(32)           DEFAULT NULL COMMENT '所属目录ID',
    name            VARCHAR(200) NOT NULL COMMENT '用例名称',
    description     VARCHAR(500)          DEFAULT '' COMMENT '描述',
    browser_type    VARCHAR(20)  NOT NULL DEFAULT 'CHROMIUM' COMMENT '浏览器',
    driver_type     VARCHAR(20)  NOT NULL DEFAULT 'PLAYWRIGHT' COMMENT '驱动',
    headless        TINYINT      NOT NULL DEFAULT 1 COMMENT '无头模式',
    window_width    INT                   DEFAULT 1920 COMMENT '窗口宽度',
    window_height   INT                   DEFAULT 1080 COMMENT '窗口高度',
    base_url        VARCHAR(500)          DEFAULT '' COMMENT '基础URL',
    tags            JSON                  DEFAULT NULL COMMENT '标签列表',
    timeout_ms      INT                   DEFAULT 30000 COMMENT '全局超时(ms)',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_by      VARCHAR(32)           DEFAULT NULL COMMENT '创建人ID',
    created_by_name VARCHAR(100)          DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project_deleted (project_id, deleted),
    INDEX idx_directory (directory_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI测试用例表';

-- 4) UI 测试步骤
CREATE TABLE IF NOT EXISTS ui_test_steps
(
    id                VARCHAR(32)  NOT NULL COMMENT '步骤ID',
    case_id           VARCHAR(32)  NOT NULL COMMENT '所属用例ID',
    sort_order        INT          NOT NULL DEFAULT 0 COMMENT '步骤顺序',
    step_type         VARCHAR(30)  NOT NULL DEFAULT 'CLICK' COMMENT '步骤类型',
    element_id        VARCHAR(32)           DEFAULT NULL COMMENT '关联元素ID',
    locator_type      VARCHAR(30)           DEFAULT NULL COMMENT '直接定位方式',
    locator_value     VARCHAR(500)          DEFAULT NULL COMMENT '直接定位值',
    input_value       VARCHAR(2000)         DEFAULT NULL COMMENT '输入值',
    target_url        VARCHAR(500)          DEFAULT NULL COMMENT 'NAVIGATE目标URL',
    wait_type         VARCHAR(30)           DEFAULT NULL COMMENT '等待类型',
    wait_timeout_ms   INT                   DEFAULT 5000 COMMENT '等待超时(ms)',
    assert_type       VARCHAR(30)           DEFAULT NULL COMMENT '断言类型',
    assert_expression VARCHAR(500)          DEFAULT NULL COMMENT '断言表达式',
    assert_expected   VARCHAR(2000)         DEFAULT NULL COMMENT '断言预期值',
    script_content    TEXT                  DEFAULT NULL COMMENT 'JS脚本内容',
    variable_name     VARCHAR(100)          DEFAULT NULL COMMENT '变量名',
    description       VARCHAR(500)          DEFAULT '' COMMENT '步骤描述',
    enabled           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY (id),
    INDEX idx_case (case_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI测试步骤表';

-- 5) UI 场景
CREATE TABLE IF NOT EXISTS ui_scenarios
(
    id              VARCHAR(32)  NOT NULL COMMENT '场景ID',
    project_id      VARCHAR(32)  NOT NULL COMMENT '所属项目ID',
    directory_id    VARCHAR(32)           DEFAULT NULL COMMENT '所属目录ID',
    name            VARCHAR(200) NOT NULL COMMENT '场景名称',
    description     VARCHAR(500)          DEFAULT '' COMMENT '描述',
    fail_strategy   VARCHAR(10)  NOT NULL DEFAULT 'STOP' COMMENT '失败策略',
    tags            JSON                  DEFAULT NULL COMMENT '标签列表',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_by      VARCHAR(32)           DEFAULT NULL COMMENT '创建人ID',
    created_by_name VARCHAR(100)          DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project_deleted (project_id, deleted),
    INDEX idx_directory (directory_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI场景表';

-- 6) 场景-用例关联
CREATE TABLE IF NOT EXISTS ui_scenario_cases
(
    id          VARCHAR(32) NOT NULL COMMENT '记录ID',
    scenario_id VARCHAR(32) NOT NULL COMMENT '场景ID',
    case_id     VARCHAR(32) NOT NULL COMMENT '用例ID',
    sort_order  INT         NOT NULL DEFAULT 0 COMMENT '排序',
    enabled     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY (id),
    INDEX idx_scenario (scenario_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI场景-用例关联表';

-- 7) UI 测试计划（不含 environment_id，环境在运行时选择）
CREATE TABLE IF NOT EXISTS ui_test_plans
(
    id              VARCHAR(32)  NOT NULL COMMENT '计划ID',
    project_id      VARCHAR(32)  NOT NULL COMMENT '所属项目ID',
    directory_id    VARCHAR(32)           DEFAULT NULL COMMENT '所属目录ID',
    name            VARCHAR(200) NOT NULL COMMENT '计划名称',
    description     VARCHAR(500)          DEFAULT '' COMMENT '描述',
    browser_type    VARCHAR(20)           DEFAULT 'CHROMIUM' COMMENT '浏览器类型',
    driver_type     VARCHAR(20)           DEFAULT 'PLAYWRIGHT' COMMENT '驱动类型',
    headless        TINYINT               DEFAULT 1 COMMENT '无头模式',
    base_url        VARCHAR(500)          DEFAULT '' COMMENT '基础URL(兼容字段)',
    parallel        TINYINT               DEFAULT 0 COMMENT '是否并行执行',
    cron_expression VARCHAR(100)          DEFAULT NULL COMMENT 'cron定时表达式',
    status          VARCHAR(20)           DEFAULT 'ACTIVE' COMMENT '状态',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_by      VARCHAR(32)           DEFAULT NULL COMMENT '创建人ID',
    created_by_name VARCHAR(100)          DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(32)           DEFAULT NULL COMMENT '修改人ID',
    updated_by_name VARCHAR(100)          DEFAULT NULL COMMENT '修改人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project_deleted (project_id, deleted),
    INDEX idx_directory (directory_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI测试计划表';

-- 8) 计划-场景关联
CREATE TABLE IF NOT EXISTS ui_plan_scenarios
(
    id          VARCHAR(32) NOT NULL COMMENT '记录ID',
    plan_id     VARCHAR(32) NOT NULL COMMENT '计划ID',
    scenario_id VARCHAR(32) NOT NULL COMMENT '场景ID',
    sort_order  INT         NOT NULL DEFAULT 0 COMMENT '排序',
    enabled     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY (id),
    INDEX idx_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI计划-场景关联表';

-- 9) UI 环境配置
CREATE TABLE IF NOT EXISTS ui_environments
(
    id              VARCHAR(32)  NOT NULL,
    project_id      VARCHAR(32)  NOT NULL COMMENT '所属项目',
    name            VARCHAR(100) NOT NULL COMMENT '环境名称',
    base_url        VARCHAR(500) NOT NULL COMMENT '基础URL',
    variables       JSON                  DEFAULT NULL COMMENT '环境变量',
    description     VARCHAR(500)          DEFAULT '' COMMENT '描述',
    created_by      VARCHAR(32),
    created_by_name VARCHAR(100),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI自动化环境配置';

-- 10) UI 执行记录（含 environment_id）
CREATE TABLE IF NOT EXISTS ui_executions
(
    id               VARCHAR(32)  NOT NULL COMMENT '执行ID',
    project_id       VARCHAR(32)  NOT NULL COMMENT '所属项目ID',
    plan_id          VARCHAR(32)           DEFAULT NULL COMMENT '计划ID',
    scenario_id      VARCHAR(32)           DEFAULT NULL COMMENT '场景ID',
    case_id          VARCHAR(32)           DEFAULT NULL COMMENT '用例ID',
    environment_id   VARCHAR(32)           DEFAULT NULL COMMENT '运行环境ID',
    trigger_type     VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式',
    status           VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态',
    browser_type     VARCHAR(20)           DEFAULT NULL COMMENT '浏览器',
    driver_type      VARCHAR(20)           DEFAULT NULL COMMENT '驱动',
    total_steps      INT                   DEFAULT 0,
    passed_steps     INT                   DEFAULT 0,
    failed_steps     INT                   DEFAULT 0,
    error_steps      INT                   DEFAULT 0,
    skipped_steps    INT                   DEFAULT 0,
    duration_ms      BIGINT                DEFAULT 0,
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    executed_by      VARCHAR(32)           DEFAULT NULL COMMENT '执行人ID',
    executed_by_name VARCHAR(100)          DEFAULT NULL COMMENT '执行人',
    started_at       DATETIME              DEFAULT NULL,
    finished_at      DATETIME              DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_project (project_id),
    INDEX idx_started (started_at DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI执行记录表';

-- 11) UI 执行详情
CREATE TABLE IF NOT EXISTS ui_execution_details
(
    id              VARCHAR(32)  NOT NULL COMMENT '详情ID',
    execution_id    VARCHAR(32)  NOT NULL COMMENT '执行记录ID',
    scenario_id     VARCHAR(32)           DEFAULT NULL COMMENT '场景ID',
    case_id         VARCHAR(32)           DEFAULT NULL COMMENT '用例ID',
    step_order      INT          NOT NULL DEFAULT 0 COMMENT '步骤序号',
    step_type       VARCHAR(30)           DEFAULT NULL COMMENT '步骤类型',
    element_name    VARCHAR(200)          DEFAULT NULL COMMENT '元素名称',
    action_desc     VARCHAR(500)          DEFAULT NULL COMMENT '操作描述',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PASS' COMMENT '状态',
    duration_ms     BIGINT                DEFAULT 0 COMMENT '耗时(ms)',
    screenshot_path VARCHAR(500)          DEFAULT NULL COMMENT '截图路径',
    error_message   TEXT                  DEFAULT NULL COMMENT '错误信息',
    page_url        VARCHAR(500)          DEFAULT NULL COMMENT '当前页面URL',
    log_output      TEXT                  DEFAULT NULL COMMENT '日志输出',
    PRIMARY KEY (id),
    INDEX idx_execution (execution_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'UI执行详情表';

-- ============================================================
-- UI 自动化菜单与权限
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES
    ('d_ui_auto', NULL, 'UI自动化', NULL, 'DIR', '/ui-auto', 4),
    ('m_ui_pages', 'd_ui_auto', '页面对象', NULL, 'MENU', '/ui-auto/pages', 1),
    ('m_ui_cases', 'd_ui_auto', '测试用例', NULL, 'MENU', '/ui-auto/cases', 2),
    ('m_ui_scenarios', 'd_ui_auto', '测试场景', NULL, 'MENU', '/ui-auto/scenarios', 3),
    ('m_ui_plans', 'd_ui_auto', '测试计划', NULL, 'MENU', '/ui-auto/plans', 4),
    ('m_ui_executions', 'd_ui_auto', '执行记录', NULL, 'MENU', '/ui-auto/executions', 5),
    ('m_ui_env', 'd_ui_auto', '环境管理', NULL, 'MENU', '/ui-auto/env', 6);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES
    ('b_ui_page_view', 'm_ui_pages', '查看页面', 'ui:page:view', 'BTN', NULL, 0),
    ('b_ui_page_create', 'm_ui_pages', '新建页面', 'ui:page:create', 'BTN', NULL, 1),
    ('b_ui_page_edit', 'm_ui_pages', '编辑页面', 'ui:page:edit', 'BTN', NULL, 2),
    ('b_ui_page_delete', 'm_ui_pages', '删除页面', 'ui:page:delete', 'BTN', NULL, 3),
    ('b_ui_case_view', 'm_ui_cases', '查看用例', 'ui:case:view', 'BTN', NULL, 0),
    ('b_ui_case_create', 'm_ui_cases', '新建用例', 'ui:case:create', 'BTN', NULL, 1),
    ('b_ui_case_edit', 'm_ui_cases', '编辑用例', 'ui:case:edit', 'BTN', NULL, 2),
    ('b_ui_case_delete', 'm_ui_cases', '删除用例', 'ui:case:delete', 'BTN', NULL, 3),
    ('b_ui_scenario_view', 'm_ui_scenarios', '查看场景', 'ui:scenario:view', 'BTN', NULL, 0),
    ('b_ui_scenario_create', 'm_ui_scenarios', '新建场景', 'ui:scenario:create', 'BTN', NULL, 1),
    ('b_ui_scenario_edit', 'm_ui_scenarios', '编辑场景', 'ui:scenario:edit', 'BTN', NULL, 2),
    ('b_ui_scenario_delete', 'm_ui_scenarios', '删除场景', 'ui:scenario:delete', 'BTN', NULL, 3),
    ('b_ui_plan_view', 'm_ui_plans', '查看计划', 'ui:plan:view', 'BTN', NULL, 0),
    ('b_ui_plan_create', 'm_ui_plans', '新建计划', 'ui:plan:create', 'BTN', NULL, 1),
    ('b_ui_plan_edit', 'm_ui_plans', '编辑计划', 'ui:plan:edit', 'BTN', NULL, 2),
    ('b_ui_plan_delete', 'm_ui_plans', '删除计划', 'ui:plan:delete', 'BTN', NULL, 3),
    ('b_ui_plan_run', 'm_ui_plans', '执行计划', 'ui:plan:run', 'BTN', NULL, 4),
    ('b_ui_exec_view', 'm_ui_executions', '查看执行记录', 'ui:execution:view', 'BTN', NULL, 0),
    ('b_ui_exec_delete', 'm_ui_executions', '删除执行记录', 'ui:execution:delete', 'BTN', NULL, 1),
    ('b_ui_env_view', 'm_ui_env', '查看环境', 'ui:env:view', 'BTN', NULL, 0),
    ('b_ui_env_create', 'm_ui_env', '新建环境', 'ui:env:create', 'BTN', NULL, 1),
    ('b_ui_env_edit', 'm_ui_env', '编辑环境', 'ui:env:edit', 'BTN', NULL, 2),
    ('b_ui_env_delete', 'm_ui_env', '删除环境', 'ui:env:delete', 'BTN', NULL, 3);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT REPLACE(UUID(), '-', ''), 'role_super_admin', id
FROM sys_menu
WHERE id LIKE 'd_ui_%' OR id LIKE 'm_ui_%' OR id LIKE 'b_ui_%';

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT REPLACE(UUID(), '-', ''), 'role_admin', id
FROM sys_menu
WHERE id LIKE 'd_ui_%' OR id LIKE 'm_ui_%' OR id LIKE 'b_ui_%';

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
VALUES
    (REPLACE(UUID(), '-', ''), 'role_member', 'd_ui_auto'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'm_ui_pages'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'm_ui_cases'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'm_ui_scenarios'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'm_ui_plans'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'm_ui_executions'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'm_ui_env'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_page_view'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_page_create'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_page_edit'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_case_view'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_case_create'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_case_edit'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_scenario_view'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_scenario_create'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_scenario_edit'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_plan_view'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_plan_create'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_plan_edit'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_plan_run'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_exec_view'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_env_view'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_env_create'),
    (REPLACE(UUID(), '-', ''), 'role_member', 'b_ui_env_edit');
