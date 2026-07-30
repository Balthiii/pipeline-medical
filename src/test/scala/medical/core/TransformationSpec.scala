package medical.core

import munit.FunSuite
import medical.domain.{Mesure, NiveauVigilance}
import java.time.LocalDateTime

class TransformationSpec extends FunSuite:

  private def mesure(
      tensionSys: Option[Int] = Some(120),
      tensionDia: Option[Int] = Some(80),
      fc: Option[Int] = Some(75),
      temp: Option[Double] = Some(37.0),
      spo2: Option[Int] = Some(97)
  ): Mesure =
    Mesure(
      patientId = "P001",
      timestamp = LocalDateTime.parse("2026-07-28T08:00:00"),
      service = "Chirurgie A",
      age = 60,
      frequenceCardiaque = fc,
      tensionSystolique = tensionSys,
      tensionDiastolique = tensionDia,
      temperature = temp,
      spo2 = spo2
    )

  // --- pressionPulsee (déjà existant) ---

  test("pressionPulsee calcule systolique moins diastolique") {
    assertEquals(
      Transformation.pressionPulsee(mesure(Some(120), Some(80))),
      Some(40)
    )
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

  // --- niveauVigilance ---

  test(
    "niveauVigilance est Normal quand tous les champs sont dans la plage normale"
  ) {
    assertEquals(
      Transformation.niveauVigilance(mesure()),
      NiveauVigilance.Normal
    )
  }

  test("niveauVigilance est Normal quand des champs sont manquants (None)") {
    assertEquals(
      Transformation.niveauVigilance(mesure(fc = None, temp = None)),
      NiveauVigilance.Normal
    )
  }

  test(
    "niveauVigilance est Surveillance si un champ est hors normale mais valide"
  ) {
    // fréquence cardiaque à 110 : hors [50,100] mais toujours < 220 (non aberrant)
    assertEquals(
      Transformation.niveauVigilance(mesure(fc = Some(110))),
      NiveauVigilance.Surveillance
    )
  }

  test(
    "niveauVigilance est Alerte si le SpO2 est en dessous de 90 (seuil explicite du sujet)"
  ) {
    assertEquals(
      Transformation.niveauVigilance(mesure(spo2 = Some(86))),
      NiveauVigilance.Alerte
    )
  }

  test(
    "niveauVigilance est Surveillance si le SpO2 est entre 90 et 94 inclus"
  ) {
    assertEquals(
      Transformation.niveauVigilance(mesure(spo2 = Some(92))),
      NiveauVigilance.Surveillance
    )
  }

  test("niveauVigilance retient le pire niveau parmi plusieurs champs") {
    // fc en Surveillance (110), spo2 en Alerte (86) => le résultat doit être Alerte
    assertEquals(
      Transformation.niveauVigilance(mesure(fc = Some(110), spo2 = Some(86))),
      NiveauVigilance.Alerte
    )
  }

  // --- pipeline composé (transformer = initialiser andThen ...) ---

  test(
    "transformer produit une MesureEnrichie cohérente avec pressionPulsee et niveauVigilance"
  ) {
    val m =
      mesure(tensionSys = Some(120), tensionDia = Some(80), spo2 = Some(86))
    val enrichie = Transformation.transformer(m)

    assertEquals(enrichie.mesure, m)
    assertEquals(enrichie.pressionPulsee, Some(40))
    assertEquals(enrichie.niveauVigilance, NiveauVigilance.Alerte)
  }

  test("transformer s'applique via map sans modifier les mesures d'origine") {
    val mesures = List(mesure(), mesure(spo2 = Some(86)))
    val enrichies = mesures.map(Transformation.transformer)

    assertEquals(
      enrichies.map(_.niveauVigilance),
      List(NiveauVigilance.Normal, NiveauVigilance.Alerte)
    )
    assertEquals(
      mesures.map(_.spo2),
      List(Some(97), Some(86))
    ) // originales inchangées
  }
