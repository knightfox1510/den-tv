# Looks

One file per look. A look is a recipe: every value names a part from the shared library, so any look
can borrow any part, and a new look is usually a new mix rather than new code.

| Key | What it picks | Values |
|---|---|---|
| `id`, `name` | File name and the name shown on the phone | text |
| `category` | Where it shows in the gallery | Football, Cricket, Heroes, Anime, Outdoors, One Piece, Night & neon, Seasonal, Together, After dark |
| `for` | Whose gallery it appears in | `owner`, `partner`, `everyone` (guest mode shows only `everyone`) |
| `scene` | The painted backdrop | `campnou`, `wankhede`, `bluebillion`, `saiyan`, `ui`, `kurama`, `hokage`, `zoro`, `tangerine`, `wisteria`, `lounge`, `leaf`, `firefist`, `room`, `baratie`, `drum`, `hitman`, `noir`, `velvet`, `casino`, `goat`, `albiceleste`, `king`, `crimson`, `domain`, `grandline`, `wave`, `manga`, `dusk`, `dawn`, `monsoon`, `neon`, `seoul`, `diwali`, `marquee`, `floodlights`, `black` |
| `hero` | Poster over the scene | `none`, `rotate`, `messi`, `kohli`, `rohit`, `goku`, `naruto`, `luffy`, `zoro`, `nami`, `shinobu`, `kakashi`, `itachi`, `ace`, `law`, `sanji`, `robin`, `chopper`, `gojo`, `tanjiro` |
| `watcher` | What follows the selected app | `none`, `sharingan`, `mangekyo`, `six`, `saiyan`, `ui`, `kurama`, `sage`, `luffy` (straw hat), `zoro` (three swords), `dragonballs`, `pole` (Power Pole), `cloud` (thundercloud), `butterflies`, `chidori`, `crows`, `room`, `hearts`, `hands`, `chopperhat`, `bat`, `fedora`, `dice`, `ballondor`, `dribble`, `crown` |
| `focus` | Effect when you move between apps | `spot`, `pass`, `arc`, `ripple`, `aura` |
| `wake` | Plays once when the TV switches on | `room`, `fire`, `powerup`, `slash`, `storm`, `flutter`, `sharingan`, `tunnel`, `panels`, `ink`, `bloom`, `crt` |
| `clock` | Clock style | `analog`, `words`, `neon`, `flap` (split-flap), `ring` (seconds ring), `thin`, `roll`, `board`, `over`, `brush`, `crt` |
| `icons` | App icons | `white` (logo only), `tiles` |
| `extras` | Small moving things on top | `auto` or `none`, `flashes`, `flags`, `fireworks`, `crows`, `petals`, `embers`, `confetti`, `sparks`, `gold`, `silver`, `leaves`, `wisteria`, `rain`, `snow` |
| `moment` | The big event the look reacts to | `goal`, `six`, `beam`, `drop` (new episode), `none` |
| `accent` | Highlight colour | `#RRGGBB` |
| `goal_text` | Optional line shown with the goal moment | text |
| `scoreboard_text` | Optional line under the scoreboard clock | text |
| `season` | Optional months the look belongs to | text |

Every key is optional for the engine: a missing one falls back to the scene's own default, and an
unknown value is ignored, so a broken look can never break Home.

To try a look before the launcher exists, open `mockups/den-looks.html` in a browser.
