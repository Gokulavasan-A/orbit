package com.enterprise.api.entity;

import java.time.Instant;
import java.util.UUID;

/** Row of {@code usp_User_GetPaged} (without the TotalCount column). */
public class UserListItem {

    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private boolean active;
    private boolean lockedOut;
    private Instant lastLoginOn;
    private Instant createdOn;
    private Instant modifiedOn;
    /** Comma-separated role names, ordered by name. */
    private String roles;
    private UUID concurrencyStamp;

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

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public UUID getConcurrencyStamp() {
        return concurrencyStamp;
    }

    public void setConcurrencyStamp(UUID concurrencyStamp) {
        this.concurrencyStamp = concurrencyStamp;
    }
}
