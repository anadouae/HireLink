# HireLink — Architecture des couches

Image : `HireLink_Architecture_Couches.png`

## Comment lire le schema (du haut vers le bas)

1. **Frontend** — l'utilisateur clique ; le JS envoie une requete HTTP
2. **Controller** — recoit la requete (pas de logique metier)
3. **Service** — applique les regles metier
4. **Repository** — lit / ecrit en base
5. **Entity + MySQL** — objets Java <-> tables

## Injection de dependances (IoC / DI)

Spring cree les objets et les branche automatiquement :
Controller recoit le Service, Service recoit le Repository.
