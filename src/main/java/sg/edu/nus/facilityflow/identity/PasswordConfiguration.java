package sg.edu.nus.facilityflow.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import java.util.Map;

@Configuration
public class PasswordConfiguration {

    @Bean
    PasswordEncoder passwordEncoder(
            @Value("${facilityflow.security.password.salt-length}") int saltLength,
            @Value("${facilityflow.security.password.hash-length}") int hashLength,
            @Value("${facilityflow.security.password.parallelism}") int parallelism,
            @Value("${facilityflow.security.password.memory-kib}") int memoryKib,
            @Value("${facilityflow.security.password.iterations}") int iterations) {
        var argon2 = new Argon2PasswordEncoder(
                saltLength, hashLength, parallelism, memoryKib, iterations);
        // REQ-SH-003: preserve verification of already initialized PBKDF2 accounts.
        var encoder = new DelegatingPasswordEncoder("argon2", Map.of(
                "argon2", argon2,
                "pbkdf2@SpringSecurity_v5_8", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
        return encoder;
    }
}
