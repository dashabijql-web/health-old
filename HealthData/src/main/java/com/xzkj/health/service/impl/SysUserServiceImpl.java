package com.xzkj.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xzkj.health.mapper.PermissionMapper;
import com.xzkj.health.mapper.SysUserMapper;
import com.xzkj.health.model.entity.SysUser;
import com.xzkj.health.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║           系统用户 Service 实现类（新手必读）                         ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【MyBatis-Plus Service 层架构说明】
 *
 * MyBatis-Plus 提供了两层接口：
 *   BaseMapper<T>：最基础的 CRUD（selectById、insert、updateById、deleteById 等）
 *   IService<T>：在 BaseMapper 基础上扩展了批量操作、分页等功能
 *
 * 用法：
 *   Mapper 接口   继承 BaseMapper<SysUser>    → 数据库 SQL 操作
 *   Service 接口  继承 IService<SysUser>      → 业务方法声明
 *   ServiceImpl   继承 ServiceImpl<M, T>     → 提供 IService 的默认实现
 *
 * 本类 SysUserServiceImpl 继承 ServiceImpl<SysUserMapper, SysUser>：
 *   第一个参数 SysUserMapper：告诉 ServiceImpl 使用哪个 Mapper 操作数据库
 *   第二个参数 SysUser：实体类类型
 *
 * 继承后，可以直接调用父类方法（无需注入 Mapper）：
 *   getOne(wrapper)  → 查询单条记录
 *   count(wrapper)   → 查询记录数
 *   save(entity)     → 保存记录
 *   getById(id)      → 按主键查询
 *   list(wrapper)    → 查询列表
 *
 * 【LambdaQueryWrapper 说明】
 *
 * MyBatis-Plus 的条件构造器，用于构建 SQL WHERE 子句：
 *
 *   new LambdaQueryWrapper<SysUser>()
 *       .eq(SysUser::getUsername, "admin")  → WHERE username = 'admin'
 *       .eq(SysUser::getStatus, 0)          → AND status = 0
 *       .like(SysUser::getRealName, "张")   → AND real_name LIKE '%张%'
 *       .orderByDesc(SysUser::getCreateTime) → ORDER BY create_time DESC
 *
 * 使用 Lambda 方法引用（SysUser::getUsername）而不是字符串（"username"）的好处：
 *   - 编译时检查：字段名写错会编译报错，而不是运行时 SQL 错误
 *   - 重构安全：字段名改变时 IDE 会自动更新所有引用
 *
 * 【BCrypt 密码校验说明】
 *
 * 数据库中存储的是 BCrypt 加密后的哈希值，登录时的校验流程：
 *   1. 用户输入明文密码（如 "admin123"）
 *   2. passwordEncoder.matches("admin123", user.getPassword())
 *      内部：用 BCrypt 对明文密码加密，与存储的哈希值比对
 *      注意：不能直接比较字符串！BCrypt 每次加密同一密码结果不同
 *   3. 返回 true（密码正确）或 false（密码错误）
 *
 * 【@Autowired 注入说明】
 *
 * @Autowired 让 Spring 自动找到对应类型的 Bean 并注入。
 * 不需要手动 new PasswordEncoder()，Spring 会从容器中取 SecurityConfig 里配置的那个。
 */
