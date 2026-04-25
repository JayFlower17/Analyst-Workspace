package com.analysis.model.enums;

/**
 * 用户状态枚举
 */
public enum UserStatus {
    /**
     * 激活状态
     */
    ACTIVE("ACTIVE", "激活"),

    /**
     * 禁用状态
     */
    INACTIVE("INACTIVE", "禁用"),

    /**
     * 待激活状态
     */
    PENDING("PENDING", "待激活");

    private final String code;
    private final String description;

    UserStatus(String code, String description) {
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
     * 根据代码获取用户状态枚举
     */
    public static UserStatus fromCode(String code) {
        for (UserStatus status : UserStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的用户状态代码: " + code);
    }
}