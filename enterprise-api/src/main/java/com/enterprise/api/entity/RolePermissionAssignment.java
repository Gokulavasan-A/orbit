package com.enterprise.api.entity;

/** Second result set of {@code usp_Role_GetById}: one assigned permission. */
public class RolePermissionAssignment {

    private Long permissionId;
    private String permissionCode;

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long permissionId) {
        this.permissionId = permissionId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }
}
