package com.xzkj.health.dto.realtime;

import lombok.Data;

@Data
public class RealtimeStatisticsRow {
    private Long onlineUsers;
    private Long totalUsers;
    private Long weekRecords;
    private Long todayRecords;
    private Double onlineRate;
    private Double normalRate;
}
