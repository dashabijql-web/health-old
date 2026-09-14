package com.xzkj.health.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              MyBatis-Plus 配置（新手必读）                          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【什么是 MyBatis-Plus？】
 *
 * MyBatis 是一个 SQL 映射框架，把 Java 方法和 SQL 语句关联起来。
 * MyBatis-Plus 是 MyBatis 的增强版，在其基础上内置了常见的 CRUD 操作，
 * 不需要写大量重复的 SQL，极大提升开发效率。
 *
 * 普通 MyBatis 查询一个用户需要：
 *   1. 在 XML 文件写：<select id="findById"> SELECT * FROM sys_user WHERE id=#{id} </select>
 *   2. 在 Mapper 接口写：SysUser findById(Long id);
 *
 * MyBatis-Plus 直接用（无需写 SQL）：
 *   userMapper.selectById(1L);   // 自动生成 SELECT * FROM sys_user WHERE id=1
 *   userMapper.insert(user);     // 自动生成 INSERT INTO sys_user (...)
 *   userMapper.updateById(user); // 自动生成 UPDATE sys_user SET ... WHERE id=?
 *   userMapper.deleteById(1L);   // 自动生成 DELETE FROM sys_user WHERE id=1
 *
 * 还有条件构造器（LambdaQueryWrapper）：
 *   new LambdaQueryWrapper<SysUser>()
 *       .eq(SysUser::getUsername, "admin")  // WHERE username = 'admin'
 *       .eq(SysUser::getStatus, 0);          // AND status = 0
 *
 * 【注解说明】
 *
 * @Configuration：声明这是配置类，Spring 启动时会加载
 *
 * @EnableTransactionManagement：开启 Spring 的声明式事务管理
 *   开启后，可以在 Service 方法上加 @Transactional 注解来开启数据库事务
 *   例如：一个操作需要同时插入用户表和角色关联表，如果中途报错需要回滚
 *   @Transactional 就能保证"要么都成功，要么都失败"
 *
 * @MapperScan("com.xzkj.health.mapper")：扫描指定包下的所有 Mapper 接口
 *   MyBatis 需要知道哪些接口是 Mapper（数据访问层）
 *   加了 @MapperScan 后，包内所有接口会被自动识别为 Mapper，
 *   Spring 会为它们自动生成实现类并注册为 Bean，可以直接 @Autowired 注入
 *   不加这个注解的话，需要在每个 Mapper 接口上加 @Mapper 注解
 */
@Configuration
@EnableTransactionManagement
@MapperScan({"com.xzkj.health.mapper", "com.xzkj.health.ai"})
public class MybatisPlusConfig {

    /**
     * 分页插件配置
     *
     * 【为什么需要分页插件？】
     *
     * 当数据量很大时（如有 10 万条健康记录），一次性返回所有数据：
     *   - 会让数据库承受巨大压力（全表扫描）
     *   - 会消耗大量网络带宽
     *   - 前端页面无法展示这么多数据
     *
     * 分页查询只返回当前页的数据（如每页 20 条），极大优化性能。
     *
     * 【MyBatis-Plus 分页使用方式】
     *
     * 在 Service 中：
     *   Page<HealthRecord> page = new Page<>(1, 20);  // 第1页，每页20条
     *   Page<HealthRecord> result = mapper.selectPage(page, queryWrapper);
     *   List<HealthRecord> records = result.getRecords();  // 当前页数据
     *   long total = result.getTotal();  // 总记录数
     *
     * 分页插件会自动在 SQL 后面加上分页语句（不同数据库语法不同）：
     *   MySQL：LIMIT 0, 20
     *   SQL Server：OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY
     *
     * 【为什么要指定 DbType.SQL_SERVER？】
     *
     * 不同数据库的分页 SQL 语法不同：
     *   MySQL：    SELECT * FROM t LIMIT 0, 20
     *   Oracle：   SELECT * FROM (SELECT *, ROWNUM rn FROM t) WHERE rn BETWEEN 1 AND 20
     *   SQL Server：SELECT * FROM t ORDER BY id OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY
     *
     * 必须告诉插件使用的是哪种数据库，才能生成正确的分页 SQL。
     * 本项目使用 SQL Server，所以配置 DbType.SQL_SERVER。
     *
     * @return 配置好的 MyBatis-Plus 拦截器（包含分页功能）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页拦截器，指定数据库类型为 SQL Server
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQL_SERVER));
        return interceptor;
    }

    /**
     * SQL 注入器（支持自定义方法）
     *
     * 【什么是 SQL 注入器？】
     *
     * MyBatis-Plus 的 BaseMapper 内置了 insert/delete/update/select 等方法，
     * 这些方法是通过 SQL 注入器（ISqlInjector）在启动时动态注入 SQL 语句实现的。
     *
     * DefaultSqlInjector 是默认实现，提供所有内置 CRUD 方法的 SQL 注入。
     *
     * 如果以后需要扩展自定义的全局方法（如"逻辑删除恢复"），
     * 可以继承 DefaultSqlInjector 并添加自定义方法。
     *
     * 目前使用默认实现就够了，显式注册是最佳实践。
     *
     * @return 默认的 SQL 注入器实例
     */
    @Bean
    public ISqlInjector sqlInjector() {
        return new DefaultSqlInjector();
    }
}
