-- ============================================================
-- V009: 初始化 RBAC 菜单权限数据
-- 层级: 目录(DIR) → 菜单(MENU) → 按钮(BTN)
-- 规则: 只有 BTN 才设 permission_code，DIR/MENU 仅做结构分组
-- ============================================================

-- 清理旧数据（重建菜单结构时执行）
DELETE FROM sys_role_menu;
DELETE FROM sys_menu;

-- ======================== 一、工作台 ========================
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('d_dashboard', NULL, '工作台', NULL, 'DIR', '/dashboard', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_dashboard_view', 'd_dashboard', '工作台首页', NULL, 'MENU', '/dashboard', 1);

-- ======================== 二、用例管理 ========================
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('d_cases', NULL, '用例管理', NULL, 'DIR', NULL, 2);

-- 2.1 用例列表
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_cases', 'd_cases', '用例列表', NULL, 'MENU', '/cases', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_cases_create', 'm_cases', '新建用例集', 'cases:create', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_cases_edit', 'm_cases', '编辑用例集', 'cases:edit', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_cases_delete', 'm_cases', '删除用例集', 'cases:delete', 'BTN', NULL, 3);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_cases_export', 'm_cases', '导出Excel', 'cases:export', 'BTN', NULL, 4);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_cases_import', 'm_cases', '导入Excel', 'cases:import', 'BTN', NULL, 5);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_cases_move', 'm_cases', '移动用例集', 'cases:move', 'BTN', NULL, 6);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_cases_copy', 'm_cases', '复制用例集', 'cases:copy', 'BTN', NULL, 7);

-- 2.2 思维导图
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_mindmap', 'd_cases', '思维导图', NULL, 'MENU', '/mind-map', 2);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_mindmap_edit', 'm_mindmap', '编辑节点', 'mindmap:edit', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_mindmap_save', 'm_mindmap', '保存/同步', 'mindmap:save', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_mindmap_export', 'm_mindmap', '导出Excel', 'mindmap:export', 'BTN', NULL, 3);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_mindmap_import', 'm_mindmap', '导入Excel', 'mindmap:import', 'BTN', NULL, 4);

-- 2.3 评审管理
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_review', 'd_cases', '评审管理', NULL, 'MENU', '/review', 3);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_review_submit', 'm_review', '提交评审', 'review:submit', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_review_approve', 'm_review', '审批评审', 'review:approve', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_review_comment', 'm_review', '评论', 'review:comment', 'BTN', NULL, 3);

-- 2.4 目录管理
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_directory', 'd_cases', '目录管理', NULL, 'MENU', NULL, 4);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_dir_create', 'm_directory', '新建目录', 'directory:create', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_dir_edit', 'm_directory', '编辑目录', 'directory:edit', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_dir_delete', 'm_directory', '删除目录', 'directory:delete', 'BTN', NULL, 3);

-- 2.5 回收站
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_recycle', 'd_cases', '回收站', NULL, 'MENU', '/recycle-bin', 5);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_recycle_restore', 'm_recycle', '恢复', 'recycle:restore', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_recycle_delete', 'm_recycle', '永久删除', 'recycle:delete', 'BTN', NULL, 2);

-- ======================== 三、测试计划 ========================
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('d_plans', NULL, '测试计划', NULL, 'DIR', NULL, 3);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_plans', 'd_plans', '计划列表', NULL, 'MENU', '/test-plans', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_plans_create', 'm_plans', '新建计划', 'plans:create', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_plans_edit', 'm_plans', '编辑计划', 'plans:edit', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_plans_delete', 'm_plans', '删除计划', 'plans:delete', 'BTN', NULL, 3);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_plans_execute', 'm_plans', '执行计划', 'plans:execute', 'BTN', NULL, 4);

-- ======================== 四、系统管理 ========================
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('d_settings', NULL, '系统管理', NULL, 'DIR', NULL, 4);

-- 4.1 成员管理
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_members', 'd_settings', '成员管理', NULL, 'MENU', '/settings/members', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_members_add', 'm_members', '添加成员', 'settings:members:add', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_members_edit', 'm_members', '编辑成员', 'settings:members:edit', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_members_toggle', 'm_members', '禁用/启用成员', 'settings:members:toggle', 'BTN', NULL, 3);

-- 4.2 用例属性管理
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_attrs', 'd_settings', '用例属性管理', NULL, 'MENU', '/settings/attributes', 2);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_attrs_create', 'm_attrs', '新建属性', 'settings:attributes:create', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_attrs_edit', 'm_attrs', '编辑属性', 'settings:attributes:edit', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_attrs_delete', 'm_attrs', '删除属性', 'settings:attributes:delete', 'BTN', NULL, 3);

-- 4.3 项目空间管理
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_projects', 'd_settings', '项目空间管理', NULL, 'MENU', '/settings/projects', 3);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_projects_create', 'm_projects', '新建项目', 'settings:projects:create', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_projects_edit', 'm_projects', '编辑项目', 'settings:projects:edit', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_projects_delete', 'm_projects', '删除项目', 'settings:projects:delete', 'BTN', NULL, 3);

