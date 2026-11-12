package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CvSectionParserTest {

    private final CvSectionParser parser = new CvSectionParser();

    @Test
    void unStageJavaEstUneExperience() {
        CvSectionParser.Parsed parsed = parser.parse("""
                Sara El Amrani
                Etudiante ingenieure - Tetouan
                EXPERIENCES
                Stage developpeur Java, Atlas Digital
                FORMATION
                ENSA Tetouan, genie informatique
                COMPETENCES
                Java, Spring Boot, SQL
                LANGUES
                Francais, Anglais, Arabe
                """, List.of());
        assertEquals(1, parsed.experiences().size());
        CvSectionParser.Experience stage = parsed.experiences().get(0);
        assertEquals("STAGE", stage.kind());
        assertEquals("developpeur Java", stage.title());
        assertEquals("Atlas Digital", stage.organization());
        assertNull(stage.start());
        assertTrue(stage.datesIncomplete());
        assertTrue(parsed.sections().get("experience").contains("Atlas Digital"));
        assertTrue(parsed.sections().get("education").contains("ENSA"));
        assertTrue(parsed.sections().get("skills").contains("Java"));
        assertTrue(parsed.sections().get("languages").contains("Anglais"));
    }

    @Test
    void plusieursEmploisEtUneAlternance() {
        CvSectionParser.Parsed parsed = parser.parse("""
                EXPERIENCES
                Developpeur Java, Atlas Digital
                2020 - 2022
                Alternance data, Inwi
                2023
                """, List.of());
        assertEquals(2, parsed.experiences().size());
        assertEquals("EMPLOI", parsed.experiences().get(0).kind());
        assertEquals("2020", parsed.experiences().get(0).start());
        assertEquals("2022", parsed.experiences().get(0).end());
        assertEquals("ALTERNANCE", parsed.experiences().get(1).kind());
        assertEquals("Inwi", parsed.experiences().get(1).organization());
        assertTrue(parsed.experiences().get(1).datesIncomplete());
    }

    @Test
    void sansExperienceLeChampResteVide() {
        CvSectionParser.Parsed parsed = parser.parse("""
                Ali Ben
                FORMATION
                ENSA Tetouan
                COMPETENCES
                Java
                """, List.of());
        assertTrue(parsed.experiences().isEmpty());
        assertTrue(parsed.sections().get("experience").isBlank());
    }

    @Test
    void unProjetAcademiqueNestPasUnEmploi() {
        CvSectionParser.Parsed parsed = parser.parse("""
                PROJECTS
                Campus library app
                EDUCATION
                BSc Computer Science
                """, List.of());
        assertEquals(1, parsed.experiences().size());
        assertEquals("PROJET", parsed.experiences().get(0).kind());
        assertTrue(parsed.sections().get("experience").isBlank());
        assertTrue(parsed.sections().get("projects").contains("Campus"));
    }

    @Test
    void unCvAnglaisGardeLePosteEtLentreprise() {
        CvSectionParser.Parsed parsed = parser.parse("""
                Jane Doe
                WORK EXPERIENCE
                Software Engineer at Northwind
                January 2021 - March 2023
                SKILLS
                Java, SQL
                LANGUAGES
                English, French
                """, List.of());
        CvSectionParser.Experience job = parsed.experiences().get(0);
        assertEquals("EMPLOI", job.kind());
        assertEquals("Software Engineer", job.title());
        assertEquals("Northwind", job.organization());
        assertEquals("2021", job.start());
        assertEquals("2023", job.end());
        assertTrue(parsed.sections().get("languages").contains("Anglais"));
        assertTrue(parsed.sections().get("languages").contains("Français"));
    }

    @Test
    void desColonnesNonSepareesNeSontPasDevinees() {
        CvSectionParser.Parsed parsed = parser.parse("EXPERIENCES FORMATION\nStage Java Atlas ENSA\n", List.of());
        assertTrue(parsed.experiences().isEmpty());
        assertTrue(parsed.note().contains("colonnes"));
    }
}
