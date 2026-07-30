package medical.io

import medical.domain.Mesure
import java.time.LocalDateTime
import scala.io.Source
import scala.util.{Try, Using}

/** Seul point d'effet de bord en entrée du pipeline : lecture du fichier
  * CSV de relevés et conversion en `Mesure`. Le cœur fonctionnel ne
  * manipule jamais le fichier source, uniquement la `List[Mesure]` produite
  * ici. Les erreurs de lecture ou de parsing sont renvoyées via `Either`,
  * sans exception.
  */
object LecteurCsv:

  def lire(chemin: String): Either[String, List[Mesure]] =
    Using(Source.fromFile(chemin))(_.getLines().toList).toEither.left
      .map(e => s"impossible de lire $chemin : ${e.getMessage}")
      .flatMap(parserLignes)

  private def parserLignes(lignes: List[String]): Either[String, List[Mesure]] =
    lignes.filter(_.nonEmpty) match
      case Nil => Left("fichier CSV vide")
      case _ :: donnees =>
        val (erreurs, mesures) = donnees.map(parserLigne).partitionMap(identity)
        if erreurs.nonEmpty then Left(s"${erreurs.size} ligne(s) invalide(s) : ${erreurs.mkString(" | ")}")
        else Right(mesures)

  private def parserLigne(ligne: String): Either[String, Mesure] =
    val champs = ligne.split(",", -1).map(_.trim)
    if champs.length != 9 then
      Left(s"ligne malformée (${champs.length} champs, 9 attendus) : $ligne")
    else
      Try {
        Mesure(
          patientId = champs(0),
          timestamp = LocalDateTime.parse(champs(1)),
          service = champs(2),
          age = champs(3).toInt,
          frequenceCardiaque = parseOptionInt(champs(4)),
          tensionSystolique = parseOptionInt(champs(5)),
          tensionDiastolique = parseOptionInt(champs(6)),
          temperature = parseOptionDouble(champs(7)),
          spo2 = parseOptionInt(champs(8))
        )
      }.toEither.left.map(e => s"champ non numérique ($ligne) : ${e.getMessage}")

  private def parseOptionInt(s: String): Option[Int] =
    if s.isEmpty then None else Some(s.toInt)

  private def parseOptionDouble(s: String): Option[Double] =
    if s.isEmpty then None else Some(s.toDouble)
