package ma.enset.security.tp_jwt.service;

import ma.enset.security.tp_jwt.entity.AppUser;
import ma.enset.security.tp_jwt.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomerUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    // Injection de dépendance via le constructeur
    public CustomerUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Récupération de l'utilisateur depuis la base de données
        AppUser utilisateur = repository
                .findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));

        // Construction de l'objet UserDetails requis par Spring Security
        return User.builder()
                .username(utilisateur.getEmail())
                .password(utilisateur.getPassword())
                .roles(utilisateur.getRole())
                .build();
    }
}