import request from './request';
import type {
  ApiResult, CaseSet, CommentData, CustomAttribute, DirectoryNode,
  MindNodeData, PageResult, Project, RecycleBinItem, ReviewAssignment,
  TestPlan, TestPlanCase, User, ValidationResult, CaseHistory,
} from '../types';

type R<T> = Promise<ApiResult<T>>;

export const authApi = {
  login: (username: string, password: string): R<{ token: string; userId: number; username: string; displayName: string; role: string }> =>
    request.post('/auth/login', { username, password }),
  currentUser: (): R<User> => request.get('/auth/current-user'),
};

export const userApi = {
  list: (page = 1, size = 20, keyword?: string): R<PageResult<User>> =>
    request.get('/users', { params: { page, size, keyword } }),
  create: (data: Partial<User & { password: string; projectIds: number[] }>): R<User> =>
    request.post('/users', data),
  update: (id: number, data: Partial<User>): R<User> =>
    request.put(`/users/${id}`, data),
  toggleStatus: (id: number): R<void> => request.put(`/users/${id}/status`),
  listAll: (): R<User[]> => request.get('/users/all'),
};

export const projectApi = {
  list: (): R<Project[]> => request.get('/projects'),
  listAll: (): R<Project[]> => request.get('/projects/all'),
  create: (name: string, description?: string): R<Project> =>
    request.post('/projects', null, { params: { name, description } }),
  update: (id: number, name: string, description?: string): R<Project> =>
    request.put(`/projects/${id}`, null, { params: { name, description } }),
  delete: (id: number): R<void> => request.delete(`/projects/${id}`),
  getMembers: (id: number): R<number[]> => request.get(`/projects/${id}/members`),
  addMember: (id: number, userId: number): R<void> =>
    request.post(`/projects/${id}/members`, null, { params: { userId } }),
  removeMember: (id: number, userId: number): R<void> =>
    request.delete(`/projects/${id}/members/${userId}`),
};

export const directoryApi = {
  tree: (projectId: number, dirType: string): R<DirectoryNode[]> =>
    request.get('/directories/tree', { params: { projectId, dirType } }),
  create: (name: string, parentId: number | null, projectId: number, dirType: string): R<DirectoryNode> =>
    request.post('/directories', null, { params: { name, parentId, projectId, dirType } }),
  rename: (id: number, name: string): R<void> =>
    request.put(`/directories/${id}/rename`, null, { params: { name } }),
  move: (id: number, newParentId: number): R<void> =>
    request.put(`/directories/${id}/move`, null, { params: { newParentId } }),
  delete: (id: number): R<void> => request.delete(`/directories/${id}`),
};

export const caseSetApi = {
  list: (params: { directoryId?: number; projectId?: number; keyword?: string; status?: string; page?: number; size?: number }): R<PageResult<CaseSet>> =>
    request.get('/case-sets', { params }),
  get: (id: number): R<CaseSet> => request.get(`/case-sets/${id}`),
  create: (data: { name: string; directoryId: number; projectId: number; requirementLink?: string }): R<CaseSet> =>
    request.post('/case-sets', data),
  updateStatus: (id: number, status: string, reviewerIds?: number[]): R<void> =>
    request.put(`/case-sets/${id}/status`, reviewerIds, { params: { status } }),
  move: (id: number, targetDirectoryId: number): R<void> =>
    request.put(`/case-sets/${id}/move`, null, { params: { targetDirectoryId } }),
  copy: (id: number, targetDirectoryId: number): R<CaseSet> =>
    request.post(`/case-sets/${id}/copy`, null, { params: { targetDirectoryId } }),
  delete: (id: number): R<void> => request.delete(`/case-sets/${id}`),
  validate: (id: number): R<ValidationResult> => request.get(`/case-sets/${id}/validate`),
  updateRequirement: (id: number, link: string): R<void> =>
    request.put(`/case-sets/${id}/requirement`, null, { params: { link } }),
  importExcel: (file: File, directoryId: number, projectId: number): R<void> => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post('/case-sets/import', formData, { params: { directoryId, projectId } });
  },
};

