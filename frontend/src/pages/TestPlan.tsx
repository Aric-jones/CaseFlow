import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Layout, Tree, Button, Table, Space, Input, Modal, Form, Select,
  Tag, Typography, message, Dropdown, Tabs, Empty, List, Checkbox, Pagination,
} from 'antd';
import {
  PlusOutlined, SearchOutlined, FolderOutlined, DeleteOutlined,
  EditOutlined, FolderOpenOutlined, EyeOutlined,
} from '@ant-design/icons';
import type { DataNode } from 'antd/es/tree';
import { directoryApi, testPlanApi, caseSetApi, mindNodeApi, userApi } from '../api';
import useStore from '../stores/useStore';
import type { DirectoryNode, TestPlan, CaseSet, MindNodeData, User, PageResult } from '../types';

const { Sider, Content } = Layout;

function dirToTreeData(dirs: DirectoryNode[]): DataNode[] {
  return dirs.map(d => ({
    key: d.id,
    title: d.name,
    icon: <FolderOutlined />,
    children: d.children?.length ? dirToTreeData(d.children) : [],
  }));
}

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  NOT_STARTED: { label: '未开始', color: 'default' },
  IN_PROGRESS: { label: '进行中', color: 'processing' },
  COMPLETED: { label: '已完成', color: 'success' },
};

function countCases(node: MindNodeData): number {
  if (node.nodeType === 'EXPECTED' && (!node.children || node.children.length === 0)) return 1;
  let count = 0;
  if (node.children) for (const child of node.children) count += countCases(child);
  return count;
}

function collectCases(node: MindNodeData, caseSetId: number, path: string): { nodeId: number; caseSetId: number; title: string; priority: string }[] {
  const result: { nodeId: number; caseSetId: number; title: string; priority: string }[] = [];
  const currentPath = path ? `${path} → ${node.text}` : node.text;
  if (node.nodeType === 'EXPECTED' && (!node.children || node.children.length === 0) && node.id) {
    result.push({ nodeId: node.id, caseSetId, title: currentPath, priority: node.priority || '' });
  }
  if (node.children) {
    for (const child of node.children) result.push(...collectCases(child, caseSetId, currentPath));
  }
  return result;
}

