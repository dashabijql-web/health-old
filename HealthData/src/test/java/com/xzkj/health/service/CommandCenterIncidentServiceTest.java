package com.xzkj.health.service;

import com.xzkj.health.dto.commandcenter.CommandCenterIncidentPageView;
import com.xzkj.health.dto.commandcenter.CommandCenterIncidentView;
import com.xzkj.health.dto.commandcenter.CommandCenterActionResultView;
import com.xzkj.health.dto.riskwarning.RiskWarningItemView;
import com.xzkj.health.dto.riskwarning.RiskWarningPageView;
import com.xzkj.health.mapper.CommandCenterIncidentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandCenterIncidentServiceTest {

    @Mock
    private RiskWarningService riskWarningService;

    @Mock
    private CommandCenterIncidentMapper incidentMapper;

    @InjectMocks
    private CommandCenterIncidentService incidentService;

    @Test
    void projectsOpenWarningWithoutInventingLocationOrSla() {
        RiskWarningItemView warning = warning(false, null, null);
        when(incidentMapper.countSchemaTables()).thenReturn(2);
        when(riskWarningService.getWarningListByTimeWindow(eq(null), eq(false), anyString(), anyString(), eq(1), eq(20)))
                .thenReturn(new RiskWarningPageView(List.of(warning), 1, 1, 20));
        when(incidentMapper.getIncidentStatesInWindow(anyString(), anyString())).thenReturn(List.of());

        CommandCenterIncidentPageView page = incidentService.getIncidents("today", "OPEN", null, null, 1, 20);
        CommandCenterIncidentView incident = page.items().get(0);

        assertEquals("NEW", incident.status());
        assertEquals("WATCH", incident.source());
        assertEquals("SOS", incident.type());
        assertEquals("CRITICAL", incident.severity());
        assertEquals("UNAVAILABLE", incident.location().status());
        assertFalse(incident.sla().configured());
        assertEquals("UNASSIGNED", incident.owner().status());
        assertEquals(List.of("ACK", "ASSIGN", "RESOLVE", "FALSE_ALARM", "CALL", "BROADCAST", "EVACUATE"),
                incident.availableActions());
        assertTrue(incident.incidentId().startsWith("WR-20260713103000820-42"));
    }

    @Test
    void resolvesWithTimestampSafeLocatorAndReturnsLatestState() {
        RiskWarningItemView open = warning(false, null, null);
        RiskWarningItemView resolved = warning(true, "调度员", "2026-07-13 10:34:00");
        when(incidentMapper.countSchemaTables()).thenReturn(2);
        when(riskWarningService.handleWarning(42L, "调度员", "已联系现场", "2026-07-13 10:30:00.820"))
                .thenReturn(true);
        when(riskWarningService.getWarningDetail(42L, "2026-07-13 10:30:00.820"))
                .thenReturn(open, resolved);
        when(incidentMapper.getIncidentState(42L, "2026-07-13 10:30:00.820"))
                .thenReturn(null);

        CommandCenterActionResultView result = incidentService.resolveIncident(
                42L, "2026-07-13 10:30:00.820", "调度员", "已联系现场");
        CommandCenterIncidentView incident = result.incident();

        verify(riskWarningService).handleWarning(42L, "调度员", "已联系现场", "2026-07-13 10:30:00.820");
        verify(incidentMapper).updateIncidentStatus(42L, "2026-07-13 10:30:00.820", "RESOLVED");
        assertEquals("RECORDED", result.status());
        assertEquals("RESOLVED", incident.status());
        assertEquals("调度员", incident.owner().name());
        assertTrue(incident.availableActions().isEmpty());
    }

    @Test
    void rejectsBareWarningIdBecauseMonthlyTablesCanReuseIds() {
        assertThrows(RuntimeException.class, () -> incidentService.getIncident(42L, null));
    }

    @Test
    void assignsAnOwnerAndSlaWithoutChangingTheUnderlyingWarning() {
        RiskWarningItemView open = warning(false, null, null);
        when(incidentMapper.countSchemaTables()).thenReturn(2);
        when(riskWarningService.getWarningDetail(42L, "2026-07-13 10:30:00.820"))
                .thenReturn(open);
        when(incidentMapper.getIncidentState(42L, "2026-07-13 10:30:00.820"))
                .thenReturn(null);

        incidentService.assignIncident(
                42L, "2026-07-13 10:30:00.820", 8L, "值班员", null, 30, "管理员", "班组跟进");

        verify(incidentMapper).assignIncident(
                eq(42L),
                eq("2026-07-13 10:30:00.820"),
                eq(8L),
                eq("值班员"),
                eq(null),
                anyString(),
                eq(30),
                eq("ACKED"));
    }

    private RiskWarningItemView warning(boolean handled, String handleBy, String handleTime) {
        return new RiskWarningItemView(
                42L,
                "张三",
                "EMP1001",
                "综采队",
                1,
                32,
                "SOS 求救",
                "高危",
                "120",
                "heartRate",
                "DEVICE_ALARM",
                "SOS",
                "359456780000001",
                null,
                handled,
                "2026-07-13 10:30:00.820",
                handleBy,
                handleTime,
                handled ? "已联系现场" : null
        );
    }
}
