package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.ApplicationStatus;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.EmailTemplates.Email;
import ma.ensat.hirelink.service.EmailTemplates.Fact;
import ma.ensat.hirelink.service.MailService.Attachment;
import ma.ensat.hirelink.service.MailService.Kind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Événements métier → notification dans l'application <b>et</b> e-mail, avec les mêmes mots :
 * candidature, étapes du pipeline, entretiens, vérification d'entreprise, bienvenue.
 */
@Service
public class EventNotifier {

    private final NotificationService notifications;
    private final OrganizationService organizations;
    private final InterviewService interviews;

    @Value("${hirelink.frontend.base-url:http://localhost:5500}")
    private String frontendBase;

    public EventNotifier(NotificationService notifications, OrganizationService organizations, InterviewService interviews) {
        this.notifications = notifications;
        this.organizations = organizations;
        this.interviews = interviews;
    }

    // ------------------------------------------------------------- compte

    public void welcome(User user) {
        notifications.pushAndMail(user.getId(), "Bienvenue sur HireLink",
                "Votre compte est créé. Complétez votre profil pour commencer.", "SYSTEM", Kind.ACCOUNT, "bienvenue",
                Email.of("Bienvenue sur HireLink",
                        "Bonjour " + user.getFullName() + ", votre compte est créé et votre adresse e-mail est vérifiée.",
                        user.getRole() == Role.CANDIDAT
                                ? "Complétez votre profil et vos compétences : HireLink vous montrera les offres qui vous correspondent."
                                : "Renseignez votre entreprise pour la faire vérifier, puis publiez votre première offre.")
                        .withButton("Ouvrir HireLink", page(user.getRole() == Role.CANDIDAT
                                ? "candidate-dashboard.html" : "recruiter.html")),
                null);
    }

    public void organizationDecision(Organization org, boolean verified, Long userId) {
        String title = verified ? "Entreprise vérifiée" : "Vérification retirée";
        String body = verified
                ? "« " + org.getName() + " » est vérifiée : vous pouvez publier des offres."
                : "La vérification de « " + org.getName() + " » a été retirée par l'administration.";
        notifications.pushAndMail("team", userId, title, body, "SYSTEM", Kind.ACCOUNT, title + " : " + org.getName(),
                Email.of(title, body,
                        verified ? "Vos offres peuvent désormais être publiées et sont visibles des candidats avec le badge « entreprise vérifiée »."
                                : "Vos offres ne peuvent plus être publiées tant que l'entreprise n'est pas à nouveau vérifiée. Vérifiez vos informations (ICE, site web, e-mail professionnel) ou contactez l'administration.")
                        .withButton(verified ? "Publier une offre" : "Voir ma fiche entreprise", page(verified ? "recruiter-offers.html" : "recruiter-team.html")),
                null);
    }

    // -------------------------------------------------------- candidatures

    public void applicationSubmitted(Application app) {
        JobOffer offer = app.getJobOffer();
        User candidate = app.getCandidate();
        for (Long target : organizations.offerNotificationTargets(offer)) {
            notifications.pushAndMail("application:" + app.getId(), target, "Nouvelle candidature",
                    candidate.getFullName() + " a postulé à « " + offer.getTitle() + " »", "APPLICATION", Kind.APPLICATION,
                    "nouvelle candidature : " + offer.getTitle(),
                    Email.of("Nouvelle candidature reçue",
                            candidate.getFullName() + " vient de postuler à votre offre « " + offer.getTitle() + " ».",
                            "Objectif interne de première réponse : " + offer.getResponseSlaDays() + " jours.")
                            .withFacts(new Fact("Candidat", candidate.getFullName()),
                                    new Fact("Compatibilité au dépôt", app.getMatchScore() == null ? "Non disponible" : Math.round(app.getMatchScore()) + " %"))
                            .withButton("Ouvrir le pipeline", page("recruiter-pipeline.html")),
                    null);
        }
        notifications.pushAndMail("application:" + app.getId(), candidate.getId(), "Candidature envoyée",
                "Votre dossier pour « " + offer.getTitle() + " » est soumis.",
                "APPLICATION", Kind.APPLICATION, "candidature envoyée : " + offer.getTitle(),
                Email.of("Votre candidature est bien envoyée",
                        "Merci d'avoir postulé à « " + offer.getTitle() + " ». L'entreprise examine les dossiers et vous répondra ici dès sa première décision.",
                        "Vous suivez l'avancement de votre dossier à chaque étape depuis votre espace candidat.")
                        .withButton("Suivre ma candidature", page("candidate-applications.html?id=" + app.getId())),
                null);
    }

