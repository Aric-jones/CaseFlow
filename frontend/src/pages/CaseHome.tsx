import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Layout, Tree, Input, Button, Table, Space, Tag, Modal, Form, Select,
  Dropdown, message, Upload, Pagination, Popconfirm, Typography, Empty,
} from 'antd';
import {
  PlusOutlined, ImportOutlined, FolderOutlined, SearchOutlined,
  DeleteOutlined, EditOutlined, CopyOutlined, SwapOutlined,
  EyeOutlined, MoreOutlined, FolderOpenOutlined, InboxOutlined,
} from '@ant-design/icons';
import type { DataNode } from 'antd/es/tree';
import { directoryApi, caseSetApi } from '../api';
import useStore from '../stores/useStore';
import type { DirectoryNode, CaseSet, PageResult } from '../types';

const { Sider, Content } = Layout;
const { Search } = Input;

function dirToTreeData(dirs: DirectoryNode[]): DataNode[] {
  return dirs.map(d => ({
    key: d.id,
    title: d.name,
    icon: <FolderOutlined />,
    children: d.children?.length ? dirToTreeData(d.children) : [],
  }));
}

function flatDirs(dirs: DirectoryNode[]): DirectoryNode[] {
  const result: DirectoryNode[] = [];
  const walk = (list: DirectoryNode[]) => {
    for (const d of list) {
      result.push(d);
      if (d.children?.length) walk(d.children);
    }
  };
  walk(dirs);
  return result;
}

function isLeaf(dirs: DirectoryNode[], id: number): boolean {
  const flat = flatDirs(dirs);
  return !flat.some(d => d.parentId === id);
}

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  WRITING: { label: '编写中', color: 'processing' },
  PENDING_REVIEW: { label: '待评审', color: 'warning' },
  NO_REVIEW: { label: '无需评审', color: 'default' },
};

