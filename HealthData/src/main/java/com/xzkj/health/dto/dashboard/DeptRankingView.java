package com.xzkj.health.dto.dashboard;

public record DeptRankingView(
        int rank,
        String department,
        int memberCount,
        int healthScore
) {
}
