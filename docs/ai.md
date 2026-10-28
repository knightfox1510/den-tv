# AI features (optional)

den-tv works fully without AI. Connecting a key (Gemini, Claude, OpenAI or another provider) adds
the features below. The rule that keeps the TV fast: **AI never runs while you are moving around
Home.** Every call happens on the phone page or in a background job on the TV, the answer is saved
as a small file, and Home only ever reads saved files. If the key is missing, the network is down or
a call takes too long, Home shows the last saved answer or nothing.

## What it adds

| Feature | Where it runs | What it saves |
|---|---|---|
| **Describe a look:** "rainy Tokyo night with Itachi's crows and a neon clock" becomes a look | Phone page, when you press Create | One look file, about 1 KB |
| **Rules in plain words:** "when India bats, switch to Blue Billion and mute after 11" becomes an auto rule | Phone page | One rule |
| **Ask the TV:** "put on something calm", "show Barça highlights", "what's on tonight for both of us" | Phone page, or voice on the phone | An action: a look, an app or a search |
| **Match previews and recaps:** two lines before kickoff and after the final whistle, written from Fan Hub's score data | TV background job, at most a few times a day | A short text card |
| **Spoiler-safe "previously on":** a recap of the last episode, with no spoilers for the new one | TV background job, when a new episode drops | A short text card |
| **What to watch tonight:** suggestions that mix both profiles' interests | Phone page | A list |
| **Weekend planner:** fits hikes around match times and the forecast | TV background job, Friday evening | A Fan Hub card |
| **New scene art** made from a description, for your own TV | Phone page; image models cost more per picture | One 1920×1080 JPEG |

## Rules for safety and cost

- **The key stays on the TV**, encrypted with Android's keystore, or only on the phone page. It never
  goes into a look file, a shared QR code or this repo.
- **What is sent** is only what the feature needs: the description you typed, team names and scores,
  and show titles. No photos, no viewing history unless a feature asks for it and you allow it.
- **A daily cap** on the number of calls, set on the phone page, so a bug can't run up a bill.
- **Facts are checked, not invented.** The "Did you know?" facts stay hand-written and checked; AI text
  is labelled as AI wherever it appears.
- **Content limits** follow the provider's rules, and the guest profile never shows AI suggestions.
