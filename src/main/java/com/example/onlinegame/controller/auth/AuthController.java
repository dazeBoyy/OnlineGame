 package com.example.onlinegame.controller.auth;

 import com.example.onlinegame.dto.UserDto;
 import com.example.onlinegame.dto.request.AuthRequest;
 import com.example.onlinegame.dto.request.RefreshTokenRequest;
 import com.example.onlinegame.dto.response.AuthResponse;
 import com.example.onlinegame.model.user.RefreshToken;
 import com.example.onlinegame.model.user.Role;
 import com.example.onlinegame.repo.user.RoleRepository;
 import com.example.onlinegame.security.UserPrincipal;
 import com.example.onlinegame.service.user.RefreshTokenService;
 import jakarta.servlet.http.HttpServletRequest;
 import jakarta.servlet.http.HttpServletResponse;
 import lombok.RequiredArgsConstructor;
 import com.example.onlinegame.model.user.User;
 import lombok.extern.slf4j.Slf4j;
 import org.springframework.beans.factory.annotation.Value;
 import org.springframework.http.HttpStatus;
 import org.springframework.http.ResponseCookie;
 import org.springframework.http.ResponseEntity;
 import org.springframework.security.authentication.AuthenticationManager;
 import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
 import org.springframework.security.core.Authentication;
 import org.springframework.security.core.AuthenticationException;
 import org.springframework.security.core.GrantedAuthority;
 import org.springframework.security.crypto.password.PasswordEncoder;
 import org.springframework.web.bind.annotation.*;
 import com.example.onlinegame.repo.user.UserRepository;
 import com.example.onlinegame.security.JwtUtil;

 import java.util.List;
 import java.util.Optional;
 import java.util.stream.Collectors;


 @RestController
 @RequestMapping("/api/auth")
 @RequiredArgsConstructor
 @Slf4j
 public class AuthController {

     private final AuthenticationManager authenticationManager;
     private final JwtUtil jwtUtil;
     private final UserRepository userRepository;
     private final RoleRepository roleRepository;
     private final PasswordEncoder passwordEncoder;
     private final RefreshTokenService refreshTokenService;

     @Value("${jwt.expiration}")
     private long jwtExpiration;
     @Value("${jwt.refreshExpiration}")
     private long refreshExpiration;

     @PostMapping("/login")
     public ResponseEntity<?> login(@RequestBody AuthRequest authRequest,
                                    HttpServletResponse response) {
         log.info("Login attempt for username='{}'", authRequest.getUsername());

         // 1. Аутентификация и получение нашего UserPrincipal
         Authentication auth = authenticationManager.authenticate(
                 new UsernamePasswordAuthenticationToken(
                         authRequest.getUsername(),
                         authRequest.getPassword()
                 )
         );
         UserPrincipal user = (UserPrincipal) auth.getPrincipal();
         log.info("Authentication successful for user id={}", user.getUserId());


         List<String> roles = auth.getAuthorities().stream()
                 .map(GrantedAuthority::getAuthority)
                 .toList();

         // 2. Генерируем access-token
         String accessToken = jwtUtil.generateToken(user, roles);

         // 3. Обрабатываем refresh-token в БД
         String refreshToken;
         var existing = refreshTokenService.findActiveRefreshTokenByUser(user.getUserId());
         if (existing.isPresent()) {
             RefreshToken rt = existing.get();
             if (!refreshTokenService.validateRefreshToken(rt.getToken())) {
                 log.warn("Existing refresh token expired for user id={}. Deactivating.", user.getUserId());
                 refreshTokenService.deactivateRefreshToken(rt.getToken());
                 refreshToken = refreshTokenService.createRefreshToken(user.getUserId(), refreshExpiration / 1000);
                 log.info("Created new refresh token for user id={}", user.getUserId());
             } else {
                 refreshToken = rt.getToken();
                 log.info("Reusing existing valid refresh token for user id={}", user.getUserId());
             }
         } else {
             refreshToken = refreshTokenService.createRefreshToken(user.getUserId(), refreshExpiration / 1000);
             log.info("Created first-time refresh token for user id={}", user.getUserId());
         }

         // 4. Формируем куки
         ResponseCookie accessCookie = ResponseCookie.from("access_token", accessToken)
                 .httpOnly(true)
                 .secure(true)
                 .path("/")
                 .maxAge(jwtExpiration / 1000)
                 .sameSite("Strict")
                 .build();

         ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                 .httpOnly(true)
                 .secure(true)
                 .path("/api/auth/refresh")
                 .maxAge(refreshExpiration / 1000)
                 .sameSite("Strict")
                 .build();

         // 5. Добавляем куки в ответ
         response.addHeader("Set-Cookie", accessCookie.toString());
         response.addHeader("Set-Cookie", refreshCookie.toString());
         log.info("Set access_token and refresh_token cookies for user id={}", user.getUserId());

         // 6. Возвращаем DTO пользователя
         UserDto dto = new UserDto(user.getUserId(),
                 user.getUsername(),
                 roles,
                 user.getEmail(),
                 user.getWins(),
                 user.getLosses());
         return ResponseEntity.ok(new AuthResponse(dto));
     }


     @PostMapping("/register")
     public ResponseEntity<?> register(@RequestBody AuthRequest authRequest,
                                       HttpServletRequest request) {
         if (userRepository.findByUsername(authRequest.getUsername()).isPresent()) {
             return ResponseEntity.badRequest().body("Username already exists");
         }

         String ip = request.getRemoteAddr();

         User user = new User();
         user.setUsername(authRequest.getUsername());
         user.setPassword(passwordEncoder.encode(authRequest.getPassword()));
         user.setRegistrationIp(ip);
         user.setWins(0);
         user.setLosses(0);
         Role role = roleRepository.findByName("ROLE_USER")
                 .orElseThrow(() -> new RuntimeException("Role not found"));
         user.getRoles().add(role);
         userRepository.save(user);
         log.info("Registered new user id={} from IP={}", user.getId(), ip);
         return ResponseEntity.ok("User registered");
     }

     @PostMapping("/refresh")
     public ResponseEntity<?> refreshToken(@CookieValue(name = "refresh_token", required = false) String token,
                                           HttpServletResponse response) {
         if (token == null) {
             log.warn("Refresh attempt failed: no refresh_token cookie present");
             return ResponseEntity
                     .status(401)
                     .body("Refresh token is missing. Please log in again.");
         }

         if (!refreshTokenService.validateRefreshToken(token)) {
             log.warn("Refresh attempt failed: token {} is invalid or expired", token);
             return ResponseEntity
                     .status(401)
                     .body("Refresh token is invalid or expired. Please authenticate again.");
         }
         RefreshToken dbToken = refreshTokenService.getRefreshToken(token);
         User user = dbToken.getUser();
         List<String> roles = user.getRoles().stream()
                 .map(Role::getName)
                 .collect(Collectors.toList());
         String newAccess = jwtUtil.generateToken(user, roles);

         log.info("Access token refreshed for user id={}", user.getId());

         // Обновляем access_token cookie
         ResponseCookie newAccessCookie = ResponseCookie.from("access_token", newAccess)
                 .httpOnly(true).secure(true)
                 .path("/")
                 .maxAge(jwtExpiration / 1000)
                 .sameSite("Strict")
                 .build();
         response.addHeader("Set-Cookie", newAccessCookie.toString());

         UserDto dto = new UserDto(
                 user.getId(), user.getUsername(), roles, user.getEmail(), user.getWins(), user.getLosses()
         );
         return ResponseEntity.ok(new AuthResponse(dto));
     }

     @PostMapping("/logout")
     public ResponseEntity<?> logout(@CookieValue(name = "refresh_token", required = false) String token,
                                     HttpServletResponse response) {
         if (token != null) {
             refreshTokenService.deactivateRefreshToken(token);
         }
         // Удаляем куки, выставляя maxAge=0
         ResponseCookie deleteAccess = ResponseCookie.from("access_token", "")
                 .path("/").maxAge(0).build();
         ResponseCookie deleteRefresh = ResponseCookie.from("refresh_token", "")
                 .path("/api/auth/refresh").maxAge(0).build();
         response.addHeader("Set-Cookie", deleteAccess.toString());
         response.addHeader("Set-Cookie", deleteRefresh.toString());

         return ResponseEntity.ok("Logged out");
     }
 }
