# API 接口说明

完整接口文档请访问 Swagger UI：http://localhost:8080/swagger-ui.html

## 认证方式

除注册、登录、健康检查外，所有接口需在 Header 中携带：

```
Authorization: Bearer <token>
```

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 主要接口

| 模块 | 方法 | 路径 | 说明 | 需登录 |
|------|------|------|------|--------|
| 系统 | GET | /api/health | 健康检查 | 否 |
| 用户 | POST | /api/user/register | 注册 | 否 |
| 用户 | POST | /api/user/login | 登录 | 否 |
| 用户 | GET | /api/user/list | 用户列表 | 是 |
| 应用 | POST | /api/app | 创建应用 | 是 |
| 应用 | GET | /api/app/list | 应用列表 | 是 |
| 代码生成 | POST | /api/codegen | 同步生成 | 是 |
| 代码生成 | POST | /api/codegen/stream | 流式生成 (SSE) | 是 |

## 错误码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 业务错误 / 参数校验失败 |
| 401 | 未登录或 Token 过期 |
| 500 | 系统内部错误 |
