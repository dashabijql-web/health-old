package com.xzkj.health.dto.commandcenter;

public record CommandCenterDashboardSummaryView(
        WarningSummary warning,
        AdmissionSummary admission,
        DeviceSummary device,
        String dataAsOf
) {
    public record WarningSummary(
            int todayNew,
            int periodNew,
            String period,
            int criticalPending,
            int pendingTotal,
            int unassignedTotal,
            int overdueTotal
    ) {
    }

    public record AdmissionSummary(
            int passed,
            int prohibited,
            CapabilityValue awaitingReview,
            CapabilityValue retestOverdue
    ) {
    }

    public record DeviceSummary(
            int total,
            int online,
            int offline,
            int onlineRate,
            CapabilityValue lowBattery,
            CapabilityValue dataInterrupted,
            CapabilityValue faulted
    ) {
    }

    public record CapabilityValue(String status, Integer value, String message) {
        public static CapabilityValue unavailable(String message) {
            return new CapabilityValue("UNAVAILABLE", null, message);
        }

        public static CapabilityValue available(int value, String message) {
            return new CapabilityValue("AVAILABLE", value, message);
        }
    }
}
