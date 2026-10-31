package ma.ensat.hirelink.service;

import ma.ensat.hirelink.service.EmailTemplates.Email;
import ma.ensat.hirelink.service.EmailTemplates.Fact;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplatesTest {

    private final Email email = Email.of("Entretien planifié", "Bonjour <b>Sara</b>, voici les détails.")
            .withFacts(new Fact("Lieu", "<script>alert(1)</script>"))
            .withButton("Voir ma candidature", "http://localhost:5500/app/candidate-applications.html");

    @Test
    void htmlEchappeLeContenuDynamique() {
        String html = EmailTemplates.html(email, "Pied de page");
        assertThat(html).doesNotContain("<script>").doesNotContain("<b>Sara</b>");
        assertThat(html).contains("&lt;script&gt;").contains("Voir ma candidature").contains("#7a2032");
    }

    @Test
    void versionTexteContientLeLienEtLesFaits() {
        String text = EmailTemplates.text(email, "Pied de page");
        assertThat(text).contains("Entretien planifié").contains("Lieu").contains("http://localhost:5500/app/candidate-applications.html");
        assertThat(text).doesNotContain("<html");
    }
}
