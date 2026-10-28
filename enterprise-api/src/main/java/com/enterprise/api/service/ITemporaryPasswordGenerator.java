package com.enterprise.api.service;

public interface ITemporaryPasswordGenerator {

    /**
     * A random temporary password that satisfies {@code app.security.password-policy} for
     * {@code username}. The caller stores only its hash and returns it to the administrator once.
     */
    String generate(String username);
}
