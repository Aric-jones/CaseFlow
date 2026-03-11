import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Dropdown, Space, Typography, Avatar, Select } from 'antd';
import {
  FileTextOutlined, ScheduleOutlined, SettingOutlined, BookOutlined,
  UserOutlined, LogoutOutlined, SwapOutlined,
} from '@ant-design/icons';
import useStore from '../stores/useStore';
import type { Project } from '../types';

const { Header, Content } = Layout;
const { Text } = Typography;

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, currentProject, projects, setCurrentProject, logout } = useStore();

  const navItems = [
    { key: '/cases', label: '用例首页', icon: <FileTextOutlined /> },
    { key: '/test-plans', label: '测试计划', icon: <ScheduleOutlined /> },
  ];

  const settingsItems = user?.role !== 'MEMBER' ? [
    { key: '/settings/members', label: '成员管理' },
    { key: '/settings/attributes', label: '用例属性管理' },
    { key: '/settings/projects', label: '项目空间管理' },
  ] : [];

  const userMenuItems = [
    { key: 'profile', label: '个人中心', icon: <UserOutlined /> },
    { type: 'divider' as const },
    { key: 'logout', label: '退出登录', icon: <LogoutOutlined />, danger: true },
  ];

  const handleUserMenu = ({ key }: { key: string }) => {
    if (key === 'logout') {
      logout();
      navigate('/login');
    }
  };

  const activeKey = navItems.find(item => location.pathname.startsWith(item.key))?.key
    || (location.pathname.startsWith('/settings') ? '/settings' : '/cases');

  return (
    <Layout style={{ height: '100vh' }}>
      <Header style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        background: '#fff', borderBottom: '1px solid #f0f0f0', padding: '0 24px',
        position: 'sticky', top: 0, zIndex: 100,
      }}>
        <Space size={32}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }} onClick={() => navigate('/cases')}>
            <FileTextOutlined style={{ fontSize: 24, color: '#1677ff' }} />
            <Text strong style={{ fontSize: 18, color: '#1677ff' }}>CaseFlow</Text>
          </div>
          <Menu
            mode="horizontal"
            selectedKeys={[activeKey]}
            items={[
              ...navItems.map(item => ({ ...item, onClick: () => navigate(item.key) })),
              ...(settingsItems.length > 0 ? [{
                key: '/settings',
                label: '系统设置',
                icon: <SettingOutlined />,
                children: settingsItems.map(item => ({ ...item, onClick: () => navigate(item.key) })),
              }] : []),
            ]}
            style={{ border: 'none', flex: 1 }}
          />
        </Space>

        <Space size={16}>
          <BookOutlined style={{ cursor: 'pointer', fontSize: 16 }} title="使用手册" />
          <Select
            value={currentProject?.id}
            onChange={(val) => {
              const proj = projects.find((p: Project) => p.id === val);
              if (proj) setCurrentProject(proj);
            }}
            style={{ width: 160 }}
            suffixIcon={<SwapOutlined />}
            options={projects.map((p: Project) => ({ label: p.name, value: p.id }))}
            placeholder="选择项目"
          />
          <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenu }} placement="bottomRight">
            <Space style={{ cursor: 'pointer' }}>
              <Avatar size="small" icon={<UserOutlined />} style={{ backgroundColor: '#1677ff' }} />
              <Text>{user?.displayName}</Text>
            </Space>
          </Dropdown>
        </Space>
      </Header>
      <Content style={{ overflow: 'auto' }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
