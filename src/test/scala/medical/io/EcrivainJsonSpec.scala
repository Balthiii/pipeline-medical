package medical.io

import munit.FunSuite
import upickle.default.read
import java.nio.file.{Files, Path}

class EcrivainJsonSpec extends FunSuite:

  private def cheminTemporaire(): Path =
    val chemin = Files.createTempFile("rapport", ".json")
    chemin.toFile.deleteOnExit()
    chemin

  private val rapportExemple = RapportExportJson(
    services = List(
      StatistiquesServiceJson(
        service = "Chirurgie A",
        nbMesures = 2,
        nbPatients = 1,
        frequenceCardiaque = StatistiquesVitalesJson(
          Some(80.0),
          Some(80.0),
          Some(5.0),
          Some(75.0),
          Some(85.0)
        ),
        tensionSystolique =
          StatistiquesVitalesJson(None, None, None, None, None),
        tensionDiastolique =
          StatistiquesVitalesJson(None, None, None, None, None),
        temperature = StatistiquesVitalesJson(None, None, None, None, None),
        spo2 = StatistiquesVitalesJson(None, None, None, None, None),
        proportionAlerte = 0.5,
        patientsEnAlerte = List("P001")
      )
    ),
    patients = List(
      StatistiquesPatientJson(
        patientId = "P001",
        nbMesures = 2,
        frequenceCardiaque = StatistiquesVitalesJson(
          Some(80.0),
          Some(80.0),
          Some(5.0),
          Some(75.0),
          Some(85.0)
        ),
        tensionSystolique =
          StatistiquesVitalesJson(None, None, None, None, None),
        tensionDiastolique =
          StatistiquesVitalesJson(None, None, None, None, None),
        temperature = StatistiquesVitalesJson(None, None, None, None, None),
        spo2 = StatistiquesVitalesJson(None, None, None, None, None)
      )
    )
  )

  test("ecrire produit un fichier JSON valide, relisible à l'identique") {
    val chemin = cheminTemporaire()

    EcrivainJson.ecrire(rapportExemple, chemin.toString) match
      case Left(erreur) => fail(s"écriture attendue en succès : $erreur")
      case Right(())    =>
        val contenu = Files.readString(chemin)
        val relu = read[RapportExportJson](contenu)
        assertEquals(relu, rapportExemple)
  }

  test(
    "ecrire produit un JSON contenant bien la liste des patients en alerte par service"
  ) {
    val chemin = cheminTemporaire()
    EcrivainJson.ecrire(rapportExemple, chemin.toString)

    val contenu = Files.readString(chemin)
    assert(contenu.contains("patientsEnAlerte"))
    assert(contenu.contains("P001"))
  }

  test("ecrire renvoie une erreur pour un chemin invalide") {
    EcrivainJson.ecrire(
      rapportExemple,
      "chemin/qui/n/existe/pas/rapport.json"
    ) match
      case Left(_)  => // attendu
      case Right(_) => fail("un chemin invalide doit produire une erreur")
  }
