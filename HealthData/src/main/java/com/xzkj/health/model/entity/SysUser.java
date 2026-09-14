package com.xzkj.health.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              系统用户实体类（新手必读）                              ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 对应数据库表：sys_user
 * 用途：存储系统登录用户的基本信息（不是设备用户，是系统管理员/操作员）
 *
 * 【数据库表结构对照】
 *
 * sys_user 表大致结构：
 * ┌──────────────┬──────────────────────────────────────────────────────┐
 * │ 字段名       │ 说明                                                   │
 * ├──────────────┼──────────────────────────────────────────────────────┤
 * │ id           │ 主键，自增                                             │
 * │ username     │ 登录用户名（唯一）                                     │
 * │ password     │ 密码（BCrypt 加密存储，不是明文）                       │
 * │ real_name    │ 真实姓名                                               │
 * │ nickname     │ 昵称（可选）                                           │
 * │ avatar       │ 头像 URL                                               │
 * │ phone        │ 手机号                                                 │
 * │ email        │ 邮箱                                                   │
 * │ status       │ 账号状态（0=正常, 1=禁用）                             │
 * │ gender       │ 性别（0=未知, 1=男, 2=女）                             │
 * │ dept_id      │ 部门 ID，关联部门表                                    │
 * │ create_time  │ 创建时间（自动填充）                                   │
 * │ create_by    │ 创建人                                                 │
 * │ update_time  │ 最后更新时间（自动填充）                               │
 * │ update_by    │ 最后更新人                                             │
 * │ remark       │ 备注                                                   │
 * └──────────────┴──────────────────────────────────────────────────────┘
 *
 * 【密码安全说明】
 *
 * password 字段存储的是 BCrypt 加密后的哈希值，格式如：
 *   $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi
 *
 * 特点：
 *   1. 不可逆：从哈希值无法还原出原始密码
 *   2. 每次相同密码生成的哈希值都不同（加盐）
 *   3. 校验时使用 passwordEncoder.matches(rawPassword, encodedPassword)
 *
 * 【@Accessors(chain = true) 说明】
 *
 * Lombok 提供的链式调用注解，让 setter 方法返回 this 而不是 void：
 *
 * 不加此注解的普通写法：
 *   user.setUsername("admin");
 *   user.setRealName("管理员");
 *   user.setStatus(0);
 *
 * 加了此注解后的链式写法：
 *   user.setUsername("admin").setRealName("管理员").setStatus(0);
 *
 * 【FieldFill 自动填充说明】
 *
 * @TableField(fill = FieldFill.INSERT) 表示：
 *   插入记录时自动填充这个字段（不需要手动 set）
 *
 * @TableField(fill = FieldFill.INSERT_UPDATE) 表示：
 *   插入和更新时都自动填充
 *
 * 具体填充逻辑需要在 MetaObjectHandler 实现类中配置（填当前时间）。
 */
@Data                           // Lombok: 生成 getter/setter/toString/equals/hashCode
@Accessors(chain = true)        // Lombok: 开启链式调用（setter 返回 this）
@TableName("sys_user")          // MyBatis-Plus: 对应的数据库表名
public class SysUser implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID（主键，数据库自增）
     *
     * value = "id" 明确指定数据库列名
     * type = IdType.AUTO 使用数据库 IDENTITY 自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名
     * 唯一标识一个用户，用于登录时查找用户
     * 建议：只包含字母、数字、下划线，不区分大小写
     */
    private String username;

    /**
     * 登录密码（BCrypt 加密存储）
     *
     * 重要：这里存的是加密后的哈希值，不是明文密码！
     * 例如：$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi
     *
     * 校验方式：passwordEncoder.matches("明文密码", user.getPassword())
     * 绝对不能用 .equals() 比较！
     */
    private String password;

    /**
     * 真实姓名（中文名）
     * 用于界面展示，如侧边栏"欢迎，张三"
     * 如果为 null，会用 username 代替
     */
    private String realName;

    /**
     * 昵称（可选）
     * 一些系统用昵称展示，比真实姓名更随意
     */
    private String nickname;

    /**
     * 头像图片 URL
     * 可以是相对路径或完整 URL
     * 如果为 null 或空，前端会显示默认头像
     */
    private String avatar;

    /**
     * 手机号（可用于找回密码、短信通知）
     */
    private String phone;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 账号状态
     * 0 = 正常（可以登录）
     * 1 = 禁用（登录时提示"账号已禁用"）
     *
     * 禁用用户不需要删除记录，只需将 status 改为 1
     */
    private Integer status;

    /**
     * 性别
     * 0 = 未知
     * 1 = 男
     * 2 = 女
     */
    private Integer gender;

    /**
     * 所属部门 ID
     * 关联部门信息表（如果有的话）
     * 用于按部门统计健康数据
     */
    private Long deptId;

    /**
     * 记录创建时间
     *
     * FieldFill.INSERT 表示仅在 INSERT 时自动填充当前时间
     * 一旦创建就不再修改（即使执行 UPDATE 也不会改变此字段）
     *
     * 类型 LocalDateTime 是 Java 8 引入的日期时间类，比 Date 更好用
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 创建人（记录是谁创建的这个账号）
     * 通常存储操作人的用户名
     */
    private String createBy;

    /**
     * 最后更新时间
     *
     * FieldFill.INSERT_UPDATE 表示插入时和每次 UPDATE 时都自动更新为当前时间
     * 可以通过这个字段知道账号最后一次被修改是什么时候
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 最后更新人（记录是谁最后修改了这个账号）
     */
    private String updateBy;

    /**
     * 备注信息（可选，存储额外说明）
     */
    private String remark;
}
