# Pipeline IA simplifié en Scala — Monitoring post-opératoire

Projet tutoré Scala (10 h, équipe de 3 étudiants) — M1 IABD.

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

## 2. Architecture prévue

```
Couche I/O (lecture CSV / écriture JSON)   ← seule couche à effet de bord
        │
        ▼
Couche cœur fonctionnel (100 % fonctions pures, immuable)
  ├─ Nettoyage        (valeurs manquantes / aberrantes / doublons)
  ├─ Transformation   (indicateurs dérivés, catégorie de vigilance)
  └─ Statistiques     (moyenne, médiane, écart-type, groupBy service)
        │
        ▼
Pipeline principal : Dataset => Dataset => Statistiques => Unit
(composition explicite / andThen, erreurs propagées via Either)
```

Principes à respecter tout du long : immutabilité (`val`, case class,
collections immuables), fonctions pures, ADT + pattern matching, gestion
d'erreurs sans exception (`Option`/`Either`), séparation *functional core /
imperative shell*.

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
```

Les champs vitaux sont en `Option` : l'absence de valeur (capteur
débranché) est une donnée légitime à conserver, distincte d'une valeur
aberrante (hors plage physiologique), qui sera rejetée en séance 2.

Voir [Mesure.scala](src/main/scala/medical/domain/Mesure.scala) et
[NiveauVigilance.scala](src/main/scala/medical/domain/NiveauVigilance.scala).

## 4. Jeu de données d'exemple

[`data/dataset_patients_exemple.csv`](data/dataset_patients_exemple.csv) —
25 mesures, 6 patients, 3 services (Chirurgie A, Chirurgie B,
Réanimation). Contient les 3 défauts attendus : valeurs manquantes,
valeurs aberrantes (FC à 305 bpm et à -5 bpm, température à 0.0 °C) et
un doublon exact (P001 à 08:45:00).

## 5. Nettoyage (séance 2)

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

Tests unitaires : [NettoyageSpec.scala](src/test/scala/medical/core/NettoyageSpec.scala)
(seuils cliniques, doublons, pipeline complet) et
[LecteurCsvSpec.scala](src/test/scala/medical/io/LecteurCsvSpec.scala)
(parsing CSV, gestion des erreurs).

## 6. Organisation d'équipe

| Membre | Rôle pour cette séance | Rôle pressenti séances suivantes |
|---|---|---|
| Balthazar | Initialisation du GitHub | à définir |
| Patricia | Création du README | à définir |
| Marien | Modélisation du domaine | à définir |
| Meryem | Modélisation du domaine | à définir |

> Les rôles ne sont pas figés sur toute la durée du projet (cf. sujet §6).

## 7. Planning des 4 séances (10 h au total)

| Séance | Durée | Contenu | Livrable |
|---|---|---|---|
| **S1 — Cadrage & modélisation** | 2 h | Appropriation du sujet, dépôt Git, structure sbt, modèle de domaine | Dépôt initialisé, modèle validé, ce README |
| **S2 — Cœur fonctionnel** | 3 h | Nettoyage (fonctions pures), tests unitaires de base, revue de code croisée | Modules cœur + tests |
| S3 — Fonctionnalités avancées & intégration | 3 h | Statistiques, classification de vigilance, export JSON, intégration bout en bout | Application intégrée fonctionnelle |
| S4 — Finalisation & soutenance | 2 h | Tests finaux, rapport, support de soutenance | Rapport, code livré, support prêt |

## 8. Lancer le projet

Prérequis : sbt 1.10.7 (voir `project/build.properties`), JDK 11+.

```bash
sbt run       # lit data/dataset_patients_exemple.csv, nettoie et affiche le rapport
sbt test      # tests unitaires (seuils cliniques, doublons, parsing CSV)
```
