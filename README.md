# Pipeline IA simplifié en Scala — Monitoring post-opératoire

Projet tutoré Scala (10 h, équipe de 4 étudiants) — M1 IABD.

## 1. Contexte et objectif

Un service de chirurgie équipe chaque lit de moniteurs de chevet qui
mesurent en continu la fréquence cardiaque, la tension artérielle, la
température et la SpO2 des patients en phase de réveil. Toutes les 15
minutes, ces mesures sont exportées dans un CSV brut, qui contient des
valeurs manquantes, aberrantes et des doublons.

**Objectif** : construire un pipeline **fonctionnel et immuable** qui
nettoie ces relevés, calcule des statistiques descriptives et des
indicateurs de vigilance clinique, et exporte un rapport JSON exploitable
par un tableau de bord infirmier.

## 2. Architecture

```
Couche I/O (lecture CSV / écriture JSON)   ← seule couche à effet de bord
        │
        ▼
Couche cœur fonctionnel (100 % fonctions pures, immuable)
  ├─ Nettoyage        (valeurs manquantes / aberrantes / doublons)
  ├─ Transformation   (pression pulsée, classification de vigilance)
  └─ Statistiques     (moyenne, médiane, écart-type, groupBy patient/service)
        │
        ▼
Conversion JSON (DTOs dédiés, séparés du domaine)
        │
        ▼
Main : lecture CSV -> nettoyage -> transformation -> statistiques
       -> conversion JSON -> écriture disque
```

Principes respectés tout du long : immutabilité (`val`, case class,
collections immuables), fonctions pures, ADT + pattern matching, gestion
d'erreurs sans exception (`Option`/`Either`), séparation *functional core /
imperative shell* — les deux seuls effets de bord du pipeline sont la
lecture du CSV ([`LecteurCsv`](src/main/scala/medical/io/LecteurCsv.scala))
et l'écriture du JSON
([`EcrivainJson`](src/main/scala/medical/io/EcrivainJson.scala)).

## 3. Modèle de données

```scala
final case class Mesure(
  patientId: String,
  timestamp: LocalDateTime,
  service: String,
  age: Int,
  frequenceCardiaque: Option[Int],
  tensionSystolique: Option[Int],
  tensionDiastolique: Option[Int],
  temperature: Option[Double],
  spo2: Option[Int]
)

sealed trait NiveauVigilance
object NiveauVigilance {
  case object Normal extends NiveauVigilance
  case object Surveillance extends NiveauVigilance
  case object Alerte extends NiveauVigilance
}

final case class MesureEnrichie(
  mesure: Mesure,
  pressionPulsee: Option[Int] = None,
  niveauVigilance: NiveauVigilance = NiveauVigilance.Normal
)
```

Les champs vitaux de `Mesure` sont en `Option` : l'absence de valeur
(capteur débranché) est une donnée légitime à conserver, distincte d'une
valeur aberrante (hors plage physiologique), qui est rejetée au nettoyage.
`MesureEnrichie` n'est jamais construite en modifiant une `Mesure` : elle
l'enrichit avec les indicateurs calculés par la couche transformation.

