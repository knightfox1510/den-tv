# den-tv setup guide (web page)

`index.html` is a step-by-step setup guide with the apps' real screens (`img/`). It's a plain static page: no build
step, nothing to install.

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

then copy the PNGs from `launcher/fanhub/build/setup-screens/` into `img/` as JPEGs (1280 wide). Your own photos of
your TV work just as well: save them in `img/` under the same names.