    /** Étapes présélection, shortlist, offre, embauche et refus (l'entretien a son propre message). */
    public void statusChanged(Application app) {
        String job = app.getJobOffer().getTitle();
        Long candidate = app.getCandidate().getId();
        if (app.getStatus() == ApplicationStatus.REJECTED) {
            String reason = app.getRejectionReason();
            String custom = app.getAdvanceNote();
            Email email;
            if (custom != null && !custom.isBlank()) {
                // Message rédigé (ou assisté) par le recruteur au moment du refus.
                email = Email.of("Votre candidature n'a pas été retenue", custom.trim());
            } else {
                email = Email.of("Votre candidature n'a pas été retenue",
                        "Merci de l'intérêt que vous portez à « " + job + " ». Après étude de votre dossier, l'entreprise a décidé de ne pas y donner suite pour ce poste.",
                        "Cette décision ne remet pas en cause la valeur de votre parcours. Nous vous encourageons à postuler aux autres offres qui correspondent à votre profil.");
            }
            if (reason != null) email = email.withFacts(new Fact("Motif communiqué", reason));
            notifications.pushAndMail("application:" + app.getId(), candidate, "Candidature non retenue",
                    "« " + job + " »" + (reason != null ? " — " + reason : ""), "STATUS", Kind.APPLICATION,
                    "votre candidature : " + job, email.withButton("Voir les offres", page("candidate.html")), null);
            return;
        }
        if (app.getStatus() == ApplicationStatus.OFFER) {
            notifications.pushAndMail("application:" + app.getId(), candidate, "Une offre pour vous !",
                    "L'entreprise souhaite vous faire une offre pour « " + job + " ».", "STATUS", Kind.APPLICATION,
                    "une offre pour vous : " + job,
                    Email.of("Félicitations : l'entreprise vous fait une offre",
                            "Suite à vos échanges, l'entreprise souhaite vous proposer le poste « " + job + " ».",
                            "Le recruteur vous contactera pour les conditions détaillées. Vous pouvez suivre la suite depuis votre espace candidat.")
                            .withButton("Voir ma candidature", page("candidate-applications.html?id=" + app.getId())),
                    null);
            return;
        }
        if (app.getStatus() == ApplicationStatus.HIRED) {
            String company = organizations.companyNameOf(app.getJobOffer());
            notifications.pushAndMail("application:" + app.getId(), candidate, "Vous êtes embauché(e) !",
                    "Bienvenue chez " + company + " : « " + job + " ». Toutes nos félicitations !", "HIRED", Kind.APPLICATION,
                    "félicitations, vous êtes embauché(e) : " + job,
                    Email.of("Félicitations, vous êtes embauché(e) !",
                            "C'est officiel : vous rejoignez " + company + " au poste « " + job + " ».",
                            "Toute l'équipe HireLink vous souhaite une excellente prise de poste.")
                            .withButton("Voir ma candidature", page("candidate-applications.html?id=" + app.getId())),
                    null);
            return;
        }
        boolean interview = app.getStatus() == ApplicationStatus.INTERVIEW;
        boolean shortlisted = app.getStatus() == ApplicationStatus.SHORTLISTED;
        String step = interview ? "Entretien" : shortlisted ? "Shortlist" : "Présélection";
        String phase = interview ? "la phase entretien" : shortlisted ? "la shortlist" : "la présélection";
        notifications.pushAndMail("application:" + app.getId(), candidate, "Votre candidature avance",
                "« " + job + " » est maintenant en étape « " + step + " ».",
                "STATUS", Kind.APPLICATION, "votre candidature avance : " + job,
                Email.of("Bonne nouvelle : votre candidature avance",
                        "Votre dossier pour « " + job + " » a été retenu pour " + phase + ".",
                        interview ? "Le recruteur vous proposera très vite un créneau d'entretien."
                                : "Le recruteur reviendra vers vous à l'étape suivante.")
                        .withButton("Suivre ma candidature", page("candidate-applications.html?id=" + app.getId())),
                null);
    }

    // ------------------------------------------------------------ entretiens

    public void interviewScheduled(Application app, boolean rescheduled) {
        String job = app.getJobOffer().getTitle();
        String title = rescheduled ? "Entretien reporté" : "Entretien planifié";
        Email email = interviewEmail(app, title, rescheduled
                ? "Votre entretien pour « " + job + " » a été reporté. Voici le nouveau créneau."
                : "Bonne nouvelle : le recruteur vous propose un entretien pour « " + job + " ».")
                .withButton("Voir ma convocation", page("candidate-applications.html?id=" + app.getId()));
        notifications.pushAndMail("interview:" + app.getId(), app.getCandidate().getId(), title, "« " + job + " » — " + interviews.summary(app), "INTERVIEW",
                Kind.INTERVIEW, title.toLowerCase() + " : " + job, email, calendar(app));
        for (Long target : staff(app)) {
            String teamBody = app.getCandidate().getFullName() + " — " + interviews.summary(app);
            notifications.pushAndMail("interview:" + app.getId(), target, title, teamBody, "INTERVIEW", Kind.INTERVIEW,
                    title.toLowerCase() + " : " + app.getCandidate().getFullName(),
                    interviewEmail(app, title, "Entretien avec " + app.getCandidate().getFullName() + " pour « " + job + " ».")
                            .withButton("Ouvrir le pipeline", page("recruiter-pipeline.html")),
                    calendar(app));
        }
    }

