-- 初始化系统菜单权限数据（仅在 sys_menu 表为空时插入）

-- 一级菜单：工作台
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_dashboard', NULL, '工作台', NULL, 'MENU', '/dashboard', 1);

-- 一级菜单：用例管理
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_cases', NULL, '用例管理', NULL, 'MENU', '/cases', 2);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_cases_create', 'm_cases', '新建用例集', 'cases:create', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_cases_edit', 'm_cases', '编辑用例集', 'cases:edit', 'BTN', NULL, 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_cases_delete', 'm_cases', '删除用例集', 'cases:delete', 'BTN', NULL, 3);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_cases_export', 'm_cases', '导出Excel', 'cases:export', 'BTN', NULL, 4);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_cases_import', 'm_cases', '导入Excel', 'cases:import', 'BTN', NULL, 5);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_cases_move', 'm_cases', '移动用例集', 'cases:move', 'BTN', NULL, 6);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_cases_copy', 'm_cases', '复制用例集', 'cases:copy', 'BTN', NULL, 7);

-- 一级菜单：思维导图
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_mindmap', NULL, '思维导图', NULL, 'MENU', '/mind-map', 3);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_mindmap_edit', 'm_mindmap', '编辑节点', 'mindmap:edit', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_mindmap_save', 'm_mindmap', '保存/同步', 'mindmap:save', 'BTN', NULL, 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_mindmap_export', 'm_mindmap', '导出Excel', 'mindmap:export', 'BTN', NULL, 3);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_mindmap_import', 'm_mindmap', '导入Excel', 'mindmap:import', 'BTN', NULL, 4);

-- 一级菜单：评审
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_review', NULL, '评审管理', NULL, 'MENU', '/review', 4);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_review_submit', 'm_review', '提交评审', 'review:submit', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_review_approve', 'm_review', '审批评审', 'review:approve', 'BTN', NULL, 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_review_comment', 'm_review', '评论', 'review:comment', 'BTN', NULL, 3);

-- 一级菜单：测试计划
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_plans', NULL, '测试计划', NULL, 'MENU', '/test-plans', 5);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_plans_create', 'm_plans', '新建计划', 'plans:create', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_plans_edit', 'm_plans', '编辑计划', 'plans:edit', 'BTN', NULL, 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_plans_delete', 'm_plans', '删除计划', 'plans:delete', 'BTN', NULL, 3);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_plans_execute', 'm_plans', '执行计划', 'plans:execute', 'BTN', NULL, 4);

-- 一级菜单：回收站
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_recycle', NULL, '回收站', NULL, 'MENU', '/recycle-bin', 6);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_recycle_restore', 'm_recycle', '恢复', 'recycle:restore', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_recycle_delete', 'm_recycle', '永久删除', 'recycle:delete', 'BTN', NULL, 2);

-- 一级菜单：目录管理
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_directory', NULL, '目录管理', NULL, 'DIR', NULL, 7);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_dir_create', 'm_directory', '新建目录', 'directory:create', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_dir_edit', 'm_directory', '编辑目录', 'directory:edit', 'BTN', NULL, 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_dir_delete', 'm_directory', '删除目录', 'directory:delete', 'BTN', NULL, 3);

