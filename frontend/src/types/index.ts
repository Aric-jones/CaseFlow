export interface User {
  id: string;
  username: string;
  displayName: string;
  role: string;
  identity: string;
  status: number;
  createdAt: string;
  updatedAt: string;
}

export interface Project {
  id: string;
  name: string;
  description: string;
  createdBy: string;
  createdAt: string;
}

export interface DirectoryNode {
  id: string;
  name: string;
  parentId: string | null;
  projectId: string;
  dirType: string;
  sortOrder: number;
  children: DirectoryNode[];
}

export interface CaseSet {
  id: string;
  name: string;
  directoryId: string;
  projectId: string;
  status: string;
  requirementLink: string;
  caseCount: number;
  createdBy: string;
  createdByName: string;
  updatedBy: string;
  updatedByName: string;
  createdAt: string;
  updatedAt: string;
}

export interface MindNodeData {
  id?: string;
  caseSetId?: string;
  parentId?: string | null;
  text: string;
  nodeType?: string | null;
  sortOrder: number;
  isRoot?: number;
  properties?: Record<string, any>;
  children: MindNodeData[];
  commentCount?: number;
}

export interface CommentData {
  id: string;
  nodeId: string;
  caseSetId: string;
  parentId: string | null;
  userId: string;
  username: string;
  displayName: string;
  content: string;
  resolved: number;
  createdAt: string;
  replies: CommentData[];
  nodeText?: string;
}

export interface CaseHistory {
  id: string;
  caseSetId: string;
  snapshot: string;
  createdBy: string;
  createdAt: string;
}

export interface ReviewAssignment {
  id: string;
  caseSetId: string;
  reviewerId: string;
  reviewerName?: string;
  status: string;
  createdAt: string;
}

export interface TestPlan {
  id: string;
  name: string;
  directoryId: string;
  projectId: string;
  status: string;
  executorId: string | null;
  createdBy: string;
  createdByName: string;
  updatedBy: string;
  updatedByName: string;
  createdAt: string;
  updatedAt: string;
}

export interface TestPlanCase {
  id: string;
  planId: string;
  nodeId: string;
  caseSetId: string;
  executorId: string | null;
  result: string;
  reason: string | null;
  executedAt: string | null;
}

export interface CustomAttribute {
  id: string;
  projectId: string;
  name: string;
  options: string[];
  multiSelect: number;
  required: number;
  nodeTypeLimit: string | null;
  displayType: string;
  sortOrder: number;
}

export interface RecycleBinItem {
  id: string;
  /** CASE_SET 或 TEST_PLAN */
  itemType: string;
  /** 业务ID（用例集ID或测试计划ID） */
  itemId?: string;
  /** 删除时记录的名称（用于展示） */
  itemName?: string;
  /** 旧数据兼容字段 */
  caseSetName?: string;
  projectId?: string;
  originalDirectoryId?: string;
  deletedBy: string;
  deletedByName?: string;
  deletedAt: string;
}

export interface ValidationResult {
  valid: boolean;
  errorCount: number;
  errors: { nodeId: string; nodePath: string; message: string }[];
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export interface Notification {
  id: string;
  userId: string;
  type: string;
  title: string;
  content: string;
  link: string;
  isRead: number;
  createdAt: string;
}
