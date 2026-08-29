// this class is required because we are not using the spring default tables as we make our own tables.
//
// we already have the predefined UserDetailsService Interface class so here  we just write its implementation
// this class will handle how to find the user from Db when Someone Login by entering the username and password

package com.example.AuthService.service;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepo;


    @Override
    public UserDetails loadUserByUsername(String email) {
        System.out.println("loadUserByUsername called with: " + email);
        User user = userRepo.findByEmail(email);
        System.out.println("user from DB: " + user);
        if (user == null) throw new UsernameNotFoundException("User of this Email: "+ email+ " is  Not found");
        return new CustomUserDetails(user);
    }
}
