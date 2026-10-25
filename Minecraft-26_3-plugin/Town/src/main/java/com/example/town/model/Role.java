package com.example.town.model;

/** 小镇职位。 */
public enum Role {
    MAYOR("镇长"),
    OFFICER("官员"),
    MEMBER("成员");

    private final String display;

    Role(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /** 是否可以管理小镇（邀请、踢人、升降职、买 buff）。 */
    public boolean canManage() {
        return this == MAYOR || this == OFFICER;
    }
}