export default function TestPlanPage() {
  const navigate = useNavigate();
  const { currentProject, user } = useStore();
  const [dirs, setDirs] = useState<DirectoryNode[]>([]);
  const [selectedDir, setSelectedDir] = useState<number | null>(null);
  const [plans, setPlans] = useState<PageResult<TestPlan>>({ records: [], total: 0, size: 20, current: 1, pages: 0 });
  const [keyword, setKeyword] = useState('');
  const [onlyMine, setOnlyMine] = useState(false);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [caseSets, setCaseSets] = useState<CaseSet[]>([]);
  const [selectedCaseIds, setSelectedCaseIds] = useState<{ nodeId: number; caseSetId: number; executorId?: number }[]>([]);
  const [caseSelectModalOpen, setCaseSelectModalOpen] = useState(false);
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [form] = Form.useForm();

  const [dirModalOpen, setDirModalOpen] = useState(false);
  const [dirParentId, setDirParentId] = useState<number | null>(null);
  const [dirName, setDirName] = useState('');

  const loadDirs = useCallback(async () => {
    if (!currentProject) return;
    const res = await directoryApi.tree(currentProject.id, 'TEST_PLAN');
    setDirs(res.data);
  }, [currentProject]);

  const loadPlans = useCallback(async (page = 1) => {
    if (!currentProject) return;
    setLoading(true);
    try {
      const res = await testPlanApi.list({
        projectId: currentProject.id,
        directoryId: selectedDir ?? undefined,
        keyword: keyword || undefined,
        onlyMine,
        page,
        size: 20,
      });
      setPlans(res.data);
    } finally {
      setLoading(false);
    }
  }, [currentProject, selectedDir, keyword, onlyMine]);

  useEffect(() => { loadDirs(); }, [loadDirs]);
  useEffect(() => { loadPlans(); }, [loadPlans]);
  useEffect(() => { userApi.listAll().then(res => setUsers(res.data)); }, []);

  const loadCaseSets = async () => {
    if (!currentProject) return;
    const res = await caseSetApi.list({ projectId: currentProject.id, size: 1000 });
    setCaseSets(res.data.records);
  };

  const handleCreateDir = async () => {
    if (!dirName.trim() || !currentProject) return;
    await directoryApi.create(dirName.trim(), dirParentId, currentProject.id, 'TEST_PLAN');
    message.success('创建成功');
    setDirModalOpen(false);
    setDirName('');
    loadDirs();
  };

  const handleSelectCases = async (caseSetId: number) => {
    const res = await mindNodeApi.tree(caseSetId);
    if (res.data.length === 0) return;
    const cases = collectCases(res.data[0], caseSetId, '');
    const newCases = cases.filter(c => !selectedCaseIds.some(s => s.nodeId === c.nodeId));
    setSelectedCaseIds(prev => [...prev, ...newCases.map(c => ({ nodeId: c.nodeId, caseSetId: c.caseSetId }))]);
    message.success(`已添加${newCases.length}条用例`);
  };

  const handleCreatePlan = async () => {
    const values = await form.validateFields();
    if (!currentProject) return;
    await testPlanApi.create({
      name: values.name,
      directoryId: selectedDir ?? undefined,
      projectId: currentProject.id,
      executorIds: values.executorIds || [],
      cases: selectedCaseIds,
    });
    message.success('测试计划创建成功');
    setCreateModalOpen(false);
    form.resetFields();
    setSelectedCaseIds([]);
    loadPlans();
  };

  const columns = [
    { title: '计划名称', dataIndex: 'name', key: 'name' },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => { const info = STATUS_MAP[s]; return <Tag color={info?.color}>{info?.label}</Tag>; },
    },
    { title: '创建人', dataIndex: 'createdBy', key: 'createdBy', width: 100 },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    {
      title: '操作', key: 'action', width: 120,
      render: (_: unknown, record: TestPlan) => (
        <Button type="link" onClick={() => navigate(`/test-plan/${record.id}/execute`)}>执行</Button>
      ),
    },
  ];

  const treeData = dirToTreeData(dirs);

  return (
    <Layout style={{ height: '100%', background: '#fff' }}>
      <Sider width={260} style={{ background: '#fff', borderRight: '1px solid #f0f0f0', padding: '16px 8px', overflow: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 8px', marginBottom: 12 }}>
          <Typography.Text strong>测试计划目录</Typography.Text>
        </div>

        {dirs.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <Empty description="暂无目录" />
            <Button type="link" onClick={() => { setDirParentId(null); setDirModalOpen(true); }}>创建根目录</Button>
          </div>
        ) : (
          <Tree
            treeData={treeData}
            selectedKeys={selectedDir ? [selectedDir] : []}
            onSelect={(keys) => setSelectedDir(keys[0] as number || null)}
            titleRender={(node) => (
              <Dropdown
                menu={{
                  items: [
                    { key: 'addChild', label: '新建子目录', icon: <FolderOpenOutlined /> },
                    { key: 'rename', label: '重命名', icon: <EditOutlined /> },
                  ],
                  onClick: ({ key }) => {
                    if (key === 'addChild') { setDirParentId(node.key as number); setDirModalOpen(true); }
                  },
                }}
                trigger={['contextMenu']}
              >
                <span>{String(node.title)}</span>
              </Dropdown>
            )}
            defaultExpandAll
            blockNode
          />
        )}
      </Sider>

      <Content style={{ padding: 24 }}>
        <div style={{ display: 'flex', gap: 12, marginBottom: 16, alignItems: 'center' }}>
          <Input.Search
            placeholder="搜索测试计划"
            value={keyword}
            onChange={e => setKeyword(e.target.value)}
            onSearch={() => loadPlans()}
            style={{ width: 300 }}
          />
          <Checkbox checked={onlyMine} onChange={e => setOnlyMine(e.target.checked)}>只看我的</Checkbox>
          <div style={{ flex: 1 }} />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { setCreateModalOpen(true); loadCaseSets(); }}>
            新建测试计划
          </Button>
        </div>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={plans.records}
          loading={loading}
          pagination={false}
          size="middle"
        />
        {plans.total > 0 && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
            <Pagination current={plans.current} total={plans.total} pageSize={plans.size} onChange={p => loadPlans(p)} showTotal={t => `共 ${t} 条`} />
          </div>
        )}
      </Content>

      <Modal title="新建测试计划" open={createModalOpen} onOk={handleCreatePlan} onCancel={() => setCreateModalOpen(false)} width={720}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="任务名称" rules={[{ required: true }]}>
            <Input placeholder="输入计划名称" />
          </Form.Item>
          <Form.Item label="选择用例">
            <Button onClick={() => setCaseSelectModalOpen(true)}>选择用例集</Button>
            <Typography.Text type="secondary" style={{ marginLeft: 8 }}>已选{selectedCaseIds.length}条用例</Typography.Text>
          </Form.Item>
          <Form.Item name="executorIds" label="执行人">
            <Select mode="multiple" placeholder="选择执行人" options={users.map(u => ({ value: u.id, label: u.displayName }))} />
          </Form.Item>
          {selectedCaseIds.length > 0 && (
            <Form.Item label="已选用例">
              <List
                size="small"
                dataSource={selectedCaseIds.slice(0, 20)}
                renderItem={item => (
                  <List.Item actions={[<Button type="link" size="small" danger onClick={() => setSelectedCaseIds(prev => prev.filter(c => c.nodeId !== item.nodeId))}>移除</Button>]}>
                    用例#{item.nodeId} (用例集#{item.caseSetId})
                  </List.Item>
                )}
              />
              {selectedCaseIds.length > 20 && <Typography.Text type="secondary">...及另外{selectedCaseIds.length - 20}条</Typography.Text>}
            </Form.Item>
          )}
          <Button onClick={() => setPreviewModalOpen(true)} icon={<EyeOutlined />}>预览</Button>
        </Form>
      </Modal>

      <Modal title="选择用例集" open={caseSelectModalOpen} onCancel={() => setCaseSelectModalOpen(false)} footer={null} width={600}>
        <List
          dataSource={caseSets}
          renderItem={cs => (
            <List.Item actions={[<Button type="link" onClick={() => handleSelectCases(cs.id)}>选择</Button>]}>
              <List.Item.Meta title={cs.name} description={`${cs.caseCount}条用例`} />
            </List.Item>
          )}
        />
      </Modal>

      <Modal title="预览已选用例" open={previewModalOpen} onCancel={() => setPreviewModalOpen(false)} footer={null}>
        <List
          size="small"
          dataSource={selectedCaseIds}
          renderItem={item => <List.Item>用例#{item.nodeId} - 用例集#{item.caseSetId}</List.Item>}
        />
      </Modal>

      <Modal title="新建目录" open={dirModalOpen} onOk={handleCreateDir} onCancel={() => { setDirModalOpen(false); setDirName(''); }}>
        <Input value={dirName} onChange={e => setDirName(e.target.value)} placeholder="输入目录名称" />
      </Modal>
    </Layout>
  );
}
