 package com.example.onlinegame.controller.auth;

 import com.example.onlinegame.dto.request.AuthRequest;
 import com.example.onlinegame.dto.response.AuthResponse;
 import com.example.onlinegame.model.user.Role;
 import com.example.onlinegame.repo.user.RoleRepository;
 import lombok.RequiredArgsConstructor;
 import com.example.onlinegame.model.user.User;
 import org.springframework.http.HttpStatus;
 import org.springframework.http.ResponseEntity;
 import org.springframework.security.authentication.AuthenticationManager;
 import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
 import org.springframework.security.core.Authentication;
 import org.springframework.security.core.AuthenticationException;
 import org.springframework.security.core.GrantedAuthority;
 import org.springframework.security.crypto.password.PasswordEncoder;
 import org.springframework.web.bind.annotation.PostMapping;
 import org.springframework.web.bind.annotation.RequestBody;
 import org.springframework.web.bind.annotation.RequestMapping;
 import org.springframework.web.bind.annotation.RestController;
 import com.example.onlinegame.repo.user.UserRepository;
 import com.example.onlinegame.security.JwtUtil;

 import java.util.List;
 import java.util.stream.Collectors;


 @RestController
 @RequestMapping("/api/auth")
 @RequiredArgsConstructor
 public class AuthController {

     private final AuthenticationManager authenticationManager;
     private final JwtUtil jwtUtil;
     private final UserRepository userRepository;
     private final RoleRepository roleRepository; // Добавьте репозиторий для ролей
     private final PasswordEncoder passwordEncoder;

     @PostMapping("/login")
     public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest authRequest) {
         try {
             // Аутентификация пользователя
             Authentication authentication = authenticationManager.authenticate(
                     new UsernamePasswordAuthenticationToken(
                             authRequest.getUsername(),
                             authRequest.getPassword()
                     )
             );

             // Получаем роли пользователя
             List<String> roles = authentication.getAuthorities().stream()
                     .map(GrantedAuthority::getAuthority)
                     .collect(Collectors.toList());

             // Генерация JWT токена с ролями
             String jwt = jwtUtil.generateToken(authRequest.getUsername(), roles);
             return ResponseEntity.ok(new AuthResponse(jwt));
         } catch (AuthenticationException e) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("Authentication failed"));
         }
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

         // Назначаем роль ROLE_USER по умолчанию
         Role userRole = roleRepository.findByName("ROLE_USER")
                 .orElseThrow(() -> new RuntimeException("Role ROLE_USER not found"));
         user.getRoles().add(userRole);

         // Сохраняем пользователя в базе данных
         userRepository.save(user);

         return ResponseEntity.ok("User registered successfully");
     }
 }