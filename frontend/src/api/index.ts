import request from './request';
import type {
  ApiResult, CaseSet, CommentData, CustomAttribute, DirectoryNode,
  MindNodeData, Notification, PageResult, Project, RecycleBinItem, ReviewAssignment,
  TestPlan, TestPlanCase, User, ValidationResult, CaseHistory,
} from '../types';

type R<T> = Promise<ApiResult<T>>;

export const authApi = {
  login: (username: string, password: string): R<{ token: string; userId: string; username: string; displayName: string; role: string }> =>
    request.post('/auth/login', { username, password }),
  currentUser: (): R<User> => request.get('/auth/current-user'),
  changePassword: (oldPassword: string, newPassword: string): R<void> =>
    request.post('/auth/change-password', { oldPassword, newPassword }),
};

export const userApi = {
  list: (page = 1, size = 20, keyword?: string): R<PageResult<User>> =>
    request.get('/users', { params: { page, size, keyword } }),
  create: (data: any): R<User> => request.post('/users', data),
  update: (id: string, data: any): R<User> => request.put(`/users/${id}`, data),
  toggleStatus: (id: string): R<void> => request.put(`/users/${id}/status`),
  listAll: (): R<User[]> => request.get('/users/all'),
  getUserProjects: (id: string): R<string[]> => request.get(`/users/${id}/projects`),
  updateUserProjects: (id: string, projectIds: string[]): R<void> => request.put(`/users/${id}/projects`, projectIds),
  updateDisplayName: (id: string, displayName: string): R<User> => request.put(`/users/${id}`, { displayName }),
};

export const projectApi = {
  list: (): R<Project[]> => request.get('/projects'),
  listAll: (): R<Project[]> => request.get('/projects/all'),
  create: (name: string, description?: string): R<Project> =>
    request.post('/projects', null, { params: { name, description } }),
  update: (id: string, name: string, description?: string): R<Project> =>
    request.put(`/projects/${id}`, null, { params: { name, description } }),
  delete: (id: string): R<void> => request.delete(`/projects/${id}`),
  getMembers: (id: string): R<string[]> => request.get(`/projects/${id}/members`),
  addMember: (id: string, userId: string): R<void> =>
    request.post(`/projects/${id}/members`, null, { params: { userId } }),
  removeMember: (id: string, userId: string): R<void> =>
    request.delete(`/projects/${id}/members/${userId}`),
};

export const directoryApi = {
  tree: (projectId: string, dirType: string): R<DirectoryNode[]> =>
    request.get('/directories/tree', { params: { projectId, dirType } }),
  create: (name: string, parentId: string | null, projectId: string, dirType: string): R<DirectoryNode> =>
    request.post('/directories', null, { params: { name, ...(parentId ? { parentId } : {}), projectId, dirType } }),
  rename: (id: string, name: string): R<void> =>
    request.put(`/directories/${id}/rename`, null, { params: { name } }),
  move: (id: string, newParentId: string): R<void> =>
    request.put(`/directories/${id}/move`, null, { params: { newParentId } }),
  delete: (id: string): R<void> => request.delete(`/directories/${id}`),
};

export const caseSetApi = {
  list: (params: any): R<PageResult<CaseSet>> => request.get('/case-sets', { params }),
  get: (id: string): R<CaseSet> => request.get(`/case-sets/${id}`),
  create: (data: any): R<CaseSet> => request.post('/case-sets', data),
  updateStatus: (id: string, status: string, reviewerIds?: string[]): R<void> =>
    request.put(`/case-sets/${id}/status`, reviewerIds, { params: { status } }),
  move: (id: string, targetDirectoryId: string): R<void> =>
    request.put(`/case-sets/${id}/move`, null, { params: { targetDirectoryId } }),
  copy: (id: string, targetDirectoryId: string): R<CaseSet> =>
    request.post(`/case-sets/${id}/copy`, null, { params: { targetDirectoryId } }),
  delete: (id: string): R<void> => request.delete(`/case-sets/${id}`),
  rename: (id: string, name: string): R<void> =>
    request.put(`/case-sets/${id}/rename`, null, { params: { name } }),
  validate: (id: string): R<ValidationResult> => request.get(`/case-sets/${id}/validate`),
  updateRequirement: (id: string, link: string): R<void> =>
    request.put(`/case-sets/${id}/requirement`, null, { params: { link } }),
  importExcel: (file: File, directoryId: string, projectId: string): R<void> => {
    const fd = new FormData(); fd.append('file', file);
    return request.post('/case-sets/import', fd, { params: { directoryId, projectId } });
  },
};

export const mindNodeApi = {
  tree: (caseSetId: string): R<{ tree: MindNodeData[]; version: number }> =>
    request.get('/mind-nodes/tree', { params: { caseSetId } }),
  batchSave: (caseSetId: string, nodes: MindNodeData[], clientVersion?: number): R<any> =>
    request.post('/mind-nodes/batch-save', nodes, { params: { caseSetId, clientVersion } }),
  create: (node: any): R<MindNodeData> => request.post('/mind-nodes', node),
  update: (id: string, node: any): R<MindNodeData> => request.put(`/mind-nodes/${id}`, node),
  delete: (id: string): R<void> => request.delete(`/mind-nodes/${id}`),
  countCases: (caseSetId: string): R<number> => request.get('/mind-nodes/count', { params: { caseSetId } }),
  exportExcel: (caseSetId: string) =>
    request.get('/mind-nodes/export-excel', { params: { caseSetId }, responseType: 'blob' }),
  importExcel: (file: File, caseSetId: string): R<void> => {
    const fd = new FormData(); fd.append('file', file);
    return request.post('/mind-nodes/import-excel', fd, { params: { caseSetId } });
  },
};

