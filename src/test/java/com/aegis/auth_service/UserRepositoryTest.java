package com.aegis.auth_service;

import com.aegis.auth_service.entities.User;
import com.aegis.auth_service.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.aegis.auth_service.entities.Role;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindUserByEmail_WhenUserExists() {
        // 1. Arrange: Save a fake user to the database
        User user = User.builder()
                .email("test@aegis.com")
                .password("hashedpassword")
                .role(Role.USER)
                .build();
        userRepository.save(user);

        // 2. Act: Try to find them
        Optional<User> foundUser = userRepository.findByEmail("test@aegis.com");

        // 3. Assert: Verify the results
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("test@aegis.com");
    }

    @Test
    void shouldReturnEmpty_WhenEmailDoesNotExist() {
        Optional<User> foundUser = userRepository.findByEmail("ghost@aegis.com");
        assertThat(foundUser).isEmpty();
    }
}
