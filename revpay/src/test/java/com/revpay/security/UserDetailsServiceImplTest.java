package com.revpay.security;

import com.revpay.model.entity.Role;
import com.revpay.model.entity.User;
import com.revpay.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void loadUserByUsernameSuccess() {
        User user = new User();
        user.setUserId(1L);
        user.setEmail("test@revpay.com");
        user.setPasswordHash("hash");
        user.setRole(Role.PERSONAL);

        when(userRepository.findByEmail("test@revpay.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@revpay.com");

        assertEquals("test@revpay.com", userDetails.getUsername());
        assertEquals("ROLE_PERSONAL", userDetails.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void loadUserByUsernameNotFound() {
        when(userRepository.findByEmail("unknown@revpay.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            userDetailsService.loadUserByUsername("unknown@revpay.com");
        });
    }
}