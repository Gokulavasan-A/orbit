package com.enterprise.api.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code usp_User_GetById}: the user (no password hash) from the first result set,
 * and the role assignments from the second.
 */
public class UserDetail {

    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private boolean active;
    private boolean lockedOut;
    private Instant lastLoginOn;
    private boolean mustChangePassword;
    private Instant passwordChangedOn;
    private Instant createdOn;
    private Instant modifiedOn;
    /** Optimistic concurrency for administrative edits; logins don't change it. */
    private UUID concurrencyStamp;
    private List<UserRoleAssignment> roles = List.of();

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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isLockedOut() {
        return lockedOut;
    }

    public void setLockedOut(boolean lockedOut) {
        this.lockedOut = lockedOut;
    }

    public Instant getLastLoginOn() {
        return lastLoginOn;
    }

    public void setLastLoginOn(Instant lastLoginOn) {
        this.lastLoginOn = lastLoginOn;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Instant getPasswordChangedOn() {
        return passwordChangedOn;
    }

    public void setPasswordChangedOn(Instant passwordChangedOn) {
        this.passwordChangedOn = passwordChangedOn;
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

    public UUID getConcurrencyStamp() {
        return concurrencyStamp;
    }

    public void setConcurrencyStamp(UUID concurrencyStamp) {
        this.concurrencyStamp = concurrencyStamp;
    }

    public List<UserRoleAssignment> getRoles() {
        return roles;
    }

    public void setRoles(List<UserRoleAssignment> roles) {
        this.roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
