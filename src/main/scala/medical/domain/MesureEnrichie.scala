package medical.domain

/** Une mesure enrichie des indicateurs calculés par la couche transformation :
  * pression pulsée et niveau de vigilance clinique. Produite par
  * `Transformation.transformer`, jamais construite en modifiant `Mesure`.
  */
final case class MesureEnrichie(
    mesure: Mesure,
    pressionPulsee: Option[Int] = None,
    niveauVigilance: NiveauVigilance = NiveauVigilance.Normal
)
