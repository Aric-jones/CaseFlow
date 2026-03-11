import { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, Switch, Tag, Space, message, Typography } from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { customAttributeApi } from '../../api';
import useStore from '../../stores/useStore';
import type { CustomAttribute } from '../../types';

const NODE_TYPES = [
  { value: '', label: '不限制' },
  { value: 'TITLE', label: '用例标题' },
  { value: 'PRECONDITION', label: '前置条件' },
  { value: 'STEP', label: '步骤' },
  { value: 'EXPECTED', label: '预期结果' },
];

export default function AttributeManagement() {
  const { currentProject } = useStore();
  const [attributes, setAttributes] = useState<CustomAttribute[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const loadAttributes = async () => {
    if (!currentProject) return;
    setLoading(true);
    try {
      const res = await customAttributeApi.list(currentProject.id);
      setAttributes(res.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadAttributes(); }, [currentProject]);

  const handleSave = async () => {
    const values = await form.validateFields();
    const data = {
      ...values,
      projectId: currentProject?.id,
      options: values.options?.split(',').map((s: string) => s.trim()).filter(Boolean) || [],
      multiSelect: values.multiSelect ? 1 : 0,
      nodeTypeLimit: values.nodeTypeLimit || null,
    };

    if (editingId) {
      await customAttributeApi.update(editingId, data);
      message.success('更新成功');
    } else {
      await customAttributeApi.create(data);
      message.success('创建成功');
    }
    setModalOpen(false);
    setEditingId(null);
    form.resetFields();
    loadAttributes();
  };

  const handleDelete = async (id: number) => {
    await customAttributeApi.delete(id);
    message.success('删除成功');
    loadAttributes();
  };

  const handleEdit = (attr: CustomAttribute) => {
    setEditingId(attr.id);
    form.setFieldsValue({
      name: attr.name,
      options: attr.options.join(','),
      multiSelect: attr.multiSelect === 1,
      nodeTypeLimit: attr.nodeTypeLimit || '',
      displayType: attr.displayType,
    });
    setModalOpen(true);
  };

  const columns = [
    { title: '属性名', dataIndex: 'name', key: 'name' },
    {
      title: '属性值', dataIndex: 'options', key: 'options',
      render: (options: string[]) => options.map(o => <Tag key={o}>{o}</Tag>),
    },
    {
      title: '多选', dataIndex: 'multiSelect', key: 'multiSelect', width: 80,
      render: (v: number) => v ? <Tag color="blue">是</Tag> : <Tag>否</Tag>,
    },
    {
      title: '限制节点', dataIndex: 'nodeTypeLimit', key: 'nodeTypeLimit', width: 120,
      render: (v: string) => v ? NODE_TYPES.find(n => n.value === v)?.label : '不限制',
    },
    { title: '展示形式', dataIndex: 'displayType', key: 'displayType', width: 100 },
    {
      title: '操作', key: 'action', width: 120,
      render: (_: unknown, record: CustomAttribute) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.id)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4}>用例属性管理</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingId(null); form.resetFields(); setModalOpen(true); }}>新增属性</Button>
      </div>

      <Table rowKey="id" columns={columns} dataSource={attributes} loading={loading} pagination={false} size="middle" />

      <Modal title={editingId ? '编辑属性' : '新增属性'} open={modalOpen} onOk={handleSave} onCancel={() => { setModalOpen(false); setEditingId(null); form.resetFields(); }}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="属性名称" rules={[{ required: true }]}>
            <Input placeholder="属性名称" />
          </Form.Item>
          <Form.Item name="options" label="属性值（逗号分隔）" rules={[{ required: true }]}>
            <Input placeholder="如: 值1,值2,值3" />
          </Form.Item>
          <Form.Item name="multiSelect" label="是否多选" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="nodeTypeLimit" label="限制节点类型">
            <Select options={NODE_TYPES} placeholder="不限制" allowClear />
          </Form.Item>
          <Form.Item name="displayType" label="展示形式" initialValue="DROPDOWN">
            <Select options={[{ value: 'DROPDOWN', label: '下拉框' }, { value: 'TILE', label: '平铺' }]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
