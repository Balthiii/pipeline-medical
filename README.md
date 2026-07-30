# Pipeline IA simplifié en Scala — Monitoring post-opératoire T

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

À ajouter en séance 2 : `data/dataset_patients_exemple.csv`, un jeu
d'une vingtaine de mesures sur quelques patients et services (ex.
Chirurgie A, Chirurgie B, Réanimation), avec les 3 défauts attendus
(valeurs manquantes, valeur aberrante, doublon exact) pour exercer le
module de nettoyage.

## 5. Organisation d'équipe

| Membre | Rôle pour cette séance | Rôle pressenti séances suivantes |
|---|---|---|
| Balthazar | Initialisation du GitHub | à définir |
| Patricia | Création du README | à définir |
| Marien | Modélisation du domaine | à définir |
| Meryem | Modélisation du domaine | à définir |

> Les rôles ne sont pas figés sur toute la durée du projet (cf. sujet §6).

## 6. Planning des 4 séances (10 h au total)

| Séance | Durée | Contenu | Livrable |
|---|---|---|---|
| **S1 — Cadrage & modélisation** | 2 h | Appropriation du sujet, dépôt Git, structure sbt, modèle de domaine | Dépôt initialisé, modèle validé, ce README |
| S2 — Cœur fonctionnel | 3 h | Nettoyage (fonctions pures), tests unitaires de base, revue de code croisée | Modules cœur + tests |
| S3 — Fonctionnalités avancées & intégration | 3 h | Statistiques, classification de vigilance, export JSON, intégration bout en bout | Application intégrée fonctionnelle |
| S4 — Finalisation & soutenance | 2 h | Tests finaux, rapport, support de soutenance | Rapport, code livré, support prêt |

## 7. Lancer le projet

Prérequis : sbt 1.10.7 (voir `project/build.properties`), JDK 11+.

```bash
sbt compile   # compile le modèle de domaine actuel
sbt run       # exécutera Main une fois le point d'entrée ajouté (séance 2)
sbt test      # tests unitaires (à partir de la séance 2)
```
