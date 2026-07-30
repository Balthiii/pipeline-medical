package medical.domain

/** Bilan du nettoyage d'un jeu de mesures : doublons supprimés et lignes
  * rejetées pour cause de valeur hors plage physiologique, avec le détail
  * des champs responsables du rejet. Retourné en valeur par `Nettoyage`,
  * jamais affiché directement par le cœur fonctionnel.
  */
final case class RapportNettoyage(
    mesuresInitiales: Int,
    doublonsSupprimes: Int,
    lignesRejetees: Int,
    motifsRejet: Map[String, Int],
    mesuresFinales: Int
)
