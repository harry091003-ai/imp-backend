// UserDetails class is a predefined interface class here we just write its implementation
// Spring Security does not understand the User Entity. it only understand UserDetails Objects.
//so we create the bridge that converts the User Into UserDetails.
// Here  spring security reads the user data (like we tell Spring Security what “username” means (email? userId?),tell Spring Security where the encrypted password is stored)
// Here  spring security reads the user roles
//
//without this class Login will never work using your DB.
package com.example.AuthService.security;

import com.example.AuthService.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserDetails implements UserDetails {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetails.class);

    private User user;

    //    creating user bean using constructor
    public CustomUserDetails(User TheUser){
        user=TheUser;
    }
    //    user information that our UserDetails Object needs
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getEnabled();
    }


//    User role information that our UserDetails Object needs
//    Here we converts the role information in the formate that is
//    understandable by the UserDetails Object.

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<? extends GrantedAuthority> authorities =
                user.getRoles()
                        .stream()
                        .map(role -> new SimpleGrantedAuthority("" + role.getRolename()))
                        .toList();

        // C1 step 1: was 3 System.out.println lines - "Hiiiii" + the Role objects (twice, the
        // same line), then the authorities. Now one line: the role names, e.g. [ROLE_ENGINEER].
        log.debug("Granted authorities for user {}: {}", user.getUserid(), authorities);
        return authorities;
    }
}
