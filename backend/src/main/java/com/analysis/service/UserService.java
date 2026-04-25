package com.analysis.service;

import com.analysis.model.dto.JwtResponse;
import com.analysis.model.dto.LoginRequest;
import com.analysis.model.dto.RegisterRequest;
import com.analysis.model.entity.User;
import com.analysis.model.enums.UserRole;
import com.analysis.repository.UserRepository;
import com.analysis.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        User userDetails = (User) authentication.getPrincipal();

        return new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                userDetails.getRole().name());
    }

    public void registerUser(RegisterRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            throw new com.analysis.exception.BusinessException("用户名已存在!");
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new com.analysis.exception.BusinessException("邮箱已被使用!");
        }

        if (!signUpRequest.getPassword().equals(signUpRequest.getConfirmPassword())) {
            throw new com.analysis.exception.BusinessException("两次输入的密码不一致!");
        }

        // Create new user's account
        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        user.setRole(UserRole.USER);

        userRepository.save(user);
    }
}