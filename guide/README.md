# den-tv website

A plain static site: `index.html` (what den-tv is), `setup.html` (the step-by-step setup guide), `looks.html`,
`fanhub.html`, `phone.html`, `keys.html` (getting the free keys), `help.html` and `credits.html`, with the apps' real
screens in `img/`. Nothing to build on the server.

To change a page, edit its body in `src/pages/`, then run `python3 guide/src/make.py`: it wraps each one in the
shared head, navigation and footer. Styles are in `site.css`.

## Put it online with Vercel (free)

1. Sign in at [vercel.com](https://vercel.com) with your GitHub account (the free Hobby plan is enough).
2. Choose **Add New… → Project**, and import the **den-tv** repository.
3. Under **Root Directory**, choose **Edit** and pick `guide`. Leave **Framework Preset** as **Other**, and leave the
   build and output settings empty.
4. Choose **Deploy**. A minute later it's live at an address like `den-tv-guide.vercel.app`. You can rename it under
   **Settings → Domains**.

Every push to `main` that changes `guide/` updates the page by itself.

GitHub Pages only publishes a repository's root or its `docs` folder, so Vercel is the simpler of the two here.

## Updating the pictures

The TV screens come from the apps' screen tests. To refresh them after a change:

```bash
cd launcher
./gradlew :fanhub:testReleaseUnitTest -Pden.screens=1 --tests '*SetupScreensTest*'
```

then copy the PNGs from `launcher/fanhub/build/setup-screens/` into `img/` as JPEGs (1280 wide). Home in each look
comes from `./gradlew :app:testReleaseUnitTest -Pden.screens=1 --tests '*DenScreensTest.showcase'`
(`launcher/app/build/den-screens/showcase-*.png`). Your own photos of
your TV work just as well: save them in `img/` under the same names.
