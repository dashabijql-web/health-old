package com.xzkj.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzkj.health.model.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {

    @Select("SELECT id, dept_name, parent_id, sort_order, status, create_time, update_time FROM department ORDER BY sort_order ASC, id ASC")
    List<Map<String, Object>> getDepartmentTree();
}
