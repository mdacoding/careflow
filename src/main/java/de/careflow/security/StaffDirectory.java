package de.careflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StaffDirectory {

    public static final List<Staff> STAFF = List.of(
            new Staff("weber", "Dr. med. Lena Weber", "PHYSICIAN", "Oberärztin Innere Medizin"),
            new Staff("hoffmann", "Tim Hoffmann", "LAB", "MTA Klinische Chemie"),
            new Staff("schmidt", "Paula Schmidt", "NURSE", "Pflegefachperson Innere 3"));

    private final Map<String, Staff> byUsername = STAFF.stream()
            .collect(Collectors.toMap(Staff::username, Function.identity()));

    public Staff require(String username) {
        Staff staff = byUsername.get(username);
        if (staff == null) {
            throw new IllegalArgumentException("Unbekannte Kennung");
        }
        return staff;
    }

    public Staff current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails user)) {
            throw new IllegalStateException("Nicht angemeldet");
        }
        return require(user.getUsername());
    }

    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails[] users = STAFF.stream()
                .map(staff -> User.withUsername(staff.username())
                        .password(encoder.encode("demo"))
                        .roles(staff.role())
                        .build())
                .toArray(UserDetails[]::new);
        return new InMemoryUserDetailsManager(users);
    }
}
