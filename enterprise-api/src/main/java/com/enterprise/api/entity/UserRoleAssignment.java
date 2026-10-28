package com.enterprise.api.entity;

/** Second result set of {@code usp_User_GetById}: one assigned (non-deleted) role. */
public class UserRoleAssignment {

    private Long roleId;
    private String roleName;
    private boolean active;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
