# 博客系统（blog-springboot）接口测试用例

> **基础地址**：`http://localhost:8080`  
> **鉴权方式**：Sa-Token，请求头 `Authorization: Bearer <token>`  
> **说明**：本文档依据 `E:\blog\blog-springboot` **源码**整理；后台 `/admin/**` 除 `/admin/login` 外需**后台登录**；标注了 `@SaCheckPermission` 的接口需对应**权限码**。前台 `/user/**` 在拦截器配置下通常需**前台登录**，例外为 `PUT /user/password`（重置密码）。  
> **与口头需求对照**：源码中 **文章点赞**、**评论点赞**、**说说点赞** 除登录外还配置了具体 `SaCheckPermission`（如 `blog:article:like`）；**发表评论** 为 `@SaCheckLogin` + `news:comment:add`；**文件下载** 为 `GET /file/download/{fileId}` 且**无鉴权注解**（公开）；**后台 AI**（`/admin/ai/*`）仅依赖后台登录，控制器上未见额外 Permission；另含 **`GET /article/daily`**、**`POST /ai/quick-read`**、**`POST /admin/talk/upload`** 等扩展接口。  
> **优先级定义**：P0 正常主流程；P1 参数缺失/格式错误；P2 鉴权、限流、边界；P3 业务异常（不存在资源、重复数据等）。

---

## 目录

