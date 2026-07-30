package medical.io

import medical.domain.{StatistiquesPatient, StatistiquesService, StatistiquesVitales}
import upickle.default.ReadWriter

/** DTOs dédiés à l'export JSON : uniquement des types JSON-natifs, pour ne
  * jamais coupler `medical.domain` à la bibliothèque de sérialisation.
  * `derives ReadWriter` est acceptable ici car ces classes n'existent que
  * pour la sérialisation, contrairement aux case class du domaine.
  */
final case class StatistiquesVitalesJson(
    moyenne: Option[Double],
    mediane: Option[Double],
    ecartType: Option[Double],
    min: Option[Double],
    max: Option[Double]
) derives ReadWriter

final case class StatistiquesPatientJson(
    patientId: String,
    nbMesures: Int,
    frequenceCardiaque: StatistiquesVitalesJson,
    tensionSystolique: StatistiquesVitalesJson,
    tensionDiastolique: StatistiquesVitalesJson,
    temperature: StatistiquesVitalesJson,
    spo2: StatistiquesVitalesJson
) derives ReadWriter

final case class StatistiquesServiceJson(
    service: String,
    nbMesures: Int,
    nbPatients: Int,
    frequenceCardiaque: StatistiquesVitalesJson,
    tensionSystolique: StatistiquesVitalesJson,
    tensionDiastolique: StatistiquesVitalesJson,
    temperature: StatistiquesVitalesJson,
    spo2: StatistiquesVitalesJson,
    proportionAlerte: Double,
    patientsEnAlerte: List[String]
) derives ReadWriter

/** Racine du rapport exporté : la liste des services (avec, pour chacun,
  * les patients en catégorie Alerte) et la liste des patients.
  */
final case class RapportExportJson(
    services: List[StatistiquesServiceJson],
    patients: List[StatistiquesPatientJson]
) derives ReadWriter

/** Conversion pure du domaine vers les DTOs JSON. Aucun effet de bord ici :
  * seule l'écriture sur disque (voir `EcrivainJson`) en est un.
  */
object ConvertisseurJson:

  private def versJson(s: StatistiquesVitales): StatistiquesVitalesJson =
    StatistiquesVitalesJson(s.moyenne, s.mediane, s.ecartType, s.min, s.max)

  def versJson(s: StatistiquesPatient): StatistiquesPatientJson =
    StatistiquesPatientJson(
      patientId = s.patientId,
      nbMesures = s.nbMesures,
      frequenceCardiaque = versJson(s.frequenceCardiaque),
      tensionSystolique = versJson(s.tensionSystolique),
      tensionDiastolique = versJson(s.tensionDiastolique),
      temperature = versJson(s.temperature),
      spo2 = versJson(s.spo2)
    )

  def versJson(s: StatistiquesService, patientsEnAlerte: List[String]): StatistiquesServiceJson =
    StatistiquesServiceJson(
      service = s.service,
      nbMesures = s.nbMesures,
      nbPatients = s.nbPatients,
      frequenceCardiaque = versJson(s.frequenceCardiaque),
      tensionSystolique = versJson(s.tensionSystolique),
      tensionDiastolique = versJson(s.tensionDiastolique),
      temperature = versJson(s.temperature),
      spo2 = versJson(s.spo2),
      proportionAlerte = s.proportionAlerte,
      patientsEnAlerte = patientsEnAlerte.sorted
    )

  /** Assemble le rapport complet à partir des statistiques déjà calculées
    * (`Statistiques.statistiquesParService/Patient/patientsEnAlerteParService`).
    * Les listes sont triées pour un JSON stable et reproductible (utile en
    * test et pour un diff Git propre entre deux exports).
    */
  def construireRapport(
      statsParService: Map[String, StatistiquesService],
      statsParPatient: Map[String, StatistiquesPatient],
      patientsEnAlerteParService: Map[String, List[String]]
  ): RapportExportJson =
    RapportExportJson(
      services = statsParService.toList
        .sortBy(_._1)
        .map { case (service, stats) =>
          versJson(stats, patientsEnAlerteParService.getOrElse(service, Nil))
        },
      patients = statsParPatient.toList
        .sortBy(_._1)
        .map { case (_, stats) => versJson(stats) }
    )