export const mindNodeApi = {
  tree: (caseSetId: number): R<MindNodeData[]> =>
    request.get('/mind-nodes/tree', { params: { caseSetId } }),
  batchSave: (caseSetId: number, nodes: MindNodeData[]): R<void> =>
    request.post('/mind-nodes/batch-save', nodes, { params: { caseSetId } }),
  create: (node: Partial<MindNodeData>): R<MindNodeData> => request.post('/mind-nodes', node),
  update: (id: number, node: Partial<MindNodeData>): R<MindNodeData> => request.put(`/mind-nodes/${id}`, node),
  delete: (id: number): R<void> => request.delete(`/mind-nodes/${id}`),
  countCases: (caseSetId: number): R<number> => request.get('/mind-nodes/count', { params: { caseSetId } }),
};

export const commentApi = {
  nodeComments: (nodeId: number): R<CommentData[]> => request.get('/comments/node', { params: { nodeId } }),
  allComments: (caseSetId: number): R<CommentData[]> => request.get('/comments/all', { params: { caseSetId } }),
  add: (nodeId: number, caseSetId: number, content: string, parentId?: number): R<CommentData> =>
    request.post('/comments', { nodeId, caseSetId, content, parentId }),
  update: (id: number, content: string): R<void> => request.put(`/comments/${id}`, { content }),
  delete: (id: number): R<void> => request.delete(`/comments/${id}`),
  resolve: (id: number): R<void> => request.put(`/comments/${id}/resolve`),
};

export const caseHistoryApi = {
  save: (caseSetId: number): R<void> => request.post('/case-history/save', null, { params: { caseSetId } }),
  list: (caseSetId: number, limit = 3): R<CaseHistory[]> =>
    request.get('/case-history', { params: { caseSetId, limit } }),
  restore: (id: number): R<void> => request.post(`/case-history/${id}/restore`),
};

export const reviewApi = {
  list: (caseSetId: number): R<ReviewAssignment[]> => request.get('/reviews', { params: { caseSetId } }),
  updateStatus: (id: number, status: string): R<void> =>
    request.put(`/reviews/${id}`, null, { params: { status } }),
};

export const testPlanApi = {
  list: (params: { projectId: number; directoryId?: number; keyword?: string; onlyMine?: boolean; page?: number; size?: number }): R<PageResult<TestPlan>> =>
    request.get('/test-plans', { params }),
  get: (id: number): R<TestPlan> => request.get(`/test-plans/${id}`),
  create: (data: { name: string; directoryId?: number; projectId: number; executorIds: number[]; cases: { nodeId: number; caseSetId: number; executorId?: number }[] }): R<TestPlan> =>
    request.post('/test-plans', data),
  getCases: (id: number): R<TestPlanCase[]> => request.get(`/test-plans/${id}/cases`),
  executeCase: (id: number, result: string, reason?: string): R<void> =>
    request.put(`/test-plans/cases/${id}/execute`, { result, reason }),
  removeCase: (id: number): R<void> => request.delete(`/test-plans/cases/${id}`),
};

export const customAttributeApi = {
  list: (projectId: number): R<CustomAttribute[]> => request.get('/custom-attributes', { params: { projectId } }),
  create: (data: Partial<CustomAttribute>): R<CustomAttribute> => request.post('/custom-attributes', data),
  update: (id: number, data: Partial<CustomAttribute>): R<CustomAttribute> => request.put(`/custom-attributes/${id}`, data),
  delete: (id: number): R<void> => request.delete(`/custom-attributes/${id}`),
};

export const recycleBinApi = {
  list: (projectId: number): R<RecycleBinItem[]> => request.get('/recycle-bin', { params: { projectId } }),
  restore: (id: number): R<void> => request.post(`/recycle-bin/${id}/restore`),
  permanentDelete: (id: number): R<void> => request.delete(`/recycle-bin/${id}`),
};