    public void interviewCancelled(Application app) {
        String job = app.getJobOffer().getTitle();
        notifications.pushAndMail("interview:" + app.getId(), app.getCandidate().getId(), "Entretien annulé",
                "« " + job + " » — " + app.getInterviewCancelReason(), "INTERVIEW", Kind.INTERVIEW, "entretien annulé : " + job,
                Email.of("Votre entretien est annulé",
                        "L'entretien prévu le " + interviews.when(app) + " pour « " + job + " » a été annulé.")
                        .withFacts(new Fact("Motif", app.getInterviewCancelReason()))
                        .withButton("Voir ma candidature", page("candidate-applications.html?id=" + app.getId())),
                null);
        for (Long target : staff(app)) {
            notifications.pushAndMail("interview:" + app.getId(), target, "Entretien annulé",
                    app.getCandidate().getFullName() + " — " + app.getInterviewCancelReason(), "INTERVIEW", Kind.INTERVIEW,
                    "entretien annulé : " + app.getCandidate().getFullName(),
                    Email.of("Entretien annulé",
                            "L'entretien avec " + app.getCandidate().getFullName() + " prévu le " + interviews.when(app)
                                    + " pour « " + job + " » a été annulé.")
                            .withFacts(new Fact("Motif", app.getInterviewCancelReason()))
                            .withButton("Ouvrir le pipeline", page("recruiter-pipeline.html")),
                    null);
        }
    }

    public void interviewReminder(Application app) {
        String job = app.getJobOffer().getTitle();
        notifications.pushAndMail("interview:" + app.getId(), app.getCandidate().getId(), "Rappel d'entretien",
                "« " + job + " » — " + interviews.summary(app), "INTERVIEW", Kind.INTERVIEW, "rappel d'entretien : " + job,
                interviewEmail(app, "Rappel : votre entretien approche", "Votre entretien pour « " + job + " » a lieu dans moins de 24 heures.")
                        .withButton("Voir les détails", page("candidate-applications.html?id=" + app.getId())),
                calendar(app));
        for (Long target : staff(app)) {
            notifications.pushAndMail("interview:" + app.getId(), target, "Rappel d'entretien",
                    app.getCandidate().getFullName() + " — " + interviews.summary(app), "INTERVIEW", Kind.INTERVIEW,
                    "rappel d'entretien : " + app.getCandidate().getFullName(),
                    interviewEmail(app, "Rappel : entretien dans moins de 24 h", "Entretien avec " + app.getCandidate().getFullName() + " pour « " + job + " ».")
                            .withButton("Ouvrir le pipeline", page("recruiter-pipeline.html")), calendar(app));
        }
    }

    private Email interviewEmail(Application app, String heading, String intro) {
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("Quand", interviews.when(app)
                + (app.getInterviewDurationMinutes() == null ? "" : " · " + app.getInterviewDurationMinutes() + " min")));
        String nature = InterviewService.label(InterviewService.KIND_FR, app.getInterviewKind(), "Entretien");
        String mode = InterviewService.label(InterviewService.TYPE_FR, app.getInterviewType(), "");
        String type = app.getInterviewType() == null ? "" : app.getInterviewType();
        addFact(facts, "Nature", nature);
        addFact(facts, "Mode", mode);
        addFact(facts, switch (type) {
            case "ONSITE" -> "Adresse";
            case "VISIO" -> "Lien de visio";
            case "PHONE" -> "Téléphone";
            default -> "";
        }, switch (type) {
            case "ONSITE" -> app.getInterviewLocation();
            case "VISIO" -> app.getInterviewVideoUrl();
            case "PHONE" -> interviews.phoneOf(app);
            default -> "";
        });
        addFact(facts, "Plan", app.getInterviewMapUrl());
        addFact(facts, "Avec", app.getInterviewParticipants());
        addFact(facts, "Consignes", app.getInterviewNotes());
        return Email.of(heading, intro, "Le fichier joint (.ics) ajoute l'entretien à votre agenda.").withFacts(facts.toArray(Fact[]::new));
    }

    /** Une ligne de fait sans valeur sous le gabarit rend un tableau muet : on ne garde que ce qui est renseigné. */
    private static void addFact(List<Fact> facts, String label, String value) {
        if (value != null && !value.isBlank()) facts.add(new Fact(label, value.trim()));
    }

    private Attachment calendar(Application app) {
        return new Attachment("entretien-hirelink.ics", "text/calendar; charset=UTF-8; method=PUBLISH", interviews.ics(app));
    }

    /** Équipe qui suit le dossier : destinataires de l'offre + responsable de recrutement assigné. */
    private Set<Long> staff(Application app) {
        Set<Long> ids = new LinkedHashSet<>(organizations.offerNotificationTargets(app.getJobOffer()));
        if (app.getAssignedHmUserId() != null) ids.add(app.getAssignedHmUserId());
        return ids;
    }

    private String page(String file) {
        return frontendBase.replaceAll("/$", "") + "/app/" + file;
    }
}
