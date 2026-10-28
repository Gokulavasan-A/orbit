package com.enterprise.api.entity;

import java.time.Instant;
import java.util.List;

/**
 * Result of {@code usp_Auth_GetUserForLogin}: the user row (incl. the password hash),
 * the names of the user's active roles and the codes of the effective permissions.
 * The hash never leaves the service layer and is never logged.
 */
public class LoginUser {

    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private boolean active;
    private int failedLoginCount;
    private Instant lockoutUntil;
    private boolean lockedOut;
    private boolean mustChangePassword;
    private Instant lastLoginOn;
    private List<String> roles = List.of();
    private List<String> permissions = List.of();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public Instant getLockoutUntil() {
        return lockoutUntil;
    }

    public void setLockoutUntil(Instant lockoutUntil) {
        this.lockoutUntil = lockoutUntil;
    }

    public boolean isLockedOut() {
        return lockedOut;
    }

    public void setLockedOut(boolean lockedOut) {
        this.lockedOut = lockedOut;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Instant getLastLoginOn() {
        return lastLoginOn;
    }

    public void setLastLoginOn(Instant lastLoginOn) {
        this.lastLoginOn = lastLoginOn;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /** Never prints the password hash. */
    @Override
    public String toString() {
        return "LoginUser[id=" + id + ", active=" + active + ", lockedOut=" + lockedOut + "]";
    }
}
