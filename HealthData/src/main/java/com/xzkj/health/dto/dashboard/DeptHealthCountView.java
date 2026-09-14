package com.xzkj.health.dto.dashboard;

public record DeptHealthCountView(
        String name,
        int count,
        int prevCount
) {
}
