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
  updatedBy?: string;
  updatedByName?: string;
  updatedAt?: string;
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
  filters?: Record<string, Record<string, string[]>>;
  caseSetIds?: string[];
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

// ═══════════════════════════════════════════
//  接口自动化类型
// ═══════════════════════════════════════════

export interface ApiEnv {
  id: string;
  projectId: string;
  name: string;
  baseUrl: string;
  headers?: Record<string, string>;
  variables?: Record<string, string>;
  createdByName?: string;
  createdAt?: string;
}

export interface ApiDef {
  id: string;
  projectId: string;
  directoryId?: string;
  name: string;
  method: string;
  path: string;
  description?: string;
  authType?: string;
  authConfig?: Record<string, any>;
  defaultHeaders?: { key: string; value: string; desc?: string }[];
  defaultParams?: { key: string; value: string; desc?: string }[];
  defaultBodyType?: string;
  defaultBody?: string;
  tags?: string[];
  sortOrder?: number;
  caseCount?: number;
  createdByName?: string;
  createdAt?: string;
}

export interface ApiCaseItem {
  id: string;
  apiId: string;
  name: string;
  description?: string;
  headers?: { key: string; value: string; desc?: string }[];
  queryParams?: { key: string; value: string; desc?: string }[];
  bodyType?: string;
  bodyContent?: string;
  preScript?: Record<string, any>;
  postScript?: Record<string, any>;
  preScriptType?: string;
  postScriptType?: string;
  preScriptContent?: string;
  postScriptContent?: string;
  tags?: string[];
  priority?: string;
  enabled?: number;
  sortOrder?: number;
  assertions?: ApiAssertionItem[];
  createdByName?: string;
  createdAt?: string;
}

export interface ApiAssertionItem {
  id?: string;
  caseId?: string;
  type: string;
  expression?: string;
  operator: string;
  expectedValue?: string;
  sortOrder?: number;
}

export interface ApiScenarioItem {
  id: string;
  projectId: string;
  directoryId?: string;
  name: string;
  description?: string;
  failStrategy?: string;
  timeoutMs?: number;
  tags?: string[];
  stepCount?: number;
  steps?: ApiScenarioStepItem[];
  createdByName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiScenarioStepItem {
  id?: string;
  scenarioId?: string;
  stepType?: string;
  caseId?: string;
  sortOrder?: number;
  overrideHeaders?: { key: string; value: string }[];
  overrideBody?: string;
  preScript?: Record<string, any>;
  postScript?: Record<string, any>;
  scriptContent?: string;
  delayMs?: number;
  retryCount?: number;
  enabled?: number;
  apiName?: string;
  apiMethod?: string;
  apiPath?: string;
  caseName?: string;
}

export interface ApiTestPlanItem {
  id: string;
  projectId: string;
  directoryId?: string;
  name: string;
  description?: string;
  environmentId: string;
  parallel?: number;
  cronExpression?: string;
  status?: string;
  scenarioCount?: number;
  environmentName?: string;
  createdByName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiPlanScenarioItem {
  id?: string;
  planId?: string;
  scenarioId: string;
  sortOrder?: number;
  enabled?: number;
  scenarioName?: string;
  stepCount?: number;
}

export interface ApiExecutionItem {
  id: string;
  projectId: string;
  planId?: string;
  scenarioId?: string;
  caseId?: string;
  environmentId: string;
  triggerType: string;
  status: string;
  totalCases: number;
  passedCases: number;
  failedCases: number;
  errorCases: number;
  skippedCases: number;
  durationMs: number;
  executedByName?: string;
  startedAt: string;
  finishedAt?: string;
  planName?: string;
  scenarioName?: string;
  environmentName?: string;
}
