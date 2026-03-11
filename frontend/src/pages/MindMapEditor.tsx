import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Layout, Button, Space, Dropdown, Input, Modal, Select, Tag, Tooltip,
  message, Drawer, List, Typography, Badge, Popconfirm, Divider, Empty,
} from 'antd';
import {
  ArrowLeftOutlined, SaveOutlined, UndoOutlined, RedoOutlined,
  HistoryOutlined, SearchOutlined, ToolOutlined, CommentOutlined,
  ExpandOutlined, CompressOutlined, PlusOutlined, DeleteOutlined,
  CheckCircleOutlined, ExclamationCircleOutlined,
} from '@ant-design/icons';
import { caseSetApi, mindNodeApi, commentApi, caseHistoryApi, reviewApi, userApi } from '../api';
import useStore from '../stores/useStore';
import type { CaseSet, MindNodeData, CommentData, CaseHistory, User } from '../types';

const { Header, Content } = Layout;
const { TextArea } = Input;

const NODE_TYPES = ['ROOT', 'TITLE', 'PRECONDITION', 'STEP', 'EXPECTED'];
const NODE_TYPE_LABELS: Record<string, string> = { ROOT: '根节点', TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' };
const PRIORITIES = ['P0', 'P1', 'P2', 'P3'];
const MARKS = [
  { value: 'NONE', label: '无', color: undefined },
  { value: 'PENDING', label: '待完成', color: '#faad14' },
  { value: 'TO_CONFIRM', label: '待确认', color: '#722ed1' },
  { value: 'TO_MODIFY', label: '待修改', color: '#f5222d' },
];
const TAGS = ['冒烟', '回归', '集成'];
const AUTOMATION = ['接口自动化', 'UI自动化', '不涉及'];
const COVERAGE = ['仅公网', '仅私网', '仅海外', '无差异', '公私网实现不一致'];
const PLATFORMS = ['Office365', 'WPS协作', '会议客户端'];

function isValidCase(node: MindNodeData): boolean {
  if (!node.children || node.children.length === 0) return false;
  for (const child of node.children) {
    const branch = collectBranch(child);
    if (branch) return true;
  }
  return false;
}

function collectBranch(node: MindNodeData): boolean {
  if (node.nodeType === 'EXPECTED' && (!node.children || node.children.length === 0)) return true;
  if (node.children) {
    for (const child of node.children) {
      if (collectBranch(child)) return true;
    }
  }
  return false;
}

function countCases(node: MindNodeData): number {
  if (node.nodeType === 'EXPECTED' && (!node.children || node.children.length === 0)) return 1;
  let count = 0;
  if (node.children) {
    for (const child of node.children) count += countCases(child);
  }
  return count;
}

function isLastFourNode(node: MindNodeData, root: MindNodeData): boolean {
  const paths: MindNodeData[][] = [];
  function findPaths(current: MindNodeData, path: MindNodeData[]) {
    const newPath = [...path, current];
    if (!current.children || current.children.length === 0) {
      if (current.nodeType === 'EXPECTED') paths.push(newPath);
    } else {
      for (const child of current.children) findPaths(child, newPath);
    }
  }
  findPaths(root, []);
  for (const path of paths) {
    const lastFour = path.slice(-4);
    if (lastFour.some(n => n.id === node.id)) return true;
  }
  return false;
}

export default function MindMapEditor() {
  const { caseSetId } = useParams<{ caseSetId: string }>();
  const navigate = useNavigate();
  const { user } = useStore();
  const [caseSet, setCaseSet] = useState<CaseSet | null>(null);
  const [tree, setTree] = useState<MindNodeData[]>([]);
  const [selectedNode, setSelectedNode] = useState<MindNodeData | null>(null);
  const [history, setHistory] = useState<MindNodeData[][]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const [saving, setSaving] = useState(false);
  const [drawerType, setDrawerType] = useState<'tools' | 'comments' | 'history' | null>(null);
  const [comments, setComments] = useState<CommentData[]>([]);
  const [allComments, setAllComments] = useState<CommentData[]>([]);
  const [commentTab, setCommentTab] = useState<'node' | 'all'>('node');
  const [newComment, setNewComment] = useState('');
  const [versions, setVersions] = useState<CaseHistory[]>([]);
  const [searchText, setSearchText] = useState('');
  const [searchVisible, setSearchVisible] = useState(false);
  const [replaceText, setReplaceText] = useState('');
  const [collapsed, setCollapsed] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [selectedReviewers, setSelectedReviewers] = useState<number[]>([]);
  const [validationErrors, setValidationErrors] = useState<{ nodeId: number; message: string }[]>([]);
  const [validationIndex, setValidationIndex] = useState(0);
  const autoSaveRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const id = Number(caseSetId);

  const loadData = useCallback(async () => {
    const [csRes, treeRes] = await Promise.all([caseSetApi.get(id), mindNodeApi.tree(id)]);
    setCaseSet(csRes.data);
    setTree(treeRes.data);
    pushHistory(treeRes.data);
  }, [id]);

  useEffect(() => {
    loadData();
    userApi.listAll().then(res => setUsers(res.data));
  }, [loadData]);

  useEffect(() => {
    autoSaveRef.current = setInterval(() => handleSave(true), 10000);
    return () => { if (autoSaveRef.current) clearInterval(autoSaveRef.current); };
  }, [tree]);

  const pushHistory = (newTree: MindNodeData[]) => {
    setHistory(prev => {
      const next = [...prev.slice(0, historyIndex + 1), JSON.parse(JSON.stringify(newTree))];
      setHistoryIndex(next.length - 1);
      return next;
    });
  };

  const updateTree = (newTree: MindNodeData[]) => {
    setTree(newTree);
    pushHistory(newTree);
  };

  const handleUndo = () => {
    if (historyIndex > 0) {
      setHistoryIndex(historyIndex - 1);
      setTree(JSON.parse(JSON.stringify(history[historyIndex - 1])));
    }
  };

  const handleRedo = () => {
    if (historyIndex < history.length - 1) {
      setHistoryIndex(historyIndex + 1);
      setTree(JSON.parse(JSON.stringify(history[historyIndex + 1])));
    }
  };

  const handleSave = async (auto = false) => {
    if (tree.length === 0) return;
    setSaving(true);
    try {
      await mindNodeApi.batchSave(id, tree);
      const count = tree.length > 0 ? countCases(tree[0]) : 0;
      if (caseSet) {
        caseSet.caseCount = count;
        setCaseSet({ ...caseSet });
      }
      if (!auto) {
        await caseHistoryApi.save(id);
        message.success('保存成功');
      }
    } catch {
      if (!auto) message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const findNodeById = (nodes: MindNodeData[], nodeId: number): MindNodeData | null => {
    for (const node of nodes) {
      if (node.id === nodeId) return node;
      if (node.children) {
        const found = findNodeById(node.children, nodeId);
        if (found) return found;
      }
    }
    return null;
  };

  const updateNodeInTree = (nodes: MindNodeData[], nodeId: number, updater: (n: MindNodeData) => void): MindNodeData[] => {
    return nodes.map(node => {
      if (node.id === nodeId) {
        const updated = { ...node };
        updater(updated);
        return updated;
      }
      if (node.children) {
        return { ...node, children: updateNodeInTree(node.children, nodeId, updater) };
      }
      return node;
    });
  };

  const addChildNode = (parentId: number) => {
    const newNode: MindNodeData = {
      text: '新节点',
      nodeType: 'TITLE',
      sortOrder: 0,
      mark: 'NONE',
      children: [],
    };
    const newTree = updateNodeInTree(tree, parentId, (n) => {
      n.children = [...(n.children || []), newNode];
    });
    updateTree(newTree);
  };

  const deleteNodeFromTree = (nodes: MindNodeData[], nodeId: number): MindNodeData[] => {
    return nodes.filter(n => n.id !== nodeId).map(n => ({
      ...n,
      children: n.children ? deleteNodeFromTree(n.children, nodeId) : [],
    }));
  };

  const handleDeleteNode = (nodeId: number) => {
    const newTree = deleteNodeFromTree(tree, nodeId);
    updateTree(newTree);
    if (selectedNode?.id === nodeId) setSelectedNode(null);
  };

  const handleNodeUpdate = (nodeId: number, field: string, value: unknown) => {
    const newTree = updateNodeInTree(tree, nodeId, (n) => {
      (n as unknown as Record<string, unknown>)[field] = value;
    });
    updateTree(newTree);
    if (selectedNode?.id === nodeId) {
      setSelectedNode({ ...selectedNode, [field]: value } as MindNodeData);
    }
  };

  const loadComments = async (nodeId?: number) => {
    if (nodeId) {
      const res = await commentApi.nodeComments(nodeId);
      setComments(res.data);
    }
    const allRes = await commentApi.allComments(id);
    setAllComments(allRes.data);
  };

  const handleAddComment = async () => {
    if (!newComment.trim() || !selectedNode?.id) return;
    await commentApi.add(selectedNode.id, id, newComment.trim());
    setNewComment('');
    loadComments(selectedNode.id);
  };

  const handleStatusChange = async (status: string) => {
    if (status === 'PENDING_REVIEW') {
      setReviewModalOpen(true);
      return;
    }
    await caseSetApi.updateStatus(id, status);
    setCaseSet(prev => prev ? { ...prev, status: status as CaseSet['status'] } : null);
    message.success('状态已更新');
  };

  const handleSubmitReview = async () => {
    if (selectedReviewers.length === 0) { message.error('请选择评审人'); return; }
    try {
      await caseSetApi.updateStatus(id, 'PENDING_REVIEW', selectedReviewers);
      setCaseSet(prev => prev ? { ...prev, status: 'PENDING_REVIEW' } : null);
      setReviewModalOpen(false);
      message.success('已提交评审');
    } catch {
      // handled by interceptor
    }
  };

  const handleValidate = async () => {
    const res = await caseSetApi.validate(id);
    if (res.data.valid) {
      message.success('用例符合规范');
      setValidationErrors([]);
    } else {
      setValidationErrors(res.data.errors.map(e => ({ nodeId: e.nodeId, message: e.message })));
      setValidationIndex(0);
      message.warning(`共${res.data.errorCount}条用例不符合规范`);
    }
  };

  const handleLoadVersions = async () => {
    const res = await caseHistoryApi.list(id, 3);
    setVersions(res.data);
    setDrawerType('history');
  };

  const handleRestore = async (historyId: number) => {
    await caseHistoryApi.restore(historyId);
    message.success('版本已恢复');
    loadData();
    setDrawerType(null);
  };

  const handleSearchReplace = () => {
    if (!searchText) return;
    const replace = (nodes: MindNodeData[]): MindNodeData[] => {
      return nodes.map(n => ({
        ...n,
        text: n.text.replaceAll(searchText, replaceText),
        children: n.children ? replace(n.children) : [],
      }));
    };
    const newTree = replace(tree);
    updateTree(newTree);
    message.success('替换完成');
  };

  const renderNodeTree = (nodes: MindNodeData[], depth = 0): React.ReactNode => {
    if (collapsed && depth > 1) return null;
    return nodes.map((node, index) => {
      const markClass = node.mark === 'PENDING' ? 'mark-pending' : node.mark === 'TO_CONFIRM' ? 'mark-to-confirm' : node.mark === 'TO_MODIFY' ? 'mark-to-modify' : '';
      const isSelected = selectedNode?.id === node.id;
      const isError = validationErrors.some(e => e.nodeId === node.id);
      const showExtraAttrs = tree.length > 0 && isLastFourNode(node, tree[0]);

      return (
        <div key={node.id || `new-${depth}-${index}`} style={{ marginLeft: depth * 32, marginTop: 6 }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
            <div style={{ width: depth > 0 ? 24 : 0, borderBottom: depth > 0 ? '2px solid #d9d9d9' : 'none', height: 1, marginTop: 16, flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div
                className={`mind-node ${markClass} ${isSelected ? 'selected' : ''}`}
                style={{ border: isError ? '2px solid #ff4d4f' : undefined }}
                onClick={() => { setSelectedNode(node); if (drawerType === 'comments') loadComments(node.id); }}
              >
                <Tag color={node.nodeType === 'ROOT' ? 'blue' : node.nodeType === 'EXPECTED' ? 'green' : 'default'} style={{ marginRight: 6, fontSize: 11 }}>
                  {NODE_TYPE_LABELS[node.nodeType] || node.nodeType}
                </Tag>
                <span>{node.text || '(空)'}</span>
                {node.priority && <Tag className={`priority-${node.priority.toLowerCase()}`} style={{ marginLeft: 6 }}>{node.priority}</Tag>}
                {(node.commentCount || 0) > 0 && <span className="node-badge">{node.commentCount}</span>}
              </div>
              {node.tags && node.tags.length > 0 && (
                <div style={{ display: 'flex', gap: 4, marginTop: 2, marginLeft: 4 }}>
                  {node.tags.map(t => <Tag key={t} color="blue" style={{ fontSize: 11 }}>{t}</Tag>)}
                </div>
              )}
              {showExtraAttrs && node.nodeType === 'EXPECTED' && (
                <div style={{ display: 'flex', gap: 4, marginTop: 2, marginLeft: 4, flexWrap: 'wrap' }}>
                  {node.automation && <Tag color="purple" style={{ fontSize: 11 }}>{node.automation}</Tag>}
                  {node.coverage && <Tag color="orange" style={{ fontSize: 11 }}>{node.coverage}</Tag>}
                  {node.platform?.map(p => <Tag key={p} color="cyan" style={{ fontSize: 11 }}>{p}</Tag>)}
                </div>
              )}
            </div>
            <Space size={2}>
              <Tooltip title="添加子节点"><Button size="small" type="text" icon={<PlusOutlined />} onClick={() => node.id && addChildNode(node.id)} /></Tooltip>
              {node.nodeType !== 'ROOT' && (
                <Tooltip title="删除"><Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => node.id && handleDeleteNode(node.id)} /></Tooltip>
              )}
            </Space>
          </div>
          {node.children && node.children.length > 0 && renderNodeTree(node.children, depth + 1)}
        </div>
      );
    });
  };

  const statusItems = [
    { key: 'WRITING', label: '编写中' },
    { key: 'PENDING_REVIEW', label: '待评审' },
    { key: 'NO_REVIEW', label: '无需评审' },
  ];

  return (
    <Layout style={{ height: '100vh' }}>
      <Header style={{
        background: '#fff', borderBottom: '1px solid #f0f0f0', padding: '0 16px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', height: 48,
      }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate('/cases')} />
          <Dropdown menu={{ items: statusItems, onClick: ({ key }) => handleStatusChange(key) }}>
            <Tag color={caseSet?.status === 'WRITING' ? 'processing' : caseSet?.status === 'PENDING_REVIEW' ? 'warning' : 'default'} style={{ cursor: 'pointer' }}>
              {caseSet?.status === 'WRITING' ? '编写中' : caseSet?.status === 'PENDING_REVIEW' ? '待评审' : '无需评审'}
            </Tag>
          </Dropdown>
          <Typography.Text strong>{caseSet?.name}</Typography.Text>
          <Typography.Text type="secondary">({tree.length > 0 ? countCases(tree[0]) : 0}条用例)</Typography.Text>
        </Space>

        <Space>
          <Tooltip title="查找替换"><Button type="text" icon={<SearchOutlined />} onClick={() => setSearchVisible(!searchVisible)} /></Tooltip>
          <Tooltip title="小工具"><Button type="text" icon={<ToolOutlined />} onClick={() => { setDrawerType('tools'); handleValidate(); }} /></Tooltip>
          <Tooltip title="评论"><Button type="text" icon={<CommentOutlined />} onClick={() => { setDrawerType('comments'); if (selectedNode?.id) loadComments(selectedNode.id); }} /></Tooltip>
          <Tooltip title={collapsed ? '展开' : '折叠'}>
            <Button type="text" icon={collapsed ? <ExpandOutlined /> : <CompressOutlined />} onClick={() => setCollapsed(!collapsed)} />
          </Tooltip>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => handleSave(false)}>保存</Button>
          <Tooltip title="撤销"><Button type="text" icon={<UndoOutlined />} onClick={handleUndo} disabled={historyIndex <= 0} /></Tooltip>
          <Tooltip title="重做"><Button type="text" icon={<RedoOutlined />} onClick={handleRedo} disabled={historyIndex >= history.length - 1} /></Tooltip>
          <Tooltip title="历史版本"><Button type="text" icon={<HistoryOutlined />} onClick={handleLoadVersions} /></Tooltip>
        </Space>
      </Header>

      {searchVisible && (
        <div style={{ background: '#fff', padding: '8px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', gap: 8, alignItems: 'center' }}>
          <Input size="small" placeholder="查找" value={searchText} onChange={e => setSearchText(e.target.value)} style={{ width: 200 }} />
          <Input size="small" placeholder="替换" value={replaceText} onChange={e => setReplaceText(e.target.value)} style={{ width: 200 }} />
          <Button size="small" onClick={handleSearchReplace}>全部替换</Button>
          <Button size="small" type="text" onClick={() => setSearchVisible(false)}>关闭</Button>
        </div>
      )}

      <Layout style={{ height: '100%' }}>
        <Content className="mind-map-container" style={{ padding: 24 }}>
          {tree.length > 0 ? renderNodeTree(tree) : <Empty description="暂无节点" />}
        </Content>

        {selectedNode && (
          <div style={{ width: 320, background: '#fff', borderLeft: '1px solid #f0f0f0', padding: 16, overflow: 'auto' }}>
            <Typography.Title level={5}>节点属性</Typography.Title>
            <div style={{ marginBottom: 12 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>节点内容</Typography.Text>
              <Input.TextArea
                value={selectedNode.text}
                onChange={e => selectedNode.id && handleNodeUpdate(selectedNode.id, 'text', e.target.value)}
                autoSize={{ minRows: 2 }}
              />
            </div>
            <div style={{ marginBottom: 12 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>节点类型</Typography.Text>
              <Select
                value={selectedNode.nodeType}
                onChange={val => selectedNode.id && handleNodeUpdate(selectedNode.id, 'nodeType', val)}
                options={NODE_TYPES.map(t => ({ value: t, label: NODE_TYPE_LABELS[t] }))}
                style={{ width: '100%' }}
              />
            </div>
            <div style={{ marginBottom: 12 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>优先级</Typography.Text>
              <Select
                value={selectedNode.priority}
                onChange={val => selectedNode.id && handleNodeUpdate(selectedNode.id, 'priority', val)}
                options={[{ value: null, label: '无' }, ...PRIORITIES.map(p => ({ value: p, label: p }))]}
                style={{ width: '100%' }}
                allowClear
              />
            </div>
            <div style={{ marginBottom: 12 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>标记</Typography.Text>
              <Select
                value={selectedNode.mark}
                onChange={val => selectedNode.id && handleNodeUpdate(selectedNode.id, 'mark', val)}
                options={MARKS.map(m => ({ value: m.value, label: m.label }))}
                style={{ width: '100%' }}
              />
            </div>
            <div style={{ marginBottom: 12 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>标签</Typography.Text>
              <Select
                mode="multiple"
                value={selectedNode.tags || []}
                onChange={val => selectedNode.id && handleNodeUpdate(selectedNode.id, 'tags', val)}
                options={TAGS.map(t => ({ value: t, label: t }))}
                style={{ width: '100%' }}
              />
            </div>

            {selectedNode.nodeType === 'EXPECTED' && (
              <>
                <Divider style={{ margin: '12px 0' }} />
                <Typography.Text strong style={{ fontSize: 13, color: '#1677ff' }}>用例属性</Typography.Text>
                <div style={{ marginBottom: 12, marginTop: 8 }}>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>涉及自动化</Typography.Text>
                  <Select
                    value={selectedNode.automation}
                    onChange={val => selectedNode.id && handleNodeUpdate(selectedNode.id, 'automation', val)}
                    options={AUTOMATION.map(a => ({ value: a, label: a }))}
                    style={{ width: '100%' }}
                    allowClear
                  />
                </div>
                <div style={{ marginBottom: 12 }}>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>用例覆盖端</Typography.Text>
                  <Select
                    value={selectedNode.coverage}
                    onChange={val => selectedNode.id && handleNodeUpdate(selectedNode.id, 'coverage', val)}
                    options={COVERAGE.map(c => ({ value: c, label: c }))}
                    style={{ width: '100%' }}
                    allowClear
                  />
                </div>
                <div style={{ marginBottom: 12 }}>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>用例归属平台</Typography.Text>
                  <Select
                    mode="multiple"
                    value={selectedNode.belongsPlatform || []}
                    onChange={val => selectedNode.id && handleNodeUpdate(selectedNode.id, 'belongsPlatform', val)}
                    options={PLATFORMS.map(p => ({ value: p, label: p }))}
                    style={{ width: '100%' }}
                  />
                </div>
              </>
            )}
          </div>
        )}
      </Layout>

      <Drawer
        title={drawerType === 'tools' ? '用例规范检查' : drawerType === 'comments' ? '评论' : '历史版本'}
        open={!!drawerType}
        onClose={() => setDrawerType(null)}
        width={380}
      >
        {drawerType === 'tools' && (
          <div>
            <Button type="primary" block onClick={handleValidate} icon={<CheckCircleOutlined />}>检查规范</Button>
            {validationErrors.length > 0 && (
              <div style={{ marginTop: 16 }}>
                <Typography.Text type="danger">共{validationErrors.length}条不符合规范</Typography.Text>
                <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
                  <Button size="small" disabled={validationIndex <= 0} onClick={() => setValidationIndex(validationIndex - 1)}>上一个</Button>
                  <Typography.Text>{validationIndex + 1}/{validationErrors.length}</Typography.Text>
                  <Button size="small" disabled={validationIndex >= validationErrors.length - 1} onClick={() => setValidationIndex(validationIndex + 1)}>下一个</Button>
                </div>
                <div style={{ marginTop: 8, padding: 8, background: '#fff2f0', borderRadius: 6 }}>
                  <ExclamationCircleOutlined style={{ color: '#ff4d4f', marginRight: 8 }} />
                  {validationErrors[validationIndex]?.message}
                </div>
              </div>
            )}
            {validationErrors.length === 0 && <div style={{ marginTop: 16, textAlign: 'center' }}><CheckCircleOutlined style={{ color: '#52c41a', fontSize: 32 }} /><p>用例符合规范</p></div>}
          </div>
        )}

        {drawerType === 'comments' && (
          <div>
            <Space style={{ marginBottom: 12 }}>
              <Button type={commentTab === 'node' ? 'primary' : 'default'} size="small" onClick={() => { setCommentTab('node'); if (selectedNode?.id) loadComments(selectedNode.id); }}>当前节点</Button>
              <Button type={commentTab === 'all' ? 'primary' : 'default'} size="small" onClick={() => { setCommentTab('all'); loadComments(); }}>全部评论</Button>
            </Space>

            {commentTab === 'node' && selectedNode && (
              <>
                <List
                  dataSource={comments}
                  renderItem={(item: CommentData) => (
                    <List.Item
                      actions={[
                        <Button type="link" size="small" onClick={() => commentApi.resolve(item.id).then(() => loadComments(selectedNode?.id))}>
                          {item.resolved ? '已解决' : '标记解决'}
                        </Button>,
                        <Button type="link" size="small" danger onClick={() => commentApi.delete(item.id).then(() => loadComments(selectedNode?.id))}>删除</Button>,
                      ]}
                    >
                      <List.Item.Meta
                        title={<><Typography.Text strong>{item.displayName}</Typography.Text> <Typography.Text type="secondary" style={{ fontSize: 11 }}>{item.createdAt}</Typography.Text></>}
                        description={item.content}
                      />
                    </List.Item>
                  )}
                  locale={{ emptyText: '暂无评论' }}
                />
                <div style={{ marginTop: 12 }}>
                  <TextArea value={newComment} onChange={e => setNewComment(e.target.value)} placeholder="输入评论..." autoSize={{ minRows: 2 }} />
                  <Button type="primary" size="small" style={{ marginTop: 8 }} onClick={handleAddComment}>发送</Button>
                </div>
              </>
            )}

            {commentTab === 'all' && (
              <List
                dataSource={allComments}
                renderItem={(item: CommentData) => (
                  <List.Item>
                    <List.Item.Meta
                      title={<><Badge status={item.resolved ? 'success' : 'processing'} />{item.displayName} - 节点#{item.nodeId}</>}
                      description={item.content}
                    />
                  </List.Item>
                )}
                locale={{ emptyText: '暂无评论' }}
              />
            )}
          </div>
        )}

        {drawerType === 'history' && (
          <List
            dataSource={versions}
            renderItem={(item: CaseHistory) => (
              <List.Item actions={[<Popconfirm title="确认恢复此版本?" onConfirm={() => handleRestore(item.id)}><Button type="link" size="small">恢复</Button></Popconfirm>]}>
                <List.Item.Meta title={`版本 ${item.id}`} description={item.createdAt} />
              </List.Item>
            )}
            locale={{ emptyText: '暂无历史版本' }}
          />
        )}
      </Drawer>

      <Modal title="选择评审人" open={reviewModalOpen} onOk={handleSubmitReview} onCancel={() => setReviewModalOpen(false)}>
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder="选择评审人"
          value={selectedReviewers}
          onChange={setSelectedReviewers}
          options={users.filter(u => u.id !== user?.id).map(u => ({ value: u.id, label: u.displayName }))}
        />
      </Modal>
    </Layout>
  );
}
