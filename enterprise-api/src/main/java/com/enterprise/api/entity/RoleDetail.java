package com.enterprise.api.entity;

import java.time.Instant;
import java.util.List;

/**
 * {@code usp_Role_GetById}: the role from the first result set and its assigned
 * permissions from the second.
 */
public class RoleDetail {

    private Long id;
    private String name;
    private String description;
    private boolean system;
    private boolean active;
    private int userCount;
    private Instant createdOn;
    private Instant modifiedOn;
    private byte[] rowVersion;
    private List<RolePermissionAssignment> permissions = List.of();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getUserCount() {
        return userCount;
    }

    public void setUserCount(int userCount) {
        this.userCount = userCount;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Instant createdOn) {
        this.createdOn = createdOn;
    }

    public Instant getModifiedOn() {
        return modifiedOn;
    }

    public void setModifiedOn(Instant modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    public byte[] getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(byte[] rowVersion) {
        this.rowVersion = rowVersion;
    }

    public List<RolePermissionAssignment> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<RolePermissionAssignment> permissions) {
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
