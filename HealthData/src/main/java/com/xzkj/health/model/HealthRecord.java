package com.xzkj.health.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║           健康记录实体类（ORM 映射，新手必读）                       ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【什么是实体类（Entity）？】
 *
 * 实体类是 Java 对象与数据库表之间的桥梁。
 * 每个实体类对应数据库中的一张表，类的每个字段对应表中的一列。
 *
 * 例如，数据库中有一张 health_record 表：
 * ┌─────────────┬──────────┬────────────┬───────┬───────┬───────────┬─────────────┬───────────┬─────────────────────┐
 * │ id          │ heart_rate│ blood_oxygen│ sleep │ steps │ pressure  │ temperature │ user_code │ time                │
 * ├─────────────┼──────────┼────────────┼───────┼───────┼───────────┼─────────────┼───────────┼─────────────────────┤
 * │ 1           │ 72       │ 98         │ 480   │ 8000  │ 65        │ 367         │ 353456... │ 2024-01-15 08:30:00 │
 * │ 2           │ 85       │ 97         │ 360   │ 5000  │ 70        │ 370         │ 353456... │ 2024-01-15 09:00:00 │
 * └─────────────┴──────────┴────────────┴───────┴───────┴───────────┴─────────────┴───────────┴─────────────────────┘
 *
 * 查询这张表后，MyBatis-Plus 会自动将每一行转换为一个 HealthRecord 对象。
 *
 * 【MyBatis-Plus 注解说明】
 *
 * @TableName("health_record")  → 声明这个类对应数据库中哪张表
 *                               （不写这个注解的话，MyBatis-Plus 默认用类名的下划线形式，也是 health_record）
 *
 * @TableId(type = IdType.AUTO) → 声明 id 字段是主键，类型为自增（数据库自动递增）
 *                               其他 IdType 选项：
 *                                 IdType.ASSIGN_ID → MyBatis-Plus 自动生成雪花算法 ID（64位长整数）
 *                                 IdType.ASSIGN_UUID → 生成 UUID 字符串
 *                                 IdType.INPUT → 手动指定 ID（插入时你自己传）
 *
 * @TableField("heart_rate")    → 声明 Java 字段名和数据库列名的对应关系
 *                               配置了 map-underscore-to-camel-case: true 后，
 *                               heartRate ↔ heart_rate 会自动转换，所以 @TableField 不是必须的
 *                               （保留是为了代码更清晰明确）
 *
 * 【implements Serializable 说明】
 *
 * Serializable 是 Java 的序列化接口，实现它有以下用途：
 *   1. 对象可以被序列化为字节流，用于网络传输或本地存储
 *   2. 缓存框架（Redis、Ehcache）通常要求被缓存的对象实现 Serializable
 *   3. 这是 MyBatis-Plus 实体类的最佳实践，建议都实现
 *
 * serialVersionUID 是序列化版本号：
 *   当类的结构变化（新增字段等）时，旧的序列化数据可能无法反序列化，
 *   显式声明此 ID 可以更好地控制版本兼容性。
 *
 * 【体温字段的特殊说明】
 *
 * temperature 字段存储的是整数（如 367），表示 36.7°C（即实际温度 × 10）。
 * 这样做的原因是：整数比浮点数精确，避免 0.1 + 0.2 ≠ 0.3 的浮点精度问题。
 * 读取时除以 10.0 还原：367 / 10.0 = 36.7
 *
 * 【智能手表 IMEI 与 userCode 的关系】
 *
 * userCode 存储设备的 IMEI 号（15位数字），是智能手表的唯一标识符。
 * 手表上报数据时会带上自己的 IMEI，后端以此识别是哪个设备/用户。
 * 后续可以通过 IMEI 关联到 sys_user 表找到对应的真实用户。
 */
@Data                           // Lombok: 自动生成 getter/setter/toString/equals/hashCode
@TableName("v_health_record")   // 读操作走视图（UNION ALL 所有月份分区表），写操作由 insertToTable 指定具体分区表，不受此影响
public class HealthRecord implements Serializable {

