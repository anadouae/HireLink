package ma.ensat.hirelink.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du cookie de rafraîchissement (C7) : HttpOnly + SameSite=Strict partout, Secure dès que la
 * requête est en HTTPS ou que le déploiement l'exige, et contrôle de l'Origine sur les routes qui n'ont
 * que ce cookie comme preuve d'identité.
 */
class RefreshCookiesTest {

    private static final String ORIGINES = "http://localhost:5500,https://hirelink.example";

    private final RefreshCookies cookies = new RefreshCookies(false, ORIGINES);

    private String writeCookie(MockHttpServletRequest req) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        cookies.write(res, req, "jeton-de-test", 3600);
        return res.getHeader(HttpHeaders.SET_COOKIE);
    }

    @Test
    void leCookieEstMarqueSecureDesQueLaRequeteEstEnHttps() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        req.setSecure(true);

        assertThat(writeCookie(req)).contains("hl_rt=jeton-de-test").contains("Secure").contains("HttpOnly")
                .contains("SameSite=Strict").contains("Path=/api/v1/auth");
    }

    @Test
    void leFlagSecureEstImposeParLaConfigurationMemeEnHttp() {
        RefreshCookies production = new RefreshCookies(true, ORIGINES);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");

        MockHttpServletResponse res = new MockHttpServletResponse();
        production.write(res, req, "jeton", 3600);

        assertThat(res.getHeader(HttpHeaders.SET_COOKIE)).contains("Secure");
    }

    @Test
    void enLocalHttpLeCookieResteHttpOnlyEtStrictMaisSansFlagSecure() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");

        assertThat(writeCookie(req)).contains("HttpOnly").contains("SameSite=Strict").doesNotContain("Secure");
    }

    @Test
    void laSuppressionDuCookieGardeLesMemmesAttributs() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        req.setSecure(true);
        MockHttpServletResponse res = new MockHttpServletResponse();

        cookies.clear(res, req);

        assertThat(res.getHeader(HttpHeaders.SET_COOKIE)).contains("hl_rt=").contains("Max-Age=0")
                .contains("HttpOnly").contains("Secure");
    }

    @Test
    void uneOrigineHorsListeEstRejetee() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        req.addHeader(RefreshCookies.CSRF_HEADER, "1");
        req.addHeader(HttpHeaders.ORIGIN, "https://evil.example");

        assertThatThrownBy(() -> cookies.assertCsrf(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void sansEnTeteCsrfLaRequeteEstRejetee() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        req.addHeader(HttpHeaders.ORIGIN, "http://localhost:5500");

        assertThatThrownBy(() -> cookies.assertCsrf(req)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void lEnTeteCsrfSeulSuffitQuandAucuneOrigineNestDeclarée() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        req.addHeader(RefreshCookies.CSRF_HEADER, "1");

        cookies.assertCsrf(req);

        req.addHeader(HttpHeaders.ORIGIN, "http://localhost:5500");
        cookies.assertCsrf(req);
    }

    @Test
    void laLectureRetrouveLeJetonParmiPlusieursCookies() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        req.setCookies(new Cookie("autre", "x"), new Cookie(RefreshCookies.NAME, "jeton-de-test"));

        assertThat(cookies.read(req)).isEqualTo("jeton-de-test");
    }
}
