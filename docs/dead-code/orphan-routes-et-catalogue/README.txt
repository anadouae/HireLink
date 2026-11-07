Déplacé ici le 2026-11-06 — supprimé du backend parce qu'aucun appelant n'existe.

Preuve d'orphelinat (balayage de frontend/js/*.js, 150 chaînes de path extraites des
appel api()/authFetch()/fetch) : « auth/me » et « ai/status » n'apparaissent nulle part.
Le front lit la session courante via GET /api/v1/me (MeController), qui rend un profil
beaucoup plus complet ; /auth/me n'était qu'un doublon appauvri. GET /api/v1/ai/status
n'était lu par aucune page : le solde IA réel vient de GET /api/v1/ai/credits.

1) AuthController.java — GET /api/v1/auth/me (supprimé)

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Non authentifié"));
        }
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole().name(),
                "mustSetPassword", user.isMustSetPassword(),
                "emailVerified", user.isEmailVerified(),
                "mfaEnabled", user.isMfaEnabled(),
                "skills", user.getSkills() == null ? "" : user.getSkills(),
                "company", user.getCompany() == null ? "" : user.getCompany()
        ));
    }

2) AiAssistController.java — GET /api/v1/ai/status (supprimé)

    /** Quels providers cloud sont configurés (clés masquées). */
    @GetMapping("/status")
    public Map<String, Object> status(@AuthenticationPrincipal User user) {
        Map<String, Object> m = new LinkedHashMap<>(llmProvider.statusMap());
        m.put("user", user.getEmail());
        m.put("features", List.of(
                "profile-rewrite", "interview-prep", "interview-kit",
                "refusal-draft", "match-explain", "language-check", "coach"
        ));
        return m;
    }

3) HttpLlmProvider.java — statusMap() (supprimé : devenu sans appelant après 2)

    /** Statut masqué pour /ai/status — jamais la clé en clair. */
    public Map<String, Object> statusMap() { ... }   (voir l'historique git du fichier)

4) CatalogSuggestController.java — deux types de catalogue sans appelant (supprimés)

    Le front ne demande jamais kind=level ni kind=study (il n'emploie que contract,
    mode, domain, city, place, school, nationality et le métier par défaut).
    Pire : LEVELS mélangeait diplômes et seniorité (« Bac+3 » à côté de « Junior »),
    ce qui a corrompu users.experience_level de trois comptes.

    private static final List<String> LEVELS = List.of(
            "Bac", "Bac+2", "Bac+3", "Bac+4", "Bac+5", "Doctorat", "Stagiaire", "Junior", "Confirmé", "Senior"
    );
    private static final List<String> STUDY_LEVELS = List.of(
            "Bac", "Bac+1", "Bac+2", "Bac+3", "Bac+4", "Bac+5", "Master", "Doctorat", "Autre"
    );
            case "level" -> rank(LEVELS, query);
            case "study" -> rank(STUDY_LEVELS, query);

Vocabulaire de référence restant pour users.experience_level (select « pf-level » de
frontend/app/candidate-profile.html) : Stagiaire, Junior, Confirmé, Senior, Lead, Manager.
