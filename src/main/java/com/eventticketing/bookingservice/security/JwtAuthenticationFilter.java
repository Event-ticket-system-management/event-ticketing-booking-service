package com.eventticketing.bookingservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
           String authHeader = request.getHeader("Authorization");
           String jwtToken;

           if (authHeader != null && authHeader.startsWith("Bearer ")){
               jwtToken = authHeader.substring(7);

               if (jwtProvider.validationToken(jwtToken)
               && SecurityContextHolder.getContext().getAuthentication() == null) {
                  String username = jwtProvider.extractUsername(jwtToken);
                  List<String> roles =  jwtProvider.extractRoles(jwtToken);

                  List<SimpleGrantedAuthority> authorities = roles.stream()
                          .map(SimpleGrantedAuthority::new)
                          .toList();

                   UsernamePasswordAuthenticationToken authentication =
                           new UsernamePasswordAuthenticationToken(
                                   username,
                                   null,
                                   authorities
                           );

                   authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                   SecurityContextHolder.getContext()
                           .setAuthentication(authentication);


                   log.debug(
                           "Successfully authenticated user: {} with roles: {}",
                           username,
                           roles
                   );
               }
           }


        }catch (Exception e){
            log.error(
                    "Cannot set user authentication in SecurityContext: {}",
                    e.getMessage()
            );
        }

        filterChain.doFilter(request,response);

    }
}
