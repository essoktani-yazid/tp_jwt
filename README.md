# 🛡️ Sécurisation d'une API REST par Tokens JWT avec Spring Security & Java 21

<p align="center">
  <img src="https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 4" />
  <img src="https://img.shields.io/badge/Spring%20Security-6.x-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" alt="Spring Security" />
  <img src="https://img.shields.io/badge/JJWT-0.13.0-007396?style=for-the-badge&logo=jsonwebtokens&logoColor=white" alt="JJWT" />
  <img src="https://img.shields.io/badge/H2-In--Memory%20DB-003B57?style=for-the-badge&logo=h2&logoColor=white" alt="H2 Database" />
  <img src="https://img.shields.io/badge/Postman-Tests%20Validés%20(7%2F7)-FF6C37?style=for-the-badge&logo=postman&logoColor=white" alt="Postman" />
</p>

## 📑 Table des Matières

1. [Vue d'Ensemble & Architecture Technique](#1-vue-densemble--architecture-technique)
   * [Principes Fondamentaux : Architecture Sans État (Stateless)](#principes-fondamentaux--architecture-sans-état-stateless)
   * [Structure Ternaire d'un Jeton JWT](#structure-ternaire-dun-jeton-jwt)
   * [Diagramme de Flux Cryptographique & Filtres HTTP](#diagramme-de-flux-cryptographique--filtres-http)
2. [Démarrage Rapide du Serveur](#2-démarrage-rapide-du-serveur)
3. [Cahier de Recette Postman (Guide des Tests & Validation)](#3-cahier-de-recette-postman-guide-des-tests--validation)
   * [Configuration de l'Environnement Postman](#configuration-de-lenvironnement-postman)
   * [Test 1 : Inscription d'un Utilisateur (`POST /auth/register`)](#test-1--inscription-dun-utilisateur-post-authregister)
   * [Test 2 : Authentification & Récupération du Token (`POST /auth/login`)](#test-2--authentification--récupération-du-token-post-authlogin)
   * [Test 3 : Décodage & Analyse Cryptographique du Token](#test-3--décodage--analyse-cryptographique-du-token)
   * [Test 4 : Accès Autorisé à une Ressource Protégée (`GET /api/profile`)](#test-4--accès-autorisé-à-une-ressource-protégée-get-apiprofile)
   * [Test 5 : Accès Autorisé au Point d'Entrée Global (`GET /api/hello`)](#test-5--accès-autorisé-au-point-dentrée-global-get-apihello)
   * [Test 6 : Rejet en Cas d'Absence de Jeton (`403 Forbidden`)](#test-6--rejet-en-cas-dabsence-de-jeton-403-forbidden)
   * [Test 7 : Rejet lors d'une Altération / Falsification de Signature](#test-7--rejet-lors-dune-altération--falsification-de-signature)
4. [Audit Navigateur : Console Base de Données H2](#4-audit-navigateur--console-base-de-données-h2)
   * [Accès et Paramètres de Connexion](#accès-et-paramètres-de-connexion)
   * [Vérification du Hachage BCrypt en Base](#vérification-du-hachage-bcrypt-en-base)
5. [Matrice de Recette & Checklist Finale](#5-matrice-de-recette--checklist-finale)

---

## 1. Vue d'Ensemble & Architecture Technique

### Principes Fondamentaux : Architecture Sans État (Stateless)

Ce projet implémente un système complet d'authentification et d'autorisation sécurisé pour API REST en utilisant **Spring Boot 4**, **Java 21 LTS**, et la bibliothèque cryptographique de référence **JJWT 0.13.0**.

Contrairement aux architectures traditionnelles basées sur les sessions HTTP (`JSESSIONID`) qui consomment de la mémoire serveur et créent des dépendances d'état bloquantes pour le scaling horizontal, cette solution adopte une approche **strictement Stateless** :
* **Mémoire Serveur Neutre ($0$ octet stocké)** : Aucun état de session n'est conservé côté backend.
* **Autonomie Cryptographique** : Chaque requête porte son propre contexte d'habilitation via l'en-tête `Authorization: Bearer <token>`.
* **Scalabilité Horizontale Native** : N'importe quelle instance backend peut valider instantanément un token sans interroger de session partagée (Redis ou Sticky Sessions).

### Structure Ternaire d'un Jeton JWT

Un JSON Web Token (RFC 7519) se compose de 3 segments séparés par des points (`.`):

$$\mathbf{Token} = \underbrace{\text{Base64URL}(\text{Header})}_{\text{\color{red}1. Métadonnées / Algorithme}} \quad\mathbf{.}\quad \underbrace{\text{Base64URL}(\text{Payload})}_{\text{\color{purple}2. Assertions (Claims)}} \quad\mathbf{.}\quad \underbrace{\text{Signature}}_{\text{\color{green}3. Empreinte HMAC-SHA}}$$

Le calcul de la signature garantit l'intégrité et l'authenticité :
$$\text{Signature} = \text{HMAC-SHA}\Big(\text{Base64URL}(\text{Header}) + \texttt{"."} + \text{Base64URL}(\text{Payload}),\; \mathbf{K_{\text{secret}}}\Big)$$

### Diagramme de Flux Cryptographique & Filtres HTTP

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client (Postman / Web)
    participant Filter as JwtFilter (OncePerRequest)
    participant AuthCtrl as AuthController
    participant SecMgr as AuthenticationManager
    participant JwtSvc as JwtService (JJWT 0.13.0)
    participant DB as H2 Database

    Note over Client, DB: Phase 1 : Inscription & Authentification
    Client->>AuthCtrl: POST /auth/register (email, password)
    AuthCtrl->>DB: Sauvegarde AppUser (Password haché BCrypt)
    DB-->>AuthCtrl: OK
    AuthCtrl-->>Client: 200 OK ("Utilisateur cree")

    Client->>AuthCtrl: POST /auth/login (email, password)
    AuthCtrl->>SecMgr: authenticate(email, rawPassword)
    SecMgr->>DB: Vérification BCrypt
    DB-->>SecMgr: Identifiants Valides
    AuthCtrl->>JwtSvc: generateToken(UserDetails)
    JwtSvc-->>AuthCtrl: Token JWT signé (HS384)
    AuthCtrl-->>Client: 200 OK { token: "eyJhbGci..." }

    Note over Client, DB: Phase 2 : Requête Protégée avec Bearer Token
    Client->>Filter: GET /api/profile (Header: Authorization: Bearer <token>)
    Filter->>JwtSvc: extractUsername(token) & verifyWith(SecretKey)
    alt Signature Valide & Non Expiré
        JwtSvc-->>Filter: Claims extraits (Username, Roles)
        Filter->>Filter: Injection dans SecurityContextHolder
        Filter->>Client: 200 OK ("Utilisateur connecte : essoktani.yazid@gmail.com")
    else Signature Altérée ou Absente
        JwtSvc-->>Filter: SignatureException / Token Invalide
        Filter-->>Client: 403 Forbidden
    end
```

---

## 2. Démarrage Rapide du Serveur

### Prérequis
* **Java Development Kit (JDK) 21** ou supérieur.
* **Apache Maven 3.9+** (ou utilisation du wrapper `./mvnw` inclus).
* **Postman** (pour l'exécution du cahier de recette).
* Navigateur Web moderne (pour la console H2).

### Lancement de l'Application

Ouvrez un terminal dans le répertoire racine du projet :

```bash
# Compilation et démarrage avec Maven Wrapper
./mvnw spring-boot:run
```

Ou sous Windows :
```cmd
mvnw.cmd spring-boot:run
```

**Vérification de la mise en écoute :**
```text
2026-10-14T20:15:30.120+01:00  INFO [TP_JWT] Tomcat initialized with port 8080 (http)
2026-10-14T20:15:30.450+01:00  INFO [TP_JWT] H2 console available at '/h2-console'. Database at 'jdbc:h2:mem:securitydb'
2026-10-14T20:15:31.025+01:00  INFO [TP_JWT] Started TpJwtApplication in 1.842 seconds
```

---

## 3. Tests & Validation

### Test 1 : Inscription d'un Utilisateur (`POST /auth/register`)

#### 🎯 Objectif
Créer un compte utilisateur avec un mot de passe qui sera automatiquement haché via `BCryptPasswordEncoder` avant persistance en base H2.

#### ⚙️ Configuration Postman
* **Méthode** : `POST`
* **URL** : `http://localhost:8080/auth/register`
* **Headers** :
  * `Content-Type: application/json`
* **Body** (`raw JSON`) :
```json
{
  "email": "essoktani.yazid@gmail.com",
  "password": "essoktani"
}
```

#### 📥 Commande cURL Équivalente
```bash
curl -i -X POST http://localhost:8080/auth/register \
     -H "Content-Type: application/json" \
     -d '{"email": "essoktani.yazid@gmail.com", "password": "essoktani"}'
```

#### ✅ Résultat Observé
* **Code HTTP** : `200 OK`
* **Temps de Réponse** : `1076 ms`
* **Taille de la réponse** : `356 B`
* **Body de réponse** : `Utilisateur cree`

---

<div align="center">
  <table width="100%">
    <tr>
      <td align="center">
        <img src="screenshots/01_postman_register.png" alt="Capture Postman : Inscription Utilisateur" width="850" />
        <br />
        <em><b>Figure 1</b> : Envoi de la requête d'inscription POST /auth/register et confirmation HTTP 200 OK dans Postman.</em>
      </td>
    </tr>
  </table>
</div>

| Critère de Validation | Spécification | Résultat Observé | Statut |
| :--- | :--- | :--- | :---: |
| **Code Statut HTTP** | `200 OK` | `200 OK` (1076 ms) | ✅ Validé |
| **Corps de Réponse** | `"Utilisateur cree"` | `Utilisateur cree` | ✅ Validé |
| **Persistance H2** | Enregistrement créé dans la table `USERS` | Confirmé (ID: 1) | ✅ Validé |

---

### Test 2 : Authentification & Récupération du Token (`POST /auth/login`)

#### 🎯 Objectif
Soumettre les identifiants utilisateur pour validation cryptographique et générer un jeton JWT valide signé en HMAC par `JwtService`.

#### ⚙️ Configuration Postman
* **Méthode** : `POST`
* **URL** : `http://localhost:8080/auth/login`
* **Headers** :
  * `Content-Type: application/json`
* **Body** (`raw JSON`) :
```json
{
  "email": "essoktani.yazid@gmail.com",
  "password": "essoktani"
}
```

#### 📥 Commande cURL Équivalente
```bash
curl -i -X POST http://localhost:8080/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email": "essoktani.yazid@gmail.com", "password": "essoktani"}'
```

#### ✅ Résultat Observé
* **Code HTTP** : `200 OK`
* **Temps de Réponse** : `583 ms`
* **Taille de la réponse** : `523 B`
* **Format Réponse** :
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJlc3Nva3RhbmkueWF6aWRAZ21haWwuY29tIiwiaWF0IjoxNzYwNTM4MDU2LCJleHAiOjE3OTIwODk2NTZ9.Ty_0DWtKQmhfoG3iZw3TlB03mTDRJJdLM2EdWKTjGryJvSJ8Jt83WyyDTEHW0ApN"
}
```

---

<div align="center">
  <table width="100%">
    <tr>
      <td align="center">
        <img src="screenshots/02_postman_login.png" alt="Capture Postman : Login & Token JWT" width="850" />
        <br />
        <em><b>Figure 2</b> : Authentification réussie via POST /auth/login et génération du jeton JWT porteur.</em>
      </td>
    </tr>
  </table>
</div>

| Critère de Validation | Spécification | Résultat Observé | Statut |
| :--- | :--- | :--- | :---: |
| **Code Statut HTTP** | `200 OK` | `200 OK` (583 ms) | ✅ Validé |
| **Propriété `token`** | Chaîne non vide (3 segments Base64URL) | Jeton JWT complet généré | ✅ Validé |
| **Algorithme Déclaré** | HMAC (`HS384`) | Détecté et validé par JJWT | ✅ Validé |

---

### Test 3 : Décodage & Analyse Cryptographique du Token

#### 🎯 Objectif
Inspecter la structure interne du jeton généré sur [jwt.io](https://jwt.io/) pour valider les métadonnées, le sujet (`sub`), les timestamps et la conformité des claims.

#### 🔍 Analyse des Segments Décodés

```json
// 1. HEADER (Métadonnées & Algorithme)
{
  "alg": "HS384"
}

// 2. PAYLOAD (Claims Utilisateur & Expiration)
{
  "sub": "essoktani.yazid@gmail.com",
  "iat": 1792086056,
  "exp": 1792089656
}
```

* **Durée de Validité Calculée** :
  $$\Delta t = 1792089656 - 1792086056 = 3600 \text{ secondes (exactement 1 heure)}$$
* **Sujet (`sub`)** : Identifie formellement l'utilisateur `essoktani.yazid@gmail.com`.

---

<div align="center">
  <table width="100%">
    <tr>
      <td align="center">
        <img src="screenshots/03_jwt_io_decode.png" alt="Capture jwt.io : Décodage et Analyse" width="850" />
        <br />
        <em><b>Figure 3</b> : Décomposition du jeton JWT sur le débogueur officiel jwt.io (Header HS384 et Claims décodés).</em>
      </td>
    </tr>
  </table>
</div>

---

### Test 4 : Accès Autorisé à une Ressource Protégée (`GET /api/profile`)

#### 🎯 Objectif
Vérifier que le filtre `JwtFilter` intercepte correctement l'en-tête `Authorization: Bearer <token>`, valide la signature cryptographique et alimente le `SecurityContextHolder` avec l'identité de l'utilisateur authentifié.

#### ⚙️ Configuration Postman
* **Méthode** : `GET`
* **URL** : `http://localhost:8080/api/profile`
* **Onglet Authorization** :
  * **Type** : `Bearer Token`
  * **Token** : `eyJhbGciOiJIUzM4NCJ9...` *(ou variable `{{token}}`)*

#### 📥 Commande cURL Équivalente
```bash
TOKEN="eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJlc3Nva3RhbmkueWF6aWRAZ21haWwuY29tIiwiaWF0IjoxNzYwNTM4MDU2LCJleHAiOjE3OTIwODk2NTZ9.Ty_0DWtKQmhfoG3iZw3TlB03mTDRJJdLM2EdWKTjGryJvSJ8Jt83WyyDTEHW0ApN"

curl -i -X GET http://localhost:8080/api/profile \
     -H "Authorization: Bearer $TOKEN"
```

#### ✅ Résultat Observé
* **Code HTTP** : `200 OK`
* **Temps de Réponse** : `14 ms`
* **Taille de la réponse** : `388 B`
* **Corps de réponse** : `Utilisateur connecte : essoktani.yazid@gmail.com`

---

<div align="center">
  <table width="100%">
    <tr>
      <td align="center">
        <img src="screenshots/04_postman_profile_success.png" alt="Capture Postman : Accès Profil 200 OK" width="850" />
        <br />
        <em><b>Figure 4</b> : Accès autorisé à l'endpoint sécurisé /api/profile grâce au Bearer Token (200 OK en 14 ms).</em>
      </td>
    </tr>
  </table>
</div>

| Critère de Validation | Spécification | Résultat Observé | Statut |
| :--- | :--- | :--- | :---: |
| **Code Statut HTTP** | `200 OK` | `200 OK` (14 ms) | ✅ Validé |
| **Corps de Réponse** | Contient l'email authentifié | `Utilisateur connecte : essoktani.yazid@gmail.com` | ✅ Validé |
| **Contexte Sécurité** | Résolution via `Authentication.getName()` | Parfaitement résolu | ✅ Validé |

---

### Test 5 : Accès Autorisé au Point d'Entrée Global (`GET /api/hello`)

#### 🎯 Objectif
Valider l'accès à une deuxième route protégée sous le préfixe `/api/**` sous contrôle du filtre d'authentification.

#### ⚙️ Configuration Postman
* **Méthode** : `GET`
* **URL** : `http://localhost:8080/api/hello`
* **Authorization** : `Bearer Token` (`{{token}}`)

#### 📥 Commande cURL Équivalente
```bash
curl -i -X GET http://localhost:8080/api/hello \
     -H "Authorization: Bearer $TOKEN"
```

#### ✅ Résultat Observé
* **Code HTTP** : `200 OK`
* **Temps de Réponse** : `16 ms`
* **Taille de la réponse** : `370 B`
* **Corps de réponse** : `Bienvenue dans l'API securisee`

---

<div align="center">
  <table width="100%">
    <tr>
      <td align="center">
        <img src="screenshots/05_postman_hello_success.png" alt="Capture Postman : Accès Hello 200 OK" width="850" />
        <br />
        <em><b>Figure 5</b> : Réponse confirmant l'accès au service général sécurisé /api/hello (200 OK en 16 ms).</em>
      </td>
    </tr>
  </table>
</div>

---

### Test 6 : Rejet en Cas d'Absence de Jeton (`403 Forbidden`)

#### 🎯 Objectif
Éprouver la barrière de sécurité de Spring Security lorsque le client interroge l'API protégée sans fournir de jeton Bearer valide.

#### ⚙️ Configuration Postman
* **Méthode** : `GET`
* **URL** : `http://localhost:8080/api/hello`
* **Authorization** : Bearer Token non configuré (ou valeur fictive `"token"`)

#### 📥 Commande cURL Équivalente
```bash
curl -i -X GET http://localhost:8080/api/hello
```

#### ✅ Résultat Observé
* **Code HTTP** : `403 Forbidden`
* **Temps de Réponse** : `8 ms`
* **Taille de la réponse** : `440 B`
* **Corps de réponse** : *(vide)*

---

<div align="center">
  <table width="100%">
    <tr>
      <td align="center">
        <img src="screenshots/06_postman_no_token_403.png" alt="Capture Postman : Rejet Sans Token 403" width="850" />
        <br />
        <em><b>Figure 6</b> : Rejet catégorique 403 Forbidden suite à l'absence de jeton Bearer valide (8 ms).</em>
      </td>
    </tr>
  </table>
</div>

| Critère de Validation | Spécification | Résultat Observé | Statut |
| :--- | :--- | :--- | :---: |
| **Code Statut HTTP** | `403 Forbidden` | `403 Forbidden` (8 ms) | ✅ Validé |
| **Protection Route** | `/api/**` hermétique sans token | Blocage immédiat par la chaîne de sécurité | ✅ Validé |

---

### Test 7 : Rejet lors d'une Altération / Falsification de Signature

#### 🎯 Objectif
Démontrer la protection mathématique contre les attaques par falsification : si un tiers intercepte le token et modifie ne serait-ce qu'un seul caractère dans la charge utile ou la signature, le serveur recalcule l'empreinte HMAC locale et rejette instantanément la requête.

#### ⚙️ Démarche de Test
1. Prenons un jeton dont la signature a été altérée (modification du dernier caractère).
2. Émettons la requête `GET /api/hello` avec ce token corrompu :

```bash
# Exemple de requête avec token altéré
curl -i -X GET http://localhost:8080/api/hello \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...ALTERED"
```

#### ✅ Résultat Observé
* **Code HTTP** : `403 Forbidden`
* **Temps de Réponse** : `9 ms`
* **Taille de la réponse** : `440 B`
* **Corps de Réponse JSON** :
```json
{
  "timestamp": "2026-10-15T17:47:23.471Z",
  "status": 403,
  "error": "Forbidden",
  "path": "/api/hello"
}
```

---

<div align="center">
  <table width="100%">
    <tr>
      <td align="center">
        <img src="screenshots/07_postman_tampered_token.png" alt="Capture Postman : Signature Falsifiée Rejetée" width="850" />
        <br />
        <em><b>Figure 7</b> : Blocage instantané d'un jeton altéré par détection d'invalidité de signature cryptographique (403 Forbidden).</em>
      </td>
    </tr>
  </table>
</div>

| Critère de Validation | Spécification | Résultat Observé | Statut |
| :--- | :--- | :--- | :---: |
| **Code Statut HTTP** | `403 Forbidden` | `403 Forbidden` (9 ms) | ✅ Validé |
| **Détection Falsification** | Signature locale $\neq$ Signature reçue | Exception Signature levée et gérée | ✅ Validé |

---

## 4. Audit Navigateur : Console Base de Données H2

### Accès et Paramètres de Connexion

L'application intègre une base de données mémoire H2 accessible via une console Web interactive.

1. Ouvrez votre navigateur Web à l'adresse : [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
2. Saisissez les paramètres de connexion stricts (définis dans `application.properties`) :

| Paramètre H2 | Valeur Requise |
| :--- | :--- |
| **Saved Settings** | `Generic H2 (Embedded)` |
| **Driver Class** | `org.h2.Driver` |
| **JDBC URL** | `jdbc:h2:mem:securitydb` |
| **User Name** | `sa` |
| **Password** | *(Laisser totalement vide)* |

---

<div align="center">
  <table width="100%">
    <tr>
      <td align="center">
        <img src="screenshots/09_h2_console_login.png" alt="Capture Navigateur : Mire de Connexion H2 Console" width="850" />
        <br />
        <em><b>Figure 8</b> : Interface de connexion à la console web H2 Database (URL JDBC: jdbc:h2:mem:securitydb).</em>
      </td>
    </tr>
  </table>
</div>

---

### Vérification du Hachage BCrypt en Base

Une fois connecté, exécutez la requête SQL d'inspection :

```sql
SELECT * FROM USERS;
```

#### 🔬 Analyse de Sécurité des Données Persistées

L'exécution de la requête retourne l'enregistrement utilisateur créé lors du **Test 1** :

| ID | EMAIL | PASSWORD | ROLE |
| :-: | :--- | :--- | :-: |
| `1` | `essoktani.yazid@gmail.com` | `$2a$10$kV8gVQMuY7IX/jJ/onkb4uEdgzfh6kfdASR.Aa3dGqB9LrFVglQim` | `USER` |

* **Algorithme Détecté** : Le préfixe `$2a$` certifie l'usage de l'algorithme de hachage robuste **BCrypt**.
* **Facteur de Coût** : Le paramètre `10` indique $2^{10} = 1024$ itérations de renforcement de clé, protégeant activement contre le cassage par dictionnaires et tables arc-en-ciel (*Rainbow Tables*).
* **Confidentialité Totale** : Le mot de passe en clair (`essoktani`) n'est **jamais stocké** sur le disque ou en mémoire persistante.

---

<div align="center">
  <table width="100%">
    <tr>
      <td align="center">
        <img src="screenshots/10_h2_users_table.png" alt="Capture Navigateur : Table USERS avec Hash BCrypt" width="850" />
        <br />
        <em><b>Figure 9</b> : Preuve de persistance de l'utilisateur avec son empreinte BCrypt $2a$10$... dans la table USERS (1 row, 3 ms).</em>
      </td>
    </tr>
  </table>
</div>

---

## 5. Matrice de Recette & Checklist Finale

### Synthèse des Points de Terminaison (Endpoints API)

| Méthode | URI | Accès Sécurité | Description Fonctionnelle | Code Statut | Validation |
| :---: | :--- | :---: | :--- | :---: | :---: |
| `POST` | `/auth/register` | `permitAll()` | Inscription d'un nouvel utilisateur | `200 OK` | ✅ Validé (1076 ms) |
| `POST` | `/auth/login` | `permitAll()` | Authentification & génération du token JWT | `200 OK` | ✅ Validé (583 ms) |
| `GET` | `/api/profile` | `authenticated()` | Récupération de l'identité du profil connecté | `200 OK` | ✅ Validé (14 ms) |
| `GET` | `/api/hello` | `authenticated()` | Message d'accueil de l'espace sécurisé | `200 OK` | ✅ Validé (16 ms) |
| `GET` | `/api/hello` | `Sans token` | Rejet en l'absence de jeton Bearer | `403 Forbidden` | ✅ Validé (8 ms) |
| `GET` | `/api/hello` | `Token altéré` | Rejet en cas de signature cryptographique corrompue | `403 Forbidden` | ✅ Validé (9 ms) |
| `GET/POST`| `/h2-console/**` | `permitAll()` | Console d'administration Web de la base H2 | `200 OK` | ✅ Validé (3 ms) |

---

### Checklist de Validation Finale

- [x] L'application Spring Boot compile et démarre sans aucune erreur (`Tomcat initialized on 8080`).
- [x] Le compte utilisateur `essoktani.yazid@gmail.com` a été créé via `POST /auth/register` (Figure 1).
- [x] L'appel `POST /auth/login` génère un jeton JWT valide en HMAC (Figure 2).
- [x] Le jeton a été inspecté sur [jwt.io](https://jwt.io/) et ses claims (`sub`, `iat`, `exp`) sont conformes (Figure 3).
- [x] La requête `GET /api/profile` avec le Bearer Token retourne `200 OK` et le bon email utilisateur (Figure 4).
- [x] La requête `GET /api/hello` avec le Bearer Token confirme l'accès autorisé (Figure 5).
- [x] L'interrogation de `/api/hello` sans token retourne immédiatement `403 Forbidden` (Figure 6).
- [x] La falsification de la signature du token déclenche un rejet catégorique `403 Forbidden` (Figure 7).
- [x] La console H2 (`/h2-console`) a été connectée avec succès sur `jdbc:h2:mem:securitydb` (Figure 8).
- [x] La requête `SELECT * FROM USERS;` confirme la présence de l'utilisateur avec mot de passe haché par BCrypt `$2a$10$...` (Figure 9).
- [x] Toutes les captures d'écran des tests sont intégrées et sauvegardées dans le répertoire `screenshots/`.
