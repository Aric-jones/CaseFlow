import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Layout, Button, Space, Tag, Typography, Drawer, List, Badge,
  Input, Select, message, Divider,
} from 'antd';
import {
  ArrowLeftOutlined, CommentOutlined, CheckCircleOutlined,
} from '@ant-design/icons';
import { caseSetApi, mindNodeApi, commentApi, reviewApi, userApi } from '../api';
import useStore from '../stores/useStore';
import type { CaseSet, MindNodeData, CommentData, ReviewAssignment, User } from '../types';

const { Header, Content } = Layout;
const { TextArea } = Input;

const NODE_TYPE_LABELS: Record<string, string> = { ROOT: '根节点', TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' };
const MARKS = [
  { value: 'NONE', label: '无' },
  { value: 'PENDING', label: '待完成' },
  { value: 'TO_CONFIRM', label: '待确认' },
  { value: 'TO_MODIFY', label: '待修改' },
];

const REVIEW_STATUS: Record<string, { label: string; color: string }> = {
  PENDING: { label: '未评审', color: 'default' },
  APPROVED: { label: '通过', color: 'success' },
  REJECTED: { label: '不通过', color: 'error' },
  NEED_MODIFY: { label: '待修改', color: 'warning' },
};

export default function ReviewPage() {
  const { caseSetId } = useParams<{ caseSetId: string }>();
  const navigate = useNavigate();
  const { user } = useStore();
  const id = Number(caseSetId);

  const [caseSet, setCaseSet] = useState<CaseSet | null>(null);
  const [tree, setTree] = useState<MindNodeData[]>([]);
  const [selectedNode, setSelectedNode] = useState<MindNodeData | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [commentTab, setCommentTab] = useState<'node' | 'all'>('node');
  const [comments, setComments] = useState<CommentData[]>([]);
  const [allComments, setAllComments] = useState<CommentData[]>([]);
  const [newComment, setNewComment] = useState('');
  const [reviewers, setReviewers] = useState<ReviewAssignment[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [reviewDrawerOpen, setReviewDrawerOpen] = useState(false);

  const loadData = useCallback(async () => {
    const [csRes, treeRes, reviewRes] = await Promise.all([
      caseSetApi.get(id), mindNodeApi.tree(id), reviewApi.list(id),
    ]);
    setCaseSet(csRes.data);
    setTree(treeRes.data);
    setReviewers(reviewRes.data);
  }, [id]);

  useEffect(() => {
    loadData();
    userApi.listAll().then(res => setUsers(res.data));
  }, [loadData]);

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
    const treeRes = await mindNodeApi.tree(id);
    setTree(treeRes.data);
  };

  const handleMarkNode = async (nodeId: number, mark: string) => {
    await mindNodeApi.update(nodeId, { mark } as Partial<MindNodeData>);
    const treeRes = await mindNodeApi.tree(id);
    setTree(treeRes.data);
  };

  const handleUpdateReviewStatus = async (assignmentId: number, status: string) => {
    await reviewApi.updateStatus(assignmentId, status);
    const res = await reviewApi.list(id);
    setReviewers(res.data);
    message.success('评审状态已更新');
  };

  const myReview = reviewers.find(r => r.reviewerId === user?.id);
  const getUserName = (userId: number) => users.find(u => u.id === userId)?.displayName || `用户${userId}`;

  const renderNodeTree = (nodes: MindNodeData[], depth = 0): React.ReactNode => {
    return nodes.map((node, index) => {
      const markClass = node.mark === 'PENDING' ? 'mark-pending' : node.mark === 'TO_CONFIRM' ? 'mark-to-confirm' : node.mark === 'TO_MODIFY' ? 'mark-to-modify' : '';
      const isSelected = selectedNode?.id === node.id;

      return (
        <div key={node.id || `r-${depth}-${index}`} style={{ marginLeft: depth * 32, marginTop: 6 }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
            <div style={{ width: depth > 0 ? 24 : 0, borderBottom: depth > 0 ? '2px solid #d9d9d9' : 'none', height: 1, marginTop: 16, flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div
                className={`mind-node ${markClass} ${isSelected ? 'selected' : ''}`}
                onClick={() => { setSelectedNode(node); setDrawerOpen(true); loadComments(node.id); }}
                style={{ cursor: 'pointer' }}
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
              {node.nodeType === 'EXPECTED' && (
                <div style={{ display: 'flex', gap: 4, marginTop: 2, marginLeft: 4, flexWrap: 'wrap' }}>
                  {node.automation && <Tag color="purple" style={{ fontSize: 11 }}>{node.automation}</Tag>}
                  {node.coverage && <Tag color="orange" style={{ fontSize: 11 }}>{node.coverage}</Tag>}
                </div>
              )}
            </div>
            <Select
              size="small"
              value={node.mark}
              onChange={(val) => node.id && handleMarkNode(node.id, val)}
              options={MARKS.map(m => ({ value: m.value, label: m.label }))}
              style={{ width: 90 }}
            />
          </div>
          {node.children && node.children.length > 0 && renderNodeTree(node.children, depth + 1)}
        </div>
      );
    });
  };

  return (
    <Layout style={{ height: '100vh' }}>
      <Header style={{
        background: '#fff', borderBottom: '1px solid #f0f0f0', padding: '0 16px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', height: 48,
      }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate('/cases')} />
          <Tag color="warning">评审中</Tag>
          <Typography.Text strong>{caseSet?.name}</Typography.Text>
        </Space>
        <Space>
          {myReview && (
            <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => setReviewDrawerOpen(true)}>
              评审结果
            </Button>
          )}
          <Button icon={<CommentOutlined />} onClick={() => { setDrawerOpen(true); setCommentTab('all'); loadComments(); }}>全部评论</Button>
        </Space>
      </Header>

      <Content className="mind-map-container" style={{ padding: 24, overflow: 'auto' }}>
        {tree.length > 0 && renderNodeTree(tree)}
      </Content>

      <Drawer title="评论" open={drawerOpen} onClose={() => setDrawerOpen(false)} width={380}>
        <Space style={{ marginBottom: 12 }}>
          <Button type={commentTab === 'node' ? 'primary' : 'default'} size="small" onClick={() => { setCommentTab('node'); if (selectedNode?.id) loadComments(selectedNode.id); }}>当前节点</Button>
          <Button type={commentTab === 'all' ? 'primary' : 'default'} size="small" onClick={() => { setCommentTab('all'); loadComments(); }}>全部评论</Button>
        </Space>

        {commentTab === 'node' && selectedNode && (
          <>
            <Typography.Text type="secondary">节点: {selectedNode.text}</Typography.Text>
            <List
              style={{ marginTop: 12 }}
              dataSource={comments}
              renderItem={(item: CommentData) => (
                <List.Item actions={[
                  <Button type="link" size="small" onClick={() => commentApi.resolve(item.id).then(() => loadComments(selectedNode?.id))}>
                    {item.resolved ? '已解决' : '标记解决'}
                  </Button>,
                  item.userId === user?.id && <Button type="link" size="small" danger onClick={() => commentApi.delete(item.id).then(() => loadComments(selectedNode?.id))}>删除</Button>,
                ].filter(Boolean)}>
                  <List.Item.Meta
                    title={<><Badge status={item.resolved ? 'success' : 'processing'} /><Typography.Text strong>{item.displayName}</Typography.Text> <Typography.Text type="secondary" style={{ fontSize: 11 }}>{item.createdAt}</Typography.Text></>}
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
      </Drawer>

      <Drawer title="评审结果" open={reviewDrawerOpen} onClose={() => setReviewDrawerOpen(false)} width={380}>
        <List
          dataSource={reviewers}
          renderItem={(item: ReviewAssignment) => {
            const s = REVIEW_STATUS[item.status] || { label: item.status, color: 'default' };
            const isMe = item.reviewerId === user?.id;
            return (
              <List.Item>
                <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Typography.Text>{getUserName(item.reviewerId)}</Typography.Text>
                  {isMe ? (
                    <Select
                      value={item.status}
                      onChange={(val) => handleUpdateReviewStatus(item.id, val)}
                      options={Object.entries(REVIEW_STATUS).map(([k, v]) => ({ value: k, label: v.label }))}
                      style={{ width: 120 }}
                      size="small"
                    />
                  ) : (
                    <Tag color={s.color}>{s.label}</Tag>
                  )}
                </Space>
              </List.Item>
            );
          }}
        />
      </Drawer>
    </Layout>
  );
}
