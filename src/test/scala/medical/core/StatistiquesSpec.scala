package medical.core

import munit.FunSuite
import medical.domain.{Mesure, MesureEnrichie, NiveauVigilance}
import java.time.LocalDateTime

class StatistiquesSpec extends FunSuite:

  private val delta = 0.0001

  // --- statistiquesVitales (fonction générique sur List[Double]) ---

  test("statistiquesVitales sur une liste vide renvoie tout à None") {
    val stats = Statistiques.statistiquesVitales(List.empty)
    assertEquals(stats.moyenne, None)
    assertEquals(stats.mediane, None)
    assertEquals(stats.ecartType, None)
    assertEquals(stats.min, None)
    assertEquals(stats.max, None)
  }

  test("statistiquesVitales calcule moyenne, écart-type, min, max (nombre impair de valeurs)") {
    val stats = Statistiques.statistiquesVitales(List(70.0, 80.0, 90.0))
    assertEqualsDouble(stats.moyenne.get, 80.0, delta)
    assertEqualsDouble(stats.mediane.get, 80.0, delta)
    assertEqualsDouble(stats.ecartType.get, math.sqrt(200.0 / 3.0), delta)
    assertEqualsDouble(stats.min.get, 70.0, delta)
    assertEqualsDouble(stats.max.get, 90.0, delta)
  }

  test("statistiquesVitales calcule la médiane par moyenne des deux valeurs centrales (nombre pair)") {
    val stats = Statistiques.statistiquesVitales(List(60.0, 70.0, 80.0, 100.0))
    assertEqualsDouble(stats.moyenne.get, 77.5, delta)
    assertEqualsDouble(stats.mediane.get, 75.0, delta) // (70 + 80) / 2, différent de la moyenne
  }

  // --- helpers pour construire des MesureEnrichie de test ---

  private def mesureEnrichie(
      patientId: String,
      service: String,
      fc: Option[Int],
      niveau: NiveauVigilance = NiveauVigilance.Normal
  ): MesureEnrichie =
    val m = Mesure(
      patientId = patientId,
      timestamp = LocalDateTime.parse("2026-07-28T08:00:00"),
      service = service,
      age = 60,
      frequenceCardiaque = fc,
      tensionSystolique = Some(120),
      tensionDiastolique = Some(80),
      temperature = Some(37.0),
      spo2 = Some(97)
    )
    MesureEnrichie(mesure = m, pressionPulsee = Some(40), niveauVigilance = niveau)

  // --- proportionAlerte ---

  test("proportionAlerte renvoie 0.0 pour une liste vide") {
    assertEqualsDouble(Statistiques.proportionAlerte(List.empty), 0.0, delta)
  }

  test("proportionAlerte calcule la proportion de mesures en Alerte") {
    val mesures = List(
      mesureEnrichie("P001", "Chirurgie A", Some(75), NiveauVigilance.Alerte),
      mesureEnrichie("P001", "Chirurgie A", Some(80), NiveauVigilance.Normal),
      mesureEnrichie("P002", "Chirurgie A", Some(85), NiveauVigilance.Normal),
      mesureEnrichie("P002", "Chirurgie A", Some(90), NiveauVigilance.Alerte)
    )
    assertEqualsDouble(Statistiques.proportionAlerte(mesures), 0.5, delta)
  }

  // --- statistiquesParPatient ---

  test("statistiquesParPatient regroupe correctement par patientId") {
    val mesures = List(
      mesureEnrichie("P001", "Chirurgie A", Some(70)),
      mesureEnrichie("P001", "Chirurgie A", Some(90)),
      mesureEnrichie("P002", "Chirurgie A", Some(100))
    )
    val stats = Statistiques.statistiquesParPatient(mesures)

    assertEquals(stats.keySet, Set("P001", "P002"))
    assertEquals(stats("P001").nbMesures, 2)
    assertEqualsDouble(stats("P001").frequenceCardiaque.moyenne.get, 80.0, delta)
    assertEquals(stats("P002").nbMesures, 1)
  }

  // --- statistiquesParService ---

  test("statistiquesParService regroupe par service, compte les patients distincts et la proportion d'alerte") {
    val mesures = List(
      mesureEnrichie("P001", "Chirurgie A", Some(70), NiveauVigilance.Alerte),
      mesureEnrichie("P001", "Chirurgie A", Some(90), NiveauVigilance.Normal),
      mesureEnrichie("P002", "Chirurgie A", Some(80), NiveauVigilance.Normal),
      mesureEnrichie("P003", "Reanimation", Some(100), NiveauVigilance.Normal)
    )
    val stats = Statistiques.statistiquesParService(mesures)

    assertEquals(stats.keySet, Set("Chirurgie A", "Reanimation"))

    val chirurgieA = stats("Chirurgie A")
    assertEquals(chirurgieA.nbMesures, 3)
    assertEquals(chirurgieA.nbPatients, 2) // P001 (x2) + P002, comptés une fois chacun
    assertEqualsDouble(chirurgieA.proportionAlerte, 1.0 / 3.0, delta)

    val reanimation = stats("Reanimation")
    assertEquals(reanimation.nbMesures, 1)
    assertEqualsDouble(reanimation.proportionAlerte, 0.0, delta)
  }

  test("statistiquesVitales ignore les champs manquants (None écartés avant l'appel)") {
    val mesures = List(
      mesureEnrichie("P001", "Chirurgie A", Some(70)),
      mesureEnrichie("P001", "Chirurgie A", None),
      mesureEnrichie("P001", "Chirurgie A", Some(90))
    )
    val stats = Statistiques.statistiquesPatient("P001", mesures)
    // 3 mesures au total, mais seulement 2 valeurs de FC présentes
    assertEquals(stats.nbMesures, 3)
    assertEqualsDouble(stats.frequenceCardiaque.moyenne.get, 80.0, delta)
  }