export default function CaseHomePage() {
  const navigate = useNavigate();
  const { currentProject, user } = useStore();
  const [dirs, setDirs] = useState<DirectoryNode[]>([]);
  const [selectedDir, setSelectedDir] = useState<number | null>(null);
  const [caseData, setCaseData] = useState<PageResult<CaseSet>>({ records: [], total: 0, size: 20, current: 1, pages: 0 });
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [moveModalOpen, setMoveModalOpen] = useState(false);
  const [movingId, setMovingId] = useState<number | null>(null);
  const [moveTarget, setMoveTarget] = useState<number | null>(null);
  const [dirModalOpen, setDirModalOpen] = useState(false);
  const [dirParentId, setDirParentId] = useState<number | null>(null);
  const [dirName, setDirName] = useState('');
  const [renameId, setRenameId] = useState<number | null>(null);
  const [renameName, setRenameName] = useState('');
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);

  const loadDirs = useCallback(async () => {
    if (!currentProject) return;
    const res = await directoryApi.tree(currentProject.id, 'CASE');
    setDirs(res.data);
  }, [currentProject]);

  const loadCases = useCallback(async (page = 1) => {
    if (!currentProject) return;
    setLoading(true);
    try {
      const res = await caseSetApi.list({
        directoryId: selectedDir ?? undefined,
        projectId: currentProject.id,
        keyword: keyword || undefined,
        status: statusFilter,
        page,
        size: 20,
      });
      setCaseData(res.data);
    } finally {
      setLoading(false);
    }
  }, [currentProject, selectedDir, keyword, statusFilter]);

  useEffect(() => { loadDirs(); }, [loadDirs]);
  useEffect(() => { loadCases(); }, [loadCases]);

  const handleCreateDir = async () => {
    if (!dirName.trim() || !currentProject) return;
    await directoryApi.create(dirName.trim(), dirParentId, currentProject.id, 'CASE');
    message.success('创建成功');
    setDirModalOpen(false);
    setDirName('');
    loadDirs();
  };

  const handleRenameDir = async () => {
    if (!renameName.trim() || !renameId) return;
    await directoryApi.rename(renameId, renameName.trim());
    message.success('重命名成功');
    setRenameModalOpen(false);
    loadDirs();
  };

  const handleDeleteDir = async (id: number) => {
    await directoryApi.delete(id);
    message.success('删除成功');
    if (selectedDir === id) setSelectedDir(null);
    loadDirs();
  };

  const handleCreateCase = async () => {
    const values = await form.validateFields();
    if (!currentProject) return;
    const targetDir = selectedDir || dirs[0]?.id;
    if (!targetDir) { message.error('请先创建目录'); return; }
    const res = await caseSetApi.create({
      name: values.name,
      directoryId: targetDir,
      projectId: currentProject.id,
      requirementLink: values.requirementLink,
    });
    message.success('创建成功');
    setCreateModalOpen(false);
    form.resetFields();
    navigate(`/mind-map/${res.data.id}`);
  };

  const handleDelete = async (id: number) => {
    await caseSetApi.delete(id);
    message.success('已移入回收站');
    loadCases();
  };

  const handleMove = async () => {
    if (movingId && moveTarget) {
      await caseSetApi.move(movingId, moveTarget);
      message.success('移动成功');
      setMoveModalOpen(false);
      loadCases();
    }
  };

  const handleCopy = async (id: number) => {
    const flat = flatDirs(dirs);
    const leaves = flat.filter(d => isLeaf(dirs, d.id));
    if (leaves.length === 0) { message.error('没有可用的目录'); return; }
    const targetDir = selectedDir && isLeaf(dirs, selectedDir) ? selectedDir : leaves[0].id;
    await caseSetApi.copy(id, targetDir);
    message.success('复制成功');
    loadCases();
  };

  const handleImport = async (file: File) => {
    if (!currentProject) return;
    const targetDir = selectedDir || dirs[0]?.id;
    if (!targetDir) { message.error('请先创建目录'); return; }
    await caseSetApi.importExcel(file, targetDir, currentProject.id);
    message.success('导入成功');
    setImportModalOpen(false);
    loadCases();
  };

  const isOwnerOrAdmin = (createdBy: number) => {
    return user?.id === createdBy || user?.role === 'SUPER_ADMIN' || user?.role === 'ADMIN';
  };

  const columns = [
    {
      title: '用例集名称', dataIndex: 'name', key: 'name',
      render: (text: string, record: CaseSet) => (
        <a onClick={() => navigate(`/mind-map/${record.id}`)}>{text}</a>
      ),
    },
    { title: '用例数量', dataIndex: 'caseCount', key: 'caseCount', width: 100 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 120,
      render: (status: string) => {
        const s = STATUS_MAP[status] || { label: status, color: 'default' };
        return <Tag color={s.color}>{s.label}</Tag>;
      },
    },
    { title: '创建人', dataIndex: 'createdBy', key: 'createdBy', width: 100 },
    { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    {
      title: '关联需求', dataIndex: 'requirementLink', key: 'requirementLink', width: 120,
      render: (link: string) => link ? <a href={link} target="_blank" rel="noreferrer">查看需求</a> : '-',
    },
    {
      title: '操作', key: 'action', width: 200, fixed: 'right' as const,
      render: (_: unknown, record: CaseSet) => (
        <Space size={0}>
          {record.status === 'PENDING_REVIEW' && (
            <Button type="link" size="small" onClick={() => navigate(`/review/${record.id}`)}>评审</Button>
          )}
          <Button type="link" size="small" onClick={() => navigate(`/mind-map/${record.id}`)}>编辑</Button>
          <Dropdown menu={{
            items: [
              ...(isOwnerOrAdmin(record.createdBy) ? [
                { key: 'move', label: '移动', icon: <SwapOutlined /> },
              ] : []),
              { key: 'copy', label: '复制', icon: <CopyOutlined /> },
              ...(isOwnerOrAdmin(record.createdBy) ? [
                { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true },
              ] : []),
            ],
            onClick: ({ key }) => {
              if (key === 'move') { setMovingId(record.id); setMoveModalOpen(true); }
              if (key === 'copy') handleCopy(record.id);
              if (key === 'delete') Modal.confirm({ title: '确认删除?', content: '将移入回收站', onOk: () => handleDelete(record.id) });
            },
          }}>
            <Button type="link" size="small" icon={<MoreOutlined />} />
          </Dropdown>
        </Space>
      ),
    },
  ];

  const treeData = dirToTreeData(dirs);

  const dirContextMenu = (nodeId: number) => {
    const isRoot = dirs.some(d => d.id === nodeId && d.parentId === null);
    const items = [
      { key: 'addChild', label: '新建子目录', icon: <FolderOpenOutlined /> },
      { key: 'rename', label: '重命名', icon: <EditOutlined /> },
    ];
    if (!isRoot) {
      if (isLeaf(dirs, nodeId)) {
        items.push({ key: 'delete', label: '删除', icon: <DeleteOutlined /> });
      }
    }
    return items;
  };

  return (
    <Layout style={{ height: '100%', background: '#fff' }}>
      <Sider width={260} style={{ background: '#fff', borderRight: '1px solid #f0f0f0', padding: '16px 8px', overflow: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 8px', marginBottom: 12 }}>
          <Typography.Text strong>用例目录</Typography.Text>
          <Space size={4}>
            <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
              新建用例
            </Button>
            <Button size="small" icon={<ImportOutlined />} onClick={() => setImportModalOpen(true)}>
              导入
            </Button>
          </Space>
        </div>

        {dirs.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <Empty description="暂无目录" />
            <Button type="link" onClick={() => { setDirParentId(null); setDirModalOpen(true); }}>
              创建根目录
            </Button>
          </div>
        ) : (
          <Tree
            treeData={treeData}
            selectedKeys={selectedDir ? [selectedDir] : []}
            onSelect={(keys) => setSelectedDir(keys[0] as number || null)}
            titleRender={(node) => (
              <Dropdown
                menu={{
                  items: dirContextMenu(node.key as number),
                  onClick: ({ key }) => {
                    if (key === 'addChild') { setDirParentId(node.key as number); setDirModalOpen(true); }
                    if (key === 'rename') { setRenameId(node.key as number); setRenameName(String(node.title)); setRenameModalOpen(true); }
                    if (key === 'delete') handleDeleteDir(node.key as number);
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

        <div
          style={{ padding: '12px 8px', borderTop: '1px solid #f0f0f0', marginTop: 12, cursor: 'pointer', color: '#8c8c8c' }}
          onClick={() => navigate('/recycle-bin')}
        >
          <DeleteOutlined /> 用例回收站
        </div>
      </Sider>

      <Content style={{ padding: 24 }}>
        <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
          <Search
            placeholder="搜索用例集名称"
            value={keyword}
            onChange={e => setKeyword(e.target.value)}
            onSearch={() => loadCases()}
            style={{ width: 300 }}
            enterButton={<SearchOutlined />}
          />
          <Select
            placeholder="状态筛选"
            allowClear
            style={{ width: 140 }}
            value={statusFilter}
            onChange={val => setStatusFilter(val)}
            options={[
              { value: 'WRITING', label: '编写中' },
              { value: 'PENDING_REVIEW', label: '待评审' },
              { value: 'NO_REVIEW', label: '无需评审' },
            ]}
          />
          <Button icon={<SearchOutlined />} type="primary" onClick={() => loadCases()}>搜索</Button>
        </div>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={caseData.records}
          loading={loading}
          pagination={false}
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys as number[]),
          }}
          scroll={{ x: 1200 }}
          size="middle"
        />

        {caseData.total > 0 && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
            <Pagination
              current={caseData.current}
              total={caseData.total}
              pageSize={caseData.size}
              onChange={(page) => loadCases(page)}
              showTotal={(total) => `共 ${total} 条`}
              showSizeChanger={false}
            />
          </div>
        )}
      </Content>

      <Modal title="新建用例" open={createModalOpen} onOk={handleCreateCase} onCancel={() => { setCreateModalOpen(false); form.resetFields(); }}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="用例集名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="输入用例集名称" />
          </Form.Item>
          <Form.Item name="requirementLink" label="关联需求（可选）">
            <Input placeholder="输入需求链接" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="导入用例" open={importModalOpen} onCancel={() => setImportModalOpen(false)} footer={null}>
        <Typography.Paragraph type="secondary">
          请上传 Excel（.xlsx）。表头必须包含：用例标题、前置条件、步骤、预期结果。
          可选：用例集名称、测试点1、测试点2、标签、优先级、涉及自动化、用例覆盖端、公私网海外是否存在差异、用例归属平台。
        </Typography.Paragraph>
        <Upload.Dragger
          accept=".xlsx"
          maxCount={1}
          beforeUpload={(file) => { handleImport(file); return false; }}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p>点击或拖拽文件到此处上传</p>
        </Upload.Dragger>
      </Modal>

      <Modal title="移动用例集" open={moveModalOpen} onOk={handleMove} onCancel={() => setMoveModalOpen(false)}>
        <Typography.Text>选择目标目录：</Typography.Text>
        <Tree
          treeData={treeData}
          selectedKeys={moveTarget ? [moveTarget] : []}
          onSelect={(keys) => setMoveTarget(keys[0] as number || null)}
          defaultExpandAll
          blockNode
          style={{ marginTop: 12 }}
        />
      </Modal>

      <Modal title="新建目录" open={dirModalOpen} onOk={handleCreateDir} onCancel={() => { setDirModalOpen(false); setDirName(''); }}>
        <Input value={dirName} onChange={e => setDirName(e.target.value)} placeholder="输入目录名称" />
      </Modal>

      <Modal title="重命名" open={renameModalOpen} onOk={handleRenameDir} onCancel={() => setRenameModalOpen(false)}>
        <Input value={renameName} onChange={e => setRenameName(e.target.value)} />
      </Modal>
    </Layout>
  );
}
