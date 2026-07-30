package medical.core

import munit.FunSuite
import medical.domain.Mesure
import java.time.LocalDateTime

class NettoyageSpec extends FunSuite:

  private def mesure(
      patientId: String = "P001",
      timestamp: LocalDateTime = LocalDateTime.parse("2026-07-28T08:00:00"),
      fc: Option[Int] = Some(75),
      tensionSys: Option[Int] = Some(120),
      tensionDia: Option[Int] = Some(80),
      temp: Option[Double] = Some(37.0),
      spo2: Option[Int] = Some(97)
  ): Mesure =
    Mesure(patientId, timestamp, "Chirurgie A", 60, fc, tensionSys, tensionDia, temp, spo2)

  // --- fréquence cardiaque : 30-220, sinon aberrant ---

  test("frequenceCardiaqueValide accepte une valeur manquante") {
    assert(Nettoyage.frequenceCardiaqueValide(mesure(fc = None)))
  }

  test("frequenceCardiaqueValide accepte les bornes 30 et 220") {
    assert(Nettoyage.frequenceCardiaqueValide(mesure(fc = Some(30))))
    assert(Nettoyage.frequenceCardiaqueValide(mesure(fc = Some(220))))
  }

  test("frequenceCardiaqueValide rejette en dessous de 30 ou au dessus de 220") {
    assert(!Nettoyage.frequenceCardiaqueValide(mesure(fc = Some(29))))
    assert(!Nettoyage.frequenceCardiaqueValide(mesure(fc = Some(221))))
    assert(!Nettoyage.frequenceCardiaqueValide(mesure(fc = Some(305)))) // cas du sujet
  }

  // --- tensions ---

  test("tensionSystoliqueValide rejette en dehors de [40, 250]") {
    assert(!Nettoyage.tensionSystoliqueValide(mesure(tensionSys = Some(39))))
    assert(!Nettoyage.tensionSystoliqueValide(mesure(tensionSys = Some(251))))
    assert(Nettoyage.tensionSystoliqueValide(mesure(tensionSys = Some(40))))
  }

  test("tensionDiastoliqueValide rejette en dehors de [20, 150]") {
    assert(!Nettoyage.tensionDiastoliqueValide(mesure(tensionDia = Some(19))))
    assert(!Nettoyage.tensionDiastoliqueValide(mesure(tensionDia = Some(151))))
    assert(Nettoyage.tensionDiastoliqueValide(mesure(tensionDia = Some(150))))
  }

  // --- température : 30-42, sinon aberrant ---

  test("temperatureValide rejette 0.0°C (cas du sujet)") {
    assert(!Nettoyage.temperatureValide(mesure(temp = Some(0.0))))
  }

  test("temperatureValide accepte la plage normale") {
    assert(Nettoyage.temperatureValide(mesure(temp = Some(36.5))))
  }

  // --- SpO2 : 0-100, une valeur basse est une alerte clinique, pas une aberration ---

  test("spo2Valide rejette une valeur négative ou supérieure à 100") {
    assert(!Nettoyage.spo2Valide(mesure(spo2 = Some(-5))))
    assert(!Nettoyage.spo2Valide(mesure(spo2 = Some(101))))
  }

  test("spo2Valide accepte une valeur basse mais physiologique") {
    assert(Nettoyage.spo2Valide(mesure(spo2 = Some(85))))
  }

  // --- mesureValide / motifsInvalidite ---

  test("mesureValide est vraie quand tous les champs sont dans les bornes ou manquants") {
    assert(Nettoyage.mesureValide(mesure(fc = None, temp = None)))
  }

  test("motifsInvalidite liste tous les champs en cause") {
    val m = mesure(fc = Some(305), temp = Some(0.0))
    assertEquals(Nettoyage.motifsInvalidite(m).toSet, Set("frequenceCardiaque", "temperature"))
  }

  // --- doublons (même patientId + même timestamp) ---

  test("supprimerDoublons supprime les mesures avec même patientId + timestamp") {
    val t = LocalDateTime.parse("2026-07-28T08:45:00")
    val m1 = mesure(patientId = "P001", timestamp = t)
    val m2 = mesure(patientId = "P001", timestamp = t)
    val (resultat, nbSupprimes) = Nettoyage.supprimerDoublons(List(m1, m2))
    assertEquals(resultat, List(m1))
    assertEquals(nbSupprimes, 1)
  }

  test("supprimerDoublons ne supprime rien si patientId ou timestamp diffère") {
    val t = LocalDateTime.parse("2026-07-28T08:45:00")
    val m1 = mesure(patientId = "P001", timestamp = t)
    val m2 = mesure(patientId = "P002", timestamp = t)
    val (resultat, nbSupprimes) = Nettoyage.supprimerDoublons(List(m1, m2))
    assertEquals(resultat.size, 2)
    assertEquals(nbSupprimes, 0)
  }

  // --- pipeline complet ---

  test("nettoyer combine suppression des doublons et rejet des valeurs aberrantes") {
    val t1 = LocalDateTime.parse("2026-07-28T08:00:00")
    val t2 = LocalDateTime.parse("2026-07-28T08:15:00")
    val valide = mesure(patientId = "P001", timestamp = t1)
    val doublon = mesure(patientId = "P001", timestamp = t1)
    val aberrante = mesure(patientId = "P002", timestamp = t2, fc = Some(305))

    val (mesuresPropres, rapport) = Nettoyage.nettoyer(List(valide, doublon, aberrante))

    assertEquals(rapport.mesuresInitiales, 3)
    assertEquals(rapport.doublonsSupprimes, 1)
    assertEquals(rapport.lignesRejetees, 1)
    assertEquals(rapport.motifsRejet, Map("frequenceCardiaque" -> 1))
    assertEquals(rapport.mesuresFinales, 1)
    assertEquals(mesuresPropres, List(valide))
  }

  test("nettoyer ne modifie pas la collection d'origine (immutabilité)") {
    val original = List(mesure(fc = Some(305)))
    Nettoyage.nettoyer(original)
    assertEquals(original.head.frequenceCardiaque, Some(305))
  }
