package com.xzkj.health.service;

import com.xzkj.health.dto.dashboard.DashboardOverviewView;
import com.xzkj.health.dto.dashboard.PersonCountsView;
import com.xzkj.health.dto.dashboard.UnifiedControlSnapshotView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedControlSnapshotServiceTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private StatisticsService statisticsService;

    @Mock
    private RealtimeService realtimeService;

    @Mock
    private CommandCenterDashboardSummaryService commandCenterDashboardSummaryService;

    @InjectMocks
    private UnifiedControlSnapshotService snapshotService;

    @Test
    void loadsDashboardSectionsThroughOneSnapshotCall() {
        DashboardOverviewView overview = new DashboardOverviewView(1, 2, 3, 4, 5, 6);
        PersonCountsView personCounts = new PersonCountsView(1, 2, 3, 4, 5, 6, 7);
        when(dashboardService.getCurrentMonthCounts("2026-09-01", "2026-09-12")).thenReturn(overview);
        when(dashboardService.getPersonCounts("2026-09-01", "2026-09-12")).thenReturn(personCounts);

        UnifiedControlSnapshotView result = snapshotService.getSnapshot(
                "2026-09-01", "2026-09-12", 30, "day", "2026-09", "month");

        assertSame(overview, result.overview());
        assertSame(personCounts, result.personCounts());
        assertEquals(7, result.personCounts().totalPersons());
        verify(statisticsService).getWarningTypeCounts("2026-09");
        verify(realtimeService).getHealthSnapshot();
        verify(commandCenterDashboardSummaryService).getSummary("month");
    }

    @Test
    void keepsOtherSectionsWhenOneSectionFails() {
        PersonCountsView personCounts = new PersonCountsView(0, 0, 0, 0, 0, 0, 9);
        when(dashboardService.getCurrentMonthCounts("2026-09-01", "2026-09-12"))
                .thenThrow(new IllegalStateException("query timed out"));
        when(dashboardService.getPersonCounts("2026-09-01", "2026-09-12")).thenReturn(personCounts);

        UnifiedControlSnapshotView result = snapshotService.getSnapshot(
                "2026-09-01", "2026-09-12", 30, "day", "invalid", "month");

        assertNull(result.overview());
        assertSame(personCounts, result.personCounts());
    }
}
