package com.xzkj.health.dto.dashboard;

public record Top5UserView(
        String userName,
        String userCode,
        int count
) {
}
