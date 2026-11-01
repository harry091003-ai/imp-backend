package com.example.AuthService.controller;

import com.example.AuthService.service.LogoutUserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/logout")
public class LogoutController {

    private final LogoutUserService service;

    public LogoutController( LogoutUserService service) {
        this.service = service;
    }
    // Issue 5 (2026-10-28): was @RequestMapping (every method, GET too). A GET must never change anything - a link
    // preview or a prefetch could log the user out. POST only now.
    @PostMapping("/user")
    public String LogoutOfOwn(){
        // C1 step 1: removed System.out.println("Enter the Logout Controller"). Pure noise -
        // LogoutUserService logs the actual logout (with the user id) one step later.
        service.LogoutUser();
        return "Logout Successfully";
    }

    // Issue 5: the old LogoutAnyUser(User) method had no URL mapping, so nobody could ever call it - removed.
    // An admin logs a user out through a role change (UpdateRoleService -> LogoutUserService.LogoutUserByAdmin).

}
