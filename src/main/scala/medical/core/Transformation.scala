package medical.core

import medical.domain.Mesure

/** Fonctions pures de transformation métier : indicateurs dérivés et
  * classification clinique, appliqués mesure par mesure via `map`.
  */
object Transformation:

  /** Pression pulsée = tension systolique − tension diastolique.
    * `None` dès que l'une des deux valeurs est manquante.
    */
  def pressionPulsee(m: Mesure): Option[Int] =
    for
      systolique <- m.tensionSystolique
      diastolique <- m.tensionDiastolique
    yield systolique - diastolique