    /**
     * 序列化版本号（建议所有实体类都声明）
     * 如果不声明，JVM 会根据类结构自动计算一个值，
     * 类结构变化后这个自动值可能变化导致反序列化失败
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     *
     * IdType.AUTO 表示由数据库 IDENTITY（自增）功能自动赋值
     * 插入时不需要手动设置 id，数据库会自动分配下一个可用 ID
     * 插入完成后，MyBatis-Plus 会将自动生成的 ID 回填到这个字段
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 心率（单位：bpm，次/分钟）
     *
     * 正常范围：60-100 bpm（休息时）
     * 数据来源：智能手表 AP49 协议上报
     * 保存时经过有效性校验：20 <= heartRate <= 300
     */
    @TableField("heart_rate")
    private Integer heartRate;

    /**
     * 血氧饱和度（单位：%）
     *
     * 正常范围：95-100%
     * 低于 90% 为低氧血症，需要预警
     * 数据来源：智能手表 APHP 协议（综合健康数据包）
     * 保存时经过有效性校验：50 <= bloodOxygen <= 100
     */
    @TableField("blood_oxygen")
    private Integer bloodOxygen;

    /**
     * 睡眠时长（单位：分钟）
     *
     * 例如：480 表示睡了 480 分钟 = 8 小时
     * 成人推荐睡眠：7-9 小时（420-540 分钟）
     * 数据来源：手表按照设定的睡眠时间段自动上报
     */
    @TableField("sleep_minutes")
    private Integer sleepMinutes;

    /**
     * 步数（单位：步）
     *
     * 数据来源：手表心跳包 AP03 协议上报（含步数信息）
     * 每日建议步数：8000-10000 步
     */
    private Integer steps;

    /**
     * 卡路里消耗（单位：千卡 kcal）
     *
     * 数据来源：根据步数、心率等综合计算
     * 正常成人每日基础代谢：1200-2000 kcal
     * 运动消耗参考：走路约 0.05 kcal/步
     */
    private Integer calories;

    /**
     * 压力指数（范围：30-100）
     *
     * 正常范围：50-70（根据 HRV 心率变异性等指标综合计算）
     * 数据来源：压力监测功能（如果手表支持）
     */
    @TableField("pressure")
    private Integer pressure;

    /**
     * 血压 - 收缩压（高压）（单位：mmHg）
     *
     * 正常范围：90-140 mmHg
     * 高血压：>= 140 mmHg
     * 数据来源：APHT、APHP 协议
     */
    @TableField("blood_pressure_high")
    private Integer bloodPressureHigh;

    /**
     * 血压 - 舒张压（低压）（单位：mmHg）
     *
     * 正常范围：60-90 mmHg
     * 高血压：>= 90 mmHg
     * 数据来源：APHT、APHP 协议
     */
    @TableField("blood_pressure_low")
    private Integer bloodPressureLow;

    /**
     * 体温（单位：整数，实际温度 × 10）
     *
     * 存储示例：367 表示体温 36.7°C
     * 正常体温：36.0-37.5°C（即存储值 360-375）
     * 发烧：>= 37.5°C（即存储值 >= 375）
     * 保存时经过有效性校验：35.0 <= 实际温度 <= 42.0
     * 数据来源：手表 AP50 协议（体温上报）
     *
     * 如何读取真实温度：temperature / 10.0 = 367 / 10.0 = 36.7°C
     */
    private Integer temperature;

    /**
     * 用户编码（用户账号名，如 admin、testuser001）
     *
     * 从 sys_user 表的 username 字段获取
     * 用于标识数据归属于哪个用户
     */
    @TableField("user_code")
    private String userCode;

    /**
     * 记录时间（格式：yyyy-MM-dd HH:mm:ss）
     *
     * 由后端服务器在保存时自动填入当前时间
     * 不是使用数据库的 GETDATE()，而是由 Java 代码写入
     * 格式固定为字符串，如 "2024-01-15 08:30:00"
     */
    @TableField("record_time")
    private String time;

    /**
     * Redis 实时快照使用的逐指标采集时间，不落入月分表。
     * 旧缓存没有该字段时，读取方会回退到 {@link #time}。
     */
    @TableField(exist = false)
    private Map<String, String> metricTimes;
}
