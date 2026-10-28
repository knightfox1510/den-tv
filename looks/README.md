# Looks

One file per look. A look is a recipe: every value names a part from the shared library, so any look
can borrow any part, and a new look is usually a new mix rather than new code.

| Key | What it picks | Values |
|---|---|---|
| `id`, `name` | File name and the name shown on the phone | text |
| `category` | Where it shows in the gallery | Football, Cricket, Heroes, Anime, Outdoors, Night & neon, Seasonal, Together |
| `for` | Whose gallery it appears in | `owner`, `partner`, `everyone` (guest mode shows only `everyone`) |
| `scene` | The painted backdrop | `campnou`, `wankhede`, `bluebillion`, `saiyan`, `ui`, `kurama`, `hokage`, `zoro`, `crimson`, `domain`, `grandline`, `wave`, `manga`, `dusk`, `dawn`, `monsoon`, `neon`, `seoul`, `diwali`, `marquee`, `floodlights`, `black` |
| `hero` | Poster over the scene | `none`, `rotate`, `messi`, `kohli`, `rohit`, `goku`, `naruto`, `luffy`, `zoro`, `gojo`, `tanjiro` |
| `watcher` | What follows the selected app | `none`, `sharingan`, `mangekyo`, `six`, `saiyan`, `ui`, `kurama`, `sage`, `luffy` (straw hat), `zoro` (three swords) |
| `focus` | Effect when you move between apps | `spot`, `pass`, `arc`, `ripple`, `aura` |
| `wake` | Plays once when the TV switches on | `powerup`, `slash`, `sharingan`, `tunnel`, `panels`, `ink`, `bloom`, `crt` |
| `clock` | Clock style | `thin`, `roll`, `board`, `over`, `brush`, `crt` |
| `icons` | App icons | `white` (logo only), `tiles` |
| `extras` | Small moving things on top | `auto` or `none`, `flashes`, `flags`, `fireworks`, `crows`, `petals`, `embers`, `sparks`, `gold`, `silver`, `leaves`, `rain`, `snow` |
| `moment` | The big event the look reacts to | `goal`, `six`, `beam`, `drop` (new episode), `none` |
| `accent` | Highlight colour | `#RRGGBB` |
| `scoreboard_text` | Optional line under the scoreboard clock | text |
| `season` | Optional months the look belongs to | text |

Every key is optional for the engine: a missing one falls back to the scene's own default, and an
unknown value is ignored, so a broken look can never break Home.

To try a look before the launcher exists, open `mockups/den-looks.html` in a browser.
