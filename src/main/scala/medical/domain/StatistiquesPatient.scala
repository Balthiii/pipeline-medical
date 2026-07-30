package medical.domain

/** Statistiques descriptives agrégées pour un patient donné, calculées sur
  * l'ensemble de ses mesures nettoyées et transformées.
  */
final case class StatistiquesPatient(
    patientId: String,
    nbMesures: Int,
    frequenceCardiaque: StatistiquesVitales,
    tensionSystolique: StatistiquesVitales,
    tensionDiastolique: StatistiquesVitales,
    temperature: StatistiquesVitales,
    spo2: StatistiquesVitales
)