export const commentApi = {
  nodeComments: (nodeId: string): R<CommentData[]> => request.get('/comments/node', { params: { nodeId } }),
  allComments: (caseSetId: string, page = 1, size = 20): R<CommentData[]> =>
    request.get('/comments/all', { params: { caseSetId, page, size } }),
  unresolvedCount: (nodeId: string): R<number> => request.get('/comments/node/count', { params: { nodeId } }),
  add: (nodeId: string, caseSetId: string, content: string, parentId?: string): R<CommentData> =>
    request.post('/comments', { nodeId, caseSetId, content, parentId }),
  update: (id: string, content: string): R<void> => request.put(`/comments/${id}`, { content }),
  delete: (id: string): R<void> => request.delete(`/comments/${id}`),
  resolve: (id: string): R<void> => request.put(`/comments/${id}/resolve`),
};

export const caseHistoryApi = {
  save: (caseSetId: string): R<void> => request.post('/case-history/save', null, { params: { caseSetId } }),
  list: (caseSetId: string, limit = 3): R<CaseHistory[]> =>
    request.get('/case-history', { params: { caseSetId, limit } }),
  restore: (id: string): R<void> => request.post(`/case-history/${id}/restore`),
};

export const reviewApi = {
  list: (caseSetId: string): R<ReviewAssignment[]> => request.get('/reviews', { params: { caseSetId } }),
  updateStatus: (id: string, status: string): R<void> =>
    request.put(`/reviews/${id}`, null, { params: { status } }),
};

export const testPlanApi = {
  list: (params: any): R<PageResult<TestPlan>> => request.get('/test-plans', { params }),
  get: (id: string): R<TestPlan> => request.get(`/test-plans/${id}`),
  getExecutors: (id: string): R<any[]> => request.get(`/test-plans/${id}/executors`),
  getCaseSetIds: (id: string): R<string[]> => request.get(`/test-plans/${id}/case-set-ids`),
  create: (data: any): R<TestPlan> => request.post('/test-plans', data),
  update: (id: string, data: any): R<void> => request.put(`/test-plans/${id}`, data),
  delete: (id: string): R<void> => request.delete(`/test-plans/${id}`),
  getCases: (id: string): R<any[]> => request.get(`/test-plans/${id}/cases`),
  refreshCases: (id: string): R<void> => request.post(`/test-plans/${id}/refresh`),
  getAttributeValues: (caseSetId: string): R<Record<string, string[]>> =>
    request.get('/test-plans/attribute-values', { params: { caseSetId } }),
  previewCases: (caseSetId: string, filters?: Record<string, string[]>): R<any[]> =>
    request.post('/test-plans/preview-cases', { caseSetId, filters }),
  updateCaseExecutor: (id: string, executorId: string | null): R<void> =>
    request.put(`/test-plans/cases/${id}/executor`, { executorId }),
  executeCase: (id: string, result: string, reason?: string): R<void> =>
    request.put(`/test-plans/cases/${id}/execute`, { result, reason }),
  removeCase: (id: string): R<void> => request.delete(`/test-plans/cases/${id}`),
};

export const customAttributeApi = {
  list: (projectId: string): R<CustomAttribute[]> => request.get('/custom-attributes', { params: { projectId } }),
  create: (data: any): R<CustomAttribute> => request.post('/custom-attributes', data),
  update: (id: string, data: any): R<CustomAttribute> => request.put(`/custom-attributes/${id}`, data),
  delete: (id: string): R<void> => request.delete(`/custom-attributes/${id}`),
};

export const rbacApi = {
  getRoles: (): R<any[]> => request.get('/rbac/roles'),
  createRole: (data: any): R<any> => request.post('/rbac/roles', data),
  updateRole: (id: string, data: any): R<void> => request.put(`/rbac/roles/${id}`, data),
  deleteRole: (id: string): R<void> => request.delete(`/rbac/roles/${id}`),
  getMenus: (): R<any[]> => request.get('/rbac/menus'),
  createMenu: (data: any): R<any> => request.post('/rbac/menus', data),
  updateMenu: (id: string, data: any): R<void> => request.put(`/rbac/menus/${id}`, data),
  deleteMenu: (id: string): R<void> => request.delete(`/rbac/menus/${id}`),
  updateRoleMenus: (roleId: string, menuIds: string[]): R<void> =>
    request.put(`/rbac/roles/${roleId}/menus`, menuIds),
  getUsersWithRoles: (): R<any[]> => request.get('/rbac/users'),
  updateUserRoles: (userId: string, roleIds: string[]): R<void> =>
    request.put(`/rbac/users/${userId}/roles`, roleIds),
};

export const recycleBinApi = {
  list: (projectId: string, type = 'CASE_SET'): R<RecycleBinItem[]> =>
    request.get('/recycle-bin', { params: { projectId, type } }),
  restore: (id: string): R<void> => request.post(`/recycle-bin/${id}/restore`),
  permanentDelete: (id: string): R<void> => request.delete(`/recycle-bin/${id}`),
  batchDelete: (ids: string[]): R<void> => request.delete('/recycle-bin/batch', { data: ids }),
  batchRestore: (ids: string[]): R<void> => request.post('/recycle-bin/batch-restore', ids),
};

export const notificationApi = {
  list: (): R<Notification[]> => request.get('/notifications'),
  unreadCount: (): R<number> => request.get('/notifications/unread-count'),
  markRead: (id: string): R<void> => request.put(`/notifications/${id}/read`),
  markAllRead: (): R<void> => request.put('/notifications/read-all'),
};

export const dashboardApi = {
  stats: (projectId: string): R<any> => request.get('/dashboard', { params: { projectId } }),
};
