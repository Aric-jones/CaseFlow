<template>
  <div class="page-wrap" style="padding:20px;background:#f0f2f5;height:100%;overflow:auto">
    <div class="content-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">

        <!-- ==================== 角色管理 ==================== -->
        <el-tab-pane label="角色管理" name="roles">
          <div class="tab-toolbar">
            <el-button type="primary" :icon="Plus" @click="openRoleForm()">新增角色</el-button>
          </div>
          <el-table :data="roles" v-loading="rolesLoading" border style="width:100%">
            <el-table-column label="角色名称" prop="roleName" min-width="120" />
            <el-table-column label="权限字符" prop="roleCode" min-width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ row.roleCode }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="描述" prop="description" min-width="180" show-overflow-tooltip />
            <el-table-column label="排序" prop="sortOrder" min-width="60" align="center" />
            <el-table-column label="用户数" prop="userCount" min-width="80" align="center" />
            <el-table-column label="权限菜单" min-width="250">
              <template #default="{ row }">
                <el-tag v-for="mid in (row.menuIds || []).slice(0, 5)" :key="mid" size="small"
                  style="margin:2px" :type="getMenuTagType(mid)">{{ getMenuName(mid) }}</el-tag>
                <el-tag v-if="(row.menuIds || []).length > 5" size="small" type="info" style="margin:2px">
                  +{{ row.menuIds.length - 5 }}
                </el-tag>
                <span v-if="!row.menuIds?.length" style="color:#c0c4cc;font-size:12px">未分配</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="200">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openRoleForm(row)">编辑</el-button>
                <el-button text type="primary" size="small" @click="openRoleMenus(row)">分配权限</el-button>
                <el-popconfirm title="确认删除该角色？" @confirm="deleteRole(row.id)">
                  <template #reference>
                    <el-button text type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ==================== 菜单管理 ==================== -->
        <el-tab-pane label="菜单管理" name="menus">
          <div class="tab-toolbar">
            <el-button type="primary" :icon="Plus" @click="openMenuForm()">新增菜单</el-button>
            <el-button :icon="Sort" @click="toggleMenuExpand">
              {{ menuExpandAll ? '折叠全部' : '展开全部' }}
            </el-button>
          </div>
          <el-table ref="menuTableRef" :data="menuTree" v-loading="menusLoading" row-key="id" border
            :default-expand-all="menuExpandAll"
            :tree-props="{ children: 'children' }" style="width:100%">
            <el-table-column label="菜单名称" prop="menuName" min-width="200" />
            <el-table-column label="权限标识" min-width="160">
              <template #default="{ row }">
                <el-tag v-if="row.permissionCode" size="small" type="info">{{ row.permissionCode }}</el-tag>
                <span v-else style="color:#c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="类型" min-width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="menuTypeTag(row.menuType)" size="small">{{ menuTypeLabel(row.menuType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="排序" prop="sortOrder" min-width="60" align="center" />
            <el-table-column label="路由" prop="path" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" min-width="200">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openMenuForm(row)">编辑</el-button>
                <el-button v-if="row.menuType !== 'BTN'" text type="primary" size="small"
                  @click="openMenuForm(undefined, row.id)">新增子菜单</el-button>
                <el-popconfirm title="确认删除该菜单？" @confirm="deleteMenu(row.id)">
                  <template #reference>
                    <el-button text type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ==================== 用户角色 ==================== -->
        <el-tab-pane label="用户角色" name="users">
          <el-table :data="usersWithRoles" v-loading="usersLoading" border style="width:100%">
            <el-table-column label="用户名" prop="username" min-width="100" />
            <el-table-column label="显示名" prop="displayName" min-width="100" />
            <el-table-column label="状态" min-width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="角色" min-width="200">
              <template #default="{ row }">
                <el-tag v-for="(name, i) in row.roleNames" :key="i" size="small"
                  :type="getRoleTagType(row.roleIds[i])" style="margin:2px">{{ name }}</el-tag>
                <span v-if="!row.roleNames?.length" style="color:#c0c4cc;font-size:12px">未分配角色</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="100">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openUserRoles(row)">分配角色</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ==================== 角色表单弹窗 ==================== -->
    <el-dialog v-model="roleForm.visible" :title="roleForm.id ? '编辑角色' : '新增角色'" width="520px">
      <el-form :model="roleForm" label-width="90px">
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.roleName" placeholder="如：超级管理员" />
        </el-form-item>
        <el-form-item label="权限字符" required>
          <el-input v-model="roleForm.roleCode" placeholder="如：SUPER_ADMIN"
            :disabled="!!roleForm.id" />
        </el-form-item>
        <el-form-item label="角色描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="2" placeholder="角色描述（可选）" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="roleForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="roleForm.saving" @click="saveRole">确定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 角色分配权限弹窗 ==================== -->
    <el-dialog v-model="roleMenuDlg.visible" :title="'分配权限 - ' + roleMenuDlg.roleName" width="520px">
      <div style="margin-bottom:8px">
        <el-checkbox v-model="roleMenuDlg.checkAll" @change="onCheckAllMenus">全选/全不选</el-checkbox>
        <el-checkbox v-model="roleMenuDlg.expandAll" @change="onExpandAllMenus">展开/折叠</el-checkbox>
      </div>
      <el-tree ref="roleMenuTreeRef" :data="menuTree" show-checkbox node-key="id"
        :default-checked-keys="roleMenuDlg.checkedIds"
        :default-expand-all="roleMenuDlg.expandAll"
        :props="{ label: 'menuName', children: 'children' }" />
      <template #footer>
        <el-button @click="roleMenuDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="roleMenuDlg.saving" @click="saveRoleMenus">确定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 菜单表单弹窗 ==================== -->
    <el-dialog v-model="menuForm.visible" :title="menuForm.id ? '编辑菜单' : '新增菜单'" width="580px">
      <el-form :model="menuForm" label-width="90px">
        <el-form-item label="上级菜单">
          <el-tree-select v-model="menuForm.parentId" :data="menuSelectTree"
            :render-after-expand="false" default-expand-all check-strictly
            clearable placeholder="顶级菜单" style="width:100%" />
        </el-form-item>
        <el-form-item label="菜单类型" required>
          <el-radio-group v-model="menuForm.menuType">
            <el-radio value="DIR">目录</el-radio>
            <el-radio value="MENU">菜单</el-radio>
            <el-radio value="BTN">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" required>
          <el-input v-model="menuForm.menuName" placeholder="如：用例管理" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="menuForm.permissionCode" placeholder="如：case:delete（按钮权限必填）" />
        </el-form-item>
        <el-form-item v-if="menuForm.menuType !== 'BTN'" label="路由地址">
          <el-input v-model="menuForm.path" placeholder="如：/settings/members" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="menuForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="menuForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="menuForm.saving" @click="saveMenu">确定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 用户分配角色弹窗 ==================== -->
    <el-dialog v-model="userRoleDlg.visible" :title="'分配角色 - ' + userRoleDlg.displayName" width="420px">
      <el-checkbox-group v-model="userRoleDlg.checkedRoleIds">
        <div v-for="r in roles" :key="r.id" style="margin-bottom:12px">
          <el-checkbox :value="r.id">
            <strong>{{ r.roleName }}</strong>
            <span style="color:#909399;font-size:12px;margin-left:8px">{{ r.roleCode }}</span>
          </el-checkbox>
          <div style="color:#909399;font-size:12px;padding-left:24px">{{ r.description }}</div>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="userRoleDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="userRoleDlg.saving" @click="saveUserRoles">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus, Sort } from '@element-plus/icons-vue';
