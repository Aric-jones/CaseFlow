-- =============================================
-- CaseFlow 接口自动化模块 - 数据库初始化脚本
-- =============================================

-- 1. 环境配置
CREATE TABLE IF NOT EXISTS api_environments (
    id              VARCHAR(32)  NOT NULL,
    project_id      VARCHAR(32)  NOT NULL COMMENT '所属项目',
    name            VARCHAR(100) NOT NULL COMMENT '环境名称(开发/测试/生产)',
    base_url        VARCHAR(500) NOT NULL COMMENT '基础URL',
    headers         JSON         DEFAULT NULL COMMENT '全局请求头',
    variables       JSON         DEFAULT NULL COMMENT '环境变量',
    created_by      VARCHAR(32)  DEFAULT NULL,
    created_by_name VARCHAR(100) DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-环境配置';

-- 2. 接口定义
CREATE TABLE IF NOT EXISTS api_definitions (
    id                VARCHAR(32)  NOT NULL,
    project_id        VARCHAR(32)  NOT NULL,
    directory_id      VARCHAR(32)  DEFAULT NULL COMMENT '所属目录(dir_type=API)',
    name              VARCHAR(200) NOT NULL COMMENT '接口名称',
    method            VARCHAR(10)  NOT NULL DEFAULT 'GET',
    path              VARCHAR(500) NOT NULL COMMENT '请求路径(相对baseUrl)',
    description       TEXT         DEFAULT NULL,
    auth_type         VARCHAR(30)  DEFAULT 'NONE' COMMENT 'NONE/BEARER_TOKEN/BASIC/API_KEY/CUSTOM',
    auth_config       JSON         DEFAULT NULL COMMENT '鉴权配置',
    default_headers   JSON         DEFAULT NULL COMMENT '默认请求头',
    default_params    JSON         DEFAULT NULL COMMENT '默认Query参数',
    default_body_type VARCHAR(20)  DEFAULT 'NONE' COMMENT 'NONE/JSON/FORM/FORM_DATA/RAW/XML',
    default_body      TEXT         DEFAULT NULL COMMENT '默认请求体',
    tags              JSON         DEFAULT NULL COMMENT '标签数组',
    sort_order        INT          NOT NULL DEFAULT 0,
    created_by        VARCHAR(32)  DEFAULT NULL,
    created_by_name   VARCHAR(100) DEFAULT NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project (project_id),
    INDEX idx_directory (directory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-接口定义';

-- 3. 接口用例
CREATE TABLE IF NOT EXISTS api_cases (
    id              VARCHAR(32)  NOT NULL,
    api_id          VARCHAR(32)  NOT NULL COMMENT '所属接口定义',
    name            VARCHAR(200) NOT NULL COMMENT '用例名称',
    description     TEXT         DEFAULT NULL,
    headers         JSON         DEFAULT NULL COMMENT '请求头(覆盖)',
    query_params    JSON         DEFAULT NULL COMMENT 'Query参数(覆盖)',
    body_type       VARCHAR(20)  DEFAULT NULL COMMENT '请求体类型(NULL则继承)',
    body_content    TEXT         DEFAULT NULL,
    pre_script      JSON         DEFAULT NULL COMMENT '前置脚本',
    post_script     JSON         DEFAULT NULL COMMENT '后置脚本',
    tags            JSON         DEFAULT NULL,
    priority        VARCHAR(10)  DEFAULT 'P1' COMMENT 'P0/P1/P2/P3',
    enabled         TINYINT      NOT NULL DEFAULT 1,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_by      VARCHAR(32)  DEFAULT NULL,
    created_by_name VARCHAR(100) DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_api (api_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-接口用例';

-- 4. 断言规则
CREATE TABLE IF NOT EXISTS api_assertions (
    id              VARCHAR(32)  NOT NULL,
    case_id         VARCHAR(32)  NOT NULL COMMENT '所属用例',
    type            VARCHAR(30)  NOT NULL COMMENT 'STATUS_CODE/HEADER/JSON_PATH/BODY_CONTAINS/RESPONSE_TIME',
    expression      VARCHAR(500) DEFAULT NULL,
    operator        VARCHAR(20)  NOT NULL COMMENT 'EQUALS/NOT_EQUALS/CONTAINS/GT/LT/EXISTS/NOT_EXISTS/REGEX等',
    expected_value  VARCHAR(1000) DEFAULT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-断言规则';

-- 5. 测试场景
CREATE TABLE IF NOT EXISTS api_scenarios (
    id              VARCHAR(32)  NOT NULL,
    project_id      VARCHAR(32)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT         DEFAULT NULL,
    fail_strategy   VARCHAR(20)  DEFAULT 'STOP' COMMENT 'STOP/CONTINUE',
    timeout_ms      INT          DEFAULT 300000,
    tags            JSON         DEFAULT NULL,
    created_by      VARCHAR(32)  DEFAULT NULL,
    created_by_name VARCHAR(100) DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-测试场景';

-- 6. 场景步骤
CREATE TABLE IF NOT EXISTS api_scenario_steps (
    id              VARCHAR(32)  NOT NULL,
    scenario_id     VARCHAR(32)  NOT NULL,
    case_id         VARCHAR(32)  NOT NULL COMMENT '关联的接口用例',
    sort_order      INT          NOT NULL DEFAULT 0,
    override_headers JSON        DEFAULT NULL,
    override_body   TEXT         DEFAULT NULL,
    pre_script      JSON         DEFAULT NULL,
    post_script     JSON         DEFAULT NULL,
    delay_ms        INT          DEFAULT 0,
    retry_count     INT          DEFAULT 0,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_scenario (scenario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-场景步骤';

-- 7. 自动化测试计划
CREATE TABLE IF NOT EXISTS api_test_plans (
    id               VARCHAR(32)  NOT NULL,
    project_id       VARCHAR(32)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    description      TEXT         DEFAULT NULL,
    environment_id   VARCHAR(32)  NOT NULL COMMENT '执行环境',
    parallel         TINYINT      DEFAULT 0 COMMENT '0=串行 1=并行',
    cron_expression  VARCHAR(100) DEFAULT NULL,
    status           VARCHAR(20)  DEFAULT 'DRAFT' COMMENT 'DRAFT/SCHEDULED/RUNNING/COMPLETED',
    created_by       VARCHAR(32)  DEFAULT NULL,
    created_by_name  VARCHAR(100) DEFAULT NULL,
    updated_by       VARCHAR(32)  DEFAULT NULL,
    updated_by_name  VARCHAR(100) DEFAULT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-测试计划';

-- 8. 计划-场景关联
CREATE TABLE IF NOT EXISTS api_plan_scenarios (
    id              VARCHAR(32)  NOT NULL,
    plan_id         VARCHAR(32)  NOT NULL,
    scenario_id     VARCHAR(32)  NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_plan (plan_id),
    UNIQUE KEY uk_plan_scenario (plan_id, scenario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-计划关联场景';

-- 9. 执行记录
CREATE TABLE IF NOT EXISTS api_executions (
    id               VARCHAR(32)  NOT NULL,
    project_id       VARCHAR(32)  NOT NULL,
    plan_id          VARCHAR(32)  DEFAULT NULL,
    scenario_id      VARCHAR(32)  DEFAULT NULL,
    case_id          VARCHAR(32)  DEFAULT NULL,
    environment_id   VARCHAR(32)  NOT NULL,
    trigger_type     VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/SCHEDULED/CI',
    status           VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/PASS/FAIL/ERROR/CANCELLED',
    total_cases      INT          DEFAULT 0,
    passed_cases     INT          DEFAULT 0,
    failed_cases     INT          DEFAULT 0,
    error_cases      INT          DEFAULT 0,
    skipped_cases    INT          DEFAULT 0,
    duration_ms      BIGINT       DEFAULT 0,
    executed_by      VARCHAR(32)  DEFAULT NULL,
    executed_by_name VARCHAR(100) DEFAULT NULL,
    started_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at      DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_project (project_id),
    INDEX idx_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-执行记录';

-- 10. 执行详情
CREATE TABLE IF NOT EXISTS api_execution_details (
    id                VARCHAR(32)  NOT NULL,
    execution_id      VARCHAR(32)  NOT NULL,
    scenario_id       VARCHAR(32)  DEFAULT NULL,
    case_id           VARCHAR(32)  NOT NULL,
    api_id            VARCHAR(32)  NOT NULL,
    step_order        INT          NOT NULL DEFAULT 0,
    request_url       VARCHAR(1000) DEFAULT NULL,
    request_method    VARCHAR(10)  DEFAULT NULL,
    request_headers   JSON         DEFAULT NULL,
    request_body      TEXT         DEFAULT NULL,
    response_status   INT          DEFAULT NULL,
    response_headers  JSON         DEFAULT NULL,
    response_body     MEDIUMTEXT   DEFAULT NULL,
    duration_ms       BIGINT       DEFAULT 0,
    assertion_results JSON         DEFAULT NULL,
    extracted_vars    JSON         DEFAULT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PASS/FAIL/ERROR/SKIP',
    error_message     TEXT         DEFAULT NULL,
    retry_count       INT          DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_execution (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口自动化-执行详情';
