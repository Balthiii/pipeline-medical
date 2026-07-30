package medical.domain

/** Statistiques descriptives d'un paramètre vital sur un ensemble de mesures.
  * Tous les champs sont en `Option` : `None` si aucune valeur n'était
  * présente dans l'ensemble considéré (paramètre entièrement manquant).
  */
final case class StatistiquesVitales(
    moyenne: Option[Double],
    mediane: Option[Double],
    ecartType: Option[Double],
    min: Option[Double],
    max: Option[Double]
)
