 package com.example.onlinegame.controller;

 import com.example.onlinegame.dto.request.AuthRequest;
 import com.example.onlinegame.dto.response.AuthResponse;
 import lombok.RequiredArgsConstructor;
 import com.example.onlinegame.model.User;
 import org.springframework.http.HttpStatus;
 import org.springframework.http.ResponseEntity;
 import org.springframework.security.authentication.AuthenticationManager;
 import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
 import org.springframework.security.core.Authentication;
 import org.springframework.security.core.AuthenticationException;
 import org.springframework.security.crypto.password.PasswordEncoder;
 import org.springframework.web.bind.annotation.PostMapping;
 import org.springframework.web.bind.annotation.RequestBody;
 import org.springframework.web.bind.annotation.RequestMapping;
 import org.springframework.web.bind.annotation.RestController;
 import com.example.onlinegame.repo.UserRepository;
 import com.example.onlinegame.security.JwtUtil;

 import java.util.Optional;


 @RestController
 @RequestMapping("/api/auth")
 @RequiredArgsConstructor
 public class AuthController {

     private final AuthenticationManager authenticationManager;
     private final JwtUtil jwtUtil;
     private final UserRepository userRepository;
     private final PasswordEncoder passwordEncoder;

     @PostMapping("/login")
     public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest authRequest) {
         // Ищем пользователя в базе данных по имени
         Optional<User> userOptional = userRepository.findByUsername(authRequest.getUsername());

         // Проверяем, существует ли пользователь
         if (userOptional.isEmpty()) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("User not found"));
         }

         User user = userOptional.get();

         // Проверяем, совпадает ли пароль
         if (!passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("Invalid password"));
         }

         // Генерация JWT токена
         String jwt = jwtUtil.generateToken(authRequest.getUsername());
         return ResponseEntity.ok(new AuthResponse(jwt));
     }

     @PostMapping("/register")
     public ResponseEntity<String> register(@RequestBody AuthRequest authRequest) {
         // Проверяем, существует ли пользователь с таким именем
         if (userRepository.findByUsername(authRequest.getUsername()).isPresent()) {
             return ResponseEntity.badRequest().body("Username already exists");
         }

         // Создаем нового пользователя
         User user = new User();
         user.setUsername(authRequest.getUsername());
         user.setPassword(passwordEncoder.encode(authRequest.getPassword()));

         // Сохраняем пользователя в базе данных
         userRepository.save(user);

         return ResponseEntity.ok("User registered successfully");
     }
 }