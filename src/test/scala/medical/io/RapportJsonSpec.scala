package medical.io

import munit.FunSuite
import medical.domain.{StatistiquesPatient, StatistiquesService, StatistiquesVitales}

class RapportJsonSpec extends FunSuite:

  private val statsExemple = StatistiquesVitales(
    moyenne = Some(80.0),
    mediane = Some(80.0),
    ecartType = Some(8.16),
    min = Some(70.0),
    max = Some(90.0)
  )

  private def statsVitalesVides = StatistiquesVitales(None, None, None, None, None)

  test("versJson(StatistiquesPatient) conserve toutes les valeurs") {
    val patient = StatistiquesPatient(
      patientId = "P001",
      nbMesures = 3,
      frequenceCardiaque = statsExemple,
      tensionSystolique = statsVitalesVides,
      tensionDiastolique = statsVitalesVides,
      temperature = statsVitalesVides,
      spo2 = statsVitalesVides
    )
    val dto = ConvertisseurJson.versJson(patient)

    assertEquals(dto.patientId, "P001")
    assertEquals(dto.nbMesures, 3)
    assertEquals(dto.frequenceCardiaque.moyenne, Some(80.0))
  }

  test("versJson(StatistiquesService) trie et intègre la liste des patients en alerte") {
    val service = StatistiquesService(
      service = "Chirurgie A",
      nbMesures = 10,
      nbPatients = 3,
      frequenceCardiaque = statsExemple,
      tensionSystolique = statsVitalesVides,
      tensionDiastolique = statsVitalesVides,
      temperature = statsVitalesVides,
      spo2 = statsVitalesVides,
      proportionAlerte = 0.2
    )
    val dto = ConvertisseurJson.versJson(service, patientsEnAlerte = List("P003", "P001"))

    assertEquals(dto.service, "Chirurgie A")
    assertEqualsDouble(dto.proportionAlerte, 0.2, 0.0001)
    assertEquals(dto.patientsEnAlerte, List("P001", "P003")) // trié
  }

  test("construireRapport assemble et trie services et patients par clé") {
    val statsParService = Map(
      "Reanimation" -> StatistiquesService(
        "Reanimation", 1, 1, statsVitalesVides, statsVitalesVides, statsVitalesVides, statsVitalesVides, statsVitalesVides, 0.0
      ),
      "Chirurgie A" -> StatistiquesService(
        "Chirurgie A", 2, 1, statsVitalesVides, statsVitalesVides, statsVitalesVides, statsVitalesVides, statsVitalesVides, 0.5
      )
    )
    val statsParPatient = Map(
      "P002" -> StatistiquesPatient("P002", 1, statsVitalesVides, statsVitalesVides, statsVitalesVides, statsVitalesVides, statsVitalesVides),
      "P001" -> StatistiquesPatient("P001", 2, statsVitalesVides, statsVitalesVides, statsVitalesVides, statsVitalesVides, statsVitalesVides)
    )
    val patientsEnAlerteParService = Map("Chirurgie A" -> List("P001"))

    val rapport = ConvertisseurJson.construireRapport(statsParService, statsParPatient, patientsEnAlerteParService)

    assertEquals(rapport.services.map(_.service), List("Chirurgie A", "Reanimation")) // trié alphabétiquement
    assertEquals(rapport.services.head.patientsEnAlerte, List("P001"))
    assertEquals(rapport.services(1).patientsEnAlerte, List.empty) // Reanimation : aucune entrée dans la map
    assertEquals(rapport.patients.map(_.patientId), List("P001", "P002")) // trié
  }

