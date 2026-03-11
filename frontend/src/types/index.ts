export interface User {
  id: number;
  username: string;
  displayName: string;
  role: 'SUPER_ADMIN' | 'ADMIN' | 'MEMBER';
  identity: 'TEST' | 'DEV' | 'PRODUCT';
  status: number;
  createdAt: string;
  updatedAt: string;
}

export interface Project {
  id: number;
  name: string;
  description: string;
  createdBy: number;
  createdAt: string;
}

export interface DirectoryNode {
  id: number;
  name: string;
  parentId: number | null;
  projectId: number;
  dirType: 'CASE' | 'TEST_PLAN';
  sortOrder: number;
  children: DirectoryNode[];
}

export interface CaseSet {
  id: number;
  name: string;
  directoryId: number;
  projectId: number;
  status: 'WRITING' | 'PENDING_REVIEW' | 'NO_REVIEW';
  requirementLink: string;
  caseCount: number;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface MindNodeData {
  id?: number;
  caseSetId?: number;
  parentId?: number | null;
  text: string;
  nodeType: 'ROOT' | 'TITLE' | 'PRECONDITION' | 'STEP' | 'EXPECTED';
  sortOrder: number;
  priority?: string | null;
  mark: string;
  tags?: string[];
  automation?: string | null;
  coverage?: string | null;
  platform?: string[];
  belongsPlatform?: string[];
  children: MindNodeData[];
  commentCount?: number;
}

export interface CommentData {
  id: number;
  nodeId: number;
  caseSetId: number;
  parentId: number | null;
  userId: number;
  username: string;
  displayName: string;
  content: string;
  resolved: number;
  createdAt: string;
  replies: CommentData[];
}

export interface CaseHistory {
  id: number;
  caseSetId: number;
  snapshot: string;
  createdBy: number;
  createdAt: string;
}

export interface ReviewAssignment {
  id: number;
  caseSetId: number;
  reviewerId: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'NEED_MODIFY';
  createdAt: string;
}

export interface TestPlan {
  id: number;
  name: string;
  directoryId: number;
  projectId: number;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface TestPlanCase {
  id: number;
  planId: number;
  nodeId: number;
  caseSetId: number;
  executorId: number | null;
  result: 'PENDING' | 'PASS' | 'FAIL' | 'SKIP';
  reason: string | null;
  executedAt: string | null;
}

export interface CustomAttribute {
  id: number;
  projectId: number;
  name: string;
  options: string[];
  multiSelect: number;
  nodeTypeLimit: string | null;
  displayType: 'DROPDOWN' | 'TILE';
  sortOrder: number;
}

export interface RecycleBinItem {
  id: number;
  caseSetId: number;
  originalDirectoryId: number;
  deletedBy: number;
  deletedAt: string;
}

export interface ValidationResult {
  valid: boolean;
  errorCount: number;
  errors: ValidationError[];
}

export interface ValidationError {
  nodeId: number;
  nodePath: string;
  message: string;
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
