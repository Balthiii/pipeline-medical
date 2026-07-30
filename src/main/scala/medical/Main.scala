package medical

import medical.io.LecteurCsv
import medical.core.Nettoyage

@main def run(): Unit =
  val chemin = "data/dataset_patients_exemple.csv"
  LecteurCsv.lire(chemin) match
    case Left(erreur) =>
      println(s"Erreur de lecture : $erreur")
    case Right(mesures) =>
      val (_, rapport) = Nettoyage.nettoyer(mesures)
      println(s"Mesures lues               : ${rapport.mesuresInitiales}")
      println(s"Doublons supprimes         : ${rapport.doublonsSupprimes}")
      println(s"Lignes rejetees (aberrant) : ${rapport.lignesRejetees}")
      rapport.motifsRejet.toList.sortBy(_._1).foreach { case (champ, n) =>
        println(s"  - $champ : $n")
      }
      println(s"Mesures valides conservees : ${rapport.mesuresFinales}")
