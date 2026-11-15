# Checklist livraison GitHub (à cocher avant autorisation de commit/push)

## Avant commit

- [ ] Aucun `application-local.properties` ni `.env` dans l’index (`git status`)
- [ ] Aucun dump `backup-db/*.sql` ni JAR runtime dans le commit (sauf si explicitement voulu)
- [ ] `git check-ignore backend/application-local.properties` → ignoré
- [ ] Lire `docs/HIRELINK_INSTALL_WINDOWS.md` : clone → MySQL → `mvn` → FE 5500
- [ ] Tests unitaires ciblés verts : apply validation + ExternalSourceCatalog web
- [ ] Optionnel : smoke isolé `scripts/smoke-web-intl-fat-jar.ps1` (ne touche pas 8081)

## Contenu attendu

- Code backend/frontend avec recherche Web internationale
- Correctifs apply 400 + attribution Remotive/Arbeitnow/Remote OK
- Docs install + rapports evidence (sans secrets)

## Après push (professeur)

1. `git clone …`
2. MySQL root (mdp vide ou `application-local`)
3. `mvn -DskipTests package` puis JAR / `spring-boot:run`
4. FE : serveur statique sur `:5500`
5. Comptes seed si `hirelink.seed.enabled=true` en local uniquement

## Non inclus sans autorisation

- Cutover 8081 vers JAR `F3C2547BBFF3`
- Commit / push GitHub
