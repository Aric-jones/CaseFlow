import { Routes, Route, Navigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { Spin } from 'antd';
import { authApi, projectApi } from './api';
import useStore from './stores/useStore';
import MainLayout from './pages/Layout';
import LoginPage from './pages/Login';
import CaseHomePage from './pages/CaseHome';
import MindMapEditor from './pages/MindMapEditor';
import ReviewPage from './pages/ReviewPage';
import TestPlanPage from './pages/TestPlan';
import TestPlanExecution from './pages/TestPlanExecution';
import RecycleBinPage from './pages/RecycleBin';
import MemberManagement from './pages/settings/MemberManagement';
import AttributeManagement from './pages/settings/AttributeManagement';
import ProjectManagement from './pages/settings/ProjectManagement';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('token');
  const { user, setUser, setProjects, setCurrentProject, currentProject } = useStore();
  const [loading, setLoading] = useState(!user && !!token);

  useEffect(() => {
    if (token && !user) {
      Promise.all([authApi.currentUser(), projectApi.list()])
        .then(([userRes, projRes]) => {
          setUser(userRes.data);
          setProjects(projRes.data);
          if (!currentProject && projRes.data.length > 0) {
            setCurrentProject(projRes.data[0]);
          }
        })
        .catch(() => {
          localStorage.removeItem('token');
        })
        .finally(() => setLoading(false));
    }
  }, [token, user, setUser, setProjects, setCurrentProject, currentProject]);

  if (!token) return <Navigate to="/login" replace />;
  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}><Spin size="large" /></div>;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
        <Route index element={<Navigate to="/cases" replace />} />
        <Route path="cases" element={<CaseHomePage />} />
        <Route path="test-plans" element={<TestPlanPage />} />
        <Route path="recycle-bin" element={<RecycleBinPage />} />
        <Route path="settings/members" element={<MemberManagement />} />
        <Route path="settings/attributes" element={<AttributeManagement />} />
        <Route path="settings/projects" element={<ProjectManagement />} />
      </Route>
      <Route path="/mind-map/:caseSetId" element={<ProtectedRoute><MindMapEditor /></ProtectedRoute>} />
      <Route path="/review/:caseSetId" element={<ProtectedRoute><ReviewPage /></ProtectedRoute>} />
      <Route path="/test-plan/:planId/execute" element={<ProtectedRoute><TestPlanExecution /></ProtectedRoute>} />
    </Routes>
  );
}