@Slf4j
@Service  // 标记为 Service Bean，Spring 启动时自动创建实例
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /**
     * 密码加密/校验工具
     * 由 SecurityConfig.passwordEncoder() 配置为 BCryptPasswordEncoder
     * 通过 @Autowired 自动注入
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 权限数据访问 Mapper
     * 用于查询用户拥有的按钮权限和菜单权限
     */
    @Autowired
    private PermissionMapper permissionMapper;

    /**
     * 根据用户名查询用户
     *
     * 使用 LambdaQueryWrapper 构建 WHERE username = #{username} 查询
     * getOne() 查询单条记录（如果有多条会抛异常，但 username 是唯一字段不会多条）
     *
     * @param username 登录用户名
     * @return 找到的用户对象，不存在则返回 null
     */
    @Override
    public SysUser getByUsername(String username) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("username", username);
        return getOne(wrapper);
    }

    /**
     * 检查用户名是否已存在
     *
     * 使用 count() 而不是 getOne()：
     *   - count() 执行 SELECT COUNT(*) FROM sys_user WHERE username = ?
     *   - 只需知道是否存在，不需要返回用户对象，count() 更高效
     *
     * @param username 用户名
     * @return true = 已存在，false = 不存在
     */
    @Override
    public boolean existsByUsername(String username) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("username", username);
        return count(wrapper) > 0;
    }

    /**
     * 用户登录验证
     *
     * 完整的登录校验流程：
     *   1. 查询用户（不存在 → 抛异常）
     *   2. 检查账号状态（禁用 → 抛异常）
     *   3. 校验密码（BCrypt 比对，错误 → 抛异常）
     *   4. 全部通过 → 返回用户对象
     *
     * 抛出的异常会被 AuthController 捕获，转换为错误响应返回前端。
     *
     * 【安全注意事项】
     *
     * 日志只记录用户名和操作结果（成功/失败），绝对不记录密码（明文或密文）。
     * 防止日志文件泄露用户密码。
     *
     * @param username 登录用户名
     * @param password 登录密码（明文）
     * @return 验证通过的用户对象
     * @throws RuntimeException 用户不存在、账号禁用、密码错误时抛出
     */
    @Override
    public SysUser login(String username, String password) {
        // 第一步：查询用户
        SysUser user = getByUsername(username);
        if (user == null) {
            log.warn("登录失败: 用户不存在 [{}]", username);
            throw new RuntimeException("用户不存在");
        }

        if (user.getStatus() != 0) {  // 0是正常
            log.warn("登录失败: 账号已禁用 [{}]", username);
            throw new RuntimeException("账号已禁用");
        }

        // 第三步：BCrypt 密码校验
        // passwordEncoder.matches(明文密码, 数据库中的哈希值)
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("登录失败: 密码错误 [{}]", username);
            throw new RuntimeException("密码错误");
        }

        log.info("登录成功: [{}]", username);
        return user;  // 返回用户对象（AuthController 用它构建 Token 响应）
    }

    /**
     * 获取用户角色列表
     *
     * 当前实现：返回固定角色 ["user"]
     * 原因：角色表关联查询逻辑尚未完善，临时返回默认角色
     *
     * 后续优化方向：
     *   查询 sys_user_role 关联表 → sys_role 表，
     *   返回如 ["admin"]、["health_manager"]、["viewer"] 等角色码
     *
     * 角色码用于前端判断用户类型（但本项目主要依赖 permCode 控制权限，角色用途有限）
     *
     * @param userId 用户 ID
     * @return 角色码列表
     */
    @Override
    public List<String> getUserRoles(Long userId) {
        try {
            // 暂时返回默认角色，后续对接角色表
            return Collections.singletonList("user");
        } catch (Exception e) {
            log.warn("查询用户角色失败，使用默认值: {}", e.getMessage());
            return Collections.singletonList("user");
        }
    }

    /**
     * 获取用户按钮权限码列表
     *
     * 按钮权限码控制前端页面中操作按钮的显示/隐藏：
     *   "user:add"    → 是否显示"新增用户"按钮
     *   "user:delete" → 是否显示"删除用户"按钮
     *   "role:edit"   → 是否显示"编辑角色"按钮
     *
     * 查询链路：
     *   sys_user → sys_user_role → sys_role → sys_role_permission → sys_permission
     *   (permissionMapper 封装了这个多表连接查询)
     *
     * 如果 permission 相关表不存在（未执行建表 SQL），会走 catch 返回空列表。
     *
     * @param userId 用户 ID
     * @return 按钮权限码列表，如 ["user:add", "user:delete"]
     */
    @Override
    public List<String> getUserButtons(Long userId) {
        try {
            List<String> buttons = permissionMapper.getUserButtonPermCodes(userId);
            log.debug("用户 {} 的按钮权限: {}", userId, buttons);
            return buttons;
        } catch (Exception e) {
            log.warn("查询用户按钮权限失败（是否已执行 permission_tables.sql？）: {}", e.getMessage());
            return Collections.emptyList();  // 查询失败返回空列表，不影响登录
        }
    }

    /**
     * 获取用户路由权限码列表（菜单权限）
     *
     * 路由权限码控制前端侧边栏菜单的显示/隐藏：
     *   "health:dashboard"  → 是否显示"统一管控"菜单
     *   "health:realtime"   → 是否显示"实时监控"菜单
     *   "user:list"         → 是否显示"用户管理"菜单
     *   "role:list"         → 是否显示"角色管理"菜单
     *
     * 前端 store/modules/user.js 中的 getPermittedAppRoutes()
     * 会根据这个列表过滤业务路由元数据，决定显示哪些菜单。
     *
     * @param userId 用户 ID
     * @return 路由权限码列表，如 ["health:dashboard", "health:realtime", "user:list"]
     */
    @Override
    public List<String> getUserRoutes(Long userId) {
        try {
            return permissionMapper.getUserMenuPermCodes(userId);
        } catch (Exception e) {
            log.warn("查询用户路由权限失败（是否已执行 permission_tables.sql？）: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
