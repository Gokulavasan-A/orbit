package com.enterprise.api.service;

import com.enterprise.api.security.IssuedToken;
import com.enterprise.api.security.TokenClaims;

public interface ITokenService {

    /** Signs a new HS256 access token with the configured issuer, audience and lifetime. */
    IssuedToken issueAccessToken(TokenClaims claims);
}
