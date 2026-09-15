# 在线选课系统（Course Selection System）

基于 **Spring Boot 3** 的在线选课系统，核心围绕"选课瞬间的高并发抢课场景"，落地了**防超卖、缓存三兄弟、MQ 异步候补排队**等工程实践。可一键容器化部署。

> 一句话定位：一个把"分布式锁 + 缓存一致性 + 消息事务一致性"真正用在业务里的完整后端项目，而非单纯 CRUD 演示。

---

## 目录

- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [核心功能与技术亮点](#核心功能与技术亮点)
- [数据库设计](#数据库设计)
- [接口清单](#接口清单)
- [快速启动](#快速启动)
- [目录结构](#目录结构)
- [性能压测](#性能压测)
- [设计取舍与待完善](#设计取舍与待完善)

---

## 技术栈

| 分类 | 技术 | 说明 |
| --- | --- | --- |
| 语言 / 框架 | Java 17、Spring Boot 3.3.5 | 最新 LTS 版本 |
| ORM | MyBatis-Plus 3.5.15 | Lambda 条件构造器 + 注解式 SQL，无 XML |
| 数据库 | MySQL 8 | 逻辑删除 + 唯一约束兜底 |
| 缓存 / 锁 | Redis 7（Lettuce） | 缓存旁路 + 分布式锁 |
| 消息队列 | RabbitMQ（Spring AMQP） | 候补队列异步化，手动 ACK |
| 认证授权 | JWT（jjwt 0.12.3）+ 自定义注解 | 无状态认证 + 注解式 RBAC |
| 密码加密 | Spring Security Crypto（BCrypt） | 仅引入加密组件，非完整 Security |
| 接口文档 | springdoc-openapi（Swagger UI） | 内置在线调试 |
| 校验 / 构建 | Bean Validation、Maven Wrapper | 参数校验 + 开箱即用构建 |
| 部署 | Docker 多阶段构建 + docker-compose | MySQL/Redis/RabbitMQ/App 一键编排 |

---

## 系统架构

单体分层 + 中间件解耦，一条选课请求的完整链路：

```
客户端（携带 JWT）
   │
   ▼
JwtAuthFilter        Servlet 过滤器：解析并校验 Token，注入用户上下文（ThreadLocal）
   │                  · 登录/注册放行，其余 /api/* 强制认证
   ▼
RoleInterceptor      Spring MVC 拦截器：@RequireRole 注解式授权（学生 / 管理员）
   │
   ▼
Controller           入参校验（@Valid），薄控制层，无业务逻辑
   │
   ▼
Service              业务规则 + 事务边界（@Transactional）
   ├── Redis        · CacheService     缓存旁路：防穿透 / 防击穿 / 防雪崩
   │                · RedisLockUtil    分布式锁：SETNX + Lua 原子解锁
   ├── RabbitMQ     · 候补入队 WaitingQueue / 空位释放 ReleaseQueue
   │
   ▼
Mapper（MyBatis-Plus） → MySQL
```

**中间件职责划分**

| 中间件 | 职责 | 核心类 |
| --- | --- | --- |
| Redis | ① 课程详情/分页缓存 ② 选课分布式锁 | `CacheService`、`RedisLockUtil` |
| RabbitMQ | 选课满员异步入队、退课异步释放空位并转正候补 | `WaitingConsumer`、`ReleaseConsumer` |

---

## 核心功能与技术亮点

### 1. 选课并发防超卖 —— 分布式锁 + 数据库条件更新双保险

选/退课按课程粒度加 Redis 分布式锁串行化，再叠加数据库原子条件更新兜底：

- **Redis 分布式锁**：`SETNX` 加锁 + 自动过期防死锁（等待时间与过期时间分离，抢不到时自旋重试到超时）；解锁用 **Lua 脚本校验 value 后再删除**，从根上避免高并发下"误删他人锁"的经典竞态。
- **数据库兜底**：`UPDATE ... SET selected_count = selected_count + 1 WHERE selected_count < capacity`，即使极端情况下锁超时失效，原子条件也能挡住超卖。
- **完整业务校验链**：课程是否开放 → 容量是否已满 → **时间冲突检测**（联表区间重叠判断）→ **学分上限**（BigDecimal 精确比较）→ 重复选课拦截。

### 2. 缓存三兄弟 —— 防穿透 / 防击穿 / 防雪崩

手写通用缓存工具 `CacheService`（不用 `@Cacheable`，便于精细控制）：

| 问题 | 方案 |
| --- | --- |
| 防穿透 | 查无数据时写入**空值标记 + 短 TTL**，拦截恶意/无效 Key 直击数据库 |
| 防击穿 | 热点 key 用**互斥锁 + 双重检查**：抢到锁的线程回源，等待线程再查一次缓存；重试 3 次后降级返回，保护数据库 |
| 防雪崩 | 写入时 TTL **加随机扰动**，避免同一时刻大规模同时过期 |

缓存策略按场景区分：**详情**（高频热点）走互斥锁防击穿，**分页列表**（低热）走无锁 + 随机 TTL；增删改时按 key/前缀主动失效，保证 Cache-Aside 一致性。

### 3. 候补队列异步化 —— RabbitMQ + 事务一致性

课程满员后不拒绝学生，而是**异步进入候补队列**，退课自动释放空位给队首：

- **消息与事务一致性**：退课有数据库写操作，通过 `TransactionSynchronization.afterCommit()` 在**事务提交后**才发送"空位释放"消息，杜绝"事务回滚了但消息已发出"的不一致。
- **可靠性**：消费者手动 ACK，处理失败 `basicNack` 重入队；生产端开启 publisher-confirm/return。
- **幂等去重**：入队前业务层 + 消费端双重校验是否已在队，配合 `(student_id, course_id)` 唯一约束兜底。

### 4. 认证与权限 —— JWT + 注解式 RBAC

- `JwtAuthFilter` 完成认证：解析 `Authorization: Bearer <token>`，用户信息写入 **ThreadLocal**，`finally` 中清理，防止线程池复用串号。
- `RoleInterceptor` 完成授权：扫描方法上的 `@RequireRole("ADMIN")`，无注解放行、有注解校验角色，零侵入。

### 5. 工程化细节

- **统一返回体 `Result<T>` + 统一异常处理**：`@RestControllerAdvice` 分层处理业务异常 / 参数校验 / 唯一键冲突 / 系统异常，前端只认一套契约。
- **Bean Validation**：实体与入参 DTO 上 `@NotBlank / @Size / @DecimalMin / @Pattern`（排序字段白名单）等注解，配合分页大小钳制（5–100）。
- **MyBatis-Plus 工程约定**：公共字段（create/update_time）自动填充、枚举 `@EnumValue` 持久化、逻辑删除。
- **容器化**：多阶段构建缩小镜像体积；docker-compose 编排四服务并带健康检查依赖，`depends_on` 等待 MySQL 就绪。

---

## 数据库设计

共 4 张表，均为逻辑删除（`deleted`）设计：

| 表 | 关键字段 | 设计要点 |
| --- | --- | --- |
| `student` | student_no、password(BCrypt)、max_credit、role | `(student_no, deleted)` 唯一约束，逻辑删除后可复用学号 |
| `course` | capacity、**selected_count(冗余)**、start/end_time、is_open | 冗余计数用于**原子扣减防超卖**；容量 0 表示不可选 |
| `course_selection` | student_id、course_id、status(0正常/1已退) | `(student_id, course_id)` 唯一约束 → **退课后再选**复用旧记录改状态 |
| `course_waiting_queue` | student_id、course_id、queue_number、status(0排队/1转正/2取消) | 候补队列持久化，唯一约束防重复入队 |

---

## 接口清单

统一前缀 `/api`，统一响应 `Result<T>`。

### 学生模块 `/api/students`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/login` | 公开 | 学号密码登录，返回 JWT |
| POST | `/register` | 公开 | 注册（BCrypt 加密） |
| GET | `/{id}` | ADMIN | 查询学生 |
| GET | `` | ADMIN | 分页查询学生（姓名/学号模糊 + 排序） |
| POST | `` | ADMIN | 新增学生 |
| PUT | `/{id}` | ADMIN | 修改学生 |
| DELETE | `/{id}` | ADMIN | 删除学生（逻辑删除） |

### 课程模块 `/api/courses`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/{id}` | 登录 | 课程详情（Redis 缓存，互斥锁防击穿） |
| GET | `` | 登录 | 分页查询课程（Redis 缓存） |
| POST | `` | ADMIN | 新增课程 |
| PUT | `/{id}` | ADMIN | 修改课程（校验时间合法性） |
| DELETE | `/{id}` | ADMIN | 删除课程 |

### 选课模块 `/api/courses`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/select?courseId=` | 登录 | 选课；**课程满员自动进入候补队列** |
| POST | `/cancel?courseId=` | 登录 | 退课；事务提交后发 MQ 释放空位，队首候补转正 |

在线接口文档：启动后访问 `http://localhost:8080/swagger-ui.html`。

---

## 快速启动

### 方式一：docker-compose 一键启动（推荐）

```bash
# 需要 Docker Desktop
docker-compose up -d --build
```

- 启动 MySQL(3307) / Redis(6379) / RabbitMQ(5672, 管理台 15672) / App(8080) 四个服务
- `init/init.sql` 会自动建表并初始化管理员与测试课程
- 验证：`http://localhost:8080/swagger-ui.html`

内置账号（见 `init/init.sql`）：

| 角色 | 学号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | 由 SQL 中的 BCrypt 密文对应 |
| 学生 | 注册接口创建 | - |

### 方式二：本地启动

1. 本地准备 MySQL / Redis / RabbitMQ，按 `src/main/resources/application.yml` 修改连接配置
2. 执行 `init/init.sql` 初始化数据库
3. `mvn spring-boot:run`

---

## 目录结构

```
src/main/java/org/example/
├── controller/       # REST 接口（学生 / 课程 / 选课）
├── service(+impl)/   # 业务层：接口与实现分离
├── mapper/           # MyBatis-Plus Mapper（注解 SQL）
├── cache/            # CacheService：防穿透/击穿/雪崩
├── consumer/         # RabbitMQ 消费者（候补入队 / 空位释放）
├── filter/           # JwtAuthFilter 认证
├── Interceptor/      # RoleInterceptor 授权
├── config/           # 中间件配置（Redis / MQ / 分页 / 过滤器）
├── util/             # JwtUtil / RedisLockUtil / UserContext / CacheKeyUtil
├── annotation/       # @RequireRole
├── exception/        # BusinessException + 全局异常处理
├── pojo/ dto/ enums/ # 实体 / 传输对象 / 枚举
└── handler/          # 公共字段自动填充
src/test/             # 并发工具测试
init/init.sql         # 建表 + 初始化数据
dockerfile / docker-compose.yml
```

---

## 性能压测

用 JMeter 模拟 **100 个真实学生账号同时抢一门容量 10 的课程**（`Synchronizing Timer` 制造同时刻请求），完整报告见 **[loadtest/压测报告.md](loadtest/压测报告.md)**。

| 指标 | 实测 |
| --- | --- |
| 成功 / 失败 | 99 / 1 |
| 吞吐 | 65.3 req/s |
| 平均 / P95 / P99 响应 | 1071 / 1374 / 1531 ms |
| `selected_count` / 选课记录数 | **10 / 10**（精确等于容量） |
| 候补队列 | 89 人 |
| **是否超卖** | **否** |

**修复后复验**（2026-09-10）：100 个请求全部成功（失败 0）、候补 90 人，`selected_count` 与选课记录数仍精确等于容量 10，无超卖。末行的「选课记录**总行数** = 10」同时验证了记录不会再残留。

压测中定位并修复的并发缺陷：

1. **Redis 锁的 fail-fast 语义**：原实现抢不到锁立即失败，导致高并发下 **97% 请求被直接拒绝**（既没选上课也没进候补）。改为自旋重试，并把「等待时间」与「过期时间」拆成两个独立参数。
2. **JWT 解析器每请求重建**：`Jwts.parser().build()` 每次调用都触发 JJWT 内部 `ServiceLoader` 查找，并发首次初始化有竞态，抛出 `NoSuchElementException` 导致 **19% 请求 HTTP 500**。`JwtParser` 不可变且线程安全，改为单例复用。
3. **锁释放早于事务提交**（已修复）：解锁原先写在 `@Transactional` 方法的 `finally` 里，而事务在方法返回后才提交，存在窗口导致后续线程读到旧人数、误判有余量。已改为在 `TransactionSynchronization.afterCompletion` 回调中解锁；同时把「原子扣减影响行数为 0」的分支由抛错改为转入候补队列，消除个别学生被误判为「课程刚刚已满」的症状。

**瓶颈结论**：通过对照实验（Redis 连接池 2→50、日志 DEBUG→INFO）确认调优这两项对吞吐**无影响**，真正的瓶颈是粗粒度分布式锁把 100 个请求完全串行化（平均响应 1.07s 中绝大部分是排队等待）。后续优化方向是缩小锁范围或直接依赖数据库原子条件更新。

---

## 设计取舍与待完善

有意识地记录当前设计的取舍与可演进点：

1. **消费端幂等与补偿**：当前靠唯一约束 + 双重校验兜底，可进一步加强消费幂等锁与失败补偿（死信队列）。
2. **候补转正通知**：当前以日志模拟通知，可接入短信 / 邮件 / WebSocket 推送。
3. **锁粒度**：选课全流程共用一个课程级锁，100 并发下请求完全串行化，是当前吞吐瓶颈。可缩小锁范围，或直接依赖数据库原子条件更新（`WHERE selected_count < capacity`）防超卖。
