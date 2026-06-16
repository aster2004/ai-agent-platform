# 项目结构说明

## 整体目录

```
ai-agent-platform/
├── README.md
├── docker-compose.yml
├── docs/
├── backend/          # Spring Boot 后端
├── frontend/         # Vue 3 前端
└── sql/              # 数据库脚本
```

## 后端模块划分

路径：`backend/src/main/java/com/ai/agentplatform/module/{模块名}/`

每个业务模块遵循相同的分层结构：

```
module/{模块名}/
├── controller/     # REST 接口层
├── service/        # 业务逻辑层
├── entity/         # 数据库实体（JPA）
├── dto/            # 请求参数对象
├── vo/             # 响应视图对象（不暴露敏感字段）
└── repository/     # 数据访问层
```

## 前端目录说明

路径：`frontend/src/`

```
frontend/src/
├── api/            # 按模块拆分的 HTTP 请求
├── types/          # TypeScript 接口类型
├── stores/         # Pinia 全局状态
├── views/          # 页面级组件
├── layouts/        # 布局组件
├── components/     # 可复用公共组件
├── router/         # 路由配置与守卫
└── utils/          # 工具函数（axios 封装等）
```

## 新增模块步骤

### 后端

1. 在 `backend/src/main/java/com/ai/agentplatform/module/` 下创建新模块目录
2. 按分层创建 entity → repository → service → controller
3. 创建对应的 dto 和 vo
4. 在 Swagger 中添加 `@Tag` 注解

### 前端

1. 在 `frontend/src/types/` 添加类型定义
2. 在 `frontend/src/api/` 添加接口方法
3. 在 `frontend/src/views/` 创建页面
4. 在 `frontend/src/router/index.ts` 注册路由
