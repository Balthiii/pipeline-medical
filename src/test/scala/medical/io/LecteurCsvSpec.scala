package medical.io

import munit.FunSuite
import java.nio.file.{Files, Path}

class LecteurCsvSpec extends FunSuite:

  private def fichierTemporaire(contenu: String): Path =
    val chemin = Files.createTempFile("mesures", ".csv")
    Files.writeString(chemin, contenu)
    chemin.toFile.deleteOnExit()
    chemin

  test("lire parse un CSV valide en mesures, en conservant les valeurs manquantes") {
    val csv =
      """patient_id,timestamp,service,age,frequence_cardiaque,tension_systolique,tension_diastolique,temperature,spo2
        |P001,2026-07-28T08:00:00,Chirurgie A,67,78,128,82,37.1,97
        |P002,2026-07-28T08:15:00,Chirurgie A,54,,117,75,36.7,98
        |""".stripMargin

    LecteurCsv.lire(fichierTemporaire(csv).toString) match
      case Right(mesures) =>
        assertEquals(mesures.size, 2)
        assertEquals(mesures(0).patientId, "P001")
        assertEquals(mesures(1).frequenceCardiaque, None)
      case Left(erreur) => fail(s"lecture attendue en succès : $erreur")
  }

  test("lire renvoie une erreur si un champ numérique est invalide") {
    val csv =
      """patient_id,timestamp,service,age,frequence_cardiaque,tension_systolique,tension_diastolique,temperature,spo2
        |P001,2026-07-28T08:00:00,Chirurgie A,67,abc,128,82,37.1,97
        |""".stripMargin

    LecteurCsv.lire(fichierTemporaire(csv).toString) match
      case Left(_) => // attendu
      case Right(_) => fail("une ligne avec un champ non numérique doit être rejetée")
  }

  test("lire renvoie une erreur pour un chemin inexistant") {
    LecteurCsv.lire("chemin/qui/n/existe/pas.csv") match
      case Left(_) => // attendu
      case Right(_) => fail("un chemin inexistant doit produire une erreur")
  }
