 package com.example.onlinegame.controller.auth;

 import com.example.onlinegame.dto.UserDto;
 import com.example.onlinegame.dto.request.AuthRequest;
 import com.example.onlinegame.dto.request.RefreshTokenRequest;
 import com.example.onlinegame.dto.response.AuthResponse;
 import com.example.onlinegame.model.user.RefreshToken;
 import com.example.onlinegame.model.user.Role;
 import com.example.onlinegame.repo.user.RoleRepository;
 import com.example.onlinegame.security.RefreshTokenService;
 import lombok.RequiredArgsConstructor;
 import com.example.onlinegame.model.user.User;
 import org.springframework.beans.factory.annotation.Value;
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
 import java.util.Optional;
 import java.util.stream.Collectors;


 @RestController
 @RequestMapping("/api/auth")
 @RequiredArgsConstructor
 public class AuthController {

     private final AuthenticationManager authenticationManager;
     private final JwtUtil jwtUtil;
     private final UserRepository userRepository;
     private final RoleRepository roleRepository;
     private final PasswordEncoder passwordEncoder;
     private final RefreshTokenService refreshTokenService;

     @Value("${jwt.refreshExpiration}")
     private long refreshExpiration;

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

             // Получаем пользователя
             User user = userRepository.findByUsername(authRequest.getUsername())
                     .orElseThrow(() -> new RuntimeException("User not found"));

             // Получаем роли пользователя
             List<String> roles = authentication.getAuthorities().stream()
                     .map(GrantedAuthority::getAuthority)
                     .collect(Collectors.toList());

             // Проверяем наличие активного refreshToken
             Optional<RefreshToken> existingToken = refreshTokenService.findActiveRefreshTokenByUser(user);

             String refreshToken;
             if (existingToken.isPresent()) {
                 // Если есть активный refreshToken, используем его
                 refreshToken = existingToken.get().getToken();
             } else {
                 // Если активного токена нет, создаем новый
                 refreshToken = refreshTokenService.createRefreshToken(user.getId(), refreshExpiration); // 7 дней
             }

             // Генерация JWT accessToken
             String jwt = jwtUtil.generateToken(user, roles);

             // Создаем DTO пользователя
             UserDto userDto = new UserDto(
                     user.getId(),
                     user.getUsername(),
                     roles,
                     user.getWins(),
                     user.getLosses()
             );

             // Возвращаем токены и данные пользователя
             return ResponseEntity.ok(new AuthResponse(jwt, refreshToken, userDto));
         } catch (AuthenticationException e) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                     .body(new AuthResponse(null, null, null));
         }
     }

     @PostMapping("/register")
     public ResponseEntity<?> register(@RequestBody AuthRequest authRequest) {
         // Проверяем, существует ли пользователь с таким именем
         if (userRepository.findByUsername(authRequest.getUsername()).isPresent()) {
             return ResponseEntity.badRequest().body("Username already exists");
         }

         // Создаем нового пользователя
         User user = new User();
         user.setUsername(authRequest.getUsername());
         user.setPassword(passwordEncoder.encode(authRequest.getPassword()));
         user.setWins(0);
         user.setLosses(0);

         // Назначаем роль ROLE_USER по умолчанию
         Role userRole = roleRepository.findByName("ROLE_USER")
                 .orElseThrow(() -> new RuntimeException("Role ROLE_USER not found"));
         user.getRoles().add(userRole);

         // Сохраняем пользователя
         userRepository.save(user);

         return ResponseEntity.ok("Пользователь успешно зарегистрирован!");
     }

     @PostMapping("/refresh")
     public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
         String refreshToken = request.getRefreshToken();

         // Проверка валидности refreshToken через RefreshTokenService
         if (!refreshTokenService.validateRefreshToken(refreshToken)) {
             return ResponseEntity.status(401).body("Invalid or expired refresh token");
         }

         // Получение токена и пользователя из базы данных
         RefreshToken token = refreshTokenService.getRefreshToken(refreshToken);
         User user = token.getUser(); // Связь ManyToOne позволяет получить пользователя напрямую

         // Генерация нового accessToken
         List<String> roles = user.getRoles().stream()
                 .map(Role::getName)
                 .collect(Collectors.toList());
         String newAccessToken = jwtUtil.generateToken(user, roles);

         // Создание UserDto
         UserDto userDto = new UserDto(
                 user.getId(),
                 user.getUsername(),
                 roles,
                 user.getWins(),
                 user.getLosses()
         );

         return ResponseEntity.ok(new AuthResponse(newAccessToken, refreshToken, userDto));
     }

     @PostMapping("/logout")
     public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request) {
         String refreshToken = request.getRefreshToken();

         // Деактивация токена
         refreshTokenService.deactivateRefreshToken(refreshToken);

         return ResponseEntity.ok("Logged out successfully");
     }
 }