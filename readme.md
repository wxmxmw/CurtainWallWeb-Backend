后端基础框架搭建完成。根据提供的 API 文档和数据库设计，配置了 Spring Boot 3 + MyBatis-Plus 的开发环境，并生成了核心代码结构。

### 🔨 核心构建内容

1. 项目配置 (Configuration)

   - Maven 依赖 : 升级 Spring Boot 至 3.2.1 ，引入 mybatis-plus-spring-boot3-starter , mysql-connector-j , lombok , jjwt (JWT 鉴权), spring-boot-starter-validation 。
   - 配置文件 : 创建 application.yml 配置了 MySQL 连接、MyBatis-Plus 设置及 Server 端口 (8000)。

2. 代码结构 (Package Structure)

   - com.company.curtainwall.entity : 根据数据库文档创建了 SysUser , BizDetectionTask 等 5 个实体类。
   - com.company.curtainwall.mapper : 创建了对应的 Mapper 接口，继承 BaseMapper ，开箱即用 CRUD。
   - com.company.curtainwall.common : 实现了 ApiResponse 统一响应封装和 GlobalExceptionHandler 全局异常处理。
   - com.company.curtainwall.utils : 提供了 JwtUtil 用于 Token 生成与解析。

3. 功能示例 (Implementation)

   - 用户认证 : 实现了 AuthController 和 SysUserServiceImpl ，包含登录和注册的基础逻辑（目前密码为明文，建议后续增加 Hash 处理）。

### ✅ 验证与运行

项目已通过 mvnw clean compile 编译测试，无报错。

下一步建议：

1. 在 SysUserServiceImpl 中添加密码加密逻辑 (如 BCrypt)。
2. 根据 API 文档继续实现 BizDetectionTask 相关的业务逻辑。
