package medical.core

import medical.domain.{Mesure, RapportNettoyage}
import java.time.LocalDateTime

/** Fonctions pures de nettoyage des mesures.
  *
  * Une valeur manquante (`None`, capteur débranché) est une donnée légitime
  * et ne rend jamais une mesure invalide. Une valeur hors plage
  * physiologiquement possible (cf. seuils du sujet §3.2) est en revanche
  * aberrante : la mesure entière est rejetée, avec le motif conservé dans
  * le [[RapportNettoyage]]. Aucune fonction ici ne modifie ses arguments :
  * chaque nettoyage produit une nouvelle collection.
  */
object Nettoyage:

  def frequenceCardiaqueValide(m: Mesure): Boolean =
    m.frequenceCardiaque.forall(fc => fc >= 30 && fc <= 220)

  def tensionSystoliqueValide(m: Mesure): Boolean =
    m.tensionSystolique.forall(t => t >= 40 && t <= 250)

  def tensionDiastoliqueValide(m: Mesure): Boolean =
    m.tensionDiastolique.forall(t => t >= 20 && t <= 150)

  def temperatureValide(m: Mesure): Boolean =
    m.temperature.forall(t => t >= 30.0 && t <= 42.0)

  def spo2Valide(m: Mesure): Boolean =
    m.spo2.forall(s => s >= 0 && s <= 100)

  private val reglesValidite: List[(String, Mesure => Boolean)] = List(
    "frequenceCardiaque" -> frequenceCardiaqueValide,
    "tensionSystolique" -> tensionSystoliqueValide,
    "tensionDiastolique" -> tensionDiastoliqueValide,
    "temperature" -> temperatureValide,
    "spo2" -> spo2Valide
  )

  def mesureValide(m: Mesure): Boolean =
    reglesValidite.forall { case (_, regle) => regle(m) }

  def motifsInvalidite(m: Mesure): List[String] =
    reglesValidite.collect { case (nom, regle) if !regle(m) => nom }

  /** Supprime les doublons (même patientId + même timestamp), en conservant
    * la première occurrence rencontrée. Renvoie la liste dédupliquée et le
    * nombre de mesures supprimées.
    */
  def supprimerDoublons(mesures: List[Mesure]): (List[Mesure], Int) =
    val (accInverse, _) = mesures.foldLeft((List.empty[Mesure], Set.empty[(String, LocalDateTime)])) {
      case ((acc, clesVues), m) =>
        val cle = (m.patientId, m.timestamp)
        if clesVues.contains(cle) then (acc, clesVues)
        else (m :: acc, clesVues + cle)
    }
    val dedupliquees = accInverse.reverse
    (dedupliquees, mesures.size - dedupliquees.size)

  /** Pipeline de nettoyage complet : dédoublonnage puis rejet des mesures
    * comportant au moins une valeur aberrante. Ne modifie jamais `mesures`.
    */
  def nettoyer(mesures: List[Mesure]): (List[Mesure], RapportNettoyage) =
    val (sansDoublons, nbDoublons) = supprimerDoublons(mesures)
    val (valides, invalides) = sansDoublons.partition(mesureValide)

    val motifs = invalides
      .flatMap(motifsInvalidite)
      .groupBy(identity)
      .view.mapValues(_.size)
      .toMap

    val rapport = RapportNettoyage(
      mesuresInitiales = mesures.size,
      doublonsSupprimes = nbDoublons,
      lignesRejetees = invalides.size,
      motifsRejet = motifs,
      mesuresFinales = valides.size
    )
    (valides, rapport)
