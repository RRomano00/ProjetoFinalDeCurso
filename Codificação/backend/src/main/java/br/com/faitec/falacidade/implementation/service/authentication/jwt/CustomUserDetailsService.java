package br.com.faitec.falacidade.implementation.service.authentication.jwt;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Profile("jwt")
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    public CustomUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        final UserModel userModel = userService.findByEmail(email);

        if (userModel == null) {
            throw new UsernameNotFoundException("E-mail não encontrado: " + email);
        }

        // BUGFIX: estava "Role_ " (com espaço). Corrigido para "ROLE_" (padrão Spring Security)
        List<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + userModel.getRole().name())
        );

        return new User(userModel.getEmail(), userModel.getPassword(), authorities);
    }
}
