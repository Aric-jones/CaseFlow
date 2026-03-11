import request from './request';
import type {
  ApiResult, CaseSet, CommentData, CustomAttribute, DirectoryNode,
  MindNodeData, PageResult, Project, RecycleBinItem, ReviewAssignment,
  TestPlan, TestPlanCase, User, ValidationResult, CaseHistory,
} from '../types';

type R<T> = Promise<ApiResult<T>>;

export const authApi = {
  login: (username: string, password: string): R<{ token: string; userId: string; username: string; displayName: string; role: string }> =>
    request.post('/auth/login', { username, password }),
  currentUser: (): R<User> => request.get('/auth/current-user'),
};

export const userApi = {
  list: (page = 1, size = 20, keyword?: string): R<PageResult<User>> =>
    request.get('/users', { params: { page, size, keyword } }),
  create: (data: any): R<User> => request.post('/users', data),
  update: (id: string, data: any): R<User> => request.put(`/users/${id}`, data),
  toggleStatus: (id: string): R<void> => request.put(`/users/${id}/status`),
  listAll: (): R<User[]> => request.get('/users/all'),
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
    request.post('/directories', null, { params: { name, parentId, projectId, dirType } }),
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
  validate: (id: string): R<ValidationResult> => request.get(`/case-sets/${id}/validate`),
  updateRequirement: (id: string, link: string): R<void> =>
    request.put(`/case-sets/${id}/requirement`, null, { params: { link } }),
  importExcel: (file: File, directoryId: string, projectId: string): R<void> => {
    const fd = new FormData(); fd.append('file', file);
    return request.post('/case-sets/import', fd, { params: { directoryId, projectId } });
  },
};

export const mindNodeApi = {
  tree: (caseSetId: string): R<MindNodeData[]> =>
    request.get('/mind-nodes/tree', { params: { caseSetId } }),
  batchSave: (caseSetId: string, nodes: MindNodeData[]): R<void> =>
    request.post('/mind-nodes/batch-save', nodes, { params: { caseSetId } }),
  create: (node: any): R<MindNodeData> => request.post('/mind-nodes', node),
  update: (id: string, node: any): R<MindNodeData> => request.put(`/mind-nodes/${id}`, node),
  delete: (id: string): R<void> => request.delete(`/mind-nodes/${id}`),
  countCases: (caseSetId: string): R<number> => request.get('/mind-nodes/count', { params: { caseSetId } }),
};

export const commentApi = {
  nodeComments: (nodeId: string): R<CommentData[]> => request.get('/comments/node', { params: { nodeId } }),
  allComments: (caseSetId: string): R<CommentData[]> => request.get('/comments/all', { params: { caseSetId } }),
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
  create: (data: any): R<TestPlan> => request.post('/test-plans', data),
  getCases: (id: string): R<TestPlanCase[]> => request.get(`/test-plans/${id}/cases`),
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

export const recycleBinApi = {
  list: (projectId: string): R<RecycleBinItem[]> => request.get('/recycle-bin', { params: { projectId } }),
  restore: (id: string): R<void> => request.post(`/recycle-bin/${id}/restore`),
  permanentDelete: (id: string): R<void> => request.delete(`/recycle-bin/${id}`),
};
