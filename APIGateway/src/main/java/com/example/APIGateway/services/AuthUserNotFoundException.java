package com.example.APIGateway.services;

/**
 * AuthService said clearly: "this user does not exist" (a 404).
 *
 * This is NOT a failure - AuthService is working fine and telling us
 * something real. We keep it separate from real failures so it can never
 * be mistaken for "AuthService is down". If it were, Tier 3 would skip the
 * revocation check and let a deleted user's old token straight through.
 *
 * Rule: a clear "no" from a healthy service is not the same as silence
 * from a broken one. Handle them differently.
 */
public class AuthUserNotFoundException extends RuntimeException {

    public AuthUserNotFoundException(String userid) {
        super("AuthService reports no such user: " + userid);
    }
}