-- 一级菜单：系统设置
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings', NULL, '系统设置', 'settings:*', 'MENU', '/settings', 10);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_members', 'm_settings', '成员管理', 'settings:members', 'MENU', '/settings/members', 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_members_add', 'm_settings_members', '添加成员', 'settings:members:add', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_members_disable', 'm_settings_members', '禁用/启用成员', 'settings:members:toggle', 'BTN', NULL, 2);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_attrs', 'm_settings', '用例属性管理', 'settings:attributes', 'MENU', '/settings/attributes', 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_attrs_create', 'm_settings_attrs', '新建属性', 'settings:attributes:create', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_attrs_edit', 'm_settings_attrs', '编辑属性', 'settings:attributes:edit', 'BTN', NULL, 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_attrs_delete', 'm_settings_attrs', '删除属性', 'settings:attributes:delete', 'BTN', NULL, 3);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_projects', 'm_settings', '项目空间管理', 'settings:projects', 'MENU', '/settings/projects', 3);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_projects_create', 'm_settings_projects', '新建项目', 'settings:projects:create', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_projects_edit', 'm_settings_projects', '编辑项目', 'settings:projects:edit', 'BTN', NULL, 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_projects_delete', 'm_settings_projects', '删除项目', 'settings:projects:delete', 'BTN', NULL, 3);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_rbac', 'm_settings', '权限管理', 'settings:rbac', 'MENU', '/settings/rbac', 4);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_rbac_role', 'm_settings_rbac', '角色管理', 'settings:rbac:role', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_rbac_menu', 'm_settings_rbac', '菜单管理', 'settings:rbac:menu', 'BTN', NULL, 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_rbac_user', 'm_settings_rbac', '用户角色分配', 'settings:rbac:user', 'BTN', NULL, 3);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_jobs', 'm_settings', '定时任务', 'settings:jobs', 'MENU', '/settings/jobs', 5);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_jobs_create', 'm_settings_jobs', '新建任务', 'settings:jobs:create', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_jobs_edit', 'm_settings_jobs', '编辑任务', 'settings:jobs:edit', 'BTN', NULL, 2);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_jobs_delete', 'm_settings_jobs', '删除任务', 'settings:jobs:delete', 'BTN', NULL, 3);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_settings_jobs_run', 'm_settings_jobs', '执行任务', 'settings:jobs:run', 'BTN', NULL, 4);

-- 一级菜单：通知
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_notification', NULL, '消息通知', NULL, 'DIR', NULL, 8);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_notif_view', 'm_notification', '查看通知', 'notification:view', 'BTN', NULL, 1);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, permission_code, menu_type, path, sort_order)
VALUES ('m_notif_mark', 'm_notification', '标记已读', 'notification:mark', 'BTN', NULL, 2);

-- 初始化默认角色（如不存在）
INSERT IGNORE INTO sys_role (id, role_code, role_name, description, sort_order)
VALUES ('role_super_admin', 'SUPER_ADMIN', '超级管理员', '拥有所有权限', 1);
INSERT IGNORE INTO sys_role (id, role_code, role_name, description, sort_order)
VALUES ('role_admin', 'ADMIN', '管理员', '拥有大部分管理权限', 2);
INSERT IGNORE INTO sys_role (id, role_code, role_name, description, sort_order)
VALUES ('role_member', 'MEMBER', '普通成员', '基本操作权限', 3);

-- 为超级管理员分配所有菜单权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 'role_super_admin', id FROM sys_menu;

-- 管理员：拥有除权限管理、定时任务外的所有权限（包括系统设置中的成员、属性、项目管理）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 'role_admin', id FROM sys_menu
WHERE id NOT IN ('m_settings_rbac', 'm_settings_rbac_role', 'm_settings_rbac_menu', 'm_settings_rbac_user',
                  'm_settings_jobs', 'm_settings_jobs_create', 'm_settings_jobs_edit', 'm_settings_jobs_delete', 'm_settings_jobs_run');

-- 普通成员：基本的增删改查操作，不含系统设置和回收站永久删除
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
('role_member', 'm_dashboard'),
('role_member', 'm_cases'), ('role_member', 'm_cases_create'), ('role_member', 'm_cases_edit'),
('role_member', 'm_cases_export'), ('role_member', 'm_cases_import'),
('role_member', 'm_cases_move'), ('role_member', 'm_cases_copy'),
('role_member', 'm_mindmap'), ('role_member', 'm_mindmap_edit'), ('role_member', 'm_mindmap_save'),
('role_member', 'm_mindmap_export'), ('role_member', 'm_mindmap_import'),
('role_member', 'm_review'), ('role_member', 'm_review_submit'), ('role_member', 'm_review_approve'), ('role_member', 'm_review_comment'),
('role_member', 'm_plans'), ('role_member', 'm_plans_create'), ('role_member', 'm_plans_edit'), ('role_member', 'm_plans_execute'),
('role_member', 'm_recycle'), ('role_member', 'm_recycle_restore'),
('role_member', 'm_directory'), ('role_member', 'm_dir_create'), ('role_member', 'm_dir_edit'), ('role_member', 'm_dir_delete'),
('role_member', 'm_notification'), ('role_member', 'm_notif_view'), ('role_member', 'm_notif_mark');
