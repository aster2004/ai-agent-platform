# 一、全局统一基础规范
## 1.1全局返回格式：所有接口统一 JSON 结构，无例外

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```
- 空对象返回
```json
{
   "code": 200,
   "message": "success",
   "data": {}
}
```
- 空列表返回
```json
{
   "code": 200,
   "message": "success",
   "data": []
}
```
## 1.2 错误码规范

| 状态码 | 含义 | 业务场景 | 前端统一处理逻辑 |
|--------|------|----------|-----------------|
| 200 | 请求成功 | 所有正常业务操作 | 直接解析 data 渲染 |
| 400 | 参数非法 / 必填缺失 / 重复 | 用户名重复、prompt 为空、参数长度超限 | 弹窗展示 message 原文 |
| 401 | Token 失效 / 未登录| 无令牌、令牌解析失败、过期 | 清空本地 token + 用户信息，强制跳转登录 |
| 403 | 权限不足 | 普通用户访问管理员接口、操作他人数据 | 弹窗提示：无操作权限 |
| 404 | 资源不存在 | 用户 / 应用 / 会话 ID 不存在 | 弹窗：请求资源不存在 |
| 500 | 服务内部异常 | SQL、AI 接口、文件读写失败 |统一提示：服务器繁忙，请稍后重试 |

## 1.3 鉴权规则
### 1.3.1 Token 传递规范
1. 用户登录成功后，所有需要登录的业务接口，请求头必须携带身份令牌：
   `Authorization: Bearer {后端返回的真实JWT令牌}`
2. 开发并行Mock阶段（D3波次1集成登录功能之前），前端统一使用固定测试Token绕过登录校验，无需调用登录接口获取 JWT，固定令牌格式：
   `Authorization: Bearer dev-mock-token`
-说明：
1. 该 Mock Token 默认绑定测试用户 ID=1、角色 admin，仅本地 dev 环境生效，生产环境必须移除该逻辑；
2. 后端处理 Mock Token 规则：匹配dev-mock-token直接注入 userId=1、role=admin，不校验有效期。

### 1.3.2 无需登录放行白名单
以下接口不校验Token，游客、未登录用户均可直接访问：
1. 用户模块：用户注册接口、用户登录接口(POST /api/user/register、POST /api/user/login)
2. 应用模块：首页精选应用列表接口(GET /api/app/featured);应用预览页面(GET /api/app/{id}/preview)

### 1.3.3 权限分级约束
1. 普通登录用户：仅查询、修改自身创建的应用、对话会话、代码生成记录，无法操作他人私有数据；后端自动校验 user_id 匹配，不匹配返回 403；
2. 管理员（admin角色）：管理员接口添加注解@PreAuthorize("hasRole('ADMIN')")，可全量查看 / 管理数据

### 1.3.4 异常处理约束
1. 请求无 Token、Token 非法 / 过期：后端返回401；前端清空本地 token 与用户信息，自动跳转登录页面；
2. 普通用户访问管理员接口 / 操作他人资源：后端返回403；前端全局弹窗提示「当前账号无该操作权限」。

## 1.4时间统一规范
1. 所有数据库、接口返回时间格式：yyyy-MM-dd HH:mm:ss
2. create_time、update_time、last_message_time 统一遵循该格式:
3. 数据库自动填充，序列化 GMT+8 时区；新增消息自动更新会话 last_message_time

## 1.5 通用分页参数（所有普通列表接口统一使用）
1. 请求拼接参数格式：`?pageNum=1&pageSize=10`
2. `pageNum`：当前页码，数据类型：Integer，默认值：1
3. `pageSize`：每页展示数据条数，数据类型：Integer，默认值：10
4. 兼容规则：后端同时兼容page、size别名，优先读取 pageNum/pageSize
5. 通用分页返回数据结构示例：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 32,
    "list": [],
    "pageNum": 1,
    "pageSize": 10
  }
}
```

