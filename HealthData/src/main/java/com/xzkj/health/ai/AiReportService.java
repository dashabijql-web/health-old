package com.xzkj.health.ai;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 健康诊断报告服务
 *
 * 流程：
 *   1. 查询员工基本信息（姓名、部门、岗位、年龄）
 *   2. 查询近30天体征平均值（心率、血氧、血压、体温、睡眠、步数、卡路里、压力）
 *   3. 查询近30天预警汇总
 *   4. 将以上数据拼成 Prompt 传给 DeepSeek，生成 Markdown 格式诊断报告
 */
@Slf4j
@Service
public class AiReportService {

    @Autowired
    private AiReportMapper aiReportMapper;

    @Autowired
    private DeepSeekClient deepSeekClient;

    private static final String REPORT_SYSTEM_PROMPT = """
            你是一名专业的煤矿职业健康医师，擅长根据体征数据出具健康评估报告。
            请根据提供的员工健康数据，生成一份详细、专业且易于理解的健康诊断报告。

            【报告格式要求 - 严格遵守】
            使用 Markdown 格式，结构如下：

            ## 🏥 健康诊断报告

            **员工：** 姓名 | **部门：** 部门名 | **岗位：** 岗位名 | **年龄：** XX岁

            ---

            ## 📊 体征数据概览（近30天）

            | 指标 | 均值 | 参考范围 | 状态 |
            |------|------|---------|------|
            | 心率 | XX bpm | 60~100 bpm | ✅正常 / ⚠️偏高 / 🔴异常 |
            ...（其他指标）

            ---

            ## 🔍 重点分析

            逐项分析异常或需关注的指标，说明可能原因及影响。

            ---

            ## ⚠️ 预警记录（近30天）

            列出预警类型和次数，如无则写"近30天无预警记录"。

            ---

            ## 💡 健康建议

            针对该员工具体数据，给出3-5条具体、可执行的健康改善建议。

            ---

            ## 🎯 综合评分

            **健康评分：XX / 100**（根据各项指标偏离正常范围的程度综合打分）

            > 本报告基于近30天体征数据自动生成，仅供参考，不替代专业医疗诊断。

            【内容要求】
            - 异常值用 ⚠️ 或 🔴 标注，正常用 ✅
            - 心率正常范围 60~100 bpm；血氧正常 ≥ 95%；体温正常 36.0~37.5℃
            - 收缩压正常 < 140 mmHg；舒张压正常 < 90 mmHg
            - 睡眠正常 360~480 分钟（6~8小时）；压力正常 < 60
            - 数值保留1位小数
            - 煤矿工人职业特点：高体力劳动，需关注心肺功能和疲劳积累
            """;

    private static final String DEPT_REPORT_SYSTEM_PROMPT = """
            你是一名专业的煤矿职业健康医师，擅长根据部门整体体征数据出具健康评估报告。
            请根据提供的部门健康数据，生成一份详细、专业且易于理解的部门健康诊断报告。

            【报告格式要求】
            使用 Markdown 格式：

            ## 🏥 部门健康诊断报告

            **部门：** 部门名 | **分析周期：** 近30天

            ---

            ## 📊 体征均值概览（近30天）

            | 指标 | 均值 | 参考范围 | 状态 |
            |------|------|---------|------|
            | 心率 | XX bpm | 60~100 | ✅/⚠️/🔴 |
            ...

            ---

            ## 🔍 重点分析

            分析部门整体健康状况，说明突出的风险点和良好点。

            ---

            ## ⚠️ 预警情况（近30天）

            汇总预警频次和类型分布。

            ---

            ## 💡 管理建议

            针对部门整体数据，给出3-5条具体的健康管理改进建议。

            ---

            ## 🎯 部门健康评分

            **健康评分：XX / 100**

            > 本报告基于近30天体征数据自动生成，仅供参考。
            """;

    /**
     * 生成部门健康诊断报告
     */
    public String generateDepartmentReport(String deptName) {
        log.info("生成部门健康报告: {}", deptName);

        List<AiReportDepartmentInfoRow> deptInfo = aiReportMapper.selectDepartmentInfo(deptName);
        if (deptInfo.isEmpty()) return "未找到部门：" + deptName;

        AiReportHealthSummaryRow healthData = aiReportMapper.selectDepartmentHealthSummary(deptName);
        List<AiReportWarningSummaryRow> warnings;
        try {
            warnings = aiReportMapper.selectDepartmentWarnings(deptName);
        } catch (Exception e) {
            warnings = List.of();
        }

        String userMessage = String.format(
            "请根据以下数据生成部门健康诊断报告：\n\n" +
            "【部门基本信息】\n%s\n\n" +
            "【近30天体征数据（全部门汇总均值）】\n%s\n\n" +
            "【近30天预警记录】\n%s",
            JSON.toJSONString(deptInfo.get(0)),
            hasHealthData(healthData) ? JSON.toJSONString(healthData) : "暂无体征数据",
            warnings.isEmpty() ? "无预警记录" : JSON.toJSONString(warnings)
        );

        log.info("调用 DeepSeek 生成部门报告...");
        return deepSeekClient.chat(DEPT_REPORT_SYSTEM_PROMPT, userMessage);
    }

    /**
     * 生成员工健康诊断报告
     *
     * @param empCode 员工工号（如 EMP001）
     * @return Markdown 格式的报告文本
     */
    public String generateEmployeeReport(String empCode) {
        log.info("生成员工健康报告: {}", empCode);

        List<AiReportEmployeeInfoRow> empInfo = aiReportMapper.selectEmployeeInfo(empCode);
        if (empInfo.isEmpty()) {
            return "未找到员工工号 " + empCode + " 的信息";
        }

        AiReportHealthSummaryRow healthData = aiReportMapper.selectEmployeeHealthSummary(empCode);
        List<AiReportWarningSummaryRow> warnings;
        try {
            warnings = aiReportMapper.selectEmployeeWarnings(empCode);
        } catch (Exception e) {
            log.warn("查询预警记录失败: {}", e.getMessage());
            warnings = List.of();
        }

        // Step 4: 组装 Prompt
        String userMessage = String.format(
            "请根据以下数据生成健康诊断报告：\n\n" +
            "【员工基本信息】\n%s\n\n" +
            "【近30天体征数据】\n%s\n\n" +
            "【近30天预警记录】\n%s",
            JSON.toJSONString(empInfo.get(0)),
            hasHealthData(healthData) ? JSON.toJSONString(healthData) : "暂无体征数据",
            warnings.isEmpty() ? "无预警记录" : JSON.toJSONString(warnings)
        );

        log.info("调用 DeepSeek 生成报告...");
        String report = deepSeekClient.chat(REPORT_SYSTEM_PROMPT, userMessage);
        log.info("报告生成完毕: {} chars", report.length());
        return report;
    }

    private boolean hasHealthData(AiReportHealthSummaryRow row) {
        if (row == null || row.getRecordCount() == null) {
            return false;
        }
        return row.getRecordCount().longValue() > 0;
    }
}