1. [通用约定](#1-通用约定)
2. [认证与登录](#2-认证与登录-logincontroller)
3. [文章管理](#3-文章管理-articlecontroller)
4. [分类管理](#4-分类管理-categorycontroller)
5. [标签管理](#5-标签管理-tagcontroller)
6. [评论管理](#6-评论管理-commentcontroller)
7. [留言管理](#7-留言管理-messagecontroller)
8. [说说管理](#8-说说管理-talkcontroller)
9. [友链管理](#9-友链管理-friendcontroller)
10. [用户与个人信息](#10-用户与个人信息-usercontroller--userinfocontroller)
11. [角色与菜单](#11-角色与菜单-rolecontroller--menucontroller)
12. [相册与照片](#12-相册与照片-albumcontroller--photocontroller)
13. [轮播图](#13-轮播图-carouselcontroller)
14. [文件管理](#14-文件管理-blogfilecontroller)
15. [日志管理](#15-日志管理-logcontroller)
16. [定时任务](#16-定时任务-taskcontroller)
17. [博客信息与站点配置](#17-博客信息与站点配置-bloginfocontroller--siteconfigcontroller)
18. [AI 功能](#18-ai-功能-aicontroller--aipromptcontroller)
19. [个人功能：待办 / 日记 / 习惯 / 任务池 / 时间块 / 思考](#19-个人功能待办--日记--习惯--任务池--时间块--思考)
20. [附录](#20-附录)

---

## 1. 通用约定

| 项 | 说明 |
| --- | --- |
| 请求头 | `Content-Type: application/json`（文件上传为 `multipart/form-data`） |
| Token | 后台与前台登录返回的 token 可能分属不同登录体系，**勿混用** |
| 分页 | 常见参数 `current`、`size`（具体以各 Query 对象为准） |
| 统一响应 | `Result`：`code`、`msg`、`data`（以项目实现为准） |

---

## 2. 认证与登录 (LoginController)

### 2.1 后台登录

#### 后台用户登录
- **URL**: `POST /admin/login`
- **权限**: 公开
- **请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 用户名 |
| password | string | 是 | 密码，长度 ≥ 6 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AUTH-A01 | 合法账号密码登录 | P0 | 存在后台账号 | 正确 username/password | 返回 token，`code` 成功 |
| AUTH-A02 | 用户名为空 | P1 | - | username 空或缺省 | 参数校验失败 |
| AUTH-A03 | 密码不足 6 位 | P1 | - | password 长度 &lt; 6 | 参数校验失败 |
| AUTH-A04 | 错误密码 | P3 | - | 错误 password | 业务失败（账号或密码错误等） |
| AUTH-A05 | 非管理员账号（若业务限制） | P3 | 仅前台用户 | 正确凭证 | 按业务：拒绝后台登录 |

### 2.2 前台登录

#### 客户端登录
- **URL**: `POST /client/login`
- **权限**: 公开
- **请求参数**: 同 `LoginReq`（username、password）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AUTH-C01 | 合法用户登录 | P0 | 存在用户 | 正确凭证 | 返回 token |
| AUTH-C02 | password 缺省 | P1 | - | 无 password | 校验失败 |
| AUTH-C03 | 错误凭证 | P3 | - | 错误密码 | 登录失败 |

### 2.3 退出登录

#### 用户退出
- **URL**: `GET /logout`
- **权限**: `@SaCheckLogin`（需已登录）
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AUTH-L01 | 携带 token 退出 | P0 | 已登录 | Header 带 Bearer | 成功，会话失效 |
| AUTH-L02 | 无 token | P2 | - | 无 Authorization | 未登录错误 |
| AUTH-L03 | 过期/伪造 token | P2 | - | 非法 token | 鉴权失败 |

### 2.4 注册

#### 邮箱注册
- **URL**: `POST /register`
- **权限**: 公开
- **请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 邮箱格式 |
| password | string | 是 | ≥ 6 位 |
| code | string | 是 | 邮箱验证码 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AUTH-R01 | 正确注册 | P0 | 已发验证码 | 合法邮箱+密码+正确 code | 注册成功 |
| AUTH-R02 | username 非邮箱 | P1 | - | 非法邮箱 | 校验失败 |
| AUTH-R03 | code 为空 | P1 | - | 缺 code | 校验失败 |
| AUTH-R04 | 验证码错误 | P3 | - | 错误 code | 业务失败 |
| AUTH-R05 | 重复注册 | P3 | 用户已存在 | 同邮箱 | 失败提示已存在 |

### 2.5 发送验证码

#### 发送邮箱验证码
- **URL**: `GET /code`
- **权限**: 公开
- **请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 建议必填 | 收件邮箱 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AUTH-V01 | 首次发送 | P0 | 合法邮箱 | username=有效邮箱 | 成功发送 |
| AUTH-V02 | 60 秒内重复请求 | P2 | 刚发送过 | 同 username 连续请求 | 触发 `@AccessLimit`，限流提示 |
| AUTH-V03 | username 为空 | P1 | - | 不传或空 | 依实现：失败或异常 |

### 2.6 第三方 OAuth 登录

#### Gitee 登录
- **URL**: `POST /oauth/gitee`
- **权限**: 公开
- **请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| code | string | 是 | 授权码 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| OAUTH-G01 | 合法 code | P0 | OAuth 流程真实 code | 有效 code | 返回 token |
| OAUTH-G02 | code 为空 | P1 | - | 空 body | 依校验：失败或下游错误 |
| OAUTH-G03 | 无效 code | P3 | - | 随机字符串 | 第三方/业务错误 |

#### Github 登录
- **URL**: `POST /oauth/github`
- **权限/参数/用例**: 同 Gitee（用例 ID 前缀改为 `OAUTH-H`）

#### QQ 登录
- **URL**: `POST /oauth/qq`
- **权限**: 公开（Body 使用 `@Validated`）
- **请求参数**: `CodeReq.code` 必填

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| OAUTH-Q01 | 合法 code | P0 | - | 有效 code | 返回 token |
| OAUTH-Q02 | code 缺省 | P1 | - | 无 code | 参数校验失败 |

---

## 3. 文章管理 (ArticleController)

### 3.1 后台

#### 后台文章分页列表
- **URL**: `GET /admin/article/list`
- **权限**: 后台登录 + `blog:article:list`
- **请求参数**（Query）:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | string | 否 | 关键词 |
| categoryId | int | 否 | 分类 ID |
| tagId | int | 否 | 标签 ID |
| isDelete | int | 否 | 是否回收站等 |
| status | int | 否 | 状态 |
| articleType | int | 否 | 文章类型 |
| current | long | 否 | 页码 |
| size | long | 否 | 每页条数 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-B01 | 默认分页查询 | P0 | 管理员+权限 | current/size 合法 | 返回分页数据 |
| ART-B02 | 组合条件筛选 | P0 | 有数据 | keyword+categoryId 等 | 结果符合筛选 |
| ART-B03 | 无 token | P2 | - | 无 Authorization | 401/未登录 |
| ART-B04 | 无 list 权限 | P2 | 无权限角色 | 带 token | 403 |
| ART-B05 | current=0 或超大 size | P2 | - | 边界分页 | 依实现：空或默认修正 |

#### 新增文章
- **URL**: `POST /admin/article/add`
- **权限**: `blog:article:add`
- **请求参数**（JSON `ArticleReq`）:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| articleTitle | string | 是 | 标题 |
| articleDesc | string | 是 | 摘要 |
| articleContent | string | 是 | 正文 |
| categoryName | string | 是 | 分类名 |
| tagNameList | array | 否 | 标签名列表 |
| articleCover | string | 否 | 封面 |
| articleType | int | 否 | 1/2/3 |
| isTop | int | 否* | 置顶（实体上常用） |
| isRecommend | int | 否* | 推荐 |
| status | int | 否* | 1 公开 2 私密 3 草稿 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-A01 | 完整合法新增 | P0 | 权限+分类存在 | 全必填合法 | 成功 |
| ART-A02 | 标题为空 | P1 | - | 缺 articleTitle | 校验失败 |
| ART-A03 | 分类名不存在 | P3 | - | 随机 categoryName | 业务失败或自动创建（依实现） |
| ART-A04 | 无 add 权限 | P2 | - | 合法 body | 403 |

#### 修改文章
- **URL**: `PUT /admin/article/update`
- **权限**: `blog:article:update`
- **请求参数**: `ArticleReq`（含 `id` 用于更新）
- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-U01 | 更新成功 | P0 | 文章存在 | 合法 id+内容 | 成功 |
| ART-U02 | id 不存在 | P3 | - | 不存在的 id | 失败 |
| ART-U03 | 正文为空 | P1 | - | articleContent 空 | 校验失败 |

#### 删除文章
- **URL**: `DELETE /admin/article/delete`
- **权限**: `blog:article:delete`
- **请求参数**: Body `List<Integer>` 文章 ID 列表

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-D01 | 批量删除 | P0 | 文章存在 | [id1,id2] | 成功 |
| ART-D02 | 空列表 | P1 | - | [] | 依实现 |
| ART-D03 | 含不存在 ID | P3 | - | 混合 ID | 部分成功或报错（依实现） |

#### 编辑页数据
- **URL**: `GET /admin/article/edit/{articleId}`
- **权限**: `blog:article:edit`
- **请求参数**: Path `articleId`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-E01 | 查询存在文章 | P0 | - | 有效 ID | 返回详情 |
| ART-E02 | 非法 ID | P3 | - | 负数/极大值 | 失败 |
| ART-E03 | 无权限 | P2 | - | 有效 ID | 403 |

#### 置顶
- **URL**: `PUT /admin/article/top`
- **权限**: `blog:article:top`
- **请求参数**: `TopReq`：`id`（NotNull）、`isTop`（NotNull）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-T01 | 置顶切换 | P0 | 文章存在 | 合法 id、isTop | 成功 |
| ART-T02 | id 为空 | P1 | - | 缺 id | 校验失败 |

#### 推荐
- **URL**: `PUT /admin/article/recommend`
- **权限**: `blog:article:recommend`
- **请求参数**: `RecommendReq`：`id` @NotNull、`isRecommend` @NotNull

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-RC01 | 设置/取消推荐 | P0 | 文章存在 | 合法 id、isRecommend 0/1 | 成功 |
| ART-RC02 | id 为 null | P1 | - | 缺 id | 校验失败 |
| ART-RC03 | isRecommend 为 null | P1 | - | 缺 isRecommend | 校验失败 |
| ART-RC04 | 无 recommend 权限 | P2 | - | 合法 body | 403 |
| ART-RC05 | 文章不存在 | P3 | - | 无效 id | 业务失败 |

#### 回收站/恢复
- **URL**: `PUT /admin/article/recycle`
- **权限**: `blog:article:recycle`
- **请求参数**: `DeleteReq`：`idList`（NotEmpty）、`isDelete`（NotNull）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-RCY01 | 移入回收站 | P0 | 文章存在 | idList+isDelete=1 | 成功 |
| ART-RCY02 | idList 为空 | P1 | - | [] | 校验失败 |

#### 上传文章图片
- **URL**: `POST /admin/article/upload`
- **权限**: `blog:article:upload`
- **请求参数**: `multipart`：`file`（必填）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-UP01 | 上传合法图片 | P0 | 权限 | 小体积 jpg/png | 返回 URL |
| ART-UP02 | 无 file | P1 | - | 空 part | 失败 |
| ART-UP03 | 超大文件/非法类型 | P2 | - | 超大或非图片 | 失败或拦截 |

### 3.2 前台（公开/半公开）

#### 首页文章列表
- **URL**: `GET /article/list`
- **权限**: 公开
- **请求参数**: `PageQuery`：`current`、`size`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-H01 | 默认分页 | P0 | - | 默认或合法分页 | 返回列表 |
| ART-H02 | 页码边界 | P2 | - | current 极大 | 空列表或最后一页 |

#### 文章详情
- **URL**: `GET /article/{articleId}`
- **权限**: 公开

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-H10 | 存在文章 | P0 | 已发布 | 有效 ID | 返回详情 |
| ART-H11 | 不存在 ID | P3 | - | 999999 | 失败 |

#### 搜索
- **URL**: `GET /article/search`
- **权限**: 公开
- **请求参数**: `keyword`（Query）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-S01 | 有结果关键词 | P0 | 有匹配 | keyword | 列表非空 |
| ART-S02 | 空 keyword | P2 | - | 不传 | 依实现：全量或空 |

#### 推荐列表
- **URL**: `GET /article/recommend`
- **权限**: 公开
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-RM01 | 获取推荐文章列表 | P0 | 库中有推荐文章 | 无 | 返回非空列表 |
| ART-RM02 | 无推荐数据 | P2 | 无推荐位文章 | 无 | 返回空列表 |
| ART-RM03 | 并发多次请求 | P2 | - | 多次 GET | 均 200，数据一致 |

#### 浏览排行
- **URL**: `GET /article/rank`
- **权限**: 公开
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-RK01 | 获取浏览量排行 | P0 | 有浏览数据 | 无 | 返回排行列表（至多 5 条，以产品为准） |
| ART-RK02 | 无文章 | P3 | 空库 | 无 | 空列表 |

#### 每日推荐（源码）
- **URL**: `GET /article/daily`
- **权限**: 公开
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-DY01 | 获取每日推荐 | P0 | 有可用推荐 | 无 | 返回单篇推荐对象 |
| ART-DY02 | 无可推荐文章 | P3 | - | 无 | null/空或业务提示（依实现） |

#### 文章归档
- **URL**: `GET /archives/list`
- **权限**: 公开
- **请求参数**: `PageQuery`（`current`、`size`）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-AR01 | 分页查询归档 | P0 | 有归档数据 | current=1,size=10 | 分页结果 |
| ART-AR02 | 超大页码 | P2 | - | current=99999 | 空分页 |
| ART-AR03 | 缺省分页参数 | P2 | - | 不传 | 默认分页或校验（依实现） |

#### 点赞文章
- **URL**: `POST /article/{articleId}/like`
- **权限**: 登录 + `blog:article:like` + `@AccessLimit(60s,3)`
- **请求参数**: Path `articleId`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ART-LK01 | 首次点赞 | P0 | 前台登录+权限 | 有效 articleId | 成功 |
| ART-LK02 | 无 token | P2 | - | - | 未登录 |
| ART-LK03 | 无 like 权限 | P2 | 登录但无权限 | - | 403 |
| ART-LK04 | 60s 内第 4 次点赞 | P2 | 已点 3 次 | 继续请求 | 429/限流 |
| ART-LK05 | 不存在文章 | P3 | - | 无效 ID | 业务失败 |

---

## 4. 分类管理 (CategoryController)

#### 后台分类分页列表
- **URL**: `GET /admin/category/list`
- **权限**: 后台登录 + `blog:category:list`
- **请求参数**（Query）:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | string | 否 | 关键词 |
| current | int | 否 | 页码（见 `PageQuery` 转换逻辑） |
| size | int | 否 | 每页条数 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAT-L01 | 默认/合法分页 | P0 | 管理员+权限 | keyword 可选 | 返回分页列表 |
| CAT-L02 | keyword 筛选 | P0 | 有匹配数据 | keyword | 结果匹配 |
| CAT-L03 | 无 token | P2 | - | - | 未登录 |
| CAT-L04 | 无 list 权限 | P2 | 无权限角色 | - | 403 |
| CAT-L05 | 无数据页 | P3 | 空库或大页码 | current 极大 | 空分页 |

#### 新增分类
- **URL**: `POST /admin/category/add`
- **权限**: `blog:category:add`
- **请求参数**（JSON）:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| categoryName | string | 是 | @NotBlank |
| orderNum | int | 否 | 排序 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAT-A01 | 正常新增 | P0 | 权限 | 合法 categoryName | 成功 |
| CAT-A02 | categoryName 为空 | P1 | - | "" 或缺省 | 校验失败 |
| CAT-A03 | 无 add 权限 | P2 | - | 合法 body | 403 |
| CAT-A04 | 重名分类 | P3 | 同名已存在 | 相同 categoryName | 失败或允许（依库约束） |

#### 修改分类
- **URL**: `PUT /admin/category/update`
- **权限**: `blog:category:update`
- **请求参数**: `CategoryReq`（`id`、`categoryName` 必填校验同 add）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAT-U01 | 正常更新 | P0 | 分类存在 | id+新名称 | 成功 |
| CAT-U02 | categoryName 为空 | P1 | - | id 有，名称为空 | 校验失败 |
| CAT-U03 | id 不存在 | P3 | - | 无效 id | 业务失败 |
| CAT-U04 | 无 update 权限 | P2 | - | 合法 body | 403 |

#### 删除分类
- **URL**: `DELETE /admin/category/delete`
- **权限**: `blog:category:delete`
- **请求参数**: Body `List<Integer>` 分类 ID 列表

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAT-D01 | 批量删除 | P0 | 无关联或允许删 | [id] | 成功 |
| CAT-D02 | 空数组 | P1 | - | [] | 依实现 |
| CAT-D03 | 删除仍有关联文章 | P3 | 有关联 | 该分类 id | 失败或级联（依实现） |
| CAT-D04 | 无 delete 权限 | P2 | - | 合法 body | 403 |

#### 分类下拉选项
- **URL**: `GET /admin/category/option`
- **权限**: 后台登录（无 `@SaCheckPermission`）
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAT-O01 | 获取选项 | P0 | 已登录后台 | 无 | 返回选项列表 |
| CAT-O02 | 未登录 | P2 | - | 无 | 未登录 |

#### 前台分类列表
- **URL**: `GET /category/list`
- **权限**: 公开
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAT-P01 | 获取全部前台分类 | P0 | - | 无 | 列表 |
| CAT-P02 | 空库 | P3 | 无分类 | 无 | 空列表 |

#### 分类下文章
- **URL**: `GET /category/article`
- **权限**: 公开
- **请求参数**（Query，`ArticleConditionQuery`，带 `@Validated`）:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| categoryId | int | 视校验 | 分类 ID |
| current | int | 否 | 分页 |
| size | int | 否 | 每页条数 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAT-AR01 | 某分类下文章 | P0 | 分类有文 | categoryId+分页 | 分页列表 |
| CAT-AR02 | categoryId 不存在 | P3 | - | 无效 ID | 空或错误（依实现） |
| CAT-AR03 | 缺省分页 | P2 | - | 仅 categoryId | 默认分页逻辑 |

---

## 5. 标签管理 (TagController)

#### 后台标签分页列表
- **URL**: `GET /admin/tag/list`
- **权限**: `blog:tag:list`
- **请求参数**: `TagQuery`（keyword）+ `PageQuery`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TAG-L01 | 分页查询 | P0 | 权限 | 合法分页 | 分页数据 |
| TAG-L02 | keyword 过滤 | P0 | 有数据 | keyword | 匹配结果 |
| TAG-L03 | 无权限 | P2 | - | - | 403 |
| TAG-L04 | 未登录 | P2 | - | - | 未登录 |

#### 新增标签
- **URL**: `POST /admin/tag/add`
- **权限**: `blog:tag:add`
- **请求参数**: `TagReq.tagName` @NotBlank

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TAG-A01 | 正常新增 | P0 | 权限 | tagName | 成功 |
| TAG-A02 | tagName 为空 | P1 | - | 空 | 校验失败 |
| TAG-A03 | 重名标签 | P3 | - | 已存在名 | 依约束 |

#### 修改标签
- **URL**: `PUT /admin/tag/update`
- **权限**: `blog:tag:update`
- **请求参数**: `TagReq`（含 id）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TAG-U01 | 正常修改 | P0 | 标签存在 | id+tagName | 成功 |
| TAG-U02 | 不存在 id | P3 | - | 无效 id | 失败 |
| TAG-U03 | 无权限 | P2 | - | 合法 body | 403 |

#### 删除标签
- **URL**: `DELETE /admin/tag/delete`
- **权限**: `blog:tag:delete`
- **请求参数**: Body `List<Integer>`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TAG-D01 | 批量删除 | P0 | 标签存在 | [ids] | 成功 |
| TAG-D02 | 空列表 | P1 | - | [] | 依实现 |
| TAG-D03 | 关联文章引用 | P3 | 仍被使用 | 标签 id | 依业务 |

#### 标签下拉选项
- **URL**: `GET /admin/tag/option`
- **权限**: 后台登录
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TAG-O01 | 获取选项 | P0 | 已登录 | 无 | 列表 |
| TAG-O02 | 未登录 | P2 | - | 无 | 未登录 |

#### 前台标签列表
- **URL**: `GET /tag/list`
- **权限**: 公开

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TAG-P01 | 获取标签 | P0 | - | 无 | 列表 |
| TAG-P02 | 空库 | P3 | - | 无 | 空列表 |

#### 标签下文章
- **URL**: `GET /tag/article`
- **权限**: 公开
- **请求参数**: `ArticleConditionQuery`（`tagId` + 分页；**注意**：控制器未加 `@Validated`，校验行为与分类接口可能不同）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TAG-AR01 | 某标签下文章 | P0 | 标签有文 | tagId+分页 | 列表 |
| TAG-AR02 | tagId 无效 | P3 | - | 不存在 | 空或异常 |
| TAG-AR03 | 缺 tagId | P1 | - | 不传 | 依实现 |

---

## 6. 评论管理 (CommentController)

#### 后台评论分页列表
- **URL**: `GET /admin/comment/list`
- **权限**: 后台登录 + `news:comment:list`
- **请求参数**（Query）:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | string | 否 | 关键词 |
| typeId | int | 否 | 目标 ID（文章/说说等） |
| commentType | int | 否 | 1/2/3 |
| isCheck | int | 否 | 审核状态 |
| current、size | int | 否 | 分页 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CMT-L01 | 条件查询 | P0 | 权限+有数据 | 组合条件 | 分页列表 |
| CMT-L02 | 无 token | P2 | - | - | 未登录 |
| CMT-L03 | 无 list 权限 | P2 | - | - | 403 |
| CMT-L04 | 空结果 | P3 | keyword 无匹配 | keyword | 空分页 |

#### 添加评论
- **URL**: `POST /comment/add`
- **权限**: `@SaCheckLogin` + `news:comment:add`
- **请求参数**（`CommentReq`，分组校验）:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| typeId | int | 条件 | 文章/说说评论时非空；友链评论须为空（分组） |
| commentType | int | 是 | 1 文章 2 友链 3 说说 |
| parentId | int | 条件 | 顶层与回复规则互斥 |
| replyId / toUid | int | 条件 | 回复时成对非空 |
| commentContent | string | 是 | 内容 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CMT-A01 | 文章顶层评论 | P0 | 登录+权限 | typeId+commentType=1+parent 规则 | 成功 |
| CMT-A02 | 回复评论 | P0 | 登录+权限 | parentId+replyId+toUid | 成功 |
| CMT-A03 | 未登录 | P2 | - | 合法 body | 未登录 |
| CMT-A04 | 无 add 权限 | P2 | 登录 | 合法 body | 403 |
| CMT-A05 | commentType 非法值 | P1 | - | 非 1/2/3 | 校验失败 |
| CMT-A06 | typeId 与类型不匹配 | P3 | - | 友链评论却带 typeId | 分组校验失败 |

#### 删除评论
- **URL**: `DELETE /admin/comment/delete`
- **权限**: `news:comment:delete`
- **请求参数**: Body `List<Integer>` 评论 ID

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CMT-D01 | 批量删除 | P0 | 评论存在 | [id] | 成功 |
| CMT-D02 | 空列表 | P1 | - | [] | 依实现 |
| CMT-D03 | 无 delete 权限 | P2 | - | 合法 body | 403 |
| CMT-D04 | 含不存在 ID | P3 | - | 混合 | 依实现 |

#### 审核评论
- **URL**: `PUT /admin/comment/pass`
- **权限**: `news:comment:pass`
- **请求参数**（JSON `CheckReq`）:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| idList | array | 是 | 非空 |
| isCheck | int | 是 | 审核通过/驳回等 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CMT-P01 | 批量通过 | P0 | 待审评论 | idList+isCheck | 成功 |
| CMT-P02 | idList 为空 | P1 | - | [] | 校验失败 |
| CMT-P03 | isCheck 缺省 | P1 | - | 无 isCheck | 校验失败 |
| CMT-P04 | 无 pass 权限 | P2 | - | 合法 body | 403 |

#### 点赞评论
- **URL**: `POST /comment/{commentId}/like`
- **权限**: 前台登录 + `news:comment:like` + `@AccessLimit(seconds=60,maxCount=3)`
- **请求参数**: Path `commentId`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CMT-LK01 | 点赞成功 | P0 | 登录+权限 | 有效 commentId | 成功 |
| CMT-LK02 | 未登录 | P2 | - | - | 未登录 |
| CMT-LK03 | 60s 内超过 3 次 | P2 | 已点 3 次 | 再点 | 限流 |
| CMT-LK04 | 评论不存在 | P3 | - | 无效 ID | 业务失败 |

#### 最新评论
- **URL**: `GET /recent/comment`
- **权限**: 公开
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CMT-R01 | 获取最新评论 | P0 | 有评论数据 | 无 | 列表 |
| CMT-R02 | 无评论 | P3 | 空库 | 无 | 空列表 |

#### 评论列表（前台）
- **URL**: `GET /comment/list`
- **权限**: 公开
- **请求参数**: `CommentQuery` + 分页（typeId、commentType、keyword、isCheck 等，以实体为准）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CMT-F01 | 按类型分页 | P0 | 某文章有评论 | typeId+commentType+分页 | 分页列表 |
| CMT-F02 | 缺分页 | P2 | - | 仅必填筛选 | 默认分页 |
| CMT-F03 | 目标无评论 | P3 | - | 有效 typeId | 空分页 |

#### 回复列表
- **URL**: `GET /comment/{commentId}/reply`
- **权限**: 公开
- **请求参数**: Path `commentId`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CMT-RY01 | 有回复 | P0 | 评论有子回复 | 有效 commentId | 回复列表 |
| CMT-RY02 | 无回复 | P3 | - | 有效 commentId | 空列表 |
| CMT-RY03 | 评论不存在 | P3 | - | 无效 ID | 失败或空 |

---

## 7. 留言管理 (MessageController)

#### 前台留言列表
- **URL**: `GET /message/list`
- **权限**: 公开
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MSG-L01 | 获取已通过留言 | P0 | 有展示数据 | 无 | 列表 |
| MSG-L02 | 无留言 | P3 | - | 无 | 空列表 |

#### 后台留言列表
- **URL**: `GET /admin/message/list`
- **权限**: `news:message:list`
- **请求参数**: `MessageQuery`（keyword、isCheck）+ 分页

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MSG-B01 | 分页+筛选 | P0 | 权限 | keyword 等 | 分页 |
| MSG-B02 | 无权限 | P2 | - | - | 403 |

#### 添加留言
- **URL**: `POST /message/add`
- **权限**: 公开 + `@AccessLimit(60s,3)`
- **请求参数**（`MessageReq`）:

| 参数名 | 类型 | 必填 |
| --- | --- | --- |
| nickname | string | 是 |
| avatar | string | 是 |
| messageContent | string | 是 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MSG-A01 | 正常留言 | P0 | - | 全必填 | 成功 |
| MSG-A02 | 缺 nickname | P1 | - | - | 校验失败 |
| MSG-A03 | 60s 内超过 3 次 | P2 | - | 连续提交 | 限流 |

#### 删除留言
- **URL**: `DELETE /admin/message/delete`
- **权限**: `news:message:delete`
- **请求参数**: Body `List<Integer>`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MSG-D01 | 批量删除 | P0 | 留言存在 | [id] | 成功 |
| MSG-D02 | 无权限 | P2 | - | 合法 body | 403 |
| MSG-D03 | 空列表 | P1 | - | [] | 依实现 |

#### 审核留言
- **URL**: `PUT /admin/message/pass`
- **权限**: `news:message:pass`
- **请求参数**: `CheckReq`（`idList` @NotEmpty、`isCheck` @NotNull）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MSG-P01 | 批量审核 | P0 | 待审留言 | 合法 CheckReq | 成功 |
| MSG-P02 | idList 为空 | P1 | - | [] | 校验失败 |
| MSG-P03 | 无 pass 权限 | P2 | - | 合法 body | 403 |

---

## 8. 说说管理 (TalkController)

#### 后台说说列表
- **URL**: `GET /admin/talk/list`
- **权限**: `web:talk:list`
- **请求参数**: `TalkQuery.status` + `PageQuery`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-B01 | 按状态分页 | P0 | 权限 | status+分页 | 分页列表 |
| TALK-B02 | 无权限 | P2 | - | - | 403 |

#### 上传说说图片
- **URL**: `POST /admin/talk/upload`
- **权限**: `web:talk:upload`
- **请求参数**: `multipart`：`file`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-UP01 | 上传成功 | P0 | 权限 | 合法图片 | 返回 URL |
| TALK-UP02 | 无 file | P1 | - | 空 | 失败 |
| TALK-UP03 | 无 upload 权限 | P2 | - | 有 file | 403 |

#### 新增说说
- **URL**: `POST /admin/talk/add`
- **权限**: `web:talk:add`
- **请求参数**（`TalkReq`）:

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| talkContent | string | 是 | @NotBlank |
| isTop | int | 是 | @NotNull |
| status | int | 是 | @NotNull |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-A01 | 正常发布 | P0 | 权限 | 全字段合法 | 成功 |
| TALK-A02 | talkContent 空 | P1 | - | 缺内容 | 校验失败 |
| TALK-A03 | isTop 缺省 | P1 | - | 无 isTop | 校验失败 |

#### 修改说说
- **URL**: `PUT /admin/talk/update`
- **权限**: `web:talk:update`
- **请求参数**: `TalkReq`（含 id）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-U01 | 更新成功 | P0 | 说说存在 | 合法 body | 成功 |
| TALK-U02 | id 不存在 | P3 | - | 无效 id | 失败 |
| TALK-U03 | 无 update 权限 | P2 | - | 合法 body | 403 |

#### 删除说说
- **URL**: `DELETE /admin/talk/delete/{talkId}`
- **权限**: `web:talk:delete`
- **请求参数**: Path `talkId`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-D01 | 删除存在说说 | P0 | 权限 | 有效 talkId | 成功 |
| TALK-D02 | talkId 不存在 | P3 | - | 无效 | 失败 |

#### 编辑说说回显
- **URL**: `GET /admin/talk/edit/{talkId}`
- **权限**: `web:talk:edit`
- **请求参数**: Path `talkId`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-E01 | 回显 | P0 | 说说存在 | 有效 ID | 详情 |
| TALK-E02 | 无 edit 权限 | P2 | - | 有效 ID | 403 |

#### 前台说说列表
- **URL**: `GET /talk/list`
- **权限**: 公开
- **请求参数**: `PageQuery`（`@Validated`：`current`、`size` 等）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-F01 | 分页列表 | P0 | - | 合法分页 | 分页数据 |
| TALK-F02 | 分页参数非法 | P1 | - | 违反校验 | 400/校验失败 |

#### 说说详情
- **URL**: `GET /talk/{talkId}`
- **权限**: 公开
- **请求参数**: Path `talkId`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-G01 | 存在说说 | P0 | 已发布 | 有效 ID | 详情 |
| TALK-G02 | 不存在 | P3 | - | 无效 ID | 失败 |

#### 首页说说片段
- **URL**: `GET /home/talk`
- **权限**: 公开

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-H01 | 获取首页说说 | P0 | 有配置数据 | 无 | 字符串列表 |
| TALK-H02 | 无数据 | P3 | - | 无 | 空列表 |

#### 点赞说说
- **URL**: `POST /talk/{talkId}/like`
- **权限**: 登录 + `web:talk:like` + `@AccessLimit(60s,3)`
- **请求参数**: Path `talkId`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TALK-LK01 | 点赞 | P0 | 登录+权限 | 有效 talkId | 成功 |
| TALK-LK02 | 未登录 | P2 | - | - | 未登录 |
| TALK-LK03 | 触发限流 | P2 | 60s 内已 3 次 | 再点 | 限流 |

---

## 9. 友链管理 (FriendController)

#### 前台友链列表
- **URL**: `GET /friend/list`
- **权限**: 公开
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FRD-P01 | 获取友链 | P0 | 有数据 | 无 | 列表 |
| FRD-P02 | 空库 | P3 | - | 无 | 空列表 |

#### 后台友链列表
- **URL**: `GET /admin/friend/list`
- **权限**: `web:friend:list`
- **请求参数**: `FriendQuery`（keyword）+ 分页

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FRD-B01 | 分页查询 | P0 | 权限 | keyword 可选 | 分页 |
| FRD-B02 | 无权限 | P2 | - | - | 403 |

#### 新增友链
- **URL**: `POST /admin/friend/add`
- **权限**: `web:friend:add`
- **请求参数**（`FriendReq`，均 @NotBlank）:

| 参数名 | 类型 | 必填 |
| --- | --- | --- |
| color | string | 是 |
| name | string | 是 |
| avatar | string | 是 |
| url | string | 是 |
| introduction | string | 是 |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FRD-A01 | 完整新增 | P0 | 权限 | 全字段 | 成功 |
| FRD-A02 | introduction 为空 | P1 | - | 缺字段 | 校验失败 |
| FRD-A03 | 无 add 权限 | P2 | - | 合法 body | 403 |

#### 修改友链
- **URL**: `PUT /admin/friend/update`
- **权限**: `web:friend:update`
- **请求参数**: `FriendReq`（含 id）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FRD-U01 | 更新 | P0 | 记录存在 | 合法 body | 成功 |
| FRD-U02 | id 不存在 | P3 | - | 无效 id | 失败 |

#### 删除友链
- **URL**: `DELETE /admin/friend/delete`
- **权限**: `web:friend:delete`
- **请求参数**: Body `List<Integer>`

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FRD-D01 | 批量删除 | P0 | 记录存在 | [ids] | 成功 |
| FRD-D02 | 空列表 | P1 | - | [] | 依实现 |
| FRD-D03 | 无 delete 权限 | P2 | - | 合法 body | 403 |

---

## 10. 用户与个人信息 (UserController + UserInfoController)

### 10.1 后台用户

#### 当前后台用户信息
- **URL**: `GET /admin/user/getUserInfo`
- **权限**: 后台登录（无 `@SaCheckPermission`）
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UAD-I01 | 已登录获取信息 | P0 | 后台 token 有效 | 无 | 返回用户资料 |
| UAD-I02 | 未登录 | P2 | - | 无 | 未登录 |

#### 用户菜单路由
- **URL**: `GET /admin/user/getUserMenu`
- **权限**: 后台登录
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UAD-M01 | 获取动态路由 | P0 | 用户有菜单权限 | 无 | 返回 Router 树 |
| UAD-M02 | 未登录 | P2 | - | 无 | 未登录 |

#### 后台用户分页列表
- **URL**: `GET /admin/user/list`
- **权限**: `system:user:list`
- **请求参数**: `UserQuery`：`keyword`、`loginType` + 分页

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UAD-L01 | 分页查询 | P0 | 有权限 | 合法分页 | 用户列表 |
| UAD-L02 | keyword 筛选 | P0 | 有匹配 | keyword | 过滤结果 |
| UAD-L03 | loginType 筛选 | P2 | - | loginType | 过滤结果 |
| UAD-L04 | 无 list 权限 | P2 | - | - | 403 |

#### 用户角色选项
- **URL**: `GET /admin/user/role`
- **权限**: `system:user:list`
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UAD-R01 | 获取角色下拉 | P0 | 权限 | 无 | 角色列表 |
| UAD-R02 | 无权限 | P2 | - | 无 | 403 |

#### 修改用户
- **URL**: `PUT /admin/user/update`
- **权限**: `system:user:update`
- **请求参数**（`UserRoleReq`）:

| 参数名 | 类型 | 必填 |
| --- | --- | --- |
| id | int | 是 @NotNull |
| nickname | string | 是 @NotBlank |
| roleIdList | array | 是 @NotEmpty |

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UAD-U01 | 更新昵称与角色 | P0 | 用户存在 | 合法 body | 成功 |
| UAD-U02 | roleIdList 为空 | P1 | - | [] | 校验失败 |
| UAD-U03 | nickname 为空 | P1 | - | 空昵称 | 校验失败 |
| UAD-U04 | 用户不存在 | P3 | - | 无效 id | 业务失败 |

#### 禁用/启用用户
- **URL**: `PUT /admin/user/changeStatus`
- **权限**: `system:user:status`
- **请求参数**（`DisableReq`）: `id` @NotNull、`isDisable` @NotNull

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UAD-S01 | 禁用用户 | P0 | 目标非唯一超管（依业务） | id+isDisable=1 | 成功 |
| UAD-S02 | id 缺省 | P1 | - | 无 id | 校验失败 |
| UAD-S03 | 禁用自身或超管 | P3 | 策略限制 | 非法操作 | 业务拒绝 |

#### 在线用户列表
- **URL**: `GET /admin/online/list`
- **权限**: `monitor:online:list`
- **请求参数**: `OnlineUserQuery`（keyword）+ 分页

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ONL-L01 | 查询在线 | P0 | 权限 | 分页 | 在线列表 |
| ONL-L02 | 无权限 | P2 | - | - | 403 |

#### 强制下线
- **URL**: `GET /admin/online/kick/{token}`
- **权限**: `monitor:online:kick`
- **请求参数**: Path `token`（目标会话 token）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ONL-K01 | 踢出指定会话 | P0 | 权限+有效 token | 合法 token | 成功，对方失效 |
| ONL-K02 | 无效 token | P3 | - | 随机串 | 依实现 |
| ONL-K03 | 无 kick 权限 | P2 | - | 合法 token | 403 |

#### 修改管理员密码
- **URL**: `PUT /admin/password`
- **权限**: `@SaCheckRole("1")`（超级管理员）
- **请求参数**: `PasswordReq`：`oldPassword` @NotBlank；`newPassword` @NotBlank @Size(min=6)

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| USR-P01 | 正确修改 | P0 | 角色 1 | 旧密码正确 | 成功 |
| USR-P02 | 新密码不足 6 位 | P1 | 角色 1 | newPassword 短 | 校验失败 |
| USR-P03 | 非角色 1 | P2 | 普通管理员 | 合法 body | 403 |

### 10.2 前台用户中心

#### 获取个人信息
- **URL**: `GET /user/getUserInfo`
- **权限**: `@SaCheckLogin`
- **请求参数**: 无

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UCL-I01 | 已登录获取 | P0 | 前台 token | 无 | 用户信息 |
| UCL-I02 | 未登录 | P2 | - | 无 | 未登录 |

#### 修改邮箱
- **URL**: `PUT /user/email`
- **权限**: `user:email:update`（建议配合全局 `/user/**` 登录校验验证）
- **请求参数**（`EmailReq`）: `email` @NotBlank @Email；`code` @NotBlank

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UCL-E01 | 正确修改邮箱 | P0 | 验证码有效 | 合法 email+code | 成功 |
| UCL-E02 | email 格式错误 | P1 | - | 非邮箱 | 校验失败 |
| UCL-E03 | code 为空 | P1 | - | 缺 code | 校验失败 |
| UCL-E04 | 无 email 更新权限 | P2 | 登录但无权限 | 合法 body | 403 |

#### 修改头像
- **URL**: `POST /user/avatar`
- **权限**: `user:avatar:update`
- **请求参数**: `multipart`：`file`（参数名 `file`）

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UCL-A01 | 上传头像 | P0 | 权限 | 合法图片 | 返回 URL |
| UCL-A02 | 无 file | P1 | - | 空 | 失败 |
| UCL-A03 | 无 avatar 权限 | P2 | - | 有 file | 403 |

#### 修改昵称等信息
- **URL**: `PUT /user/info`
- **权限**: `user:info:update`
- **请求参数**（`UserInfoReq`）: `nickname` @NotBlank

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| UCL-N01 | 修改昵称 | P0 | 权限 | nickname | 成功 |
| UCL-N02 | nickname 为空 | P1 | - | "" | 校验失败 |

#### 重置/修改密码（公开接口例外）
- **URL**: `PUT /user/password`
- **权限**: 公开（无 Login 注解）
- **请求参数**: `UserReq`：`username` @Email @NotBlank；`password` @Size(min=6)；`code` @NotBlank

- **测试用例**:

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| USR-UPW01 | 正确重置 | P0 | 邮箱验证码有效 | 合法字段 | 成功 |
| USR-UPW02 | 无验证码 | P1 | - | 缺 code | 校验失败 |
| USR-UPW03 | 未带 token 仍可访问 | P2 | - | 无 Authorization | 允许访问（与 /user/** 全局规则核对） |

---

## 11. 角色与菜单 (RoleController + MenuController)

### 11.1 角色

#### 角色分页列表
- **URL**: `GET /admin/role/list`
- **权限**: `system:role:list`
- **请求参数**: `RoleQuery`（keyword、isDisable）+ 分页

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ROLE-L01 | 分页查询 | P0 | 权限 | 合法分页 | 列表 |
| ROLE-L02 | keyword 筛选 | P0 | 有数据 | keyword | 过滤 |
| ROLE-L03 | 无权限 | P2 | - | - | 403 |

#### 新增角色
- **URL**: `POST /admin/role/add`
- **权限**: `system:role:add`
- **请求参数**: `RoleReq`（`roleName` @NotBlank，`isDisable` @NotNull；可选 `roleDesc`、`menuIdList`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ROLE-A01 | 新增成功 | P0 | 权限 | 合法 body | 成功 |
| ROLE-A02 | roleName 空 | P1 | - | 缺名称 | 校验失败 |
| ROLE-A03 | isDisable 空 | P1 | - | 缺状态 | 校验失败 |
| ROLE-A04 | 重名角色 | P3 | - | 已存在 roleName | 业务失败 |

#### 修改角色
- **URL**: `PUT /admin/role/update`
- **权限**: `system:role:update`
- **请求参数**: `RoleReq`（含 `id`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ROLE-U01 | 更新成功 | P0 | 角色存在 | 合法 body | 成功 |
| ROLE-U02 | id 不存在 | P3 | - | 无效 id | 失败 |

#### 删除角色
- **URL**: `DELETE /admin/role/delete`
- **权限**: `system:role:delete`
- **请求参数**: Body `List<String>` 角色 id

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ROLE-D01 | 批量删除 | P0 | 无用户引用 | [id] | 成功 |
| ROLE-D02 | 删除仍绑定用户 | P3 | - | 在用角色 | 业务拒绝 |
| ROLE-D03 | 空列表 | P1 | - | [] | 依实现 |

#### 修改角色状态
- **URL**: `PUT /admin/role/changeStatus`
- **权限**: `system:role:update` **或** `system:role:status`（SaMode.OR）
- **请求参数**: `RoleStatusReq`（`id`、`isDisable` @NotNull）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ROLE-S01 | 禁用/启用 | P0 | 具 OR 权限之一 | 合法 body | 成功 |
| ROLE-S02 | 两权限皆无 | P2 | - | 合法 body | 403 |
| ROLE-S03 | id 为空 | P1 | - | 缺 id | 校验失败 |

#### 查看角色已分配菜单
- **URL**: `GET /admin/role/menu/{roleId}`
- **权限**: `system:role:list`
- **请求参数**: Path `roleId`（String）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ROLE-M01 | 获取菜单 id 列表 | P0 | 角色存在 | 有效 roleId | 菜单 id 集合 |
| ROLE-M02 | 角色不存在 | P3 | - | 无效 | 失败或空 |

### 11.2 菜单

#### 菜单列表
- **URL**: `GET /admin/menu/list`
- **权限**: `system:menu:list`
- **请求参数**: `MenuQuery`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MENU-L01 | 条件查询 | P0 | 权限 | query | 树/列表 |
| MENU-L02 | 无权限 | P2 | - | - | 403 |

#### 新增菜单
- **URL**: `POST /admin/menu/add`
- **权限**: `system:menu:add`
- **请求参数**: `MenuReq`（`menuName`、`menuType` @NotBlank；`orderNum` @NotNull 等）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MENU-A01 | 新增目录/菜单 | P0 | 权限 | 合法 body | 成功 |
| MENU-A02 | menuName 空 | P1 | - | 缺名称 | 校验失败 |
| MENU-A03 | orderNum 空 | P1 | - | 缺排序 | 校验失败 |

#### 删除菜单
- **URL**: `DELETE /admin/menu/delete/{menuId}`
- **权限**: `system:menu:delete`
- **请求参数**: Path `menuId`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MENU-D01 | 删除叶子菜单 | P0 | 无子节点 | 有效 id | 成功 |
| MENU-D02 | 存在子菜单 | P3 | - | 父节点 id | 业务拒绝 |

#### 修改菜单
- **URL**: `PUT /admin/menu/update`
- **权限**: `system:menu:update`
- **请求参数**: `MenuReq`（含 id）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MENU-U01 | 更新成功 | P0 | 菜单存在 | 合法 body | 成功 |
| MENU-U02 | 不存在 id | P3 | - | 无效 | 失败 |

#### 菜单下拉树（角色分配用）
- **URL**: `GET /admin/menu/getMenuTree`
- **权限**: `system:role:list`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MENU-T01 | 获取树 | P0 | 权限 | 无 | 树形数据 |
| MENU-T02 | 无 role:list 权限 | P2 | - | 无 | 403 |

#### 菜单选项树
- **URL**: `GET /admin/menu/getMenuOptions`
- **权限**: `system:menu:list`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MENU-O01 | 获取选项 | P0 | 权限 | 无 | 下拉树 |
| MENU-O02 | 无权限 | P2 | - | 无 | 403 |

#### 编辑菜单回显
- **URL**: `GET /admin/menu/edit/{menuId}`
- **权限**: `system:menu:edit`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| MENU-E01 | 回显 | P0 | 菜单存在 | 有效 menuId | MenuReq |
| MENU-E02 | 无效 menuId | P3 | - | 不存在 | 失败 |

---

## 12. 相册与照片 (AlbumController + PhotoController)

### 12.1 相册

#### 后台相册列表
- **URL**: `GET /admin/album/list`
- **权限**: `web:album:list`
- **请求参数**: `AlbumQuery` + 分页

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ALB-L01 | 分页 | P0 | 权限 | 分页 | 列表 |
| ALB-L02 | 无权限 | P2 | - | - | 403 |

#### 上传相册封面
- **URL**: `POST /admin/album/upload`
- **权限**: `web:album:upload`
- **请求参数**: `file`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ALB-UP01 | 上传成功 | P0 | 权限 | 图片 file | URL |
| ALB-UP02 | 无 file | P1 | - | 空 | 失败 |

#### 新增相册
- **URL**: `POST /admin/album/add`
- **权限**: `web:album:add`
- **请求参数**: `AlbumReq`（以校验注解为准）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ALB-A01 | 新增 | P0 | 权限 | 合法 body | 成功 |
| ALB-A02 | 必填缺失 | P1 | - | 缺字段 | 校验失败 |

#### 删除相册
- **URL**: `DELETE /admin/album/delete/{albumId}`
- **权限**: `web:album:delete`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ALB-D01 | 删除空相册 | P0 | 无照片或允许 | 有效 id | 成功 |
| ALB-D02 | 相册仍有照片 | P3 | - | 有内容相册 | 依业务 |

#### 修改相册
- **URL**: `PUT /admin/album/update`
- **权限**: `web:album:update`
- **请求参数**: `AlbumReq`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ALB-U01 | 更新 | P0 | 存在 | 合法 body | 成功 |
| ALB-U02 | 无效 id | P3 | - | 不存在 | 失败 |

#### 编辑相册回显
- **URL**: `GET /admin/album/edit/{albumId}`
- **权限**: `web:album:edit`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ALB-E01 | 回显 | P0 | 存在 | 有效 id | AlbumReq |

#### 前台相册列表
- **URL**: `GET /album/list`
- **权限**: 公开

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ALB-P01 | 获取列表 | P0 | - | 无 | 列表 |

### 12.2 照片

#### 后台照片列表
- **URL**: `GET /admin/photo/list`
- **权限**: `web:photo:list`
- **请求参数**: `PhotoQuery` + 分页

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PHO-L01 | 分页 | P0 | 权限 | query | 列表 |
| PHO-L02 | 无权限 | P2 | - | - | 403 |

#### 后台相册信息（照片模块）
- **URL**: `GET /admin/photo/album/{albumId}/info`
- **权限**: `web:photo:list`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PHO-I01 | 获取相册信息 | P0 | 相册存在 | 有效 albumId | AlbumBackResp |
| PHO-I02 | albumId 无效 | P3 | - | 不存在 | 失败 |

#### 上传照片文件
- **URL**: `POST /admin/photo/upload`
- **权限**: `web:photo:upload`
- **请求参数**: `file`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PHO-UP01 | 上传 | P0 | 权限 | file | URL |
| PHO-UP02 | 无 file | P1 | - | 空 | 失败 |

#### 新增照片记录
- **URL**: `POST /admin/photo/add`
- **权限**: `web:photo:add`
- **请求参数**: `PhotoReq`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PHO-A01 | 新增 | P0 | 权限 | 合法 body | 成功 |
| PHO-A02 | 校验失败 | P1 | - | 缺字段 | 失败 |

#### 修改照片信息
- **URL**: `PUT /admin/photo/update`
- **权限**: `web:photo:update`
- **请求参数**: `PhotoInfoReq`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PHO-U01 | 更新描述等 | P0 | 照片存在 | 合法 body | 成功 |

#### 删除照片
- **URL**: `DELETE /admin/photo/delete`
- **权限**: `web:photo:delete`
- **请求参数**: Body `List<Integer>`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PHO-D01 | 批量删除 | P0 | 照片存在 | [ids] | 成功 |
| PHO-D02 | 空列表 | P1 | - | [] | 依实现 |

#### 移动照片
- **URL**: `PUT /admin/photo/move`
- **权限**: `web:photo:move`
- **请求参数**: `PhotoReq`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PHO-M01 | 移动到其他相册 | P0 | 源/目标相册存在 | 合法 body | 成功 |
| PHO-M02 | 目标相册不存在 | P3 | - | 无效 | 失败 |

#### 前台照片列表
- **URL**: `GET /photo/list`
- **权限**: 公开
- **请求参数**: Query **`albumId`**（Integer，必填）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PHO-P01 | 合法 albumId | P0 | 相册有照片 | albumId | Map 结构数据 |
| PHO-P02 | 缺 albumId | P1 | - | 不传 | 400/错误 |
| PHO-P03 | albumId 不存在 | P3 | - | 无效 | 空或错误 |

---

## 13. 轮播图 (CarouselController)

#### 前台轮播列表
- **URL**: `GET /carousel/list`
- **权限**: 公开

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAR-P01 | 获取展示中轮播 | P0 | 有启用数据 | 无 | 列表 |
| CAR-P02 | 无数据 | P3 | - | 无 | 空列表 |

#### 后台轮播分页列表
- **URL**: `GET /admin/carousel/list`
- **权限**: `web:carousel:list`
- **请求参数**: `CarouselQuery` + 分页

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAR-B01 | 分页 | P0 | 权限 | query | 分页 |
| CAR-B02 | 无权限 | P2 | - | - | 403 |

#### 上传轮播图
- **URL**: `POST /admin/carousel/upload`
- **权限**: `web:carousel:upload`
- **请求参数**: `file`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAR-UP01 | 上传 | P0 | 权限 | file | URL |
| CAR-UP02 | 无 file | P1 | - | 空 | 失败 |

#### 新增轮播
- **URL**: `POST /admin/carousel/add`
- **权限**: `web:carousel:add`
- **请求参数**: `CarouselReqVo`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAR-A01 | 新增 | P0 | 权限 | 合法 body | 成功 |
| CAR-A02 | 校验失败 | P1 | - | 缺字段 | 失败 |

#### 修改轮播（POST）
- **URL**: `POST /admin/carousel/update`
- **权限**: `web:carousel:update`
- **备注**: 方法为 **POST**，非 PUT

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAR-U01 | 更新 | P0 | 记录存在 | 合法 body | 成功 |
| CAR-U02 | 误用 PUT | P1 | - | 同 body | 404 或方法不允许 |

#### 删除轮播
- **URL**: `DELETE /admin/carousel/delete`
- **权限**: `web:carousel:delete`
- **请求参数**: `List<Integer>`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAR-D01 | 批量删除 | P0 | 记录存在 | [ids] | 成功 |

#### 修改轮播状态
- **URL**: `PUT /admin/carousel/status`
- **权限**: `web:carousel:status`
- **请求参数**: `CarouselStatusReq`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| CAR-S01 | 上/下架 | P0 | 权限 | 合法 body | 成功 |
| CAR-S02 | 参数缺失 | P1 | - | 缺字段 | 校验失败 |

---

## 14. 文件管理 (BlogFileController)

#### 文件分页列表
- **URL**: `GET /admin/file/list`
- **权限**: `system:file:list`
- **请求参数**: `FileQuery` + 分页

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FIL-L01 | 分页浏览 | P0 | 权限 | query | 文件列表 |
| FIL-L02 | 无权限 | P2 | - | - | 403 |

#### 上传文件
- **URL**: `POST /admin/file/upload`
- **权限**: `system:file:upload`
- **请求参数**: `multipart`：`file`；`path`（与 `file` 一并提交，见接口签名）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FIL-U01 | 上传成功 | P0 | 权限 | file+path | 成功 |
| FIL-U02 | 缺 file | P1 | - | 仅 path | 失败 |
| FIL-U03 | 缺 path | P1 | - | 仅 file | 失败 |
| FIL-U04 | 无 upload 权限 | P2 | - | 合法参数 | 403 |

#### 创建目录
- **URL**: `POST /admin/file/createFolder`
- **权限**: `system:file:createFolder`
- **请求参数**: `FolderReq`（`@Validated`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FIL-C01 | 创建成功 | P0 | 权限 | 合法 FolderReq | 成功 |
| FIL-C02 | 校验失败 | P1 | - | 缺必填 | 校验失败 |

#### 删除文件/目录记录
- **URL**: `DELETE /admin/file/delete`
- **权限**: `system:file:delete`
- **请求参数**: Body `List<Integer>` fileId

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FIL-D01 | 批量删除 | P0 | 记录存在 | [id] | 成功 |
| FIL-D02 | 含不存在 id | P3 | - | 混合 | 依实现 |

#### 下载文件
- **URL**: `GET /file/download/{fileId}`
- **权限**: **公开**（无 `@SaCheckPermission`，注意安全风险）
- **请求参数**: Path `fileId`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| FIL-G01 | 下载存在文件 | P0 | file 存在 | 有效 id | 文件流/触发下载 |
| FIL-G02 | fileId 不存在 | P3 | - | 无效 | 失败 |
| FIL-G03 | 大文件 | P2 | - | 大资源 id | 流式正常、超时策略 |

---

## 15. 日志管理 (LogController)

> 下列接口均为 **GET 列表 + DELETE 批量删除**，`LogQuery`/`TaskQuery` 字段以实体为准；删除均为 Body `List<Integer>`（清空接口除外）。

#### 操作日志列表
- **URL**: `GET /admin/operation/list`
- **权限**: `log:operation:list`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| LOG-OP-L1 | 分页查询 | P0 | 权限 | LogQuery | 分页 |
| LOG-OP-L2 | 无权限 | P2 | - | - | 403 |

#### 删除操作日志
- **URL**: `DELETE /admin/operation/delete`
- **权限**: `log:operation:delete`
- **请求参数**: `List<Integer>`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| LOG-OP-D1 | 批量删除 | P0 | 权限 | [id] | 成功 |
| LOG-OP-D2 | 空列表 | P1 | - | [] | 依实现 |

#### 异常日志列表
- **URL**: `GET /admin/exception/list`
- **权限**: `log:exception:list`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| LOG-EX-L1 | 分页查询 | P0 | 权限 | LogQuery | 分页 |

#### 删除异常日志
- **URL**: `DELETE /admin/exception/delete`
- **权限**: `log:exception:delete`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| LOG-EX-D1 | 批量删除 | P0 | 权限 | [id] | 成功 |

#### 访问日志列表
- **URL**: `GET /admin/visit/list`
- **权限**: `log:visit:list`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| LOG-VS-L1 | 分页查询 | P0 | 权限 | LogQuery | 分页 |

#### 删除访问日志
- **URL**: `DELETE /admin/visit/delete`
- **权限**: `log:visit:delete`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| LOG-VS-D1 | 批量删除 | P0 | 权限 | [id] | 成功 |

#### 定时任务日志列表
- **URL**: `GET /admin/taskLog/list`
- **权限**: `log:task:list`
- **请求参数**: `TaskQuery` + 分页

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| LOG-TK-L1 | 分页查询 | P0 | 权限 | TaskQuery | 分页 |

#### 删除定时任务日志
- **URL**: `DELETE /admin/taskLog/delete`
- **权限**: `log:task:delete`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| LOG-TK-D1 | 批量删除 | P0 | 权限 | [id] | 成功 |

#### 清空定时任务日志
- **URL**: `DELETE /admin/taskLog/clear`
- **权限**: `log:task:clear`
- **请求参数**: 无 Body

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| LOG-TK-C1 | 清空全部 | P0 | 权限 | 无 | 成功 |
| LOG-TK-C2 | 无 clear 权限 | P2 | - | 无 | 403 |

---

## 16. 定时任务 (TaskController)

#### 定时任务列表
- **URL**: `GET /admin/task/list`
- **权限**: `monitor:task:list`
- **请求参数**: `TaskQuery` + 分页

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TSK-L01 | 分页 | P0 | 权限 | query | 列表 |
| TSK-L02 | 无权限 | P2 | - | - | 403 |

#### 新增定时任务
- **URL**: `POST /admin/task/add`
- **权限**: `monitor:task:add`
- **请求参数**: `TaskReq`（`@Validated`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TSK-A01 | 新增成功 | P0 | 权限 | 合法 Cron 等 | 成功 |
| TSK-A02 | Cron 非法 | P1/P3 | - | 错误表达式 | 校验或业务失败 |

#### 修改定时任务
- **URL**: `PUT /admin/task/update`
- **权限**: `monitor:task:update`
- **请求参数**: `TaskReq`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TSK-U01 | 更新 | P0 | 任务存在 | 合法 body | 成功 |
| TSK-U02 | 任务不存在 | P3 | - | 无效 id | 失败 |

#### 删除定时任务
- **URL**: `DELETE /admin/task/delete`
- **权限**: `monitor:task:delete`
- **请求参数**: `List<Integer>`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TSK-D01 | 批量删除 | P0 | 任务存在 | [ids] | 成功 |

#### 修改任务状态
- **URL**: `PUT /admin/task/changeStatus`
- **权限**: `monitor:task:update` **或** `monitor:task:status`（OR）
- **请求参数**: `StatusReq`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TSK-S01 | 启停任务 | P0 | OR 权限之一 | 合法 body | 成功 |
| TSK-S02 | 两权限皆无 | P2 | - | 合法 body | 403 |

#### 立即执行任务
- **URL**: `PUT /admin/task/run`
- **权限**: `monitor:task:run`
- **请求参数**: `TaskRunReq`（Body，未加 `@Validated`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TSK-R01 | 手动触发 | P0 | 任务存在 | 合法 TaskRunReq | 执行成功 |
| TSK-R02 | 运行中重复 run | P3 | - | 连续触发 | 排队/拒绝（依实现） |
| TSK-R03 | 无 run 权限 | P2 | - | 合法 body | 403 |

---

## 17. 博客信息与站点配置 (BlogInfoController + SiteConfigController)

### 17.1 博客信息

#### 站点博客信息（前台）
- **URL**: `GET /`
- **权限**: 公开

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| BLOG-H01 | 获取统计信息 | P0 | - | 无 | BlogInfoResp |
| BLOG-H02 | 服务降级/空数据 | P3 | - | 无 | 默认值（依实现） |

#### 访客上报
- **URL**: `POST /report`
- **权限**: 公开

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| BLOG-R01 | 上报成功 | P0 | - | 无 body | 成功 |
| BLOG-R02 | 高频上报 | P2 | - | 连续 POST | 限流（若配置） |

#### 后台仪表盘信息
- **URL**: `GET /admin`
- **权限**: 后台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| BLOG-B01 | 获取后台统计 | P0 | 后台 token | 无 | BlogBackInfoResp |
| BLOG-B02 | 未登录 | P2 | - | 无 | 未登录 |

#### 关于我
- **URL**: `GET /about`
- **权限**: 公开

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| BLOG-A01 | 获取关于内容 | P0 | - | 无 | 字符串/HTML |

### 17.2 站点配置

#### 获取网站配置
- **URL**: `GET /admin/site/list`
- **权限**: `web:site:list`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| SITE-L01 | 获取配置 | P0 | 权限 | 无 | SiteConfig |
| SITE-L02 | 无权限 | P2 | - | 无 | 403 |

#### 更新网站配置
- **URL**: `PUT /admin/site/update`
- **权限**: `web:site:update`
- **请求参数**: Body `SiteConfig`（未加 `@Validated`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| SITE-U01 | 全量更新 | P0 | 权限 | 合法 JSON | 成功 |
| SITE-U02 | 非法字段类型 | P1 | - | 类型错误 | 400/反序列化失败 |

#### 上传网站配置图片
- **URL**: `POST /admin/site/upload`
- **权限**: `web:site:upload`
- **请求参数**: `file`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| SITE-UP01 | 上传 | P0 | 权限 | file | 返回路径 |

---

## 18. AI 功能 (AiController + AiPromptController)

#### AI 流式对话（前台）
- **URL**: `POST /ai/chat`
- **权限**: 公开
- **请求参数**: Body `List<Map<String,String>>` messages
- **响应**: `text/event-stream`（SSE）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AI-CH01 | 正常对话 | P0 | AI 配置可用 | 多轮 messages | SSE 持续输出 |
| AI-CH02 | messages 为空 | P1 | - | [] | 失败或空流 |
| AI-CH03 | 中断连接 | P3 | - | 客户端 close | 服务端结束 emitter |

#### AI 快速阅读
- **URL**: `POST /ai/quick-read`
- **权限**: 公开
- **请求参数**: JSON `{"content":"..."}`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AI-QR01 | 有正文 | P0 | - | content 非空 | 概要文本 |
| AI-QR02 | content 空 | P1 | - | 空串 | Result.fail |

#### 后台 AI 生成摘要
- **URL**: `POST /admin/ai/summary`
- **权限**: 后台登录
- **请求参数**: `{"content":"..."}`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AI-SM01 | 合法正文 | P0 | 已登录后台 | content | 摘要字符串 |
| AI-SM02 | content 空 | P1 | - | 缺或空 | Result.fail |
| AI-SM03 | 未登录后台 | P2 | - | 合法 body | 未登录 |

#### 后台 AI 生成标题
- **URL**: `POST /admin/ai/title`
- **权限**: 后台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AI-TT01 | 生成标题 | P0 | 后台登录 | content | 标题文本 |
| AI-TT02 | content 空 | P1 | - | 空 | Result.fail |

#### 后台 AI 自动分类
- **URL**: `POST /admin/ai/category`
- **权限**: 后台登录
- **请求参数**: `content` 必填；`categories` 可选

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AI-CG01 | 带候选分类 | P0 | - | content+categories | 返回分类名 |
| AI-CG02 | content 空 | P1 | - | 无 content | Result.fail |

#### 后台 AI 自动标签
- **URL**: `POST /admin/ai/tags`
- **权限**: 后台登录
- **请求参数**: `content`；`tags` 可选

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AI-TG01 | 返回标签列表 | P0 | - | content | 字符串列表 |
| AI-TG02 | content 空 | P1 | - | 空 | Result.fail |

#### 后台 AI 一键优化（SSE）
- **URL**: `POST /admin/ai/optimize`
- **权限**: 后台登录
- **请求参数**: `{"content":"..."}`；**长度 ≤8000 字符**

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AI-OP01 | 合法长度 | P0 | 后台登录 | ≤8000 | SSE 输出 |
| AI-OP02 | 超过 8000 字 | P2 | - | 超长 | ServiceException |
| AI-OP03 | content 空 | P1 | - | 空 | ServiceException |

### 18.2 AiPromptController

#### 提示词列表
- **URL**: `GET /user/ai/prompt/list`
- **权限**: 前台登录（`/user/**`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AIP-L01 | 获取列表 | P0 | 已登录 | 无 | 提示词列表 |
| AIP-L02 | 未登录 | P2 | - | 无 | 未登录 |

#### 保存提示词
- **URL**: `POST /user/ai/prompt`
- **权限**: 前台登录
- **请求参数**: Body `AiPrompt`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AIP-S01 | 保存/更新 | P0 | 已登录 | 合法实体 | 成功 |
| AIP-S02 | 关键字段缺失 | P1 | - | 不完整 | 依实现 |

#### 重置提示词
- **URL**: `DELETE /user/ai/prompt/{promptKey}`
- **权限**: 前台登录
- **请求参数**: Path `promptKey`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AIP-R01 | 重置为默认 | P0 | 已登录 | 有效 key | 成功 |
| AIP-R02 | 未知 key | P3 | - | 随机 key | 失败 |

#### AI 优化提示词文案
- **URL**: `POST /user/ai/prompt/optimize`
- **权限**: 前台登录
- **请求参数**: `{"content":"rawPrompt"}`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| AIP-O01 | 优化成功 | P0 | 已登录 | content | 优化后字符串 |
| AIP-O02 | content 缺省 | P1 | - | 无 content | null 或异常（依实现） |

---

## 19. 个人功能：待办 / 日记 / 习惯 / 任务池 / 时间块 / 思考

> 以下接口均在 `/user/**`，**需前台登录**（与 Sa-Token 路由拦截一致；`PUT /user/password` 除外）。

### 19.1 待办 TodoController

#### 待办分页列表
- **URL**: `GET /user/todo/list`
- **权限**: 前台登录
- **请求参数**: `TodoQuery` + 分页

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-L01 | 分页查询 | P0 | 已登录 | query | PageResult |
| TODO-L02 | 未登录 | P2 | - | - | 未登录 |

#### 新增待办
- **URL**: `POST /user/todo`
- **权限**: 前台登录
- **请求参数**: `TodoReq`（`title` @NotBlank）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-A01 | 新增 | P0 | 已登录 | 合法 body | 成功 |
| TODO-A02 | title 空 | P1 | - | 缺 title | 校验失败 |

#### 修改待办
- **URL**: `PUT /user/todo`
- **权限**: 前台登录
- **请求参数**: `TodoReq`（含 id）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-U01 | 更新 | P0 | 记录存在 | 合法 body | 成功 |
| TODO-U02 | id 不存在 | P3 | - | 无效 id | 失败 |

#### 删除待办
- **URL**: `DELETE /user/todo/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-D01 | 删除 | P0 | 存在 | Path id | 成功 |
| TODO-D02 | 不存在 id | P3 | - | 无效 | 失败 |

#### 切换完成状态
- **URL**: `PUT /user/todo/status/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-ST01 | 切换 | P0 | 存在 | id | 成功 |

#### 日历视图
- **URL**: `GET /user/todo/calendar`
- **权限**: 前台登录
- **请求参数**: `startDate`、`endDate`（必填）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-CA01 | 区间内待办 | P0 | 已登录 | 合法日期范围 | 列表 |
| TODO-CA02 | 缺 endDate | P1 | - | 仅 start | 400 |

#### 待办分类列表
- **URL**: `GET /user/todo/categories`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-CG01 | 获取分类 | P0 | 已登录 | 无 | 字符串列表 |

#### AI 总结
- **URL**: `POST /user/todo/ai/summary`
- **权限**: 前台登录
- **请求参数**: Body 可选 `type`（默认 `daily`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-AS01 | 生成总结 | P0 | AI 可用 | type | 文本 |
| TODO-AS02 | AI 异常 | P3 | - | - | 错误日志/500 |

#### AI 改进建议
- **URL**: `POST /user/todo/ai/suggest`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-SG01 | 获取建议 | P0 | AI 可用 | 无 body | 文本 |

#### 待办 AI 对话（SSE）
- **URL**: `POST /user/todo/ai/chat`
- **权限**: 前台登录
- **请求参数**: `messages`（List）；`range`（默认 `7d`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-AC01 | 流式回复 | P0 | 已登录 | messages | SSE |
| TODO-AC02 | messages 缺省 | P1 | - | 无 messages | NPE/失败 |

#### 获取 AI 记录
- **URL**: `GET /user/todo/ai/record/{type}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-GR01 | 按 type 查询 | P0 | 有记录 | type | AiRecord |

#### 保存 AI 记录
- **URL**: `POST /user/todo/ai/record`
- **权限**: 前台登录
- **请求参数**: `recordType`、`content`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-SR01 | 保存 | P0 | 已登录 | 两字段 | 成功 |
| TODO-SR02 | recordType 空 | P1 | - | 缺省 | 依实现 |

#### 习惯洞察
- **URL**: `POST /user/todo/ai/habit-insight`
- **权限**: 前台登录
- **请求参数**: `habitIds`（List）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TODO-HI01 | 多习惯分析 | P0 | AI+习惯数据 | habitIds | 文本 |
| TODO-HI02 | habitIds 空 | P2 | - | [] | 依实现 |

### 19.2 日记 DiaryController

#### 获取某日日记
- **URL**: `GET /user/diary/{date}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| DRY-G01 | 有日记 | P0 | 该日有数据 | Path date | Diary |
| DRY-G02 | 无日记 | P3 | - | date | null 或空 |

#### 保存日记
- **URL**: `POST /user/diary`
- **权限**: 前台登录
- **请求参数**: `DiaryReq`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| DRY-S01 | 新建/更新 | P0 | 已登录 | 合法 body | 成功 |

#### 删除日记
- **URL**: `DELETE /user/diary/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| DRY-D01 | 删除 | P0 | 存在 | id | 成功 |
| DRY-D02 | 不存在 | P3 | - | 无效 id | 失败 |

#### 日期范围日记
- **URL**: `GET /user/diary/range`
- **权限**: 前台登录
- **请求参数**: `startDate`、`endDate`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| DRY-R01 | 范围查询 | P0 | 已登录 | 两参数 | List |
| DRY-R02 | 缺参数 | P1 | - | 缺其一 | 400 |

### 19.3 习惯 HabitController

#### 习惯列表
- **URL**: `GET /user/habit/list`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| HAB-L01 | 列表 | P0 | 已登录 | 无 | 习惯列表 |

#### 新增习惯
- **URL**: `POST /user/habit`
- **权限**: 前台登录
- **请求参数**: Body `Habit`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| HAB-A01 | 新增 | P0 | 已登录 | 合法 JSON | 成功 |

#### 修改习惯
- **URL**: `PUT /user/habit`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| HAB-U01 | 更新 | P0 | 存在 | body | 成功 |

#### 删除习惯
- **URL**: `DELETE /user/habit/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| HAB-D01 | 删除 | P0 | 存在 | id | 成功 |

#### 习惯记录列表
- **URL**: `GET /user/habit/record/list`
- **权限**: 前台登录
- **请求参数**: `habitId` 可选；`startDate`、`endDate` 必填

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| HAB-RL01 | 查询记录 | P0 | 已登录 | 日期范围 | 列表 |
| HAB-RL02 | 缺日期 | P1 | - | 无 start | 400 |

#### 新增习惯记录
- **URL**: `POST /user/habit/record`
- **权限**: 前台登录
- **请求参数**: `HabitRecord`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| HAB-RA01 | 打卡 | P0 | 已登录 | body | 成功 |

#### 修改习惯记录
- **URL**: `PUT /user/habit/record`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| HAB-RU01 | 更新记录 | P0 | 存在 | body | 成功 |

#### 删除习惯记录
- **URL**: `DELETE /user/habit/record/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| HAB-RD01 | 删除 | P0 | 存在 | id | 成功 |

#### 每日统计
- **URL**: `GET /user/habit/stats`
- **权限**: 前台登录
- **请求参数**: `habitId` 可选；`startDate`、`endDate` 必填

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| HAB-ST01 | 统计数据 | P0 | 已登录 | 范围 | List Map |

### 19.4 任务池 TaskPoolController

#### 未分配任务
- **URL**: `GET /user/taskpool/unassigned`
- **权限**: 前台登录
- **请求参数**: `keyword` 可选

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TPL-UN01 | 未分配列表 | P0 | 已登录 | 可选 keyword | 列表 |

#### 按周查询
- **URL**: `GET /user/taskpool/week`
- **权限**: 前台登录
- **请求参数**: `weekStart` 必填

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TPL-W01 | 查询 | P0 | 已登录 | weekStart | 列表 |
| TPL-W02 | 缺 weekStart | P1 | - | 无 | 400 |

#### 日期范围查询
- **URL**: `GET /user/taskpool/range`
- **权限**: 前台登录
- **请求参数**: `start`、`end`

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TPL-R01 | 范围 | P0 | 已登录 | start+end | 列表 |

#### 新增任务池项
- **URL**: `POST /user/taskpool`
- **权限**: 前台登录
- **请求参数**: `TaskPoolReq`（`@Validated`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TPL-A01 | 新增 | P0 | 已登录 | 合法 body | 成功 |
| TPL-A02 | 校验失败 | P1 | - | 缺字段 | 失败 |

#### 修改任务池项
- **URL**: `PUT /user/taskpool`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TPL-UM01 | 更新任务 | P0 | 存在 | body | 成功 |

#### 删除任务池项
- **URL**: `DELETE /user/taskpool/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TPL-D01 | 删除 | P0 | 存在 | id | 成功 |

#### 分配到周
- **URL**: `PUT /user/taskpool/assign`
- **权限**: 前台登录
- **请求参数**: `TaskPoolAssignReq`（`@Validated`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TPL-AS01 | 分配 | P0 | 已登录 | 合法 body | 成功 |

#### 取消分配
- **URL**: `PUT /user/taskpool/unassign/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TPL-UA01 | 取消 | P0 | 已分配 | id | 成功 |

#### 切换完成
- **URL**: `PUT /user/taskpool/status/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TPL-ST01 | 切换状态 | P0 | 存在 | id | 成功 |

### 19.5 时间块 TimeBlockController

#### 某日时间块列表
- **URL**: `GET /user/timeblock/list`
- **权限**: 前台登录
- **请求参数**: `date` 必填

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TBK-L01 | 查询 | P0 | 已登录 | date | 列表 |
| TBK-L02 | 缺 date | P1 | - | 无 | 400 |

#### 新增时间块
- **URL**: `POST /user/timeblock`
- **权限**: 前台登录
- **请求参数**: `TimeBlockReq`（`@Validated`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TBK-A01 | 新增 | P0 | 已登录 | 合法 body | 成功 |

#### 修改时间块
- **URL**: `PUT /user/timeblock`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TBK-U01 | 更新 | P0 | 存在 | body | 成功 |

#### 删除时间块
- **URL**: `DELETE /user/timeblock/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TBK-D01 | 删除 | P0 | 存在 | id | 成功 |

#### 历史事件去重列表
- **URL**: `GET /user/timeblock/events`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TBK-E01 | 事件列表 | P0 | 已登录 | 无 | 列表 |

#### 分类统计
- **URL**: `GET /user/timeblock/stats`
- **权限**: 前台登录
- **请求参数**: `type`（默认 `daily`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| TBK-S01 | 统计 | P0 | 已登录 | type | 列表 |

### 19.6 思考 ThinkingController

#### 思考列表
- **URL**: `GET /user/thinking/list`
- **权限**: 前台登录
- **请求参数**: `keyword` 可选

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| THK-L01 | 列表 | P0 | 已登录 | 可选 keyword | 列表 |

#### 新增思考
- **URL**: `POST /user/thinking`
- **权限**: 前台登录
- **请求参数**: `ThinkingReq`（`@Validated`）

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| THK-A01 | 新增 | P0 | 已登录 | 合法 body | 成功 |
| THK-A02 | 校验失败 | P1 | - | 缺字段 | 失败 |

#### 修改思考
- **URL**: `PUT /user/thinking`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| THK-U01 | 更新 | P0 | 存在 | body | 成功 |

#### 删除思考
- **URL**: `DELETE /user/thinking/{id}`
- **权限**: 前台登录

| 用例ID | 用例名称 | 优先级 | 前置条件 | 请求参数 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| THK-D01 | 删除 | P0 | 存在 | id | 成功 |

---

## 20. 附录

1. **权限码**以源码 `@SaCheckPermission` 为准；若数据库菜单与代码不一致，以**实际库表**为准回归。  
2. **限流**（`@AccessLimit`）返回体依全局异常处理实现，测试时断言 HTTP 状态或业务 `code`。  
3. **文件下载** `GET /file/download/{fileId}` 为公开接口，需注意**越权下载**风险（若需加固应在后续版本增加鉴权）。  
4. **OAuth** 用例依赖第三方环境与配置，建议在联调环境执行 P0。  
5. 若部署路径带 **context-path**，请在基址中追加前缀后复测。  
6. 项目中另有 `BiliController` 等扩展控制器，未列入本文档时可在对应源码上按**相同表格模板**补写用例。

---

*文档版本：与仓库 `blog-springboot` 源码同步整理（含 `/article/daily`、`/category/article`、`/tag/article`、`/comment/.../reply`、`/admin/talk/upload`、`/talk/.../like`、`/ai/quick-read`、`/user/ai/prompt/*` 等）。*
