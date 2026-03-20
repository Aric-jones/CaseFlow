USE caseflow;

-- 1. Extend dir_type enum to include API_SCENARIO and API_PLAN
ALTER TABLE directories MODIFY COLUMN dir_type ENUM('CASE','TEST_PLAN','API','API_SCENARIO','API_PLAN') NOT NULL COMMENT '目录类型';

-- 2. Add directory_id to api_scenarios
ALTER TABLE api_scenarios ADD COLUMN directory_id VARCHAR(32) DEFAULT NULL COMMENT '所属目录' AFTER project_id;
ALTER TABLE api_scenarios ADD INDEX idx_directory (directory_id);

-- 3. Add directory_id to api_test_plans
ALTER TABLE api_test_plans ADD COLUMN directory_id VARCHAR(32) DEFAULT NULL COMMENT '所属目录' AFTER project_id;
ALTER TABLE api_test_plans ADD INDEX idx_directory (directory_id);

-- 4. Add missing permission: api:execute
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('b_api_execute', 'm_api_exec', '执行接口', 'api:execute', 'BTN', 1);

-- 5. Add missing permission: api:execution:delete (currently using api:execution:view for delete)
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('b_api_exec_delete', 'm_api_exec', '删除执行记录', 'api:execution:delete', 'BTN', 2);

-- 6. Assign new permissions to all roles
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_execute'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_exec_delete'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_execute'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_exec_delete'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_execute'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_exec_delete');

SELECT 'API auto update completed' AS result;
