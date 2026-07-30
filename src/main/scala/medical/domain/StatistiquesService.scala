package medical.domain

/** Statistiques descriptives agrégées pour un service donné, ainsi que la
  * proportion de mesures classées en catégorie `Alerte`, utile pour
  * prioriser la surveillance infirmière (cf. sujet §3.4).
  */
final case class StatistiquesService(
    service: String,
    nbMesures: Int,
    nbPatients: Int,
    frequenceCardiaque: StatistiquesVitales,
    tensionSystolique: StatistiquesVitales,
    tensionDiastolique: StatistiquesVitales,
    temperature: StatistiquesVitales,
    spo2: StatistiquesVitales,
    proportionAlerte: Double
)