import { rbacApi } from '../../api';

const activeTab = ref('roles');
const rolesLoading = ref(false);
const usersLoading = ref(false);
const menusLoading = ref(false);

const roles = ref<any[]>([]);
const menus = ref<any[]>([]);
const menuTree = ref<any[]>([]);
const usersWithRoles = ref<any[]>([]);
const menuMap = ref<Record<string, any>>({});
const menuExpandAll = ref(true);
const menuTableRef = ref<any>(null);

const roleMenuTreeRef = ref<any>(null);

function buildMenuTree(list: any[]): any[] {
  const map: Record<string, any> = {};
  const roots: any[] = [];
  for (const m of list) { map[m.id] = { ...m, children: [] }; menuMap.value[m.id] = m; }
  for (const m of list) {
    if (m.parentId && map[m.parentId]) map[m.parentId].children.push(map[m.id]);
    else roots.push(map[m.id]);
  }
  return roots;
}

function toggleMenuExpand() {
  menuExpandAll.value = !menuExpandAll.value;
  const expand = menuExpandAll.value;
  function walkRows(rows: any[]) {
    for (const row of rows) {
      menuTableRef.value?.toggleRowExpansion(row, expand);
      if (row.children?.length) walkRows(row.children);
    }
  }
  if (menuTableRef.value) walkRows(menuTree.value);
}

