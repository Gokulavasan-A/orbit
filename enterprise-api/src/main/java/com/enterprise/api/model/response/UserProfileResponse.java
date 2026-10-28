package com.enterprise.api.model.response;

/** The signed-in user, as shown in the UI header. */
public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName) {
}
