package ma.ensat.hirelink.service;

import org.springframework.stereotype.Component;

/**
 * Implémentation locale déterministe (fallback) quand aucune clé API n'est configurée.
 * Respecte R15 : ne reçoit que compétences / intitulé.
 */
@Component
public class LocalLlmProvider implements LlmProvider {
    @Override
    public LlmResult complete(String systemPrompt, String userPrompt) {
        String in = (systemPrompt == null ? "" : systemPrompt) + "\n" + (userPrompt == null ? "" : userPrompt);
        String lower = in.toLowerCase();
        StringBuilder out = new StringBuilder();
        if (lower.contains("refus") || lower.contains("rejet")) {
            out.append("Brouillon de message de refus (à relire) :\n\n");
            out.append("Bonjour,\n\nNous vous remercions pour votre candidature. ");
            out.append("Après étude attentive de votre dossier au regard des critères du poste, ");
            out.append("nous ne pouvons malheureusement pas donner une suite favorable à ce stade.\n\n");
            out.append("Nous vous souhaitons pleine réussite pour la suite de votre parcours.\n\nCordialement,\nL'équipe recrutement");
        } else if (lower.contains("entretien") || lower.contains("grille") || lower.contains("questions")) {
            out.append("Grille d'entretien proposée :\n");
            out.append("1. Présentez un résultat concret lié au poste.\n");
            out.append("2. Donnez un exemple technique sous contrainte de délai.\n");
            out.append("3. STAR : Situation / Tâche / Action / Résultat.\n");
            out.append("4. Collaboration : désaccord avec un métier — comment l'avez-vous géré ?\n");
            out.append("5. Questions du candidat pour clarifier le succès à 90 jours.\n");
            out.append("\n(Proposition locale — éditez librement avant l'entretien.)");
        } else if (lower.contains("coach") || lower.contains("recommand")) {
            out.append("Recommandations pour votre CV :\n");
            out.append("1. Le titre doit reprendre le métier que vous visez, pas seulement votre diplôme.\n");
            out.append("2. Chaque expérience tient en deux lignes : ce que vous avez fait, puis un résultat chiffré.\n");
            out.append("3. Placez en haut les compétences qui reviennent dans les offres que vous regardez.\n");
            out.append("4. Retirez les missions trop anciennes si elles n’ont aucun lien avec le poste.\n");
            out.append("\nPosez une question précise dans le chat, par exemple : « Que manque-t-il pour un stage data ? »");
        } else {
            out.append("Analyse de profil (proposition) :\n");
            out.append("- Mettez en avant 3 compétences alignées sur l'offre.\n");
            out.append("- Ajoutez un chiffre concret (délai, volume, résultat).\n");
            out.append("- Clarifiez le type de mission recherchée (stage / CDI / PFE).\n");
            out.append("\nAucune donnée d'identité n'a été utilisée pour cette suggestion.");
        }
        String text = out.toString();
        return new LlmResult(text, "local-fallback", "rules-fr", in.length(), text.length(), true);
    }
}
