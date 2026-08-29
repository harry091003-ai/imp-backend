package com.example.AuthService.controller;

import com.example.AuthService.entity.User;
import com.example.AuthService.service.LogoutUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    @RequestMapping("/user")
    public String LogoutOfOwn(){
        System.out.println("Enter the Logout Controller: " );
        service.LogoutUser();
        return "Logout Successfully";
    }

    //by admin
    public String LogoutAnyUser(User user){
        service.LogoutUserByAdmin(user.getUserid());
        return "Logout Successfully";
    }

}
