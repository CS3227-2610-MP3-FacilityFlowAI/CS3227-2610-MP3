package sg.edu.nus.facilityflow.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfiguration {

    @Bean
    PasswordEncoder passwordEncoder(
            @Value("${facilityflow.security.password.salt-length}") int saltLength,
            @Value("${facilityflow.security.password.hash-length}") int hashLength,
            @Value("${facilityflow.security.password.parallelism}") int parallelism,
            @Value("${facilityflow.security.password.memory-kib}") int memoryKib,
            @Value("${facilityflow.security.password.iterations}") int iterations) {
        return new Argon2PasswordEncoder(
                saltLength, hashLength, parallelism, memoryKib, iterations);
    }
}
