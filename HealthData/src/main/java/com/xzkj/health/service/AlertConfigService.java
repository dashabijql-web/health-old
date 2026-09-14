package com.xzkj.health.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xzkj.health.mapper.AlertConfigMapper;
import com.xzkj.health.model.entity.AlertConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlertConfigService {

    @Autowired
    private AlertConfigMapper alertConfigMapper;

    public List<AlertConfig> listAll() {
        QueryWrapper<AlertConfig> qw = new QueryWrapper<>();
        qw.orderByAsc("config_type").orderByAsc("risk_level");
        return alertConfigMapper.selectList(qw);
    }

    /**
     * 按 risk_level 返回阈值 Map（key = configType）
     * 若 riskLevel 为 null 或无对应配置，回退到默认行（risk_level IS NULL）
     */
    public Map<Integer, AlertConfig> getConfigMap(Integer riskLevel) {
        QueryWrapper<AlertConfig> qw = new QueryWrapper<>();
        if (riskLevel != null) {
            qw.eq("risk_level", riskLevel);
        } else {
            qw.isNull("risk_level");
        }
        List<AlertConfig> list = alertConfigMapper.selectList(qw);
        if (list.isEmpty() && riskLevel != null) {
            // 回退到默认
            qw = new QueryWrapper<AlertConfig>().isNull("risk_level");
            list = alertConfigMapper.selectList(qw);
        }
        return list.stream().collect(Collectors.toMap(AlertConfig::getConfigType, c -> c));
    }

    public void update(AlertConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        alertConfigMapper.updateById(config);
    }

    public boolean toggle(Long id) {
        AlertConfig config = alertConfigMapper.selectById(id);
        if (config == null) {
            log.warn("AlertConfig id={} 不存在，toggle 忽略", id);
            return false;
        }
        Integer cur = config.getEnabled();
        config.setEnabled(cur == null || cur == 0 ? 1 : 0);
        config.setUpdateTime(LocalDateTime.now());
        alertConfigMapper.updateById(config);
        return true;
    }
}
