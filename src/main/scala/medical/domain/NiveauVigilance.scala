package medical.domain

/** Catégorie de vigilance clinique attribuée à une mesure, selon les seuils
  * définis dans le sujet (fréquence cardiaque, tension, température, SpO2).
  */
sealed trait NiveauVigilance

object NiveauVigilance:
  case object Normal extends NiveauVigilance
  case object Surveillance extends NiveauVigilance
  case object Alerte extends NiveauVigilance
