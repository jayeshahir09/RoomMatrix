package com.roommatrix.backend.security;

import com.roommatrix.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        log.debug("Loading user by identifier: {}", identifier);
        return userRepository.findByMobileNumber(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .map(CustomUserDetails::new)
                .orElseThrow(() -> {
                    log.warn("User lookup failed for identifier: {}", identifier);
                    return new UsernameNotFoundException("User not found with mobile or email: " + identifier);
                });
    }
}
