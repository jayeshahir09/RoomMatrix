package com.roommatrix.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request BEFORE Spring's UsernamePasswordAuthenticationFilter.
 *
 * Flow:
 *  1. Read "Authorization: Bearer <token>" header
 *  2. Extract username from JWT
 *  3. Load user from DB
 *  4. Validate token (signature, expiry, type=access)
 *  5. Set authentication in SecurityContext so Spring knows the user is logged in
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Skip /api/auth/** endpoints (login, register, refresh — no token needed)
        final String requestPath = request.getServletPath();
        if (requestPath.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Read the Authorization header
        final String authHeader = request.getHeader("Authorization");

        // 3. If header is missing or not a Bearer token, skip authentication
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. Extract the JWT (strip "Bearer " prefix)
        final String jwt = authHeader.substring(7);

        try {
            final String userEmail = jwtService.extractUsername(jwt);

            // 5. Only authenticate if username found and no existing authentication in context
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // 6. Validate token: check signature, expiry, AND that it's an ACCESS token
                //    (block refresh tokens from being used as access tokens)
                if (jwtService.isTokenValid(jwt, userDetails) && jwtService.isAccessToken(jwt)) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 7. Set authenticated user in Spring Security context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Invalid JWT, expired token, or user not found in DB
            // Clear context and let Spring Security return 401
            SecurityContextHolder.clearContext();
        }

        // 8. Always continue the request down the filter chain
        filterChain.doFilter(request, response);
    }
}
