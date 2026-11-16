package ma.ensat.hirelink.service;

import ma.ensat.hirelink.security.AuthThrottle;
import ma.ensat.hirelink.security.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaptchaServiceTest {

    private CaptchaService captcha;
    private AuthThrottle throttle;

    @BeforeEach
    void setUp() {
        throttle = new AuthThrottle();
        captcha = new CaptchaService(throttle, true);
    }

    @Test
    void issueWithoutIp_returnsTokenAndChallenge() {
        CaptchaService.Challenge c = captcha.issue();
        assertNotNull(c.token());
        assertFalse(c.token().isBlank());
        assertTrue("image".equals(c.kind()) || "question".equals(c.kind()));
        if ("image".equals(c.kind())) {
            assertNotNull(c.svg());
            assertTrue(c.svg().contains("<svg"));
        } else {
            assertNotNull(c.question());
            assertTrue(c.question().contains("Combien"));
        }
    }

    @Test
    void assertValid_consumesToken_oneShot() throws Exception {
        CaptchaService.Challenge c = captcha.issue();
        String answer = peekAnswer(c.token());
        captcha.assertValid(c.token(), answer);
        ResponseStatusException second = assertThrows(ResponseStatusException.class,
                () -> captcha.assertValid(c.token(), answer));
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, second.getStatusCode());
    }

    @Test
    void wrongAnswer_consumesToken() throws Exception {
        CaptchaService.Challenge c = captcha.issue();
        ResponseStatusException bad = assertThrows(ResponseStatusException.class,
                () -> captcha.assertValid(c.token(), "ZZZZZ"));
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, bad.getStatusCode());
        // Jeton déjà retiré : même la bonne réponse échoue.
        String answer = peekAnswerOrNull(c.token());
        assertNull(answer);
    }

    @Test
    void issueWithIp_throttlesAfterLimit() {
        for (int i = 0; i < 60; i++) {
            captcha.issue("203.0.113.50");
        }
        assertThrows(TooManyRequestsException.class, () -> captcha.issue("203.0.113.50"));
        // Autre IP non bloquée
        assertDoesNotThrow(() -> captcha.issue("203.0.113.51"));
    }

    @SuppressWarnings("unchecked")
    private String peekAnswer(String token) throws Exception {
        String a = peekAnswerOrNull(token);
        assertNotNull(a);
        return a;
    }

    @SuppressWarnings("unchecked")
    private String peekAnswerOrNull(String token) throws Exception {
        Field f = CaptchaService.class.getDeclaredField("pending");
        f.setAccessible(true);
        Map<String, Object> pending = (Map<String, Object>) f.get(captcha);
        Object p = pending.get(token);
        if (p == null) return null;
        Field answerField = p.getClass().getDeclaredField("answer");
        answerField.setAccessible(true);
        byte[] bytes = (byte[]) answerField.get(p);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
