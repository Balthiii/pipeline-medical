package medical.domain

import java.time.LocalDateTime

/** Un relevé de moniteur de chevet pour un patient, à un instant donné.
  *
  * Les champs physiologiques sont en `Option` car un capteur peut être
  * temporairement débranché : l'absence de valeur (donnée manquante) est
  * alors distincte d'une valeur aberrante, qui sera rejetée lors du nettoyage.
  */
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