## 1.6游标分页（仅聊天历史）
1. 请求拼接格式：?sessionId=1&cursor=0&size=20
2. sessionId：会话 ID，必传参数；不传/传非法数字统一返回400
3. cursor：游标分页标记，规则：
- 首次加载全部历史：固定传 `0`
- 翻页时传入上一页返回的 `nextCursor`（当前页最后一条消息id）
4. size：单页消息条数，Integer，默认20；限制区间 1~100，超出自动截断为100
5. 查询逻辑：WHERE session_id = ? AND id < ? ORDER BY id DESC LIMIT ?
6. 分页判定规则：返回 list 长度等于 size → hasMore=true；小于 size → hasMore=false、nextCursor=null
7. 还有下一页：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "list": [
         {
            "id": 20,
            "sessionId": 10,
            "appId": 1,
            "messageType": "user",
            "content": "生成登录页",
            "createTime": "2026-06-17 16:30:00"
         }
      ],
      "nextCursor": 125,
      "hasMore": true
   }
}
```
8. 无更多历史（拉完最后一页，标准空尾示例）：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "list": [],
      "nextCursor": null,
      "hasMore": false
   }
}
```

## 1.7全局枚举统一（全模块共用）
### 1.7.1 用户模块枚举
1. 用户角色
- user：普通用户
- admin：管理员
2. 用户账号状态
- normal：账号正常
- disabled：账号禁用
### 1.7.2 应用模块枚举
1. 应用上下架状态
- normal：正常上架
- offline：已下架
2. is_featured（是否精选）:
- 0：非精选应用
- 1：精选应用（可在首页广场展示）
### 1.7.3 对话聊天模块枚举
1. 消息类型
- user：用户发送的提问消息
- ai：大模型返回的回答消息
### 1.7.4 AI 代码生成模块枚举
- 项目生成类型 generate_type
- 可选固定值：HTML、VUE、MULTI_FILE、WORKFLOW
- 代码生成状态 generate_status
- 0：生成中
- 1：生成成功
- 2：生成失败

## 1.8 逻辑删除统一规范
- 适用范围：仅 `chat_message` 表（对话消息）采用逻辑删除，其他表（user/app/code_generate）暂用物理删除。
### 规则
1. 字段：is_deleted，0 = 正常，1 = 已删除
2. 查询约束：所有消息查询接口默认过滤is_deleted = 0，不展示已删除数据
3. 删除接口：DELETE /api/chat/message/{id}，仅更新字段，不删除数据库记录

## 1.9 关联字段校验规范
通用关联字段：user_id、app_id、session_id
1. 所有业务接口必须校验数据归属，普通用户只能操作 user_id 与登录用户一致的数据； 
2. app_id、session_id 入参为空说明：
- chat_session.app_id = null：独立无应用会话
- code_generate.session_id = null：单次独立生成，无对话上下文
3. 关联 ID 不存在统一返回 404 错误。

## 1.10 特殊文件接口规范（下载 / 预览）
### 1.10.1 源码下载 GET /api/app/{id}/download
1. 正常响应非 JSON，响应头：
- Content-Type: application/zip
- Content-Disposition: attachment; filename="app-{id}.zip"
2. 异常（ID 不存在、无代码）返回标准 JSON 错误结构

### 1.10.2 应用预览 GET /api/app/{id}/preview
1. 前置校验：查询 app，若 app_code 为空直接返回 400；
2. 正常返回 JSON 携带预览地址，前端 iframe 渲染页面；
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "previewUrl": "http://localhost:8080/preview/1"
   }
}
```
3. 异常返回标准 JSON 错误结构

### 1.10.3 封面图片上传 POST /api/app/{id}/cover
1. 请求类型：multipart/form-data，表单字段 file 为图片；
2. 后端校验图片格式（png/jpg），上传成功更新 app.cover_img；
3. 返回示例：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "coverImg": "https://xxx/cover.png"
   }
}
```

## 1.11 SSE 流式接口规范（AI 生成专用）
- 适用接口：/api/codegen/stream、/api/codegen/work/stream
1. 请求头：Content-Type: application/json
2. 响应头固定：Content-Type: text/event-stream;charset=utf-8、Cache-Control: no-cache、Connection: keep-alive
3. 统一规则：省略 event 标识，每条 data 完整包裹code/message/data，末尾必须\n\n
4. 三种标准推送模板：
- 进度片段推送
   data: {
   "code": 200,
   "message": "success",
   "data": {
   "type": "progress",
   "step": "generating",
   "content": "<!"
   }
   }
