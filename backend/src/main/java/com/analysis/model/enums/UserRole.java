package com.analysis.model.enums;

/**
 * 用户角色枚举
 */
public enum UserRole {
    /**
     * 管理员
     */
    ADMIN("ADMIN", "管理员"),
    
    /**
     * 普通用户
     */
    USER("USER", "普通用户"),
    
    /**
     * 访客（只读权限）
     */
    GUEST("GUEST", "访客");

    private final String code;
    private final String description;

    UserRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取角色枚举
     */
    public static UserRole fromCode(String code) {
        for (UserRole role : UserRole.values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的角色代码: " + code);
    }
}