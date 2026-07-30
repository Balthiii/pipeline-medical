package medical.core

import munit.FunSuite
import medical.domain.Mesure
import java.time.LocalDateTime

class TransformationSpec extends FunSuite:

  private def mesure(tensionSys: Option[Int], tensionDia: Option[Int]): Mesure =
    Mesure(
      patientId = "P001",
      timestamp = LocalDateTime.parse("2026-07-28T08:00:00"),
      service = "Chirurgie A",
      age = 60,
      frequenceCardiaque = Some(75),
      tensionSystolique = tensionSys,
      tensionDiastolique = tensionDia,
      temperature = Some(37.0),
      spo2 = Some(97)
    )

  test("pressionPulsee calcule systolique moins diastolique") {
    assertEquals(Transformation.pressionPulsee(mesure(Some(120), Some(80))), Some(40))
  }

  test("pressionPulsee est None si la systolique est manquante") {
    assertEquals(Transformation.pressionPulsee(mesure(None, Some(80))), None)
  }

  test("pressionPulsee est None si la diastolique est manquante") {
    assertEquals(Transformation.pressionPulsee(mesure(Some(120), None)), None)
  }

  test("pressionPulsee est None si les deux valeurs sont manquantes") {
    assertEquals(Transformation.pressionPulsee(mesure(None, None)), None)
  }
