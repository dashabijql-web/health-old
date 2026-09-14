package com.xzkj.health.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xzkj.health.mapper.DepartmentMapper;
import com.xzkj.health.model.entity.Department;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DepartmentService {

    @Autowired
    private DepartmentMapper departmentMapper;

    public List<Department> list(String keyword) {
        QueryWrapper<Department> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.like("dept_name", keyword).or().like("dept_code", keyword);
        }
        qw.orderByAsc("sort_order");
        return departmentMapper.selectList(qw);
    }

    public List<Map<String, Object>> tree() {
        List<Department> all = departmentMapper.selectList(
            new QueryWrapper<Department>().orderByAsc("sort_order"));
        return buildTree(all, null);
    }

    private List<Map<String, Object>> buildTree(List<Department> all, Long parentId) {
        return all.stream()
            .filter(d -> {
                if (parentId == null) {
                    return d.getParentId() == null || d.getParentId() == 0L;
                }
                return Objects.equals(d.getParentId(), parentId);
            })
            .map(d -> {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", d.getId());
                node.put("deptName", d.getDeptName());
                node.put("label", d.getDeptName());
                node.put("deptCode", d.getDeptCode());
                node.put("leader", d.getLeader());
                node.put("phone", d.getPhone());
                node.put("status", d.getStatus());
                node.put("sortOrder", d.getSortOrder());
                node.put("parentId", d.getParentId());
                List<Map<String, Object>> children = buildTree(all, d.getId());
                if (!children.isEmpty()) node.put("children", children);
                return node;
            })
            .collect(Collectors.toList());
    }

    public Department getById(Long id) {
        return departmentMapper.selectById(id);
    }

    public void create(Department dept) {
        dept.setCreateTime(LocalDateTime.now());
        dept.setUpdateTime(LocalDateTime.now());
        departmentMapper.insert(dept);
    }

    public void update(Department dept) {
        dept.setUpdateTime(LocalDateTime.now());
        departmentMapper.updateById(dept);
    }

    public void delete(Long id) {
        departmentMapper.deleteById(id);
    }
}
