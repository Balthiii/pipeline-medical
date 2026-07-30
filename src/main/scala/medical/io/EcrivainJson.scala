package medical.io

import upickle.default.write
import java.nio.file.{Files, Path}
import scala.util.Try

/** Seul point d'effet de bord en sortie du pipeline : sérialisation et
  * écriture du rapport sur disque. Le cœur fonctionnel (`Statistiques`,
  * `ConvertisseurJson`) ne produit que des valeurs immuables ; cette
  * fonction se contente de les écrire, symétriquement à `LecteurCsv` en
  * entrée. Les erreurs d'écriture sont renvoyées via `Either`, sans
  * exception.
  */
object EcrivainJson:

  def ecrire(rapport: RapportExportJson, chemin: String): Either[String, Unit] =
    Try {
      val json = write(rapport, indent = 2)
      Files.writeString(Path.of(chemin), json)
    }.toEither.left
      .map(e => s"impossible d'écrire $chemin : ${e.getMessage}")
      .map(_ => ())
