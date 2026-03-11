import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { User, Project } from '../types';

export const useAppStore = defineStore('app', () => {
  const user = ref<User | null>(null);
  const currentProject = ref<Project | null>(
    JSON.parse(localStorage.getItem('currentProject') || 'null')
  );
  const projects = ref<Project[]>([]);

  function setUser(u: User | null) { user.value = u; }
  function setCurrentProject(p: Project | null) {
    currentProject.value = p;
    localStorage.setItem('currentProject', JSON.stringify(p));
  }
  function setProjects(list: Project[]) { projects.value = list; }
  function logout() {
    user.value = null;
    currentProject.value = null;
    projects.value = [];
    localStorage.removeItem('token');
    localStorage.removeItem('currentProject');
  }

  return { user, currentProject, projects, setUser, setCurrentProject, setProjects, logout };
});
