package medical.core

import medical.domain.{Mesure, MesureEnrichie, NiveauVigilance}

/** Fonctions pures de transformation métier : indicateurs dérivés et
  * classification clinique, appliqués mesure par mesure via `map`.
  */
object Transformation:

  /** Pression pulsée = tension systolique − tension diastolique. `None` dès que
    * l'une des deux valeurs est manquante.
    */
  def pressionPulsee(m: Mesure): Option[Int] =
    for
      systolique <- m.tensionSystolique
      diastolique <- m.tensionDiastolique
    yield systolique - diastolique

  // --- Classification de vigilance, champ par champ ---
  //
  // Une valeur absente (None) est traitée comme Normal : l'absence de
  // donnée est gérée par le nettoyage (séance 2), pas par la vigilance.
  // Pour chaque paramètre présent, on distingue la plage normale du sujet
  // (§3.2) d'un écart hors normale mais toujours valide (Surveillance).
  // Seul le SpO2 a un seuil d'alerte explicite dans le sujet (< 90).

  private def frequenceCardiaqueVigilance(v: Option[Int]): NiveauVigilance =
    v match
      case None                              => NiveauVigilance.Normal
      case Some(fc) if fc >= 50 && fc <= 100 => NiveauVigilance.Normal
      case Some(_)                           => NiveauVigilance.Surveillance

  private def tensionSystoliqueVigilance(v: Option[Int]): NiveauVigilance =
    v match
      case None                           => NiveauVigilance.Normal
      case Some(t) if t >= 90 && t <= 140 => NiveauVigilance.Normal
      case Some(_)                        => NiveauVigilance.Surveillance

  private def tensionDiastoliqueVigilance(v: Option[Int]): NiveauVigilance =
    v match
      case None                          => NiveauVigilance.Normal
      case Some(t) if t >= 60 && t <= 90 => NiveauVigilance.Normal
      case Some(_)                       => NiveauVigilance.Surveillance

  private def temperatureVigilance(v: Option[Double]): NiveauVigilance =
    v match
      case None                              => NiveauVigilance.Normal
      case Some(t) if t >= 36.0 && t <= 37.5 => NiveauVigilance.Normal
      case Some(_)                           => NiveauVigilance.Surveillance

  private def spo2Vigilance(v: Option[Int]): NiveauVigilance =
    v match
      case None               => NiveauVigilance.Normal
      case Some(s) if s >= 95 => NiveauVigilance.Normal
      case Some(s) if s < 90  => NiveauVigilance.Alerte
      case Some(_)            => NiveauVigilance.Surveillance // entre 90 et 94

  /** Gravité numérique d'un niveau, utilisée uniquement pour prendre le pire
    * niveau parmi plusieurs champs (Alerte > Surveillance > Normal).
    */
  private def gravite(n: NiveauVigilance): Int = n match
    case NiveauVigilance.Normal       => 0
    case NiveauVigilance.Surveillance => 1
    case NiveauVigilance.Alerte       => 2

  /** Niveau de vigilance global d'une mesure : le plus grave parmi tous ses
    * champs physiologiques présents.
    */
  def niveauVigilance(m: Mesure): NiveauVigilance =
    List(
      frequenceCardiaqueVigilance(m.frequenceCardiaque),
      tensionSystoliqueVigilance(m.tensionSystolique),
      tensionDiastoliqueVigilance(m.tensionDiastolique),
      temperatureVigilance(m.temperature),
      spo2Vigilance(m.spo2)
    ).maxBy(gravite)

  // --- Composition explicite via andThen (demandée par le sujet §3.3) ---

  private val initialiser: Mesure => MesureEnrichie =
    m => MesureEnrichie(mesure = m)

  private val ajouterPressionPulsee: MesureEnrichie => MesureEnrichie =
    me => me.copy(pressionPulsee = pressionPulsee(me.mesure))

  private val ajouterVigilance: MesureEnrichie => MesureEnrichie =
    me => me.copy(niveauVigilance = niveauVigilance(me.mesure))

  /** Pipeline de transformation d'une mesure, composé explicitement via
    * `andThen` : chaque étape enrichit `MesureEnrichie` sans jamais modifier la
    * `Mesure` d'origine. À appliquer sur une collection via `.map`.
    */
  val transformer: Mesure => MesureEnrichie =
    initialiser andThen ajouterPressionPulsee andThen ajouterVigilance
