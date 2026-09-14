package com.xzkj.health.service;

import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.config.datasource.HealthDataSourceContext;
import com.xzkj.health.config.datasource.HealthDataSourceKey;
import com.xzkj.health.dto.commandcenter.CommandCenterActionResultView;
import com.xzkj.health.dto.commandcenter.CommandCenterIncidentPageView;
import com.xzkj.health.dto.commandcenter.CommandCenterIncidentTimelineItemView;
import com.xzkj.health.dto.commandcenter.CommandCenterIncidentView;
import com.xzkj.health.dto.riskwarning.RiskWarningItemView;
import com.xzkj.health.dto.riskwarning.RiskWarningPageView;
import com.xzkj.health.mapper.CommandCenterIncidentMapper;
import com.xzkj.health.util.LocalTtlCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Projects monthly warning records into one command-center incident contract.
 * This service deliberately does not manufacture position, assignment or SLA data.
 */
@Service
@RequiredArgsConstructor
public class CommandCenterIncidentService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long INCIDENT_PAGE_TTL_MS = 5_000L;

    private final RiskWarningService riskWarningService;
    private final CommandCenterIncidentMapper incidentMapper;
    private final Set<String> initializedSources = ConcurrentHashMap.newKeySet();
    private final LocalTtlCache<CommandCenterIncidentPageView> incidentPageCache = new LocalTtlCache<>();

    public CommandCenterIncidentPageView getIncidents(
            String scope, String status, String startAt, String endAt, int page, int size) {
        String cacheKey = String.join("|",
                String.valueOf(HealthDataSourceContext.get()),
                normalizeScope(scope),
                String.valueOf(status),
                String.valueOf(startAt),
                String.valueOf(endAt),
                String.valueOf(page),
                String.valueOf(size));
        return incidentPageCache.getOrLoad(cacheKey, INCIDENT_PAGE_TTL_MS,
                () -> loadIncidents(scope, status, startAt, endAt, page, size));
    }

    private CommandCenterIncidentPageView loadIncidents(
            String scope, String status, String startAt, String endAt, int page, int size) {
        IncidentWindow window = resolveWindow(scope, startAt, endAt);
        ensureSchema();
        RiskWarningPageView warnings = riskWarningService.getWarningListByTimeWindow(
                null, resolveHandled(status), format(window.start()), format(window.end()), page, size);
        Map<String, Map<String, Object>> states = indexStates(
                incidentMapper.getIncidentStatesInWindow(format(window.start()), format(window.end())));

        List<CommandCenterIncidentView> items = new ArrayList<>(warnings.list().stream()
                .map(warning -> toIncident(warning, states.get(locatorKey(warning.id(), warning.createTime()))))
                .toList());
        items.sort(Comparator
                .comparingInt(this::openStatusPriority)
                .thenComparingInt(this::severityPriority)
                .thenComparingInt(this::slaPriority)
                .thenComparing(Comparator.comparingLong(CommandCenterIncidentView::durationSeconds).reversed()));
        LocalDateTime now = LocalDateTime.now();
        return new CommandCenterIncidentPageView(
                items,
                warnings.total(),
                warnings.page(),
                warnings.size(),
                normalizeScope(scope),
                format(window.start()),
                format(window.end()),
                format(now)
        );
    }

    public CommandCenterIncidentView getIncident(Long warningId, String occurredAt) {
        String locatorTime = requireOccurredAt(occurredAt);
        ensureSchema();
        RiskWarningItemView warning = riskWarningService.getWarningDetail(warningId, locatorTime);
        if (warning == null) {
            throw new BusinessException(404, "事件不存在或已超出当前数据源范围");
        }
        return toIncident(warning, incidentMapper.getIncidentState(warningId, locatorTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandCenterActionResultView resolveIncident(
            Long warningId, String occurredAt, String operator, String remark) {
        String locatorTime = requireOccurredAt(occurredAt);
        RiskWarningItemView warning = requireWarning(warningId, locatorTime);
        ensureSchema();
        ensureMutable(warning, locatorTime);
        boolean resolved = riskWarningService.handleWarning(
                warningId,
                operator,
                remark == null ? "" : remark.trim(),
                locatorTime
        );
        if (!resolved) {
            throw new BusinessException(404, "事件不存在或已被其他操作处理");
        }
        createIncidentIfMissing(warningId, locatorTime, initialStatus(warning));
        incidentMapper.updateIncidentStatus(warningId, locatorTime, "RESOLVED");
        return recordActionResult(warningId, locatorTime, "RESOLVE", "RECORDED",
                "事件已处理并写入预警记录", operator, null, remark);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandCenterActionResultView acknowledgeIncident(
            Long warningId, String occurredAt, String operator, String remark) {
        String locatorTime = requireOccurredAt(occurredAt);
        RiskWarningItemView warning = requireWarning(warningId, locatorTime);
        ensureSchema();
        ensureMutable(warning, locatorTime);
        createIncidentIfMissing(warningId, locatorTime, initialStatus(warning));
        String currentStatus = stateStatus(incidentMapper.getIncidentState(warningId, locatorTime), initialStatus(warning));
        if ("NEW".equals(currentStatus)) {
            incidentMapper.updateIncidentStatus(warningId, locatorTime, "ACKED");
        }
        return recordActionResult(warningId, locatorTime, "ACK", "RECORDED",
                "已确认事件，等待分派或处理", operator, null, remark);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandCenterActionResultView assignIncident(
            Long warningId,
            String occurredAt,
            Long ownerUserId,
            String ownerName,
            String ownerDept,
            Integer slaMinutes,
            String operator,
            String remark) {
        String locatorTime = requireOccurredAt(occurredAt);
        if (ownerUserId == null || ownerName == null || ownerName.isBlank()) {
            throw new BusinessException(400, "分派必须指定有效责任人");
        }
        if (slaMinutes == null || slaMinutes < 1 || slaMinutes > 24 * 60) {
            throw new BusinessException(400, "SLA 时限必须在 1 到 1440 分钟之间");
        }
        RiskWarningItemView warning = requireWarning(warningId, locatorTime);
        ensureSchema();
        ensureMutable(warning, locatorTime);
        createIncidentIfMissing(warningId, locatorTime, initialStatus(warning));
        LocalDateTime occurred = parseDateTime(locatorTime);
        String slaDueAt = formatLocator(occurred.plusMinutes(slaMinutes));
        incidentMapper.assignIncident(warningId, locatorTime, ownerUserId, ownerName,
                ownerDept, slaDueAt, slaMinutes, "ACKED");
        return recordActionResult(warningId, locatorTime, "ASSIGN", "RECORDED",
                "已分派责任人并设置 SLA", operator, ownerName, remark);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandCenterActionResultView falseAlarmIncident(
            Long warningId, String occurredAt, String operator, String remark) {
        String locatorTime = requireOccurredAt(occurredAt);
        RiskWarningItemView warning = requireWarning(warningId, locatorTime);
        ensureSchema();
        ensureMutable(warning, locatorTime);
        boolean resolved = riskWarningService.handleWarning(
                warningId, operator, remark == null ? "" : remark.trim(), locatorTime);
        if (!resolved) {
            throw new BusinessException(404, "事件不存在或已被其他操作处理");
        }
        createIncidentIfMissing(warningId, locatorTime, initialStatus(warning));
        incidentMapper.updateIncidentStatus(warningId, locatorTime, "FALSE_ALARM");
        return recordActionResult(warningId, locatorTime, "FALSE_ALARM", "RECORDED",
                "误报已关闭并写入预警记录", operator, null, remark);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandCenterActionResultView recordExternalAction(
            Long warningId, String occurredAt, String action, String operator, String target, String remark) {
        String locatorTime = requireOccurredAt(occurredAt);
        requireWarning(warningId, locatorTime);
        ensureSchema();
        return recordActionResult(warningId, locatorTime, action, "NOT_CONFIGURED",
                "当前环境未配置对应外部设备接口，指令未下发", operator, target, remark);
    }

    public List<CommandCenterIncidentTimelineItemView> getIncidentTimeline(Long warningId, String occurredAt) {
        String locatorTime = requireOccurredAt(occurredAt);
        requireWarning(warningId, locatorTime);
        ensureSchema();
        List<CommandCenterIncidentTimelineItemView> timeline = new ArrayList<>();
        for (Map<String, Object> row : incidentMapper.getIncidentTimeline(warningId, locatorTime)) {
            timeline.add(new CommandCenterIncidentTimelineItemView(
                    stringValue(row.get("actionId")),
                    stringValue(row.get("action")),
                    stringValue(row.get("result")),
                    stringValue(row.get("operator")),
                    stringValue(row.get("target")),
                    stringValue(row.get("remark")),
                    formatLocator(parseDateTime(stringValue(row.get("createdAt"))))
            ));
        }
        return timeline;
    }

    private CommandCenterActionResultView recordActionResult(
            Long warningId,
            String occurredAt,
            String action,
            String result,
            String message,
            String operator,
            String target,
            String remark) {
        String actionId = UUID.randomUUID().toString();
        incidentMapper.insertActionAudit(actionId, warningId, occurredAt, action, result,
                operator, target, remark == null ? "" : remark.trim());
        return new CommandCenterActionResultView(
                actionId,
                action,
                result,
                message,
                target,
                operator,
                format(LocalDateTime.now()),
                getIncident(warningId, occurredAt)
        );
    }

    private RiskWarningItemView requireWarning(Long warningId, String occurredAt) {
        RiskWarningItemView warning = riskWarningService.getWarningDetail(warningId, occurredAt);
        if (warning == null) {
            throw new BusinessException(404, "事件不存在或已超出当前数据源范围");
        }
        return warning;
    }

    private void ensureMutable(RiskWarningItemView warning, String occurredAt) {
        Map<String, Object> state = incidentMapper.getIncidentState(warning.id(), occurredAt);
        String status = stateStatus(state, initialStatus(warning));
        if (Boolean.TRUE.equals(warning.handled()) || isClosedStatus(status)) {
            throw new BusinessException(409, "事件已关闭，不能重复处理");
        }
    }

    private void createIncidentIfMissing(Long warningId, String occurredAt, String initialStatus) {
        if (incidentMapper.getIncidentState(warningId, occurredAt) == null) {
            incidentMapper.insertIncident(warningId, occurredAt, initialStatus);
        }
    }

    private boolean isClosedStatus(String status) {
        return "RESOLVED".equals(status) || "FALSE_ALARM".equals(status) || "CLOSED".equals(status);
    }

    private String initialStatus(RiskWarningItemView warning) {
        return Boolean.TRUE.equals(warning.handled()) ? "RESOLVED" : "NEW";
    }

    private CommandCenterIncidentView toIncident(RiskWarningItemView warning, Map<String, Object> state) {
        String occurredAt = warning.createTime();
        boolean resolved = Boolean.TRUE.equals(warning.handled());
        LocalDateTime occurred = parseDateTime(occurredAt);
        String status = stateStatus(state, initialStatus(warning));
        LocalDateTime updated = state == null ? null : parseDateTime(stringValue(state.get("updatedAt")));
        if (updated == null) {
            updated = resolved ? parseDateTime(warning.handleTime()) : occurred;
        }
        LocalDateTime end = resolved && updated != null ? updated : LocalDateTime.now();
        long durationSeconds = occurred == null ? 0 : Math.max(0, Duration.between(occurred, end).getSeconds());
        String operator = blankToNull(warning.handleBy());
        Long ownerUserId = toLong(state == null ? null : state.get("ownerUserId"));
        String ownerName = blankToNull(stringValue(state == null ? null : state.get("ownerName")));
        String ownerDept = blankToNull(stringValue(state == null ? null : state.get("ownerDept")));
        String slaDueAt = stringValue(state == null ? null : state.get("slaDueAt"));
        LocalDateTime slaDue = parseDateTime(slaDueAt);

        return new CommandCenterIncidentView(
                buildIncidentId(warning.id(), occurredAt),
                warning.id(),
                resolveSource(warning.warningType()),
                resolveType(warning.warningType(), warning.indicatorName()),
                warning.warningType() == null || warning.warningType().isBlank()
                        ? warning.indicatorName() : warning.warningType(),
                resolveSeverity(warning.warningLevel()),
                status,
                occurredAt,
                formatLocator(updated == null ? occurred : updated),
                durationSeconds,
                new CommandCenterIncidentView.Person(
                        warning.userCode(), warning.userName(), warning.deptName()),
                new CommandCenterIncidentView.Location("UNAVAILABLE", "未接入定位", null),
                new CommandCenterIncidentView.VitalSnapshot(warning.indicatorName(), warning.warningValue()),
                ownerName != null
                        ? new CommandCenterIncidentView.Owner("ASSIGNED", ownerUserId, ownerName, ownerDept)
                        : resolved
                            ? new CommandCenterIncidentView.Owner("RESOLVED_BY", null, operator == null ? "系统" : operator, null)
                            : new CommandCenterIncidentView.Owner("UNASSIGNED", null, null, null),
                buildSla(slaDue, status),
                availableActions(status)
        );
    }

    private CommandCenterIncidentView.Sla buildSla(LocalDateTime dueAt, String incidentStatus) {
        if (dueAt == null) {
            return new CommandCenterIncidentView.Sla(false, "NOT_CONFIGURED", null,
                    "当前事件尚未分派责任人或配置 SLA");
        }
        if (isClosedStatus(incidentStatus)) {
            return new CommandCenterIncidentView.Sla(true, "CLOSED", formatLocator(dueAt), "事件已关闭");
        }
        boolean overdue = !LocalDateTime.now().isBefore(dueAt);
        return new CommandCenterIncidentView.Sla(true, overdue ? "OVERDUE" : "ON_TIME",
                formatLocator(dueAt), overdue ? "已超过响应时限" : "响应时限内");
    }

    private List<String> availableActions(String status) {
        if (isClosedStatus(status)) {
            return List.of();
        }
        if ("NEW".equals(status)) {
            return List.of("ACK", "ASSIGN", "RESOLVE", "FALSE_ALARM", "CALL", "BROADCAST", "EVACUATE");
        }
        return List.of("ASSIGN", "RESOLVE", "FALSE_ALARM", "CALL", "BROADCAST", "EVACUATE");
    }

    private int severityPriority(CommandCenterIncidentView incident) {
        return switch (incident.severity()) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
    }

    private int openStatusPriority(CommandCenterIncidentView incident) {
        return isClosedStatus(incident.status()) ? 1 : 0;
    }

    private int slaPriority(CommandCenterIncidentView incident) {
        return switch (incident.sla().status()) {
            case "OVERDUE" -> 0;
            case "ON_TIME" -> 1;
            case "NOT_CONFIGURED" -> 2;
            default -> 3;
        };
    }

    private Map<String, Map<String, Object>> indexStates(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long warningId = toLong(row.get("warningId"));
            String occurredAt = stringValue(row.get("occurredAt"));
            if (warningId != null && occurredAt != null) {
                result.put(locatorKey(warningId, occurredAt), row);
            }
        }
        return result;
    }

    private String locatorKey(Long warningId, String occurredAt) {
        LocalDateTime parsed = parseDateTime(occurredAt);
        return warningId + "|" + (parsed == null ? occurredAt : formatLocator(parsed));
    }

    private String stateStatus(Map<String, Object> state, String fallback) {
        String status = stringValue(state == null ? null : state.get("status"));
        return status == null || status.isBlank() ? fallback : status;
    }

    private void ensureSchema() {
        HealthDataSourceKey key = HealthDataSourceContext.get();
        String sourceKey = key == null ? "default" : key.key();
        if (initializedSources.contains(sourceKey)) {
            return;
        }
        synchronized (initializedSources) {
            if (initializedSources.contains(sourceKey)) {
                return;
            }
            if (incidentMapper.countSchemaTables() != 2) {
                throw new BusinessException(503,
                        "指挥中心事件表未迁移，请先执行 sql/command_center_incident.sql");
            }
            initializedSources.add(sourceKey);
        }
    }

    private IncidentWindow resolveWindow(String scope, String startAt, String endAt) {
        String normalizedScope = normalizeScope(scope);
        LocalDateTime now = LocalDateTime.now();
        return switch (normalizedScope) {
            case "realtime" -> new IncidentWindow(now.minusMinutes(5), now);
            case "today" -> new IncidentWindow(now.toLocalDate().atStartOfDay(), now.toLocalDate().plusDays(1).atStartOfDay());
            case "range", "shift" -> new IncidentWindow(
                    parseRequiredBoundary(startAt, false),
                    parseRequiredBoundary(endAt, true));
            default -> throw new BusinessException(400, "scope 仅支持 realtime、today、range 或 shift");
        };
    }

    private String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "today" : scope.trim().toLowerCase(Locale.ROOT);
    }

    private Boolean resolveHandled(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "OPEN", "NEW" -> false;
            case "RESOLVED", "CLOSED" -> true;
            default -> throw new BusinessException(400, "status 仅支持 OPEN、RESOLVED 或 ALL");
        };
    }

    private LocalDateTime parseRequiredBoundary(String value, boolean endBoundary) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "range 和 shift 查询必须提供 startAt 与 endAt");
        }
        LocalDateTime parsed = parseDateTime(value);
        if (parsed == null) {
            throw new BusinessException(400, "时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
        if (value.trim().length() == 10 && endBoundary) {
            return parsed.plusDays(1);
        }
        return parsed;
    }

    private String requireOccurredAt(String occurredAt) {
        LocalDateTime parsed = parseDateTime(occurredAt);
        if (parsed == null) {
            throw new BusinessException(400, "事件详情和处理必须提供 occurredAt 定位时间");
        }
        return occurredAt.trim();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        try {
            if (normalized.length() == 10) {
                return LocalDate.parse(normalized).atStartOfDay();
            }
            return LocalDateTime.parse(normalized.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private String formatLocator(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        String base = DATE_TIME.format(value);
        int millis = value.getNano() / 1_000_000;
        return millis == 0 ? base : base + String.format(Locale.ROOT, ".%03d", millis);
    }

    private String buildIncidentId(Long warningId, String occurredAt) {
        String timestamp = occurredAt == null ? "unknown" : occurredAt.replaceAll("[^0-9]", "");
        return "WR-" + timestamp + "-" + warningId;
    }

    private String resolveSource(String warningType) {
        String type = warningType == null ? "" : warningType.toLowerCase(Locale.ROOT);
        return type.contains("sos") || type.contains("跌倒") || type.contains("静止") || type.contains("fall")
                ? "WATCH" : "HEALTH";
    }

    private String resolveType(String warningType, String indicatorName) {
        String text = ((warningType == null ? "" : warningType) + " " + (indicatorName == null ? "" : indicatorName))
                .toLowerCase(Locale.ROOT);
        if (text.contains("sos")) return "SOS";
        if (text.contains("跌倒") || text.contains("fall")) return "FALL";
        if (text.contains("静止") || text.contains("static")) return "STILL";
        if (text.contains("设备") || text.contains("device")) return "DEVICE_OFFLINE";
        return "VITAL";
    }

    private String resolveSeverity(String warningLevel) {
        String level = warningLevel == null ? "" : warningLevel.toLowerCase(Locale.ROOT);
        if (level.contains("高") || level.contains("danger") || level.contains("high") || "3".equals(level)) return "CRITICAL";
        if (level.contains("中") || level.contains("warn") || level.contains("medium") || "2".equals(level)) return "HIGH";
        return "MEDIUM";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record IncidentWindow(LocalDateTime start, LocalDateTime end) {
    }
}