- 生成完成推送
   data: {
   "code": 200,
   "message": "success",
   "data": {
   "type": "complete",
   "generateId": 1001,
   "generateStatus": 1,
   "modelName": "gpt-4o-mini",
   "costTokens": 120,
   "duration": 3500
   }
   }
- 生成失败推送
   data: {
   "code": 500,
   "message": "AI调用超时",
   "data": {
   "type": "error",
   "generateId": 1001,
   "generateStatus": 2,
   "errorMsg": "模型接口请求超时"
   }
   }
5. 异常推送 error 后服务端主动关闭连接

# 二、模块一 用户模块（成员 1 开发）
## 2.1用户注册
1. 请求方式：POST
2. 请求地址：/api/user/register
3. 请求头：无（白名单免登录）
4. 请求 Body：
```json
{
  "username": "test01",  
  "password": "123456",
  "nickname": "测试账号"
}
```
4. 参数约束：username 4-50（必传且唯一）; 位字符；password 6-20 位字符（必传）;nickname 非必传（可空）
5. 异常：
- 用户名重复返回 400 message="用户名已存在"
- 参数校验失败（如长度不符）返回 400 message="参数格式错误：xxx"
6. 返回 data
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "id": 1,
      "username": "dev_user",
      "nickname": "测试用户",
      "role": "user",
      "points": 0,
      "level": "v0",
      "createTime": "2024-01-01 10:00:00"
   }
}
```

## 2.2用户登录
1. 请求方式：POST
2. 请求地址：/api/user/login
3. 请求头：无（无需登录）
4. 请求 Body
```json
{
  "username": "dev_user",
  "password": "123456"
}
```
5. 异常：
- 用户名不存在返回 400 message="用户名不存在"
- 密码错误返回 400 message="密码错误"
- 用户被禁用返回 403 message="账号已禁用"
6. 返回 data
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "token": "eyJhxxxx.xxxx.xxxx",
      "userInfo": {
         "id": 1,
         "username": "dev_user",
         "nickname": "开发测试用户",
         "avatar": null,
         "role": "admin",
         "status": "normal",
         "points": 0,
         "level": "v0"
      }
   }
}
```

## 2.3获取当前登录用户信息
1. 请求方式：GET
2. 请求地址：/api/user/current
3. 请求头：Authorization: Bearer {token}
4. 异常： Token 无效 / 过期返回 401 message="未登录或 Token 已过期"
5. data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "id": 1,
      "username": "dev_user",
      "nickname": "测试用户",
      "avatar": null,
      "role": "admin",
      "points": 0,
      "level": "v0"
   }
}
```

## 2.4 退出登录
1. 请求方式：POST
2. 请求地址：/api/user/logout
3. 请求头：Authorization: Bearer {token}
4. Body：无
5. 异常： Token 无效 / 过期返回 401 message="未登录或 Token 已过期"
6. data：
```json
{
   "code": 200,
   "message": "success",
   "data": null
}
```

## 2.5管理员 - 用户列表
1. 请求方式：GET
2. 请求地址：/api/user/list?pageNum=1&pageSize=10&username=&status=
3. 请求头：Authorization: Bearer {token}
4. 权限限制：仅admin角色可访问，普通用户访问返回 403
5. 请求参数说明：
- pageNum：页码（必传，默认 1）
- pageSize：页大小（必传，默认 10）
- username：用户名模糊查询（非必传）
- status：用户状态筛选（normal/disabled，非必传）
6. 异常：
- Token 无效 / 过期返回 401 message="未登录或 Token 已过期"
- 非管理员访问返回 403 message="无管理员权限"
7. data:
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "total": 32,
      "list": [
         {
            "id": 1,
            "username": "dev_user",
            "nickname": "开发测试用户",
            "avatar": null,
            "role": "admin",
            "status": "normal",
            "points": 0,
            "level": "v0",
            "createTime": "2026-06-17 14:20:00",
            "updateTime": "2026-06-17 14:20:00"
         }
      ]
   }
}
```
## 2.6管理员启用 / 禁用用户（新增缺失接口）
1. 请求方式：PUT
2. 请求地址：/api/user/{id}/status
3. 请求头：Authorization: Bearer {token}
4. 权限限制：仅 admin 角色可访问，普通用户访问返回 403 message="无管理员权限"
5. 请求路径参数：id - 用户 ID（必传）
6. 请求 Body：
```json
{
   "status": "disabled" 
}
```
7. 异常
- Token 无效 / 过期返回 401 message="未登录或 Token 已过期"
- 非管理员访问返回 403 message="无管理员权限"
- 用户 ID 不存在返回 404 message="用户不存在"
- 参数错误（如 status 值非法）返回 400 message="状态值只能是 normal 或 disabled"
8. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "id": 1,
      "status": "disabled"
   }
}
```


# 三、模块二 应用基础模块（成员 2 开发）
## 3.1 创建应用
1. 请求方式：POST
2. 请求地址：/api/app
3. 请求头：携带 Authorization Token
4. 请求 Body：
```json
{
  "appName": "Vue后台管理系统",
  "description": "基于Vue3+ElementPlus开发后台平台",
  "coverImg": null
}
```
5. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "id": 1,
      "appName": "我的测试应用",
      "description": "用于测试的应用",
      "coverImg": null,
      "appCode": null,
      "isFeatured": 0,
      "status": "normal",
      "deployUrl": null,
      "createTime": "2024-01-01 10:00:00",
      "updateTime": "2024-01-01 10:00:00"
   }
}
```