-- 4.4 权限管理
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_rbac', 'd_settings', '权限管理', NULL, 'MENU', '/settings/rbac', 4);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_rbac_role', 'm_rbac', '角色管理', 'settings:rbac:role', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_rbac_menu', 'm_rbac', '菜单管理', 'settings:rbac:menu', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_rbac_user', 'm_rbac', '用户角色分配', 'settings:rbac:user', 'BTN', NULL, 3);

-- 4.5 定时任务
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_jobs', 'd_settings', '定时任务', NULL, 'MENU', '/settings/jobs', 5);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_jobs_create', 'm_jobs', '新建任务', 'settings:jobs:create', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_jobs_edit', 'm_jobs', '编辑任务', 'settings:jobs:edit', 'BTN', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_jobs_delete', 'm_jobs', '删除任务', 'settings:jobs:delete', 'BTN', NULL, 3);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_jobs_run', 'm_jobs', '执行任务', 'settings:jobs:run', 'BTN', NULL, 4);

-- ======================== 五、个人设置 ========================
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('d_personal', NULL, '个人设置', NULL, 'DIR', NULL, 5);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_notification', 'd_personal', '消息通知', NULL, 'MENU', '/notifications', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_notif_view', 'm_notification', '查看通知', 'notification:view', 'BTN', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('b_notif_mark', 'm_notification', '标记已读', 'notification:mark', 'BTN', NULL, 2);


-- ============================================================
-- 初始化默认角色
-- ============================================================
INSERT IGNORE INTO sys_role (id, role_code, role_name, description, sort_order)
VALUES ('role_super_admin', 'SUPER_ADMIN', '超级管理员', '拥有所有权限', 1);
INSERT IGNORE INTO sys_role (id, role_code, role_name, description, sort_order)
VALUES ('role_admin', 'ADMIN', '管理员', '拥有大部分管理权限，不含权限管理和定时任务', 2);
INSERT IGNORE INTO sys_role (id, role_code, role_name, description, sort_order)
VALUES ('role_member', 'MEMBER', '普通成员', '基本操作权限，不含系统管理和永久删除', 3);


-- ============================================================
-- 超级管理员：所有菜单权限
-- ============================================================
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT REPLACE(UUID(), '-', ''), 'role_super_admin', id FROM sys_menu;


-- ============================================================
-- 管理员：排除权限管理和定时任务
-- ============================================================
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT REPLACE(UUID(), '-', ''), 'role_admin', id FROM sys_menu
WHERE id NOT IN (
    'm_rbac', 'b_rbac_role', 'b_rbac_menu', 'b_rbac_user',
    'm_jobs', 'b_jobs_create', 'b_jobs_edit', 'b_jobs_delete', 'b_jobs_run'
);


-- ============================================================
-- 普通成员：用例管理（不含删除）+ 测试计划（不含删除）+ 个人设置
-- 不含：系统管理全部、回收站永久删除
-- ============================================================
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
-- 工作台
(REPLACE(UUID(), '-', ''), 'role_member', 'd_dashboard'), (REPLACE(UUID(), '-', ''), 'role_member', 'm_dashboard_view'),
-- 用例管理目录
(REPLACE(UUID(), '-', ''), 'role_member', 'd_cases'),
-- 用例列表
(REPLACE(UUID(), '-', ''), 'role_member', 'm_cases'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_cases_create'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_cases_edit'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_cases_export'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_cases_import'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_cases_move'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_cases_copy'),
-- 思维导图
(REPLACE(UUID(), '-', ''), 'role_member', 'm_mindmap'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_mindmap_edit'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_mindmap_save'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_mindmap_export'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_mindmap_import'),
-- 评审管理
(REPLACE(UUID(), '-', ''), 'role_member', 'm_review'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_review_submit'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_review_approve'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_review_comment'),
-- 目录管理
(REPLACE(UUID(), '-', ''), 'role_member', 'm_directory'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_dir_create'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_dir_edit'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_dir_delete'),
-- 回收站（仅恢复，不含永久删除）
(REPLACE(UUID(), '-', ''), 'role_member', 'm_recycle'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_recycle_restore'),
-- 测试计划
(REPLACE(UUID(), '-', ''), 'role_member', 'd_plans'), (REPLACE(UUID(), '-', ''), 'role_member', 'm_plans'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_plans_create'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_plans_edit'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_plans_execute'),
-- 个人设置
(REPLACE(UUID(), '-', ''), 'role_member', 'd_personal'), (REPLACE(UUID(), '-', ''), 'role_member', 'm_notification'),
(REPLACE(UUID(), '-', ''), 'role_member', 'b_notif_view'), (REPLACE(UUID(), '-', ''), 'role_member', 'b_notif_mark');
