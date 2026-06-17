# AI Agent Platform

基于 Java 21 + Spring Boot 3.5.4 + LangChain4j + LangGraph4j + Vue 3 的 AI 代码生成平台。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.5.4, JPA, LangChain4j, LangGraph4j |
| 前端 | Vue 3, TypeScript, Vite, Ant Design Vue 4, Pinia, Vue Router |
| 数据库 | MySQL 8, Redis 7（可选） |
| 文档 | Swagger (springdoc-openapi) |

## 项目结构

```
ai-agent-platform/
├── README.md                  # 项目说明、启动步骤、分工
├── docker-compose.yml         # MySQL + Redis 一键启动
├── docs/                      # 设计文档、API 说明
│
├── backend/                   # 后端（Spring Boot）
│   └── src/main/java/com/ai/agentplatform/
│       ├── module/user/       # 用户模块
│       ├── module/app/        # 应用模块
│       ├── module/codegen/    # 代码生成模块
│       ├── common/            # 公共组件
│       └── config/            # 全局配置
│
├── frontend/                  # 前端（Vue 3）
│   └── src/
│       ├── api/               # 按模块拆分
│       ├── types/             # TS 类型
│       ├── stores/            # Pinia 状态
│       ├── views/
│       ├── components/        # 公共组件
│       ├── layouts/
│       ├── router/
│       └── utils/
│
└── sql/
    └── init.sql
```

## 组员从零开始（必读）

> 把下面步骤发给新加入的组员，按顺序执行即可。

### 第 0 步：安装必备软件

| 软件 | 版本 | 下载 / 检查 |
|------|------|-------------|
| Git | 任意 | https://git-scm.com/download/win ，终端执行 `git -v` |
| JDK | 21 | https://adoptium.net/ ，终端执行 `java -version` |
| Node.js | 18+ | https://nodejs.org/ ，终端执行 `node -v` 和 `npm -v` |
| MySQL | 8 | 本地安装，**root 密码建议设为 `123456`**（与团队配置一致） |

> **注意：** 前端用 `npm`，后端用 `mvnw`，**不要在 `backend/` 目录里运行 `npm install`**。

### 第 1 步：克隆项目

```powershell
# 进入你想放项目的目录，例如 D:\code
cd D:\code

# 克隆仓库（地址以 GitHub 上显示的为准）
git clone https://github.com/aster2004/ai-agent-platform.git

# 进入项目
cd ai-agent-platform
```

### 第 2 步：创建数据库

用 Navicat 或 MySQL 命令行执行：

```sql
CREATE DATABASE IF NOT EXISTS ai_agent_platform
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 第 3 步：确认数据库配置（已提交到 Git）

团队默认数据库配置在 `backend/src/main/resources/application-dev.yml`：

| 配置项 | 默认值 |
|--------|--------|
| 地址 | `localhost:3306` |
| 数据库 | `ai_agent_platform` |
| 用户名 | `root` |
| 密码 | `123456` |

**组员 MySQL 密码也是 `123456` 的话，无需任何额外配置，直接启动即可。**

若本机密码不同，可选创建个人覆盖配置：

```powershell
cd backend
copy src\main\resources\application-local.yml.example src\main\resources\application-local.yml
# 编辑 application-local.yml，只改 password 即可
```

### 第 4 步：启动后端

```powershell
# 确保在 backend 目录
cd backend

.\mvnw.cmd spring-boot:run
```

看到 `Started AiAgentPlatformApplication` 表示成功。**保持这个窗口不要关。**

- 健康检查：http://localhost:8080/api/health

### 第 5 步：启动前端（新开一个终端）

```powershell
cd D:\code\ai-agent-platform\frontend

npm install

npm run dev
```

看到 `http://localhost:5173/` 后，浏览器打开：**http://localhost:5173/codegen**

### 第 6 步：日常更新代码

```powershell
# 拉取最新代码
git pull

# 如果 frontend/package.json 有变化，重新安装依赖
cd frontend
npm install
```

### 常见错误

| 报错 | 原因 | 解决 |
|------|------|------|
| `Access denied for user 'root'` | MySQL 密码不是 `123456` | 创建 `application-local.yml` 覆盖密码 |
| `backend 找不到 package.json` | 在 backend 里运行了 npm | npm 只在 `frontend/` 用 |
| `'vite' 不是内部命令` | 没执行 `npm install` | 在 frontend 先 `npm install` |
| 8080 端口被占用 | 后端已在运行 | 关掉旧进程或换端口 |

## 快速开始

### 1. 环境要求

- JDK 21+
- Node.js 18+
- Maven 3.9+
- Docker（可选，用于 MySQL/Redis）

### 2. 启动基础设施

**方式 A：Docker（推荐）**

```bash
docker-compose up -d
```

**方式 B：本地 MySQL**

```sql
CREATE DATABASE ai_agent_platform DEFAULT CHARACTER SET utf8mb4;
```

### 3. 数据库配置

团队默认配置已提交在 `backend/src/main/resources/application-dev.yml`（用户名 `root`，密码 `123456`）。

密码不同时，可复制 `application-local.yml.example` 为 `application-local.yml` 覆盖。

### 4. 启动后端

```bash
cd backend
.\mvnw.cmd spring-boot:run
```

- API 地址：http://localhost:8080
- Swagger 文档：http://localhost:8080/swagger-ui.html
- 健康检查：http://localhost:8080/api/health

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

- 前端地址：http://localhost:5173

## 模块分工建议

| 模块 | 目录 | 职责 |
|------|------|------|
| 用户模块 | `backend/.../module/user/` + `frontend/src/views/login/` | 注册、登录、JWT、用户管理 |
| 应用模块 | `backend/.../module/app/` + `frontend/src/views/app/` | 应用 CRUD |
| 代码生成 | `backend/.../module/codegen/` + `frontend/src/views/codegen/` | LangChain4j 流式生成、LangGraph4j 工作流 |
| 公共组件 | `backend/.../common/` + `frontend/src/components/` | 通用工具、公共 UI |

## 开发规范

1. **后端**：每个业务模块包含 `controller / service / entity / dto / vo / repository`
2. **前端**：API 调用放 `api/`，类型定义放 `types/`，禁止 Entity 直接暴露给前端
3. **接口**：统一返回 `Result<T>` 格式
4. **分支**：功能分支命名 `feat/模块名-功能描述`

## 注意事项

- `application-dev.yml` 为团队共享的数据库配置（已提交 Git）
- `application-local.yml` 可选，用于覆盖本机密码或 OpenAI API Key（不提交 Git）