## 3.2 获取我的应用列表
1. 请求方式：GET
2. 请求地址：/api/app/list?pageNum=1&pageSize=10
3. 请求头：Authorization: Bearer {token}
4. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "total": 1,
      "list": [
         {
            "id": 1,
            "appName": "Mock 测试应用",
            "description": "并行开发期默认应用",
            "coverImg": null,
            "isFeatured": 0,
            "status": "normal",
            "deployUrl": null,
            "createTime": "2024-01-01 10:00:00",
            "updateTime": "2024-01-01 10:00:00"
         }
      ]
   }
}
```

## 3.3应用详情 
1. 请求方式：GET
2. 请求地址：/api/app/{id}
3. 请求头：携带 Authorization Token
4. 路径参数：id = 应用主键 ID
4. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "id": 1,
      "appName": "Mock 测试应用",
      "description": "并行开发期默认应用",
      "coverImg": null,
      "appCode": "<html><body><h1>Hello Mock App</h1></body></html>",
      "isFeatured": 0,
      "status": "normal",
      "deployUrl": null,
      "createTime": "2024-01-01 10:00:00",
      "updateTime": "2024-01-01 10:00:00"
   }
}
```

## 3.4编辑应用基础信息
1. 请求方式：PUT
2. 请求地址：/api/app/{id}
3. 请求头：携带 Authorization Token
4. 路径参数：id = 应用主键 ID
5. 请求 Body
```json
{
   "appName": "新版后台系统",
   "description": "更新后的项目描述",
   "coverImg": "https://xxx/cover.png"
}
```
6. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "id": 1,
      "appName": "新版后台系统",
      "description": "更新后的项目描述",
      "coverImg": "https://xxx/cover.png",
      "appCode": "<html><body><h1>Hello Mock App</h1></body></html>",
      "isFeatured": 0,
      "status": "normal",
      "deployUrl": null,
      "createTime": "2024-01-01 10:00:00",
      "updateTime": "2024-01-01 11:00:00"
   }
}
```

## 3.5删除应用
1. 请求方式：DELETE
2. 请求地址：/api/app/{id}
3. 请求头：携带 Authorization Token
4. 路径参数：id = 应用主键 ID
5. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": null
}
```

## 3.6 更新应用代码（供 AI 模块调用）
1. 请求方式：PUT
2. 请求地址：/api/app/{id}/code
3. 请求头：携带 Authorization Token
4. 路径参数：id = 应用主键 ID
5. 请求 Body
```json
{
  "codeContent": "<html><body>页面代码</body></html>"
}
```
6. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "id": 1,
      "appCode": "<html><body>页面代码</body></html>",
      "updateTime": "2024-01-01 12:00:00"
   }
}
```

## 3.7 精选应用广场（游客 / 登录均可访问
1. 请求方式：GET
2. 请求地址：/api/app/featured
3. 请求头：无 Token 也可访问
4. 请求参数（可选）：pageNum、pageSize 分页参数
5. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "total": 0,
      "list": [
         {
            "id": 2,
            "appName": "精选Vue应用",
            "description": "精选应用示例",
            "coverImg": "https://xxx/featured.png",
            "isFeatured": 1,
            "status": "normal",
            "deployUrl": "http://localhost:8080/sites/2/",
            "createTime": "2024-01-02 10:00:00",
            "updateTime": "2024-01-02 10:00:00"
         }
      ]
   }
}
```

