import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Layout, Tree, Button, Space, Tag, Typography, Card, Input, Select,
  message, Descriptions, Divider, Empty,
} from 'antd';
import {
  ArrowLeftOutlined, CheckCircleOutlined, CloseCircleOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import type { DataNode } from 'antd/es/tree';
import { testPlanApi, mindNodeApi, caseSetApi } from '../api';
import type { TestPlan, TestPlanCase, MindNodeData } from '../types';

const { Sider, Content } = Layout;
const { TextArea } = Input;

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  NOT_STARTED: { label: '未开始', color: 'default' },
  IN_PROGRESS: { label: '进行中', color: 'processing' },
  COMPLETED: { label: '已完成', color: 'success' },
};

const RESULT_MAP: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  PENDING: { label: '待执行', color: 'default', icon: <MinusCircleOutlined /> },
  PASS: { label: '通过', color: 'success', icon: <CheckCircleOutlined style={{ color: '#52c41a' }} /> },
  FAIL: { label: '不通过', color: 'error', icon: <CloseCircleOutlined style={{ color: '#ff4d4f' }} /> },
  SKIP: { label: '跳过', color: 'warning', icon: <MinusCircleOutlined style={{ color: '#faad14' }} /> },
};

function findExpectedNode(nodes: MindNodeData[], nodeId: number): MindNodeData | null {
  for (const n of nodes) {
    if (n.id === nodeId) return n;
    if (n.children) {
      const found = findExpectedNode(n.children, nodeId);
      if (found) return found;
    }
  }
  return null;
}

function getCasePath(nodes: MindNodeData[], nodeId: number, path: string[] = []): string[] {
  for (const n of nodes) {
    const currentPath = [...path, n.text];
    if (n.id === nodeId) return currentPath;
    if (n.children) {
      const found = getCasePath(n.children, nodeId, currentPath);
      if (found.length > 0) return found;
    }
  }
  return [];
}

