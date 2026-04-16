package com.vishal.sp_III.service;

import com.vishal.sp_III.entity.Role;
import com.vishal.sp_III.entity.User;
import com.vishal.sp_III.repository.RoleRepository;
import com.vishal.sp_III.repository.UserRepository;
import com.vishal.sp_III.utils.JWTUtils;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.javassist.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JWTUtils jwt;
    private final PasswordEncoder passwordEncoder;

    public String register(User user) {

        try{
            if(userRepo.existsByUsername(user.getUsername())){
                throw new NotFoundException("User already registered");
            }
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            User saveUser = userRepo.save(user);
        }
        catch (NotFoundException e){
            e.printStackTrace();
        }
        return "Registered";
    }

    public String login(String username, String password) {

        try{
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            var user = userRepo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not registered!!!"));

            var token = jwt.generateToken(user);
            return token;
        }
        catch (Exception e){
            throw new UsernameNotFoundException("User not found");
        }
    }
}
