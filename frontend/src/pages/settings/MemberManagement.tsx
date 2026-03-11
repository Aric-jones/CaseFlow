import { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, Tag, Space, message, Typography } from 'antd';
import { PlusOutlined, UserOutlined } from '@ant-design/icons';
import { userApi, projectApi } from '../../api';
import type { User, Project } from '../../types';

const ROLE_MAP: Record<string, { label: string; color: string }> = {
  SUPER_ADMIN: { label: '超管', color: 'red' },
  ADMIN: { label: '管理员', color: 'orange' },
  MEMBER: { label: '成员', color: 'blue' },
};

const IDENTITY_MAP: Record<string, string> = { TEST: '测试', DEV: '研发', PRODUCT: '产品' };

export default function MemberManagement() {
  const [users, setUsers] = useState<User[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);

  const loadUsers = async (p = 1) => {
    setLoading(true);
    try {
      const res = await userApi.list(p, 20);
      setUsers(res.data.records);
      setTotal(res.data.total);
      setPage(p);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
    projectApi.listAll().then(res => setProjects(res.data));
  }, []);

  const handleCreate = async () => {
    const values = await form.validateFields();
    await userApi.create({
      ...values,
      password: 'wps123456',
    });
    message.success('创建成功，默认密码 wps123456');
    setModalOpen(false);
    form.resetFields();
    loadUsers();
  };

  const handleRoleChange = async (id: number, role: string) => {
    await userApi.update(id, { role } as Partial<User>);
    message.success('角色已更新');
    loadUsers(page);
  };

  const handleIdentityChange = async (id: number, identity: string) => {
    await userApi.update(id, { identity } as Partial<User>);
    message.success('身份已更新');
    loadUsers(page);
  };

  const handleToggleStatus = async (id: number) => {
    await userApi.toggleStatus(id);
    message.success('状态已更新');
    loadUsers(page);
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '显示名', dataIndex: 'displayName', key: 'displayName' },
    {
      title: '角色', dataIndex: 'role', key: 'role', width: 130,
      render: (role: string, record: User) => (
        <Select
          size="small"
          value={role}
          onChange={val => handleRoleChange(record.id, val)}
          options={Object.entries(ROLE_MAP).map(([k, v]) => ({ value: k, label: v.label }))}
          style={{ width: 100 }}
        />
      ),
    },
    {
      title: '身份', dataIndex: 'identity', key: 'identity', width: 120,
      render: (identity: string, record: User) => (
        <Select
          size="small"
          value={identity}
          onChange={val => handleIdentityChange(record.id, val)}
          options={Object.entries(IDENTITY_MAP).map(([k, v]) => ({ value: k, label: v }))}
          style={{ width: 80 }}
        />
      ),
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (status: number) => <Tag color={status === 1 ? 'success' : 'error'}>{status === 1 ? '启用' : '禁用'}</Tag>,
    },
    {
      title: '操作', key: 'action', width: 100,
      render: (_: unknown, record: User) => (
        <Button type="link" size="small" onClick={() => handleToggleStatus(record.id)}>
          {record.status === 1 ? '禁用' : '启用'}
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4}>成员管理</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>添加成员</Button>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={{ current: page, total, pageSize: 20, onChange: p => loadUsers(p) }}
        size="middle"
      />

      <Modal title="添加成员" open={modalOpen} onOk={handleCreate} onCancel={() => { setModalOpen(false); form.resetFields(); }}>
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input placeholder="显示名称" />
          </Form.Item>
          <Form.Item name="identity" label="成员身份" rules={[{ required: true }]}>
            <Select options={Object.entries(IDENTITY_MAP).map(([k, v]) => ({ value: k, label: v }))} />
          </Form.Item>
          <Form.Item name="projectIds" label="项目权限">
            <Select mode="multiple" options={projects.map(p => ({ value: p.id, label: p.name }))} placeholder="选择项目" />
          </Form.Item>
        </Form>
        <Typography.Text type="secondary">默认密码: wps123456</Typography.Text>
      </Modal>
    </div>
  );
}
