# CaseFlow - 测试用例管理平台

## 技术栈

### 后端
- Java 17 + Spring Boot 3.2
- MyBatis Plus 3.5
- MySQL 8.0
- JWT 认证
- Apache POI (Excel导入)

### 前端
- Vue 3.4 + TypeScript
- Vite 5
- Ant Design Vue 4
- Vue Router 4
- Pinia (状态管理)
- Axios (HTTP请求)
- simple-mind-map (思维导图)

## 项目结构

```
├── backend/                  # Spring Boot 后端
│   ├── src/main/java/com/caseflow/
│   │   ├── config/          # 配置类 (Security, JWT, MyBatis)
│   │   ├── common/          # 通用类 (Result, Exception)
│   │   ├── entity/          # 实体类
│   │   ├── mapper/          # MyBatis Mapper
│   │   ├── service/         # 业务逻辑层
│   │   ├── controller/      # REST API 控制器
│   │   └── dto/             # 数据传输对象
│   └── src/main/resources/
│       └── application.yml  # 应用配置
├── frontend/                 # Vue 3 前端
│   ├── src/
│   │   ├── api/             # API 请求层
│   │   ├── views/           # 页面组件 (Vue SFC)
│   │   ├── stores/          # Pinia 状态管理
│   │   ├── router/          # Vue Router 路由
│   │   └── types/           # TypeScript 类型
│   └── vite.config.ts       # Vite 配置
└── sql/
    └── init.sql             # 数据库初始化脚本
```

## 快速启动

### 1. 数据库
```bash
mysql -u root -p < sql/init.sql
```

### 2. 后端
```bash
cd backend
# 修改 src/main/resources/application.yml 中的数据库配置
mvn spring-boot:run
```
后端启动后自动创建管理员账号: `admin / wps123456`

### 3. 前端
```bash
cd frontend
npm install
npm run dev
```
访问 http://localhost:3000

## 功能模块

### 用例管理
- 树形目录管理（右键新建/重命名/移动/删除）
- 用例集创建、搜索、筛选、分页
- 思维导图编辑器（节点增删改、属性设置、标记、标签）
- 用例规范校验（结构/顺序/优先级/必填属性）
- 自动保存（每10秒）+ 手动保存
- 历史版本（最近3个版本可恢复）
- 查找替换
- Excel 导入

### 评审
- 只读查看 + 节点评论
- 评审人选择 + 评审状态管理
- 节点标记（待完成/待确认/待修改）
- 评论解决状态追踪

### 测试计划
- 测试计划目录管理
- 选择用例集创建计划
- 执行人分配
- 执行结果记录（通过/不通过/跳过）
- 测试报告统计

### 系统设置
- 成员管理（角色/身份/权限）
- 用例属性管理（自定义属性）
- 项目空间管理

### 回收站
- 已删除用例集恢复
- 彻底删除（管理员/创建者）

## 默认账号
- 用户名: `admin`
- 密码: `wps123456`
