package medical.core

import medical.domain.{
  MesureEnrichie,
  NiveauVigilance,
  StatistiquesPatient,
  StatistiquesService,
  StatistiquesVitales
}

/** Calcul de statistiques descriptives par patient et par service, à partir de
  * mesures nettoyées et transformées (`MesureEnrichie`). Toutes les agrégations
  * utilisent `fold`/`foldLeft` plutôt que des variables mutables ; seul le tri
  * nécessaire à la médiane utilise `sorted`, une opération intrinsèquement
  * séquentielle qui ne s'exprime pas naturellement en `fold`.
  */
object Statistiques:

  private def moyenne(valeurs: List[Double]): Double =
    valeurs.foldLeft(0.0)(_ + _) / valeurs.length

  private def mediane(valeurs: List[Double]): Double =
    val tries = valeurs.sorted
    val n = tries.length
    if n % 2 == 1 then tries(n / 2)
    else (tries(n / 2 - 1) + tries(n / 2)) / 2.0

  private def ecartType(valeurs: List[Double], moy: Double): Double =
    val sommeCarresEcarts =
      valeurs.foldLeft(0.0)((acc, v) => acc + math.pow(v - moy, 2))
    math.sqrt(sommeCarresEcarts / valeurs.length)

  private def minEtMax(valeurs: List[Double]): (Double, Double) =
    valeurs.foldLeft((valeurs.head, valeurs.head)) { case ((mn, mx), v) =>
      (math.min(mn, v), math.max(mx, v))
    }

  /** Statistiques descriptives d'un paramètre vital à partir des valeurs
    * présentes uniquement (les `None` d'origine ont déjà été écartés en amont
    * par `.flatMap` avant l'appel à cette fonction).
    */
  def statistiquesVitales(valeurs: List[Double]): StatistiquesVitales =
    if valeurs.isEmpty then StatistiquesVitales(None, None, None, None, None)
    else
      val moy = moyenne(valeurs)
      val (mn, mx) = minEtMax(valeurs)
      StatistiquesVitales(
        moyenne = Some(moy),
        mediane = Some(mediane(valeurs)),
        ecartType = Some(ecartType(valeurs, moy)),
        min = Some(mn),
        max = Some(mx)
      )

  private def statsChamp[A](
      mesuresEnrichies: List[MesureEnrichie],
      champ: MesureEnrichie => Option[A]
  )(using
      numeric: Numeric[A]
  ): StatistiquesVitales =
    statistiquesVitales(mesuresEnrichies.flatMap(champ).map(numeric.toDouble))

  private def frequenceCardiaqueStats(
      ms: List[MesureEnrichie]
  ): StatistiquesVitales =
    statsChamp(ms, _.mesure.frequenceCardiaque)

  private def tensionSystoliqueStats(
      ms: List[MesureEnrichie]
  ): StatistiquesVitales =
    statsChamp(ms, _.mesure.tensionSystolique)

  private def tensionDiastoliqueStats(
      ms: List[MesureEnrichie]
  ): StatistiquesVitales =
    statsChamp(ms, _.mesure.tensionDiastolique)

  private def temperatureStats(ms: List[MesureEnrichie]): StatistiquesVitales =
    statsChamp(ms, _.mesure.temperature)

  private def spo2Stats(ms: List[MesureEnrichie]): StatistiquesVitales =
    statsChamp(ms, _.mesure.spo2)

  /** Proportion de mesures classées en catégorie `Alerte`, entre 0.0 et 1.0.
    * Renvoie 0.0 pour un ensemble vide plutôt qu'une division par zéro.
    */
  def proportionAlerte(mesuresEnrichies: List[MesureEnrichie]): Double =
    if mesuresEnrichies.isEmpty then 0.0
    else
      val nbAlertes =
        mesuresEnrichies.count(_.niveauVigilance == NiveauVigilance.Alerte)
      nbAlertes.toDouble / mesuresEnrichies.length

  /** Statistiques agrégées pour un patient, à partir de ses mesures. */
  def statistiquesPatient(
      patientId: String,
      mesuresEnrichies: List[MesureEnrichie]
  ): StatistiquesPatient =
    StatistiquesPatient(
      patientId = patientId,
      nbMesures = mesuresEnrichies.length,
      frequenceCardiaque = frequenceCardiaqueStats(mesuresEnrichies),
      tensionSystolique = tensionSystoliqueStats(mesuresEnrichies),
      tensionDiastolique = tensionDiastoliqueStats(mesuresEnrichies),
      temperature = temperatureStats(mesuresEnrichies),
      spo2 = spo2Stats(mesuresEnrichies)
    )

  /** Statistiques agrégées pour un service, avec la proportion d'alerte. */
  def statistiquesService(
      service: String,
      mesuresEnrichies: List[MesureEnrichie]
  ): StatistiquesService =
    StatistiquesService(
      service = service,
      nbMesures = mesuresEnrichies.length,
      nbPatients = mesuresEnrichies.map(_.mesure.patientId).distinct.length,
      frequenceCardiaque = frequenceCardiaqueStats(mesuresEnrichies),
      tensionSystolique = tensionSystoliqueStats(mesuresEnrichies),
      tensionDiastolique = tensionDiastoliqueStats(mesuresEnrichies),
      temperature = temperatureStats(mesuresEnrichies),
      spo2 = spo2Stats(mesuresEnrichies),
      proportionAlerte = proportionAlerte(mesuresEnrichies)
    )

  /** Regroupe les mesures par patient (`groupBy`) et calcule les statistiques
    * de chacun.
    */
  def statistiquesParPatient(
      mesuresEnrichies: List[MesureEnrichie]
  ): Map[String, StatistiquesPatient] =
    mesuresEnrichies
      .groupBy(_.mesure.patientId)
      .map { case (patientId, ms) =>
        patientId -> statistiquesPatient(patientId, ms)
      }

  /** Regroupe les mesures par service (`groupBy`) et calcule les statistiques
    * de chacun, proportion d'alerte incluse.
    */
  def statistiquesParService(
      mesuresEnrichies: List[MesureEnrichie]
  ): Map[String, StatistiquesService] =
    mesuresEnrichies
      .groupBy(_.mesure.service)
      .map { case (service, ms) => service -> statistiquesService(service, ms) }

  /** Pour chaque service, la liste triée et sans doublon des patients ayant au
    * moins une mesure en catégorie `Alerte`. Utilisée par l'export JSON (sujet
    * §3.5 : "afficher, par service, la liste des patients en Alerte").
    */
  def patientsEnAlerteParService(
      mesuresEnrichies: List[MesureEnrichie]
  ): Map[String, List[String]] =
    mesuresEnrichies
      .filter(_.niveauVigilance == NiveauVigilance.Alerte)
      .groupBy(_.mesure.service)
      .view
      .mapValues(_.map(_.mesure.patientId).distinct.sorted)
      .toMap
