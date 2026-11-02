package com.example.AuthService.controller;

import com.example.AuthService.service.LogoutUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/logout")
public class LogoutController {

    private final LogoutUserService service;
    private final RefreshCookie refreshCookie;

    public LogoutController( LogoutUserService service, RefreshCookie refreshCookie) {
        this.service = service;
        this.refreshCookie = refreshCookie;
    }
    // Issue 5 (2026-10-28): was @RequestMapping (every method, GET too). A GET must never change anything - a link
    // preview or a prefetch could log the user out. POST only now.
    @PostMapping("/user")
    public ResponseEntity<String> LogoutOfOwn(){
        // C1 step 1: removed System.out.println("Enter the Logout Controller"). Pure noise -
        // LogoutUserService logs the actual logout (with the user id) one step later.
        service.LogoutUser();
        // BCR-02: LogoutUser already cancelled every refresh token of the user (found by user id, so the cookie is
        // never read here). This also makes the browser delete the now useless cookie.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .body("Logout Successfully");
    }

    // Issue 5: the old LogoutAnyUser(User) method had no URL mapping, so nobody could ever call it - removed.
    // An admin logs a user out through a role change (UpdateRoleService -> LogoutUserService.LogoutUserByAdmin).

}
