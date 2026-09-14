package com.xzkj.health;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具测试类
 * 用于生成 BCrypt 加密后的密码
 */
public class PasswordGeneratorTest {

    @Test
    public void generatePassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 需要加密的原始密码
        String password1 = "admin123";
        String password2 = "user123";
        String password3 = "test123";

        // 生成加密后的密码
        System.out.println("========== 密码加密结果 ==========");
        System.out.println("admin123 加密后:");
        System.out.println(encoder.encode(password1));
        System.out.println();

        System.out.println("user123 加密后:");
        System.out.println(encoder.encode(password2));
        System.out.println();

        System.out.println("test123 加密后:");
        System.out.println(encoder.encode(password3));
        System.out.println("==================================");
    }

    @Test
    public void testPasswordMatch() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 原始密码
        String rawPassword = "admin123";

        // 加密后的密码（从数据库获取）
        String encodedPassword = "$2a$10$KU1YW9w22ug.h9to.YFsluXvr7gH3Y2KA9tjTOWLgmKFqokdRfgN2";

        // 验证密码是否匹配
        boolean matches = encoder.matches(rawPassword, encodedPassword);

        System.out.println("密码验证结果: " + (matches ? "匹配成功 ✓" : "匹配失败 ✗"));
    }
}
