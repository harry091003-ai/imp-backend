package com.example.UserService.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 63 (2026-11-01): UserService answered with fields nothing keeps up to date. AuthService owns them and never
 * sends changes here, so every user showed enabled=false, accountLocked=false, failedLoginAttempts=0, and the
 * password date of signup. A wrong number is worse than no number, so they are not in the answer any more.
 */
class UserResponseDtoTest {

    private JsonNode answer() {
        return new ObjectMapper().valueToTree(UserResponseDTO.builder().userid(UUID.randomUUID()).username("pat").build());
    }

    @Test
    void theFieldsNobodyUpdatesAreNotInTheAnswer() {
        JsonNode json = answer();
        for (String field : List.of("enabled", "accountLocked", "failedLoginAttempts", "passwordUpdatedAt")) {
            assertFalse(json.has(field), field + " is always out of date here");
        }
    }

    @Test
    void theFieldsThatAreKeptUpToDateStay() {
        JsonNode json = answer();
        for (String field : List.of("userid", "username", "email", "status", "roles", "lastLoginAt", "supervisorId")) {
            assertTrue(json.has(field), field);
        }
    }
}
