package com.openclassrooms.starterjwt.security.jwt;

import com.openclassrooms.starterjwt.models.User;
import com.openclassrooms.starterjwt.repository.UserRepository;
import com.openclassrooms.starterjwt.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional // rollback automatique après chaque test
class JwtUtilsIntegrationWithDatabaseTest {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Authentication auth;

    @BeforeEach
    void setup() {
        // Supprime tous les utilisateurs pour repartir à zéro
        userRepository.deleteAll();

        // Création d'un utilisateur réel dans la base MySQL
        testUser = new User();
        testUser.setEmail("integration@test.com");
        testUser.setPassword("password"); // normalement hashé via service
        testUser.setAdmin(false);
        testUser.setFirstName("Integration");
        testUser.setLastName("Test");
        userRepository.saveAndFlush(testUser);

        // Crée l'Authentication via UserDetailsImpl
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(testUser.getId())
                .username(testUser.getEmail())
                .firstName(testUser.getFirstName())
                .lastName(testUser.getLastName())
                .password(testUser.getPassword())
                .admin(testUser.isAdmin())
                .build();

        auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    @DisplayName("validateJwtToken() – JWT expiré retourne false + ExpiredJwtException")
    void testExpiredJwtToken() throws InterruptedException {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 1);

        String token = jwtUtils.generateJwtToken(auth);
        Thread.sleep(10);

        assertFalse(jwtUtils.validateJwtToken(token));
        assertThrows(ExpiredJwtException.class, () -> jwtUtils.getUserNameFromJwtToken(token));
    }

    @Test
    @DisplayName("validateJwtToken() – signature invalide retourne false")
    void testInvalidSignatureJwtToken() {
        String wrongKey = "r8XyGa6A8nXnJk5lCqfBv1fRESjpnPR0cVpt5Rr9xWry1m3X52z4qJVZ8y4nQp7utjWZNA4xS9tQVn8zHcQq7g==";
        String tokenWithWrongKey = Jwts.builder()
                .setSubject(testUser.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 10000))
                .signWith(SignatureAlgorithm.HS512, wrongKey)
                .compact();

        assertFalse(jwtUtils.validateJwtToken(tokenWithWrongKey));
    }

    @Test
    @DisplayName("validateJwtToken() – token vide retourne false")
    void testValidateEmptyToken() {
        assertFalse(jwtUtils.validateJwtToken(""));
    }

    @Test
    @DisplayName("validateJwtToken() – token null retourne false")
    void testValidateNullToken() {
        assertFalse(jwtUtils.validateJwtToken(null));
    }

    @Test
    @DisplayName("validateJwtToken() – token mal formé retourne false")
    void testMalformedJwtToken() {
        assertFalse(jwtUtils.validateJwtToken("abc.def"));
    }

    @Test
    @DisplayName("validateJwtToken() – algorithme non supporté retourne false")
    void testUnsupportedJwtAlgorithm() {
        String unsupportedToken = Jwts.builder()
                .setHeaderParam("alg", "none")
                .setSubject(testUser.getEmail())
                .compact();

        assertFalse(jwtUtils.validateJwtToken(unsupportedToken));
    }

    @Test
    @DisplayName("generateJwtToken() – génération et validation pour utilisateur en base")
    void testGenerateAndValidateJwtToken() {
        String token = jwtUtils.generateJwtToken(auth);
        assertNotNull(token);
        assertTrue(jwtUtils.validateJwtToken(token));
        assertEquals(testUser.getEmail(), jwtUtils.getUserNameFromJwtToken(token));
    }
}
