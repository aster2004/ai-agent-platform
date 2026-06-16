# AI Agent Platform

基于 Java 21 + Spring Boot 3.5.4 + LangChain4j + LangGraph4j + Vue 3 的 AI 代码生成平台。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.5.4, Spring Security, JWT, JPA, LangChain4j, LangGraph4j |
| 前端 | Vue 3, TypeScript, Vite, Ant Design Vue 4, Pinia, Vue Router |
| 数据库 | MySQL 8, Redis 7 |
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

### 3. 配置后端

```bash
copy backend\src\main\resources\application-local.yml.example backend\src\main\resources\application-local.yml
```

编辑 `backend/src/main/resources/application-local.yml`，填入 MySQL 密码和 OpenAI API Key。

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
3. **接口**：统一返回 `Result<T>` 格式，需登录接口携带 `Authorization: Bearer <token>`
4. **分支**：功能分支命名 `feat/模块名-功能描述`

## 注意事项

- `application-local.yml` 含敏感信息，已在 `.gitignore` 中忽略
- 密码使用 BCrypt 加密存储
- AI 代码生成需配置有效的 OpenAI API Key
