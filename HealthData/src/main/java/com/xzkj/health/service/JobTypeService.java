package com.xzkj.health.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xzkj.health.mapper.JobTypeMapper;
import com.xzkj.health.model.entity.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class JobTypeService {

    @Autowired
    private JobTypeMapper jobTypeMapper;

    public List<JobType> list(String keyword) {
        QueryWrapper<JobType> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.like("type_name", keyword).or().like("type_code", keyword);
        }
        qw.orderByAsc("id");
        return jobTypeMapper.selectList(qw);
    }

    public JobType getById(Long id) {
        return jobTypeMapper.selectById(id);
    }

    public void create(JobType jobType) {
        jobType.setCreateTime(LocalDateTime.now());
        jobType.setUpdateTime(LocalDateTime.now());
        jobTypeMapper.insert(jobType);
    }

    public void update(JobType jobType) {
        jobType.setUpdateTime(LocalDateTime.now());
        jobTypeMapper.updateById(jobType);
    }

    public void delete(Long id) {
        jobTypeMapper.deleteById(id);
    }
}
