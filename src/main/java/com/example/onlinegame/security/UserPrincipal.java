package com.example.onlinegame.security;

import com.example.onlinegame.model.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Data
@RequiredArgsConstructor
public class UserPrincipal implements UserDetails, Principal {
    private final Long userId;
    private final String username;
    private final String password;
    private final String email;
    private final Integer wins;
    private final Integer losses;
    private final LocalDateTime createdAt;
    private final String registrationIp;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.email = user.getEmail();
        this.wins = user.getWins();
        this.losses = user.getLosses();
        this.createdAt = user.getCreatedAt();
        this.registrationIp = user.getRegistrationIp();
        this.authorities = user.getRoles().stream()
                .map(r -> (GrantedAuthority) r::getName)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}