export default function TestPlanExecution() {
  const { planId } = useParams<{ planId: string }>();
  const navigate = useNavigate();
  const id = Number(planId);

  const [plan, setPlan] = useState<TestPlan | null>(null);
  const [cases, setCases] = useState<TestPlanCase[]>([]);
  const [selectedCase, setSelectedCase] = useState<TestPlanCase | null>(null);
  const [nodeTree, setNodeTree] = useState<MindNodeData[]>([]);
  const [reason, setReason] = useState('');
  const [searchText, setSearchText] = useState('');
  const [filterResult, setFilterResult] = useState<string | undefined>();

  const loadData = useCallback(async () => {
    const [planRes, casesRes] = await Promise.all([testPlanApi.get(id), testPlanApi.getCases(id)]);
    setPlan(planRes.data);
    setCases(casesRes.data);
    if (casesRes.data.length > 0) {
      setSelectedCase(casesRes.data[0]);
      loadCaseTree(casesRes.data[0].caseSetId);
    }
  }, [id]);

  const loadCaseTree = async (caseSetId: number) => {
    const res = await mindNodeApi.tree(caseSetId);
    setNodeTree(res.data);
  };

  useEffect(() => { loadData(); }, [loadData]);

  const handleExecute = async (result: string) => {
    if (!selectedCase) return;
    if ((result === 'FAIL' || result === 'SKIP') && !reason.trim()) {
      message.error('请填写原因');
      return;
    }
    await testPlanApi.executeCase(selectedCase.id, result, reason || undefined);
    message.success('执行结果已保存');
    setReason('');
    const casesRes = await testPlanApi.getCases(id);
    setCases(casesRes.data);
    const updated = casesRes.data.find((c: TestPlanCase) => c.id === selectedCase.id);
    if (updated) setSelectedCase(updated);
  };

  const handleRemoveCase = async (caseId: number) => {
    await testPlanApi.removeCase(caseId);
    message.success('已移除');
    const casesRes = await testPlanApi.getCases(id);
    setCases(casesRes.data);
    if (selectedCase?.id === caseId) setSelectedCase(casesRes.data[0] || null);
  };

  const filteredCases = cases.filter(c => {
    if (filterResult && c.result !== filterResult) return false;
    return true;
  });

  const treeData: DataNode[] = filteredCases.map(c => {
    const info = RESULT_MAP[c.result];
    return {
      key: c.id,
      title: (
        <Space>
          {info.icon}
          <span>用例#{c.nodeId}</span>
          <Tag color={info.color} style={{ fontSize: 11 }}>{info.label}</Tag>
        </Space>
      ),
    };
  });

  const casePath = selectedCase && nodeTree.length > 0 ? getCasePath(nodeTree, selectedCase.nodeId) : [];
  const expectedNode = selectedCase && nodeTree.length > 0 ? findExpectedNode(nodeTree, selectedCase.nodeId) : null;

  const stats = {
    total: cases.length,
    pass: cases.filter(c => c.result === 'PASS').length,
    fail: cases.filter(c => c.result === 'FAIL').length,
    skip: cases.filter(c => c.result === 'SKIP').length,
    pending: cases.filter(c => c.result === 'PENDING').length,
  };

  return (
    <Layout style={{ height: '100vh' }}>
      <div style={{
        background: '#fff', borderBottom: '1px solid #f0f0f0', padding: '8px 16px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate('/test-plans')} />
          <Typography.Text strong>{plan?.name}</Typography.Text>
          <Tag color={STATUS_MAP[plan?.status || '']?.color}>{STATUS_MAP[plan?.status || '']?.label}</Tag>
        </Space>
        <Space>
          <Tag>总计: {stats.total}</Tag>
          <Tag color="success">通过: {stats.pass}</Tag>
          <Tag color="error">不通过: {stats.fail}</Tag>
          <Tag color="warning">跳过: {stats.skip}</Tag>
          <Tag>待执行: {stats.pending}</Tag>
        </Space>
      </div>

      <Layout style={{ height: '100%' }}>
        <Sider width={320} style={{ background: '#fff', borderRight: '1px solid #f0f0f0', padding: 12, overflow: 'auto' }}>
          <div style={{ marginBottom: 12 }}>
            <Input.Search
              size="small"
              placeholder="搜索"
              value={searchText}
              onChange={e => setSearchText(e.target.value)}
            />
            <Select
              size="small"
              placeholder="筛选结果"
              allowClear
              style={{ width: '100%', marginTop: 8 }}
              value={filterResult}
              onChange={val => setFilterResult(val)}
              options={Object.entries(RESULT_MAP).map(([k, v]) => ({ value: k, label: v.label }))}
            />
          </div>
          <Tree
            treeData={treeData}
            selectedKeys={selectedCase ? [selectedCase.id] : []}
            onSelect={(keys) => {
              const c = cases.find(c => c.id === (keys[0] as number));
              if (c) {
                setSelectedCase(c);
                loadCaseTree(c.caseSetId);
              }
            }}
            blockNode
          />
        </Sider>

        <Content style={{ padding: 24, overflow: 'auto', background: '#fafafa' }}>
          {selectedCase ? (
            <Card>
              <Typography.Title level={5}>用例详情</Typography.Title>
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="用例路径">
                  {casePath.length > 0 ? casePath.join(' → ') : `用例#${selectedCase.nodeId}`}
                </Descriptions.Item>
                {expectedNode && (
                  <>
                    <Descriptions.Item label="预期结果">{expectedNode.text}</Descriptions.Item>
                    {expectedNode.priority && <Descriptions.Item label="优先级"><Tag className={`priority-${expectedNode.priority.toLowerCase()}`}>{expectedNode.priority}</Tag></Descriptions.Item>}
                    {expectedNode.automation && <Descriptions.Item label="涉及自动化">{expectedNode.automation}</Descriptions.Item>}
                    {expectedNode.coverage && <Descriptions.Item label="覆盖端">{expectedNode.coverage}</Descriptions.Item>}
                  </>
                )}
                <Descriptions.Item label="当前结果">
                  <Tag color={RESULT_MAP[selectedCase.result]?.color}>{RESULT_MAP[selectedCase.result]?.label}</Tag>
                </Descriptions.Item>
                {selectedCase.reason && <Descriptions.Item label="原因">{selectedCase.reason}</Descriptions.Item>}
              </Descriptions>

              <Divider />
              <Typography.Title level={5}>执行操作</Typography.Title>
              <Space direction="vertical" style={{ width: '100%' }}>
                <TextArea
                  placeholder="不通过或跳过时请填写原因"
                  value={reason}
                  onChange={e => setReason(e.target.value)}
                  autoSize={{ minRows: 3 }}
                />
                <Space>
                  <Button type="primary" style={{ background: '#52c41a', borderColor: '#52c41a' }} icon={<CheckCircleOutlined />} onClick={() => handleExecute('PASS')}>通过</Button>
                  <Button danger icon={<CloseCircleOutlined />} onClick={() => handleExecute('FAIL')}>不通过</Button>
                  <Button style={{ background: '#faad14', borderColor: '#faad14', color: '#fff' }} icon={<MinusCircleOutlined />} onClick={() => handleExecute('SKIP')}>跳过</Button>
                  <Button type="text" danger onClick={() => handleRemoveCase(selectedCase.id)}>移除用例</Button>
                </Space>
              </Space>
            </Card>
          ) : (
            <Empty description="请选择一条用例" />
          )}
        </Content>
      </Layout>
    </Layout>
  );
}
