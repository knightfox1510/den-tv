# Make your own character

No drawing skills needed. You make a few pictures with an AI image tool, and den-tv animates them:
the eyes follow the selected app, it blinks, the head tilts toward the app, and it breathes, leans and
walks along the dock. On the phone page this is **Customise → Your characters**.

## The pictures

| Picture | What it is | Needed? | What it adds |
|---|---|---|---|
| **Character** | The full body, transparent background | Yes | Breathing, leaning toward the app, walking |
| **Pupils** | Only the two pupils, same canvas size | Optional | Eyes that follow the selected app |
| **Eyes closed** | The same picture with the eyes shut | Optional | Blinking |
| **Head** | Only the head, same canvas size | Optional | A head that tilts toward the app |

All of them must be exactly the same size and line up, so they stack into one character.

## Step by step

1. **Make the character.** In Gemini, ChatGPT or another image tool, use prompt 1 below (the phone
   page builds it for you). Pick your favourite result.
2. **Make the matching pictures in the same chat**, with prompts 2 and 3: eyes closed, and plain eyes
   with no pupils. Check that the pose and size didn't change; ask again if they did.
3. **Remove the white background** from each one: Photopea (photopea.com, Image → Remove
   background), Canva's background remover, or your phone's "lift subject". Save as PNG.
4. **Make the pupils picture:** open the original in Photopea, erase everything except the two pupils
   and save it at the same size. The no-pupils picture from step 2 is your main character picture.
5. **Optional head:** erase everything except the head and save it, then erase the head from the main
   picture.
6. **Upload** in Customise → Your characters, tap the middle of each eye, choose how far the eyes move,
   and press Save. Use it as the big figure or as a walker.

Only have one picture? Upload just the character. It will still breathe, lean and walk.

## Prompts

**1 · The character**

```text
Full-body character illustration of [WHO, OUTFIT, EXPRESSION].
[STYLE]. Standing, front view, facing the viewer, eyes wide open
and looking straight ahead, arms slightly away from the body.
The whole character in frame from head to feet with some space
around. Even, flat lighting. Plain pure white background, no
ground shadow, no text. Portrait 9:16, high resolution.
```

**2 · Eyes closed (same chat)**

```text
Keep this exact image identical in every way, same pose, size and
position, and only close the character's eyes, as if mid-blink.
Plain white background.
```

**3 · No pupils (same chat)**

```text
Keep this exact image identical in every way, same pose, size and
position, but make the eyes plain white with no pupils or irises.
Plain white background.
```

### Ideas for [WHO, OUTFIT, EXPRESSION]

- a cheerful young pirate captain with messy black hair, a straw hat with a red band, a red open
  short-sleeved shirt, a yellow sash, blue shorts with white cuffs and straw sandals, a huge grin,
  one fist raised
- a calm young swordsman with short green hair, a white shirt, a green sash, three katanas at his
  hip, a small scar over one eye, a confident smirk
- an energetic young ninja with spiky blond hair, an orange and black tracksuit, a metal-plated
  headband, a big grin, a thumbs-up
- a cricket batter in a blue kit with orange trim and the number 18, bat on the shoulder (an
  original person, not a real player)
- a football striker in a deep-red and blue striped kit with the number 10, a ball at the feet (an
  original person, not a real player)
- a trekker with a big backpack, trekking poles and a bright raincoat, a happy tired smile
- a relaxed coder in a dark hoodie with headphones round the neck, holding a glowing laptop
- **you or your partner:** attach a photo and use "the person in the attached photo, keeping their
  face, hair and skin tone recognisable, in [an outfit]"

### Ideas for [STYLE]

- clean modern anime illustration, cel shading, bold clean outlines
- cute chibi style, big head and small body, cel shading, bold outlines
- soft 3D toy-figure style, smooth rounded shapes, gentle studio lighting
- western comic-book style, bold ink lines, flat colours
- detailed pixel-art sprite, crisp pixels, limited palette
- loose watercolour illustration with clean edges around the figure

## How the pictures are layered

The AI's edits redraw small details across the whole picture, so swapping whole pictures would make
the character jitter. den-tv keeps one main picture and takes only the **eye area** from the others:

- the background is removed by filling inward from the edges, so whites inside the outline (eyes,
  teeth, fur trim) stay;
- the pupils are the dark pixels inside each eye that differ between the normal and no-pupils
  pictures;
- the blink is the eye band from the eyes-closed picture, softened at the edges.

The mockup expects personal character pictures in `mockups/chars/`, which git ignores. Without them,
the Wanted and Straw Hat looks fall back to the code-drawn Luffy.

## Good to know

- Most image tools won't draw named anime characters or real sports stars. Describe the look
  instead, as the ideas above do.
- Only use photos of people who are happy to be turned into a character.
- Keep each picture under 1 MB so a 1 GB TV stays quick.
- Your characters stay on your TV and phone. They never go into this repo.
