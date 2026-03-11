-- CaseFlow 测试用例管理平台 数据库初始化脚本
CREATE DATABASE IF NOT EXISTS caseflow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE caseflow;

-- ==================== 用户与权限 ====================

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('SUPER_ADMIN','ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
    identity ENUM('TEST','DEV','PRODUCT') NOT NULL DEFAULT 'TEST',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认超管账号由应用启动时自动创建 (admin / wps123456)

-- ==================== 项目空间 ====================

CREATE TABLE projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) DEFAULT '',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE project_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_user (project_id, user_id),
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 目录树 (用例 & 测试计划共用) ====================

CREATE TABLE directories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT DEFAULT NULL,
    project_id BIGINT NOT NULL,
    dir_type ENUM('CASE','TEST_PLAN') NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_type (project_id, dir_type),
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (parent_id) REFERENCES directories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 用例集 ====================

CREATE TABLE case_sets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    directory_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    status ENUM('WRITING','PENDING_REVIEW','NO_REVIEW') NOT NULL DEFAULT 'WRITING',
    requirement_link VARCHAR(500) DEFAULT '',
    case_count INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_directory (directory_id),
    INDEX idx_project (project_id),
    FOREIGN KEY (directory_id) REFERENCES directories(id),
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 思维导图节点 ====================

CREATE TABLE mind_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_set_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT NULL,
    text VARCHAR(500) NOT NULL DEFAULT '',
    node_type ENUM('ROOT','TITLE','PRECONDITION','STEP','EXPECTED') NOT NULL DEFAULT 'ROOT',
    sort_order INT NOT NULL DEFAULT 0,
    priority ENUM('P0','P1','P2','P3') DEFAULT NULL,
    mark ENUM('NONE','PENDING','TO_CONFIRM','TO_MODIFY') NOT NULL DEFAULT 'NONE',
    tags JSON DEFAULT NULL COMMENT '["SMOKE","REGRESSION","INTEGRATION"]',
    automation ENUM('API','UI','NONE') DEFAULT NULL,
    coverage VARCHAR(50) DEFAULT NULL,
    platform JSON DEFAULT NULL,
    belongs_platform JSON DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_case_set (case_set_id),
    INDEX idx_parent (parent_id),
    FOREIGN KEY (case_set_id) REFERENCES case_sets(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES mind_nodes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 评论 ====================

CREATE TABLE comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id BIGINT NOT NULL,
    case_set_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT NULL COMMENT '回复的评论ID',
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    resolved TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_node (node_id),
    INDEX idx_case_set (case_set_id),
    FOREIGN KEY (node_id) REFERENCES mind_nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (case_set_id) REFERENCES case_sets(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 用例集历史版本 ====================

CREATE TABLE case_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_set_id BIGINT NOT NULL,
    snapshot JSON NOT NULL COMMENT '节点树快照',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_case_set (case_set_id),
    FOREIGN KEY (case_set_id) REFERENCES case_sets(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 评审 ====================

CREATE TABLE review_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_set_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    status ENUM('PENDING','APPROVED','REJECTED','NEED_MODIFY') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_case_set (case_set_id),
    FOREIGN KEY (case_set_id) REFERENCES case_sets(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 测试计划 ====================

CREATE TABLE test_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    directory_id BIGINT DEFAULT NULL,
    project_id BIGINT NOT NULL,
    status ENUM('NOT_STARTED','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'NOT_STARTED',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project (project_id),
    FOREIGN KEY (directory_id) REFERENCES directories(id),
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE test_plan_executors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    UNIQUE KEY uk_plan_user (plan_id, user_id),
    FOREIGN KEY (plan_id) REFERENCES test_plans(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE test_plan_cases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL COMMENT '预期结果节点ID，标识一条用例',
    case_set_id BIGINT NOT NULL,
    executor_id BIGINT DEFAULT NULL,
    result ENUM('PENDING','PASS','FAIL','SKIP') NOT NULL DEFAULT 'PENDING',
    reason TEXT DEFAULT NULL COMMENT '不通过/跳过原因',
    executed_at DATETIME DEFAULT NULL,
    INDEX idx_plan (plan_id),
    FOREIGN KEY (plan_id) REFERENCES test_plans(id) ON DELETE CASCADE,
    FOREIGN KEY (node_id) REFERENCES mind_nodes(id),
    FOREIGN KEY (case_set_id) REFERENCES case_sets(id),
    FOREIGN KEY (executor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 自定义属性 ====================

CREATE TABLE custom_attributes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    options JSON NOT NULL COMMENT '属性值列表',
    multi_select TINYINT NOT NULL DEFAULT 0,
    node_type_limit VARCHAR(50) DEFAULT NULL COMMENT 'null=不限制, TITLE/PRECONDITION/STEP/EXPECTED',
    display_type ENUM('DROPDOWN','TILE') NOT NULL DEFAULT 'DROPDOWN',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_project (project_id),
    FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 节点-自定义属性关联值
CREATE TABLE node_attribute_values (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id BIGINT NOT NULL,
    attribute_id BIGINT NOT NULL,
    attr_value JSON NOT NULL COMMENT '选中的值',
    INDEX idx_node (node_id),
    FOREIGN KEY (node_id) REFERENCES mind_nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (attribute_id) REFERENCES custom_attributes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 回收站 ====================

CREATE TABLE recycle_bin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_set_id BIGINT NOT NULL,
    original_directory_id BIGINT NOT NULL,
    deleted_by BIGINT NOT NULL,
    deleted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (case_set_id) REFERENCES case_sets(id),
    FOREIGN KEY (deleted_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
