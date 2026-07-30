package medical

import munit.FunSuite
import medical.domain.Mesure
import java.time.LocalDateTime

class MainSpec extends FunSuite:

  private def mesure(patientId: String, service: String, timestamp: LocalDateTime, fc: Option[Int]): Mesure =
    Mesure(patientId, timestamp, service, 60, fc, Some(120), Some(80), Some(37.0), Some(97))

  test("executerPipeline enchaîne nettoyage, transformation et statistiques de bout en bout") {
    val t1 = LocalDateTime.parse("2026-07-28T08:00:00")
    val t2 = LocalDateTime.parse("2026-07-28T08:15:00")
    val t3 = LocalDateTime.parse("2026-07-28T08:30:00")

    val mesures = List(
      mesure("P001", "Chirurgie A", t1, Some(75)),
      mesure("P001", "Chirurgie A", t1, Some(75)), // doublon exact de la ligne précédente
      mesure("P002", "Chirurgie A", t2, Some(305)), // aberrant, doit être rejeté
      mesure("P003", "Reanimation", t3, Some(70))
    )

    val (rapportNettoyage, rapportJson) = Main.executerPipeline(mesures)

    // --- nettoyage ---
    assertEquals(rapportNettoyage.mesuresInitiales, 4)
    assertEquals(rapportNettoyage.doublonsSupprimes, 1)
    assertEquals(rapportNettoyage.lignesRejetees, 1)
    assertEquals(rapportNettoyage.mesuresFinales, 2) // P001 (une fois) + P003

    // --- statistiques / export ---
    assertEquals(rapportJson.services.map(_.service).toSet, Set("Chirurgie A", "Reanimation"))
    assertEquals(rapportJson.patients.map(_.patientId).toSet, Set("P001", "P003"))
  }
