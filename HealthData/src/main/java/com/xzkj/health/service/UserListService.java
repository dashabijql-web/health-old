package com.xzkj.health.service;

import com.xzkj.health.mapper.UserListMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 用户列表Service
 */
@Slf4j
@Service
public class UserListService {

    @Autowired
    private UserListMapper userListMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 获取用户列表 */
    public Map<String, Object> getUserList(String keyword, Integer status, Long deptId, int page, int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = userListMapper.getUserList(keyword, status, deptId, offset, size);
        int total = userListMapper.countUsers(keyword, status, deptId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** 获取用户详细信息（含角色ID列表） */
    public Map<String, Object> getUserDetail(Long id) {
        Map<String, Object> userDetail = userListMapper.getUserDetail(id);
        if (userDetail != null) {
            List<Long> roleIds = userListMapper.getUserRoleIds(id);
            userDetail.put("roleIds", roleIds);
        }
        return userDetail;
    }

    /** 获取用户统计数据 */
    public Map<String, Object> getUserStats() {
        return userListMapper.getUserStats();
    }

    /** 获取用户健康统计 */
    public Map<String, Object> getUserHealthStats(String userCode) {
        Map<String, Object> stats = userListMapper.getUserHealthStats(userCode);
        return stats != null ? stats : new HashMap<>();
    }

    /** 新增用户 */
    @Transactional(rollbackFor = Exception.class)
    public void createUser(Map<String, Object> params) {
        // 校验必填
        String realName = getString(params, "realName");
        String phone    = getString(params, "phone");
        String password = getString(params, "password");
        if (realName.isEmpty()) throw new IllegalArgumentException("姓名不能为空");
        if (phone.isEmpty())    throw new IllegalArgumentException("手机号不能为空");
        if (password.isEmpty()) throw new IllegalArgumentException("密码不能为空");

        // 以手机号作为用户名（唯一）
        if (userListMapper.countByUsername(phone) > 0) {
            throw new IllegalArgumentException("手机号已被注册");
        }

        Map<String, Object> insertParams = new HashMap<>();
        insertParams.put("username",  phone);
        insertParams.put("password",  passwordEncoder.encode(password));
        insertParams.put("realName",  realName);
        insertParams.put("nickname",  getString(params, "nickname"));
        insertParams.put("phone",     phone);
        insertParams.put("email",     getString(params, "email"));
        insertParams.put("gender",    getInt(params, "gender", 0));
        insertParams.put("deptId",    getLong(params, "deptId"));
        insertParams.put("status",    getInt(params, "status", 0));
        insertParams.put("remark",    getString(params, "remark"));
        insertParams.put("createBy",  "admin");

        userListMapper.insertUser(insertParams);

        // 分配角色
        Long userId = toLong(insertParams.get("id"));
        if (userId != null) {
            assignRoles(userId, params);
        }
    }

    /** 编辑用户 */
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Map<String, Object> params) {
        Long id       = toLong(params.get("id"));
        String phone  = getString(params, "phone");
        String realName = getString(params, "realName");
        if (realName.isEmpty()) throw new IllegalArgumentException("姓名不能为空");

        // 校验手机号唯一性（排除自身）
        if (!phone.isEmpty() && userListMapper.countByPhone(phone, id) > 0) {
            throw new IllegalArgumentException("手机号已被其他用户使用");
        }

        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("id",       id);
        updateParams.put("realName", realName);
        updateParams.put("nickname", getString(params, "nickname"));
        updateParams.put("phone",    phone);
        updateParams.put("email",    getString(params, "email"));
        updateParams.put("gender",   getInt(params, "gender", 0));
        updateParams.put("deptId",   getLong(params, "deptId"));
        updateParams.put("status",   getInt(params, "status", 0));
        updateParams.put("remark",   getString(params, "remark"));
        updateParams.put("updateBy", "admin");

        // 有新密码则加密，否则传空字符串（Mapper动态SQL会跳过）
        String password = getString(params, "password");
        updateParams.put("password", password.isEmpty() ? "" : passwordEncoder.encode(password));

        userListMapper.updateUser(updateParams);

        // 重新分配角色
        userListMapper.deleteUserRoles(id);
        assignRoles(id, params);
    }

    /** 删除用户 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        if (userListMapper.getUserDetail(id) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        userListMapper.deleteUserRoles(id);
        userListMapper.deleteUser(id);
    }

    /** 修改用户状态 */
    public void updateUserStatus(Long id, Integer status) {
        userListMapper.updateUserStatus(id, status);
    }

    // ── 私有工具方法 ──

    private void assignRoles(Long userId, Map<String, Object> params) {
        Object roleIdsObj = params.get("roleIds");
        if (roleIdsObj instanceof List) {
            for (Object roleId : (List<?>) roleIdsObj) {
                if (roleId != null) {
                    userListMapper.insertUserRole(userId, toLong(roleId));
                }
            }
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v == null) return defaultVal;
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return defaultVal; }
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    /**
     * 搜索用户（用于自动补全）
     * 返回轻量级用户信息：id, realName, deptName
     */
    public List<Map<String, Object>> searchUsers(String query) {
        return userListMapper.searchUsers(query);
    }
}