const menuSelectTree = computed(() => {
  function toSelect(nodes: any[]): any[] {
    return nodes.map(n => ({
      value: n.id, label: n.menuName,
      children: n.children?.length ? toSelect(n.children) : undefined,
    }));
  }
  return toSelect(menuTree.value);
});

function getMenuName(id: string) { return menuMap.value[id]?.menuName || id; }
function getMenuTagType(id: string): any {
  const m = menuMap.value[id]; if (!m) return 'info';
  return m.menuType === 'BTN' ? 'danger' : m.menuType === 'DIR' ? 'warning' : 'primary';
}
function getRoleTagType(roleId: string): any {
  const r = roles.value.find((x: any) => x.id === roleId);
  if (!r) return 'info';
  if (r.roleCode === 'SUPER_ADMIN') return 'danger';
  if (r.roleCode === 'ADMIN') return 'warning';
  return 'info';
}
function menuTypeLabel(t: string) { return ({ DIR: '目录', MENU: '菜单', BTN: '按钮' } as any)[t] || t; }
function menuTypeTag(t: string): any { return ({ DIR: 'warning', MENU: 'primary', BTN: 'danger' } as any)[t] || 'info'; }

async function loadRoles() {
  rolesLoading.value = true;
  try { roles.value = (await rbacApi.getRoles()).data; } finally { rolesLoading.value = false; }
}
async function loadMenus() {
  menusLoading.value = true;
  try { menus.value = (await rbacApi.getMenus()).data; menuTree.value = buildMenuTree(menus.value); } finally { menusLoading.value = false; }
}
async function loadUsers() {
  usersLoading.value = true;
  try { usersWithRoles.value = (await rbacApi.getUsersWithRoles()).data; } finally { usersLoading.value = false; }
}

function onTabChange(tab: string) {
  if (tab === 'roles') loadRoles();
  else if (tab === 'menus') loadMenus();
  else if (tab === 'users') loadUsers();
}

// =============== 角色 CRUD ===============
const roleForm = ref({
  visible: false, saving: false, id: '', roleName: '', roleCode: '', description: '', sortOrder: 0,
});

function openRoleForm(row?: any) {
  if (row) {
    roleForm.value = { visible: true, saving: false, id: row.id, roleName: row.roleName, roleCode: row.roleCode, description: row.description || '', sortOrder: row.sortOrder ?? 0 };
  } else {
    roleForm.value = { visible: true, saving: false, id: '', roleName: '', roleCode: '', description: '', sortOrder: 0 };
  }
}

async function saveRole() {
  const f = roleForm.value;
  if (!f.roleName.trim() || !f.roleCode.trim()) { ElMessage.error('角色名称和权限字符必填'); return; }
  f.saving = true;
  try {
    if (f.id) {
      await rbacApi.updateRole(f.id, { roleName: f.roleName, roleCode: f.roleCode, description: f.description, sortOrder: f.sortOrder });
    } else {
      await rbacApi.createRole({ roleName: f.roleName, roleCode: f.roleCode, description: f.description, sortOrder: f.sortOrder });
    }
    ElMessage.success(f.id ? '更新成功' : '创建成功');
    f.visible = false;
    loadRoles();
  } finally { f.saving = false; }
}

async function deleteRole(id: string) {
  try { await rbacApi.deleteRole(id); ElMessage.success('删除成功'); loadRoles(); } catch { /* */ }
}

