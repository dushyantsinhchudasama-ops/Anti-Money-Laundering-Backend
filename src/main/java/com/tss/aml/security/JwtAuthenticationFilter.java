package com.tss.aml.security;

import com.tss.aml.entities.system.Tenant;
import com.tss.aml.enums.TenantStatus;
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
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path != null && path.startsWith("/auth/");
    }

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

                if (tenantId != null) {
                    try {
                        Tenant tenant = tenantService.getTenant(tenantId);
                        if (tenant != null) {
                            if (tenant.getStatus() == TenantStatus.SUSPENDED) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"error\":\"Institution suspended Please contact Admin!\",\"message\":\"Institution suspended Please contact Admin!\"}");
                                return;
                            }
                            if (tenant.getStatus() == TenantStatus.OFFBOARDED) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"error\":\"Bad credentials\",\"message\":\"Bad credentials\"}");
                                return;
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    String schemaName = tenantService.getSchemaName(tenantId);
                    if (schemaName != null) {
                        TenantContext.setCurrentTenant(schemaName);
                        log.debug(
                                "Authenticated request for tenant schema: {}",
                                schemaName
                        );
                    }
                }

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(
                                jwtTokenProvider.getUsername(token)
                        );

                if (userDetails instanceof CustomUserDetails customUser && customUser.isMustResetPassword()) {
                    String path = request.getServletPath();
                    if (path == null || !path.startsWith("/auth/")) {
                        response.setStatus(428); // 428 Precondition Required
                        response.setContentType("application/json");
                        response.getWriter().write("{\"status\":428,\"error\":\"Precondition Required\",\"message\":\"Mandatory password reset required before accessing system resources.\"}");
                        return;
                    }
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);
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