## 3.8 管理员设置应用精选状态
1. 请求方式：PUT
2. 请求地址：/api/app/{id}/featured
3. 请求头：admin 角色 Token
4. 路径参数：id = 应用主键 ID
5. 请求 Body
```json
{
  "isFeatured": 1
}
```
6. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "id": 1,
      "isFeatured": 1,
      "updateTime": "2024-01-01 13:00:00"
   }
}
```

## 3.9管理员上下架应用
1. 请求方式：PUT
2. 请求地址：/api/app/{id}/status
3. 请求头：admin 角色 Token
4. 路径参数：id = 应用主键 ID
5. 请求 Body：
```json
{
   "status": "offline"
}
```
6. 返回 data：
 ```json  
{
   "code": 200,
   "message": "success",
   "data": {
      "id": 1,
      "status": "offline",
      "updateTime": "2024-01-01 14:00:00"
   }
}
```
## 3.10管理员获取全量应用列表
1. 请求方式：GET
2. 请求地址：/api/app/admin/list?pageNum=1&pageSize=10&status=normal&keyword=测试
3. 请求头：admin 角色 Token
4. 请求参数：pageNum：页码；pageSize：每页条数；status：筛选上下架；keyword：应用名称模糊搜索
5. 返回 data：
 ```json  
{
   "code": 200,
   "message": "success",
   "data": {
      "total": 2,
      "list": [
         {
            "id": 1,
            "appName": "Mock 测试应用",
            "username": "dev_user",
            "description": "并行开发期默认应用",
            "isFeatured": 1,
            "status": "normal",
            "createTime": "2024-01-01 10:00:00",
            "updateTime": "2024-01-01 13:00:00"
         },
         {
            "id": 2,
            "appName": "精选Vue应用",
            "username": "admin",
            "description": "精选应用示例",
            "isFeatured": 1,
            "status": "normal",
            "createTime": "2024-01-02 10:00:00",
            "updateTime": "2024-01-02 10:00:00"
         }
      ]
   }
}
```

# 四、模块三 应用部署模块（成员 3 开发）
## 4.1获取预览地址
1. 请求方式：GET
2. 请求地址：/api/app/{id}/preview
3. 请求头：携带 Authorization Token（白名单，Token 可选）
4. 返回 data
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "previewUrl": "http://localhost:8080/preview/1"
   }
}
```
5. 说明：
- 预览地址指向的静态资源需保证跨域配置合规
- 若应用未生成代码（app_code 为空），返回 code=400，msg="应用代码为空，无法预览"

## 4.2 下载项目源码包
1. 请求方式：GET
2. 请求地址：/api/app/{id}/download
3. 请求头：携带 Authorization Token
4. 响应类型：二进制文件流，浏览器自动下载 zip
5. 异常场景： 
- 应用不存在：返回 code=404，msg="应用不存在"
- 应用代码为空：返回 code=400，msg="无源码可下载"

## 4.3一键部署生成分享链接
1. 请求方式：POST
2. 请求地址：/api/app/{id}/deploy
3. 请求头：携带 Authorization Token
4. 返回 data
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "deployUrl": "http://localhost:8080/sites/1",
      "deployType": "LOCAL",
      "deployTime": "2024-08-01 10:00:00"
   }
}
```
## 4.4 生成应用封面截图（扩展，P2 优先级）
1. 请求方式：POST
2. 请求地址：/api/app/{id}/screenshot
3. 请求头：携带 Authorization Token
4. 返回data:
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "coverImgUrl": "http://localhost:8080/uploads/cover/1.png"
   }
}
```
5. 补充说明：
- 截图成功后自动更新 app 表的 cover_img 字段
- 异常场景：预览地址不可访问：code=500，msg="截图失败：预览地址无法访问";应用不存在：code=404，msg="应用不存在"


