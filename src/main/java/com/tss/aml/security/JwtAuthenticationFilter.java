package com.tss.aml.security;

import com.tss.aml.tenant.TenantContext;
import com.tss.aml.tenant.TenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TenantService tenantService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {

            String token = resolveToken(request);

            if (token != null && jwtTokenProvider.validateToken(token)) {

                UUID tenantId = jwtTokenProvider.getTenantId(token);
                String schemaName = tenantService.getSchemaName(tenantId);

                TenantContext.setCurrentTenant(schemaName);

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(
                                jwtTokenProvider.getUsername(token)
                        );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);

                log.debug(
                        "Authenticated request for tenant schema: {}",
                        schemaName
                );
            }

            filterChain.doFilter(request, response);

        } finally {

            TenantContext.clear();

            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {

        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {

            return bearerToken.substring(7);
        }

        return null;
    }
}