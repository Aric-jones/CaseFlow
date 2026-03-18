import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { User, Project } from '../types';

export const useAppStore = defineStore('app', () => {
  const user = ref<User | null>(null);
  const currentProject = ref<Project | null>(
    JSON.parse(localStorage.getItem('currentProject') || 'null')
  );
  const projects = ref<Project[]>([]);
  const permissions = ref<string[]>([]);
  const userRoles = ref<string[]>([]);

  function setUser(u: User | null) { user.value = u; }
  function setCurrentProject(p: Project | null) {
    currentProject.value = p;
    localStorage.setItem('currentProject', JSON.stringify(p));
  }
  function setProjects(list: Project[]) { projects.value = list; }
  function setPermissions(perms: string[], roles: string[]) {
    permissions.value = perms;
    userRoles.value = roles;
  }
  function hasPermission(code: string): boolean {
    return permissions.value.includes(code);
  }
  function hasAnyPermission(...codes: string[]): boolean {
    return codes.some(c => permissions.value.includes(c));
  }
  function logout() {
    user.value = null;
    currentProject.value = null;
    projects.value = [];
    permissions.value = [];
    userRoles.value = [];
    localStorage.removeItem('token');
    localStorage.removeItem('currentProject');
  }

  return {
    user, currentProject, projects, permissions, userRoles,
    setUser, setCurrentProject, setProjects, setPermissions,
    hasPermission, hasAnyPermission, logout,
  };
});
