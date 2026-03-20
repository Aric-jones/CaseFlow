USE caseflow;

-- =============================================
-- API Automation Menu Structure
-- =============================================

-- DIR: top-level directory
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('d_api_auto', NULL, '接口自动化', NULL, 'DIR', 3);

-- MENUs under d_api_auto
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('m_api_def',      'd_api_auto', '接口定义',   NULL, 'MENU', 1),
('m_api_case',     'd_api_auto', '接口用例',   NULL, 'MENU', 2),
('m_api_scenario', 'd_api_auto', '测试场景',   NULL, 'MENU', 3),
('m_api_plan',     'd_api_auto', '自动化计划', NULL, 'MENU', 4),
('m_api_exec',     'd_api_auto', '执行记录',   NULL, 'MENU', 5),
('m_api_env',      'd_api_auto', '环境管理',   NULL, 'MENU', 6);

-- BTNs: API Definition
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('b_api_def_view',   'm_api_def', '查看接口定义', 'api:def:view',   'BTN', 0),
('b_api_def_create', 'm_api_def', '新建接口定义', 'api:def:create', 'BTN', 1),
('b_api_def_edit',   'm_api_def', '编辑接口定义', 'api:def:edit',   'BTN', 2),
('b_api_def_delete', 'm_api_def', '删除接口定义', 'api:def:delete', 'BTN', 3);

-- BTNs: API Case
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('b_api_case_view',   'm_api_case', '查看接口用例', 'api:case:view',   'BTN', 0),
('b_api_case_create', 'm_api_case', '新建接口用例', 'api:case:create', 'BTN', 1),
('b_api_case_edit',   'm_api_case', '编辑接口用例', 'api:case:edit',   'BTN', 2),
('b_api_case_delete', 'm_api_case', '删除接口用例', 'api:case:delete', 'BTN', 3);

-- BTNs: API Scenario
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('b_api_scen_view',   'm_api_scenario', '查看测试场景', 'api:scenario:view',   'BTN', 0),
('b_api_scen_create', 'm_api_scenario', '新建测试场景', 'api:scenario:create', 'BTN', 1),
('b_api_scen_edit',   'm_api_scenario', '编辑测试场景', 'api:scenario:edit',   'BTN', 2),
('b_api_scen_delete', 'm_api_scenario', '删除测试场景', 'api:scenario:delete', 'BTN', 3);

-- BTNs: API Test Plan
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('b_api_plan_view',   'm_api_plan', '查看自动化计划', 'api:plan:view',   'BTN', 0),
('b_api_plan_create', 'm_api_plan', '新建自动化计划', 'api:plan:create', 'BTN', 1),
('b_api_plan_edit',   'm_api_plan', '编辑自动化计划', 'api:plan:edit',   'BTN', 2),
('b_api_plan_delete', 'm_api_plan', '删除自动化计划', 'api:plan:delete', 'BTN', 3);

-- BTNs: API Execution
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('b_api_exec_view', 'm_api_exec', '查看执行记录', 'api:execution:view', 'BTN', 0);

-- BTNs: API Environment
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, sort_order) VALUES
('b_api_env_manage', 'm_api_env', '管理环境', 'api:env', 'BTN', 0);

-- =============================================
-- Assign all API auto permissions to all roles
-- =============================================
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
-- role_super_admin
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'd_api_auto'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'm_api_def'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'm_api_case'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'm_api_scenario'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'm_api_plan'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'm_api_exec'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'm_api_env'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_def_view'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_def_create'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_def_edit'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_def_delete'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_case_view'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_case_create'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_case_edit'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_case_delete'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_scen_view'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_scen_create'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_scen_edit'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_scen_delete'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_plan_view'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_plan_create'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_plan_edit'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_plan_delete'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_exec_view'),
(REPLACE(UUID(), '-', ''), 'role_super_admin', 'b_api_env_manage'),
-- role_admin
(REPLACE(UUID(), '-', ''), 'role_admin', 'd_api_auto'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'm_api_def'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'm_api_case'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'm_api_scenario'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'm_api_plan'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'm_api_exec'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'm_api_env'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_def_view'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_def_create'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_def_edit'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_def_delete'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_case_view'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_case_create'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_case_edit'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_case_delete'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_scen_view'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_scen_create'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_scen_edit'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_scen_delete'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_plan_view'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_plan_create'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_plan_edit'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_plan_delete'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_exec_view'),
(REPLACE(UUID(), '-', ''), 'role_admin', 'b_api_env_manage'),
-- role_member (all view + create + edit, no delete for def; full for case/scenario)
(REPLACE(UUID(), '-', ''), 'role_member', 'd_api_auto'),
(REPLACE(UUID(), '-', ''), 'role_member', 'm_api_def'),
(REPLACE(UUID(), '-', ''), 'role_member', 'm_api_case'),
(REPLACE(UUID(), '-', ''), 'role_member', 'm_api_scenario'),
(REPLACE(UUID(), '-', ''), 'role_member', 'm_api_plan'),
(REPLACE(UUID(), '-', ''), 'role_member', 'm_api_exec'),
(REPLACE(UUID(), '-', ''), 'role_member', 'm_api_env'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_def_view'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_def_create'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_def_edit'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_case_view'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_case_create'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_case_edit'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_case_delete'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_scen_view'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_scen_create'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_scen_edit'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_scen_delete'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_plan_view'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_plan_create'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_plan_edit'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_exec_view'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_api_env_manage');

SELECT 'API automation menus and role assignments inserted successfully' AS result;
