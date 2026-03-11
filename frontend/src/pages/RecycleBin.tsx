import { useState, useEffect } from 'react';
import { Table, Button, Space, Tag, Typography, Popconfirm, message, Empty } from 'antd';
import { UndoOutlined, DeleteOutlined } from '@ant-design/icons';
import { recycleBinApi, caseSetApi } from '../api';
import useStore from '../stores/useStore';
import type { RecycleBinItem, CaseSet } from '../types';

export default function RecycleBinPage() {
  const { currentProject, user } = useStore();
  const [items, setItems] = useState<(RecycleBinItem & { caseSetName?: string })[]>([]);
  const [loading, setLoading] = useState(false);

  const loadData = async () => {
    if (!currentProject) return;
    setLoading(true);
    try {
      const res = await recycleBinApi.list(currentProject.id);
      const enriched = await Promise.all(
        res.data.map(async (item) => {
          try {
            const csRes = await caseSetApi.get(item.caseSetId);
            return { ...item, caseSetName: csRes.data?.name || `用例集#${item.caseSetId}` };
          } catch {
            return { ...item, caseSetName: `用例集#${item.caseSetId}` };
          }
        })
      );
      setItems(enriched);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [currentProject]);

  const handleRestore = async (id: number) => {
    await recycleBinApi.restore(id);
    message.success('已恢复');
    loadData();
  };

  const handlePermanentDelete = async (id: number) => {
    await recycleBinApi.permanentDelete(id);
    message.success('已彻底删除');
    loadData();
  };

  const isOwnerOrAdmin = (deletedBy: number) => {
    return user?.id === deletedBy || user?.role === 'SUPER_ADMIN' || user?.role === 'ADMIN';
  };

  const columns = [
    { title: '用例集名称', dataIndex: 'caseSetName', key: 'caseSetName' },
    { title: '删除时间', dataIndex: 'deletedAt', key: 'deletedAt', width: 180 },
    { title: '删除人', dataIndex: 'deletedBy', key: 'deletedBy', width: 100 },
    {
      title: '操作', key: 'action', width: 200,
      render: (_: unknown, record: RecycleBinItem & { caseSetName?: string }) => (
        <Space>
          <Button type="link" size="small" icon={<UndoOutlined />} onClick={() => handleRestore(record.id)}>恢复</Button>
          {isOwnerOrAdmin(record.deletedBy) && (
            <Popconfirm title="确认彻底删除？此操作不可恢复" onConfirm={() => handlePermanentDelete(record.id)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>彻底删除</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Typography.Title level={4}>用例回收站</Typography.Title>
      {items.length === 0 ? (
        <Empty description="回收站为空" />
      ) : (
        <Table rowKey="id" columns={columns} dataSource={items} loading={loading} pagination={false} size="middle" />
      )}
    </div>
  );
}
