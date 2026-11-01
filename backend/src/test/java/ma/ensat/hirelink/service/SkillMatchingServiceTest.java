package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMatchingServiceTest {

    private final SkillMatchingService service =
            new SkillMatchingService(new SkillCatalogService(new TextMatcher()));

    @Test
    void perfectOverlapYieldsHighScore() {
        assertEquals(100.0, service.computeMatchScore("Java,Spring,MySQL", "Java,Spring,MySQL"), 0.01);
    }

    @Test
    void partialOverlap() {
        double score = service.computeMatchScore("Java,Python", "Java,Spring,MySQL");
        assertTrue(score > 0 && score < 100);
    }

    @Test
    void emptyRequiredYieldsZero() {
        assertEquals(0.0, service.computeMatchScore("Java", ""), 0.01);
    }

    @Test
    void synonymsAndTyposAreMatched() {
        var detail = service.explain("js, spring, pyhton, Anglais (C1)", "JavaScript, Spring Boot, Python, Anglais (B2), Docker");
        assertEquals(4, detail.covered().size());
        assertEquals(java.util.List.of("Docker"), detail.missing());
        assertEquals(80.0, detail.score(), 0.01);
    }

    @Test
    void richProfileIsNotPenalised() {
        assertEquals(100.0, service.computeMatchScore("Java,Spring Boot,MySQL,Docker,Git,React,Linux,AWS", "Java,MySQL"), 0.01);
    }

    private static ma.ensat.hirelink.model.User candidate(String skills, String level, String contracts, String locs) {
        var u = new ma.ensat.hirelink.model.User();
        u.setSkills(skills);
        u.setExperienceLevel(level);
        u.setPreferredContracts(contracts);
        u.setPreferredLocations(locs);
        return u;
    }

    private static ma.ensat.hirelink.model.JobOffer offer(String req, String nice) {
        var o = new ma.ensat.hirelink.model.JobOffer();
        o.setRequiredSkills(req);
        o.setNiceToHaveSkills(nice);
        o.setContractType("CDI");
        o.setLocation("Tétouan · Hybride");
        o.setWorkMode(ma.ensat.hirelink.model.WorkMode.HYBRID);
        o.setExperienceLevel(ma.ensat.hirelink.model.ExperienceLevel.JUNIOR);
        return o;
    }

    @Test
    void weightedScoreIsFullWhenEverythingFits() {
        var c = service.evaluate(candidate("Java,Spring Boot,React,Anglais (C1)", "Junior", "CDI", "Tétouan,Hybride"),
                offer("Java,Spring Boot,Anglais (B2)", "React"));
        assertEquals(100.0, c.score(), 0.01);
        assertEquals(6, c.criteria().size());
    }

    @Test
    void criteriaNotStatedByOfferAreRenormalised() {
        var o = offer("Java", null);
        var c = service.evaluate(candidate("Java", "Junior", "CDI", "Tétouan"), o);
        assertTrue(c.criteria().stream().filter(k -> k.code().equals("NICE")).allMatch(k -> !k.applicable()));
        assertEquals(100.0, c.score(), 0.01);
    }

    @Test
    void missingRequiredSkillsCapTheScore() {
        var c = service.evaluate(candidate("Java,Spring Boot,React", "Junior", "CDI", "Tétouan,Hybride"),
                offer("Java,Spring Boot,MySQL,Docker,Kubernetes", "React"));
        assertTrue(c.capped());
        assertEquals(60.0, c.score(), 0.01); // 2/5 = 40 % couvert → plafond min(60, 40 + 20)
        var none = service.evaluate(candidate("Figma", "Junior", "CDI", "Tétouan,Hybride"), offer("Java,Spring Boot", null));
        assertEquals(20.0, none.score(), 0.01); // aucune compétence obligatoire → plafond 20
    }

    @Test
    void languageLevelBelowRequirementCountsHalf() {
        var c = service.evaluate(candidate("Java,Anglais (B1)", "Junior", "CDI", "Tétouan"), offer("Java,Anglais (B2)", null));
        var lang = c.criteria().stream().filter(k -> k.code().equals("LANGUAGES")).findFirst().orElseThrow();
        assertEquals(0.5, lang.ratio(), 0.001);
    }

    @Test
    void scoresAreOrdered() {
        var o = offer("Java,Spring Boot,MySQL", "Docker");
        double good = service.evaluate(candidate("Java,Spring Boot,MySQL,Docker", "Junior", "CDI", "Tétouan"), o).score();
        double mid = service.evaluate(candidate("Java,Spring Boot,MySQL", "Senior", "Stage", "Casablanca"), o).score();
        double low = service.evaluate(candidate("Java", "Stagiaire", "Stage", "Remote"), o).score();
        assertTrue(good > mid && mid > low, good + " > " + mid + " > " + low);
    }
}
