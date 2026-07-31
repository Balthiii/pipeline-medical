package medical

import medical.core.{Nettoyage, Statistiques, Transformation}
import medical.domain.{Mesure, MesureEnrichie, RapportNettoyage}
import medical.io.{ConvertisseurJson, EcrivainJson, LecteurCsv, RapportExportJson}

/** Point d'entrée du pipeline complet :
  *   lecture CSV -> nettoyage -> transformation -> statistiques -> export JSON
  *
  * Toute la logique métier reste dans `medical.core` (100% pure). `Main` se
  * contente d'enchaîner les étapes et de déclencher les deux seuls effets
  * de bord du pipeline : lire le CSV (`LecteurCsv`) et écrire le JSON
  * (`EcrivainJson`). L'affichage console fait aussi partie de cette couche
  * "imperative shell", jamais du cœur fonctionnel.
  */
object Main:

  private val cheminCsvParDefaut = "data/dataset_patients_exemple.csv"
  private val cheminJsonParDefaut = "rapport.json"

  /** Assemble nettoyage, transformation et statistiques à partir de mesures
    * déjà lues. Fonction pure : testable sans lire ni écrire de fichier.
    */
  def executerPipeline(mesuresBrutes: List[Mesure]): (RapportNettoyage, RapportExportJson) =
    val (mesuresPropres, rapportNettoyage) = Nettoyage.nettoyer(mesuresBrutes)
    val mesuresEnrichies: List[MesureEnrichie] = mesuresPropres.map(Transformation.transformer)

    val statsParPatient = Statistiques.statistiquesParPatient(mesuresEnrichies)
    val statsParService = Statistiques.statistiquesParService(mesuresEnrichies)
    val patientsEnAlerte = Statistiques.patientsEnAlerteParService(mesuresEnrichies)

    val rapportJson = ConvertisseurJson.construireRapport(statsParService, statsParPatient, patientsEnAlerte)
    (rapportNettoyage, rapportJson)

  def main(args: Array[String]): Unit =
    val cheminEntree = args.headOption.getOrElse(cheminCsvParDefaut)
    val cheminSortie = args.drop(1).headOption.getOrElse(cheminJsonParDefaut)

    val resultat =
      for
        mesuresBrutes <- LecteurCsv.lire(cheminEntree)
        (rapportNettoyage, rapportJson) = executerPipeline(mesuresBrutes)
        _ <- EcrivainJson.ecrire(rapportJson, cheminSortie)
      yield (mesuresBrutes.size, rapportNettoyage, rapportJson)

    resultat match
      case Right((nbLues, rapportNettoyage, rapportJson)) =>
        println(s"Lecture : $nbLues mesure(s) lue(s) depuis $cheminEntree")
        println(
          s"Nettoyage : ${rapportNettoyage.doublonsSupprimes} doublon(s) supprime(s), " +
            s"${rapportNettoyage.lignesRejetees} ligne(s) rejetee(s), " +
            s"${rapportNettoyage.mesuresFinales} mesure(s) conservee(s)"
        )
        println(s"Statistiques : ${rapportJson.services.size} service(s), ${rapportJson.patients.size} patient(s)")
        println(s"Export JSON ecrit dans $cheminSortie")
      case Left(erreur) =>
        println(s"Erreur du pipeline : $erreur")
        sys.exit(1)
