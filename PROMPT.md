# Set up den-tv with Claude Code

Do [step 1 of INSTALL.md](INSTALL.md#step-1-get-the-tv-ready-all-ways-need-this) on the TV first (USB debugging on,
IP address written down). Then paste the part between the lines into Claude Code (or another coding assistant that
can run commands) on a computer on the same Wi-Fi as the TV, with your TV's details filled in.

---

Install den-tv on my TV. My TV is a <TV MODEL> and its IP address is <TV IP>.

1. Read https://github.com/knightfox1510/den-tv/blob/main/INSTALL.md, then download the installer for this computer
   from the same repo: `install/install.ps1` on Windows, `install/install.sh` on Mac or Linux. Read it before you run
   it.
2. Run it with my TV's IP address and `--keep-debugging` (`-KeepDebugging` on Windows), so the TV stays reachable
   while you check the result. Tell me when to choose Allow on the TV. Ask me before turning on the den-tv helper or
   the screensaver, and pass `--no-helper` / `--no-screensaver` if I say no.
3. If it stops, read what it said and the Troubleshooting part of INSTALL.md, and fix it if you can. Safety rules:
   never uninstall or disable any app other than the ones the installer itself handles, never leave the TV without
   a working home screen (if in doubt, run the installer with `--undo`), and ask me before anything that depends on
   my preferences. On a Vu, TCL or Xiaomi set never disable `com.vt.tv.*`, `com.tcl.*` or `com.mitv.*` packages.
4. When den-tv is on, check that Home opens it (`cmd package resolve-activity --brief -a android.intent.action.MAIN
   -c android.intent.category.HOME` should name `tv.den.launcher`), then turn USB debugging off
   (`settings put global adb_enabled 0`) and confirm the TV no longer answers on its ADB port.
5. Tell me how to pair my phone (the TV shows a QR code and a 6-digit code).

---

To also clean up the TV (remove ads and unused system apps safely, with lists tested per TV), use
[cleanyourtv](https://github.com/Gathin23/cleanyourtv)'s own PROMPT.md first; den-tv then replaces its launcher.