Le reste du domaine (voir
[src/main/scala/medical/domain/](src/main/scala/medical/domain/)) est
composé uniquement de case class immuables :
[`RapportNettoyage`](src/main/scala/medical/domain/RapportNettoyage.scala)
(bilan du nettoyage),
[`StatistiquesVitales`](src/main/scala/medical/domain/StatistiquesVitales.scala)
(moyenne/médiane/écart-type/min/max d'un paramètre),
[`StatistiquesPatient`](src/main/scala/medical/domain/StatistiquesPatient.scala)
et
[`StatistiquesService`](src/main/scala/medical/domain/StatistiquesService.scala)
(agrégats par patient / par service, ce dernier incluant la proportion de
mesures en `Alerte`).

## 4. Jeu de données d'exemple

[`data/dataset_patients_exemple.csv`](data/dataset_patients_exemple.csv) —
25 mesures, 6 patients (P001 à P006), 3 services (Chirurgie A, Chirurgie B,
Réanimation). Contient les 3 défauts attendus : valeurs manquantes,
valeurs aberrantes (FC à 305 bpm et à -5 bpm, température à 0.0 °C) et
un doublon exact (P001 à 08:45:00).

## 5. Nettoyage

Le module [Nettoyage.scala](src/main/scala/medical/core/Nettoyage.scala)
implémente les fonctions pures de nettoyage, à partir des seuils
cliniques du sujet :

| Paramètre | Plage acceptée (sinon rejet) |
|---|---|
| Fréquence cardiaque (bpm) | 30 – 220 |
| Tension systolique (mmHg) | 40 – 250 |
| Tension diastolique (mmHg) | 20 – 150 |
| Température (°C) | 30 – 42 |
| SpO2 (%) | 0 – 100 |

Une valeur manquante (`None`) est toujours conservée. Une mesure ayant
au moins un champ hors plage est rejetée dans son ensemble ; le motif du
rejet (champ en cause) et le nombre de doublons supprimés (même
`patientId` + `timestamp`) sont renvoyés sous forme de valeur — un
[`RapportNettoyage`](src/main/scala/medical/domain/RapportNettoyage.scala)
— jamais affichés directement par le cœur fonctionnel. La lecture du CSV
est isolée dans
[LecteurCsv.scala](src/main/scala/medical/io/LecteurCsv.scala), seul
point d'effet de bord en entrée, qui renvoie `Either[String, List[Mesure]]`.

Sur le jeu d'exemple : 25 mesures lues → 1 doublon supprimé, 3 lignes
rejetées (2 pour fréquence cardiaque, 1 pour température) → 21 mesures
valides conservées.

Tests : [NettoyageSpec.scala](src/test/scala/medical/core/NettoyageSpec.scala)
(seuils cliniques, doublons, pipeline complet) et
[LecteurCsvSpec.scala](src/test/scala/medical/io/LecteurCsvSpec.scala)
(parsing CSV, gestion des erreurs).

## 6. Transformation et classification de vigilance

Le module
[Transformation.scala](src/main/scala/medical/core/Transformation.scala)
calcule, pour chaque `Mesure` déjà nettoyée, les indicateurs dérivés
regroupés dans une `MesureEnrichie` :

- **Pression pulsée** = tension systolique − tension diastolique
  (`None` dès qu'une des deux valeurs est manquante) ;
- **Niveau de vigilance** : le plus grave parmi les niveaux calculés
  champ par champ (une valeur absente est traitée comme `Normal`, gérée
  en amont par le nettoyage) :

| Paramètre | Normal | Surveillance | Alerte |
|---|---|---|---|
| Fréquence cardiaque (bpm) | 50 – 100 | sinon | — |
| Tension systolique (mmHg) | 90 – 140 | sinon | — |
| Tension diastolique (mmHg) | 60 – 90 | sinon | — |
| Température (°C) | 36.0 – 37.5 | sinon | — |
| SpO2 (%) | ≥ 95 | 90 – 94 | < 90 |

Le pipeline `Mesure => MesureEnrichie` est composé explicitement via
`andThen` (`initialiser andThen ajouterPressionPulsee andThen
ajouterVigilance`), puis appliqué à la collection via `.map`.

Tests : [TransformationSpec.scala](src/test/scala/medical/core/TransformationSpec.scala).

## 7. Statistiques descriptives

Le module
[Statistiques.scala](src/main/scala/medical/core/Statistiques.scala)
calcule, à partir des `MesureEnrichie`, pour chaque paramètre vital
(fréquence cardiaque, tensions, température, SpO2) : moyenne, médiane,
écart-type, min et max — via `fold`/`foldLeft`, `sorted` n'étant utilisé
que pour la médiane. Les valeurs absentes (`None`) sont écartées avant
calcul (`flatMap`) et un ensemble vide renvoie des statistiques à `None`
plutôt qu'une division par zéro.

Deux niveaux d'agrégation, obtenus par `groupBy` :

- `statistiquesParPatient` : statistiques par `patientId` ;
- `statistiquesParService` : statistiques par `service`, complétées par
  `proportionAlerte` (part des mesures classées `Alerte`) ;
- `patientsEnAlerteParService` : pour chaque service, la liste triée et
  dédupliquée des patients ayant au moins une mesure en `Alerte`.

Sur le jeu d'exemple, seul le service Réanimation présente des alertes
(SpO2 basse), avec le patient **P005** identifié dans
`patientsEnAlerte`.

Tests : [StatistiquesSpec.scala](src/test/scala/medical/core/StatistiquesSpec.scala).

## 8. Export JSON

Le rapport final est sérialisé en JSON par
[RapportJson.scala](src/main/scala/medical/io/RapportJson.scala), qui
définit :

- des **DTOs dédiés** (`StatistiquesVitalesJson`, `StatistiquesPatientJson`,
  `StatistiquesServiceJson`, `RapportExportJson`, tous `derives
  ReadWriter` via [upickle](https://github.com/com-lihaoyi/upickle)) —
  pour ne jamais coupler `medical.domain` à la bibliothèque de
  sérialisation ;
- `ConvertisseurJson`, une conversion **pure** du domaine vers ces DTOs,
  qui trie services et patients par identifiant pour produire un JSON
  stable et reproductible (diff Git propre entre deux exports).

L'écriture sur disque est isolée dans
[EcrivainJson.scala](src/main/scala/medical/io/EcrivainJson.scala), seul
point d'effet de bord en sortie, qui renvoie `Either[String, Unit]`.
Un exemple de rapport généré sur le jeu de données fourni est disponible
dans [`rapport.json`](rapport.json).

Tests : [RapportJsonSpec.scala](src/test/scala/medical/io/RapportJsonSpec.scala)
(conversion domaine → JSON) et
[EcrivainJsonSpec.scala](src/test/scala/medical/io/EcrivainJsonSpec.scala)
(écriture sur disque, gestion des erreurs).

## 9. Pipeline complet (`Main`)

[Main.scala](src/main/scala/medical/Main.scala) orchestre l'ensemble :

```
lecture CSV -> nettoyage -> transformation -> statistiques -> export JSON
```

`Main.executerPipeline` (nettoyage → transformation → statistiques →
conversion JSON) est une **fonction pure**, testable sans lire ni écrire
de fichier — seuls `LecteurCsv.lire` et `EcrivainJson.ecrire`, appelés
depuis `main`, sont des effets de bord. Les erreurs de chaque étape sont
propagées via une compréhension `for` sur `Either`, et le programme
affiche un résumé (mesures lues, doublons/rejets, nombre de
services/patients) avant de terminer avec un code de sortie non nul en
cas d'erreur.

Tests : [MainSpec.scala](src/test/scala/medical/MainSpec.scala).

## 10. Organisation d'équipe

Le projet est découpé en 4 parties de taille équivalente, qui suivent la
structure du sujet ; chaque membre a pris en charge une partie de bout en
bout (implémentation + tests) et la présente à la soutenance. Le travail
de chaque partie a fait l'objet d'une revue de code croisée par le reste
de l'équipe (cf. §11, séance 2).

| # | Membre | Partie prise en charge | Contenu |
|---|---|---|---|
| 1 | Meryem | Contexte & architecture | Cadrage du sujet, architecture en 3 couches (I/O isolé / cœur fonctionnel / orchestration), modèle de données (`Mesure`, `NiveauVigilance`) |
| 2 | Marien | Lecture & nettoyage | Lecture CSV avec `Either` (§5), valeurs manquantes vs aberrantes (`Option` + `forall`), suppression des doublons, seuils cliniques |
| 3 | Patricia | Transformation & statistiques | Pression pulsée, classification `NiveauVigilance` (composition `andThen`, §6), moyenne/médiane/écart-type via `foldLeft`, regroupement par patient/service (§7) |
| 4 | Balthazar | Export JSON & démo | Sérialisation avec upickle, DTOs séparés du domaine (§8), orchestration `Main` (§9), démo `sbt run` |

> Les rôles ne sont pas figés sur toute la durée du projet (cf. sujet §6).
> Répartition de la soutenance (10 min) : 4 parties de 2 min 30, dans
> l'ordre du tableau ci-dessus.

## 11. Planning des 4 séances (10 h au total)

| Séance | Durée | Contenu | Statut |
|---|---|---|---|
| **S1 — Cadrage & modélisation** | 2 h | Appropriation du sujet, dépôt Git, structure sbt, modèle de domaine | ✅ Fait |
| **S2 — Cœur fonctionnel** | 3 h | Nettoyage (fonctions pures), tests unitaires de base | ✅ Fait |
| **S3 — Fonctionnalités avancées & intégration** | 3 h | Transformation (pression pulsée, vigilance), statistiques, export JSON, intégration bout en bout (`Main`) | ✅ Fait |
| S4 — Finalisation & soutenance | 2 h | Tests finaux, rapport, support de soutenance | ⬜ À faire |

## 12. Lancer le projet

Prérequis : sbt 1.10.7 (voir `project/build.properties`), JDK 11+.

```bash
sbt run       # lit data/dataset_patients_exemple.csv, écrit rapport.json
sbt test      # tous les tests unitaires (nettoyage, transformation, statistiques, JSON, Main)
```

`Main` accepte deux arguments optionnels — chemin CSV d'entrée puis
chemin JSON de sortie (valeurs par défaut : `data/dataset_patients_exemple.csv`
et `rapport.json`) :

```bash
sbt "run chemin/vers/donnees.csv chemin/vers/sortie.json"
```
