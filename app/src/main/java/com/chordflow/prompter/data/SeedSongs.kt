package com.chordflow.prompter.data

/**
 * Two hymns written into the database on first launch, so the app is usable on stage
 * straight after install with no internet and no import.
 *
 * Both texts are public domain:
 *   "Amazing Grace"      — John Newton, 1779
 *   "Holy, Holy, Holy"   — Reginald Heber, 1826 / tune NICAEA, J. B. Dykes, 1861
 *
 * NOTE ON "HOW GREAT THOU ART": the familiar English lyric is Stuart K. Hine's 1949
 * translation and is still under copyright, so it is not bundled. The second seed is a
 * public-domain hymn in Bb instead. Add your own licensed copy through Import File if
 * your church holds a CCLI licence that covers reproduction.
 */
object SeedSongs {

    val AMAZING_GRACE_G = """
{title: Amazing Grace}
{artist: John Newton (1779), Public Domain}
{key: G}
{tempo: 72}

{comment: VERSE 1}
[G]Amazing [G7]grace, how [C]sweet the [G]sound
That [G]saved a wretch like [D]me
I [G]once was [G7]lost, but [C]now am [G]found
Was [G]blind but [D]now I [G]see

{comment: VERSE 2}
'Twas [G]grace that [G7]taught my [C]heart to [G]fear
And [G]grace my fears re[D]lieved
How [G]precious [G7]did that [C]grace ap[G]pear
The [G]hour I [D]first be[G]lieved

{comment: VERSE 3}
Through [G]many [G7]dangers, [C]toils and [G]snares
I [G]have already [D]come
'Tis [G]grace hath [G7]brought me [C]safe thus [G]far
And [G]grace will [D]lead me [G]home

{comment: VERSE 4}
When [G]we've been [G7]there ten [C]thousand [G]years
Bright [G]shining as the [D]sun
We've [G]no less [G7]days to [C]sing God's [G]praise
Than [G]when we'd [D]first be[G]gun
""".trimIndent()

    val HOLY_HOLY_HOLY_BB = """
{title: Holy, Holy, Holy}
{artist: Reginald Heber (1826), Public Domain}
{key: Bb}
{tempo: 76}

{comment: VERSE 1}
[Bb]Holy, holy, [Eb]holy! [Bb]Lord God Al[F]mighty!
[Bb]Early in the [Eb]morning our [Bb]song shall [F]rise to [Bb]Thee
[Bb]Holy, holy, [Eb]holy! [Bb]Merciful and [F]mighty!
[Bb]God in three [Eb]Persons, [Bb]blessed [F]Trin[Bb]ity!

{comment: VERSE 2}
[Bb]Holy, holy, [Eb]holy! [Bb]All the saints a[F]dore Thee
[Bb]Casting down their [Eb]golden crowns a[Bb]round the [F]glassy [Bb]sea
[Bb]Cherubim and [Eb]seraphim [Bb]falling down be[F]fore Thee
[Bb]Which wert and [Eb]art and [Bb]ever[F]more shalt [Bb]be

{comment: VERSE 3}
[Bb]Holy, holy, [Eb]holy! [Bb]Though the darkness [F]hide Thee
[Bb]Though the eye of [Eb]sinful man Thy [Bb]glory [F]may not [Bb]see
[Bb]Only Thou art [Eb]holy, [Bb]there is none be[F]side Thee
[Bb]Perfect in [Eb]power, in [Bb]love and [F]pur[Bb]ity

{comment: VERSE 4}
[Bb]Holy, holy, [Eb]holy! [Bb]Lord God Al[F]mighty!
[Bb]All Thy works shall [Eb]praise Thy name in [Bb]earth and [F]sky and [Bb]sea
[Bb]Holy, holy, [Eb]holy! [Bb]Merciful and [F]mighty!
[Bb]God in three [Eb]Persons, [Bb]blessed [F]Trin[Bb]ity!
""".trimIndent()

    fun seeds(): List<Song> = listOf(
        Song(
            title = "Amazing Grace",
            artist = "John Newton (Public Domain)",
            originalKey = "G",
            transposeSemitones = 0,
            tempo = 72,
            content = AMAZING_GRACE_G,
            setlist = Song.DEFAULT_SETLIST,
            position = 0,
            source = "Bundled public domain hymn"
        ),
        Song(
            title = "Holy, Holy, Holy",
            artist = "Reginald Heber (Public Domain)",
            originalKey = "Bb",
            transposeSemitones = 0,
            tempo = 76,
            content = HOLY_HOLY_HOLY_BB,
            setlist = Song.DEFAULT_SETLIST,
            position = 1,
            source = "Bundled public domain hymn"
        )
    )
}
