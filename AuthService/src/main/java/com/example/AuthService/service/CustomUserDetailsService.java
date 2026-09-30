// this class is required because we are not using the spring default tables as we make our own tables.
//
// we already have the predefined UserDetailsService Interface class so here  we just write its implementation
// this class will handle how to find the user from Db when Someone Login by entering the username and password

package com.example.AuthService.service;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private UserRepository userRepo;


    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepo.findByEmail(email);
        if (user == null) throw new UsernameNotFoundException("User of this Email: "+ email+ " is  Not found");
        // C1 step 1: was 2 System.out.println lines, on EVERY login: the email, and the whole
        // User object. Trimmed to the user id:
        //  - email is personal data
        //  - the User object holds the password hash. Today it prints only a memory address
        //    (no toString), but adding @Data or @ToString later would print the hash.
        log.debug("Login lookup found user {}", user.getUserid());
        return new CustomUserDetails(user);
    }
}
