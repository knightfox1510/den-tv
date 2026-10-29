# Looks

One file per look. A look is a recipe: every value names a part from the shared library, so any look
can borrow any part, and a new look is usually a new mix rather than new code.

| Key | What it picks | Values |
|---|---|---|
| `id`, `name` | File name and the name shown on the phone | text |
| `category` | Where it shows in the gallery, in this order | Football, Cricket, One Piece, Dragon Ball, Naruto, Demon Slayer, Jujutsu Kaisen, Anime, Heroes, Outdoors, Seasonal, Together, After dark, Geek & code |
| `order` | Its place in the gallery: the category's block of 100 (Football 100, Cricket 200 and so on), then 10 per look | number |
| `for` | Whose gallery it appears in | `owner`, `partner`, `everyone` (guest mode shows only `everyone`) |
| `scene` | The painted backdrop | `campnou`, `yamalnou`, `rocafonda`, `wings`, `wankhede`, `sharjah`, `bluebillion`, `saiyan`, `ui`, `kurama`, `hokage`, `zoro`, `tangerine`, `wisteria`, `lounge`, `leaf`, `firefist`, `room`, `baratie`, `drum`, `hitman`, `noir`, `velvet`, `casino`, `goat`, `albiceleste`, `king`, `grid`, `code`, `clouds`, `wanted`, `asura`, `lost`, `walls`, `soul`, `gate`, `crater`, `shrine`, `vegeta`, `crimson`, `domain`, `grandline`, `wave`, `manga`, `dusk`, `dawn`, `monsoon`, `neon`, `seoul`, `diwali`, `marquee`, `floodlights`, `black` |
| `hero` | Poster over the scene | `none`, `rotate`, `messi`, `yamal`, `wings` (Yamal and Raphinha), `kohli`, `sachin`, `rohit`, `goku`, `naruto`, `luffy`, `zoro`, `nami`, `shinobu`, `kakashi`, `itachi`, `vegeta`, `sasuke`, `levi`, `ichigo`, `jinwoo`, `saitama`, `rengoku`, `sukuna`, `ace`, `law`, `sanji`, `robin`, `chopper`, `gojo`, `tanjiro` |
| `watcher` | What follows the selected app | `none`, `sharingan`, `mangekyo`, `six`, `saiyan`, `ui`, `kurama`, `sage`, `luffy` (straw hat), `zoro` (three swords), `dragonballs`, `pole` (Power Pole), `cloud` (thundercloud), `butterflies`, `chidori`, `crows`, `room`, `hearts`, `hands`, `chopperhat`, `bat`, `fedora`, `dice`, `ballondor`, `dribble`, `crown`, `reticle`, `caret`, `rinnegan`, `walkluffy`, `walkg5`, `walkzoro`, `compass`, `scouter`, `odm`, `getsuga`, `shadows`, `fist`, `nichirin`, `cleave`, `drive` (straight drive: the ball races to it and the crowd chants), `trivela` (a ball curls in to it), `onetwo` (10 and 11 pass it in) |
| `focus` | Effect when you move between apps | `spot`, `pass`, `arc`, `ripple`, `aura` |
| `wake` | Plays once when the TV switches on | `boing`, `hudboot`, `termboot`, `room`, `fire`, `powerup`, `slash`, `storm`, `flutter`, `sharingan`, `tunnel`, `panels`, `ink`, `bloom`, `crt` |
| `clock` | Clock style | `term`, `hud`, `analog`, `words`, `neon`, `flap` (split-flap), `ring` (seconds ring), `thin`, `roll`, `board`, `over`, `brush`, `crt` |
| `icons` | How each app icon is drawn | `white` (logo only), `tiles`, `dball` (Dragon Balls), `wanted` (bounty posters), `manga` (ink, speed lines on focus), `neon` (tubes that flicker on), `patch` (jersey patch), `pin` (enamel pin), `score` (scorecard chip), `pixel` (retro 8-bit), `glass`, `sticker`, `hud` (target brackets) |
| `icon_layout` | Where the icons sit | `row` (dock row), `formation` (players on a pitch), `field` (fielding positions, with the selected app on strike), `arc` (islands along a route), `shelf` (manga spines that slide out), `carousel` (selected app as a big banner), `rail` (vertical strip on the left) |
| `extras` | Small moving things on top | `auto` or `none`, `flashes`, `flags`, `fireworks`, `crows`, `petals`, `embers`, `confetti`, `sparks`, `gold`, `silver`, `leaves`, `wisteria`, `rain`, `snow` |
| `moment` | The big event the look reacts to | `goal`, `six`, `beam`, `arise`, `punch`, `gear5`, `drop` (new episode), `none` |
| `figure` | Optional full cartoon character on the right, eyes following the app | `luffy`, `g5`, `zoro` |
| `hud` | Optional: `true` shows the detailed system, network and feed panels | `true` / `false` |
| `accent` | Highlight colour | `#RRGGBB` |
| `goal_text` | Optional line shown with the goal moment | text |
| `scoreboard_text` | Optional line under the scoreboard clock | text |
| `season` | Optional months the look belongs to | text |

On the phone, App icons can override the style and layout, and adds live badges (new episode, live
score, continue watching, screen time), smart order, a Shows folder, renamed apps, your own icon
pictures and apps hidden per person. Those settings belong to the TV, not to the look.

Every key is optional for the engine: a missing one falls back to the scene's own default, and an
unknown value is ignored, so a broken look can never break Home.

To try a look before the launcher exists, open `mockups/den-looks.html` in a browser.
