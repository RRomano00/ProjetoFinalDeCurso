package br.com.faitec.falacidade.implementation.service.authentication.jwt;

import br.com.faitec.falacidade.implementation.service.authentication.ActiveSessionStore;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Profile("jwt")
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger log = Logger.getLogger(JwtRequestFilter.class.getName());

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ActiveSessionStore activeSessions;

    public JwtRequestFilter(JwtService jwtService, UserDetailsService userDetailsService,
                            ActiveSessionStore activeSessions) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.activeSessions = activeSessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String requestTokenHeader = request.getHeader("Authorization");
        String email    = null;
        String jwtToken = null;

        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            try {
                email = jwtService.getEmailFromToken(jwtToken);
                // Sessão única: a conta foi acessada em outro aparelho e este token
                // deixou de valer. O login em si escapa da conferência — é por ele
                // que a pessoa recupera o acesso.
                if (!isLoginRequest(request)
                        && activeSessions.superseded(jwtService.getUserIdFromToken(jwtToken),
                                                     jwtService.getSessionIdFromToken(jwtToken))) {
                    log.log(Level.INFO, "Sessão substituída por novo login: {0}", email);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"reason\":\"SESSION_SUPERSEDED\"}");
                    return;
                }
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "Não foi possível obter o token JWT");
            } catch (ExpiredJwtException e) {
                log.log(Level.INFO, "Token JWT expirado para o request: {0}", request.getRequestURI());
            }
        } else {
            // Rotas públicas chegam sem token — não logar como warning para não poluir
            log.log(Level.FINE, "Request sem Bearer token: {0}", request.getRequestURI());
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(email);
            if (jwtService.validToken(jwtToken, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * O POST do login (e o do segundo fator) é justamente o caminho de volta de
     * quem foi derrubado: a tela ainda manda o token velho no cabeçalho e não
     * pode ser barrada por ele. O GET /api/authenticate/session, ao contrário, é
     * o pulso que precisa ser recusado para a tela saber que a sessão caiu.
     */
    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
            && request.getRequestURI().startsWith("/api/authenticate");
    }
}