// =============== 角色分配权限 ===============
const roleMenuDlg = ref({
  visible: false, saving: false, roleId: '', roleName: '', checkedIds: [] as string[],
  checkAll: false, expandAll: true,
});

function openRoleMenus(row: any) {
  roleMenuDlg.value = {
    visible: true, saving: false, roleId: row.id, roleName: row.roleName,
    checkedIds: [...(row.menuIds || [])], checkAll: false, expandAll: true,
  };
  nextTick(() => {
    roleMenuTreeRef.value?.setCheckedKeys(row.menuIds || []);
  });
}

function onCheckAllMenus(val: boolean) {
  if (val) {
    const allIds = menus.value.map(m => m.id);
    roleMenuTreeRef.value?.setCheckedKeys(allIds);
  } else {
    roleMenuTreeRef.value?.setCheckedKeys([]);
  }
}

function onExpandAllMenus(val: boolean) {
  const tree = roleMenuTreeRef.value;
  if (!tree) return;
  const nodes = tree.store._getAllNodes();
  for (const n of nodes) n.expanded = val;
}

async function saveRoleMenus() {
  roleMenuDlg.value.saving = true;
  try {
    const leafChecked = roleMenuTreeRef.value?.getCheckedKeys(true) || [];
    await rbacApi.updateRoleMenus(roleMenuDlg.value.roleId, leafChecked);
    ElMessage.success('权限已更新');
    roleMenuDlg.value.visible = false;
    loadRoles();
  } finally { roleMenuDlg.value.saving = false; }
}

// =============== 菜单 CRUD ===============
const menuForm = ref({
  visible: false, saving: false, id: '', parentId: null as string | null,
  menuName: '', menuType: 'MENU', permissionCode: '', path: '', sortOrder: 0,
});

function openMenuForm(row?: any, parentId?: string) {
  if (row) {
    menuForm.value = {
      visible: true, saving: false, id: row.id, parentId: row.parentId || null,
      menuName: row.menuName, menuType: row.menuType, permissionCode: row.permissionCode || '',
      path: row.path || '', sortOrder: row.sortOrder ?? 0,
    };
  } else {
    menuForm.value = {
      visible: true, saving: false, id: '', parentId: parentId || null,
      menuName: '', menuType: 'MENU', permissionCode: '', path: '', sortOrder: 0,
    };
  }
}

async function saveMenu() {
  const f = menuForm.value;
  if (!f.menuName.trim()) { ElMessage.error('菜单名称必填'); return; }
  f.saving = true;
  try {
    const data = { menuName: f.menuName, menuType: f.menuType, parentId: f.parentId, permissionCode: f.permissionCode || null, path: f.path || null, sortOrder: f.sortOrder };
    if (f.id) {
      await rbacApi.updateMenu(f.id, data);
    } else {
      await rbacApi.createMenu(data);
    }
    ElMessage.success(f.id ? '更新成功' : '创建成功');
    f.visible = false;
    loadMenus();
  } finally { f.saving = false; }
}

async function deleteMenu(id: string) {
  try { await rbacApi.deleteMenu(id); ElMessage.success('删除成功'); loadMenus(); } catch { /* */ }
}

// =============== 用户角色 ===============
const userRoleDlg = ref({
  visible: false, saving: false, userId: '', displayName: '', checkedRoleIds: [] as string[],
});

function openUserRoles(user: any) {
  userRoleDlg.value = {
    visible: true, saving: false, userId: user.id, displayName: user.displayName,
    checkedRoleIds: [...(user.roleIds || [])],
  };
}

async function saveUserRoles() {
  userRoleDlg.value.saving = true;
  try {
    await rbacApi.updateUserRoles(userRoleDlg.value.userId, userRoleDlg.value.checkedRoleIds);
    ElMessage.success('角色已更新');
    userRoleDlg.value.visible = false;
    loadUsers();
  } finally { userRoleDlg.value.saving = false; }
}

onMounted(() => { loadRoles(); loadMenus(); loadUsers(); });
</script>

<style scoped>
.content-card {
  background: #fff; border-radius: 10px; padding: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.tab-toolbar {
  display: flex; gap: 10px; margin-bottom: 16px;
}
</style>