# 五、模块四 AI 基础代码生成（成员 4 开发）
## 5.1同步生成（一次性返回完整代码）
1. 请求方式：POST
2. 请求地址：/api/codegen
3. 请求头：
- 开发前期：无需 Authorization
- 集成后（D3 波次 1）：Authorization: Bearer {token}
4. 请求 Body
```json
{
  "appId": 1,
  "prompt": "写一个用户管理HTML页面",
  "generateType": "HTML"
}
```
5. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "generateId": 1001,
      "codeContent": "<!DOCTYPE html><html><head><title>用户管理</title></head><body>...</body></html>",
      "generateStatus": 1,
      "modelName": "gpt-4o-mini",
      "costTokens": 120,
      "duration": 3500
   }
}
```

## 5.2 SSE 流式生成（前端聊天核心接口）
1. 请求方式：POST
2. 请求地址：/api/codegen/stream
3. 请求头：
- 开发前期：无需 Authorization
- 集成后（D3 波次 1）：Authorization: Bearer {token}
- Content-Type: application/json
4. Body:
```json
{
   "appId": 1,
   "prompt": "写一个用户管理HTML页面",
   "generateType": "HTML"
}
```
5.SSE 推送严格遵循 1.11 统一规范，省略 event、每条带 \n\n，外层统一 code/message

# 六、模块五 AI 工作流生成（成员 5 开发）
## 6.1 工作流流式生成
1. 请求方式：POST
2. 请求地址：/api/codegen/work/stream
3. 请求头：Content-Type: application/json；集成后携带 Token
4. Body：
```json
{
   "prompt": "生成一个 Vue 表单页面，包含用户名、密码输入框和提交按钮",
   "appId": 1,
   "generateType": "VUE",
   "sessionId": 1
}
```
5. 推送全部复用 1.11 标准 SSE 模板，每条末尾 \n\n
## 6.2工作流同步生成（一次性完整返回）
1. 请求方式：POST
2. 请求地址：/api/codegen/workflow
3. 请求头：开发前期免登录，D3 集成携带 Token
4. 请求 Body 和流式接口一致
5. 返回data:
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "generateId": 1001,
      "codeContent": "<!DOCTYPE html><html><head><title>用户管理</title></head><body>...</body></html>",
      "generateStatus": 1,
      "modelName": "gpt-4o-mini",
      "costTokens": 120,
      "duration": 3500
   }
}
```

# 七、模块六 对话后端（成员 6 开发）
## 7.1 获取当前用户所有会话（侧边栏）
1. 请求方式：GET
2. 请求地址：/api/chat/session/list
3. 请求头：携带 Authorization Token
4. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": [
      {
         "id": 10,
         "appId": 1,
         "sessionTitle": "Vue登录页面生成",
         "lastMessagePreview": "请生成Vue登录页面",
         "lastMessageTime": "2026-06-17 16:30:00",
         "messageCount": 2
      }
   ]
}
```

## 7.2 保存消息（新建会话 / 发送消息共用）
1. 请求方式：POST
2. 请求地址：/api/chat/save
3. 请求头：携带 Authorization Token
4. 请求 Body：
```json
{
   "sessionId": null,
   "appId": 1,
   "messageType": "user",
   "content": "请生成Vue登录页面"
}
```
5. 说明：sessionId 为 null 自动创建新会话
6. 返回 data：
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "sessionId": 10,
      "messag·eId": 20
   }
}
```
## 7.3 会话历史消息（游标分页）
1. 请求方式：GET
2. 请求地址：/api/chat/history?sessionId=10&cursor=0&size=20
3. 请求头：携带 Authorization Token
4. 返回 data:
```json
{
   "code": 200,
   "message": "success",
   "data": {
      "list": [
         {
            "id": 20,
            "sessionId": 10,
            "appId": 1,
            "messageType": "user",
            "content": "生成登录页",
            "createTime": "2026-06-17 16:30:00",
            "isDeleted": false
         }
      ],
      "nextCursor": null,
      "hasMore": false
   }
}
```
## 7.4删除会话
1. 请求方式：DELETE
2. 请求地址：/api/chat/session/{id}
3. 请求头：携带 Authorization Token
4. 返回 data:
```json
{
   "code": 200,
   "message": "success",
   "data": null
}
```
