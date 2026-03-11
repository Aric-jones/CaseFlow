import { create } from 'zustand';
import type { User, Project } from '../types';

interface AppState {
  user: User | null;
  currentProject: Project | null;
  projects: Project[];
  setUser: (user: User | null) => void;
  setCurrentProject: (project: Project | null) => void;
  setProjects: (projects: Project[]) => void;
  logout: () => void;
}

const useStore = create<AppState>((set) => ({
  user: null,
  currentProject: JSON.parse(localStorage.getItem('currentProject') || 'null'),
  projects: [],
  setUser: (user) => set({ user }),
  setCurrentProject: (project) => {
    localStorage.setItem('currentProject', JSON.stringify(project));
    set({ currentProject: project });
  },
  setProjects: (projects) => set({ projects }),
  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('currentProject');
    set({ user: null, currentProject: null, projects: [] });
  },
}));

export default useStore;
