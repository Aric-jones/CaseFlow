import { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Space, message, Typography, Popconfirm } from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { projectApi } from '../../api';
import type { Project } from '../../types';

export default function ProjectManagement() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const loadProjects = async () => {
    setLoading(true);
    try {
      const res = await projectApi.listAll();
      setProjects(res.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadProjects(); }, []);

  const handleSave = async () => {
    const values = await form.validateFields();
    if (editingId) {
      await projectApi.update(editingId, values.name, values.description);
      message.success('更新成功');
    } else {
      await projectApi.create(values.name, values.description);
      message.success('创建成功');
    }
    setModalOpen(false);
    form.resetFields();
    setEditingId(null);
    loadProjects();
  };

  const handleDelete = async (id: number) => {
    await projectApi.delete(id);
    message.success('删除成功');
    loadProjects();
  };

  const handleEdit = (project: Project) => {
    setEditingId(project.id);
    form.setFieldsValue({ name: project.name, description: project.description });
    setModalOpen(true);
  };

  const columns = [
    { title: '项目名称', dataIndex: 'name', key: 'name' },
    { title: '描述', dataIndex: 'description', key: 'description' },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    {
      title: '操作', key: 'action', width: 160,
      render: (_: unknown, record: Project) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4}>项目空间管理</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingId(null); form.resetFields(); setModalOpen(true); }}>创建项目</Button>
      </div>

      <Table rowKey="id" columns={columns} dataSource={projects} loading={loading} pagination={false} size="middle" />

      <Modal title={editingId ? '编辑项目' : '创建项目'} open={modalOpen} onOk={handleSave} onCancel={() => { setModalOpen(false); setEditingId(null); form.resetFields(); }}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="项目名称" rules={[{ required: true }]}>
            <Input placeholder="输入项目名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea placeholder="项目描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
