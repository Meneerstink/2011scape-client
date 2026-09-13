# Graphics/display audit — 2026-09-09

Status: implementation in progress; NOT HUMAN_VERIFIED.

Active client: C:/RSPS/2011scape-client/2011scape-client, confirmed by Start-RSPS.ps1 launcher and Git origin. Existing unrelated edits to stat/orb/overlay/protocol code must remain intact.

Evidence: upstream StrongHold/runescape-667, local upstream/master f45c5e8a. BrightnessOption.getDefaultValue returns 3. Static133.method2316 is AUTOSETUP_SETMEDIUM and sets quality 3. MaxScreenSize: 0 unrestricted, 1 1024x768, 2 800x600; InterfaceManager applies those as rendering-area caps, not desktop resolutions. The requested MID values override the original medium preset, while retaining brightness 3 and the original screen-size choices. No arbitrary modern resolution is introduced. Skybox settings are preserved.

Graph coverage: only C-RSPS-game-game generation 2026-09-05T21:51:32Z was indexed. Client coverage is outside_project; targeted source fallback used. Serena activation requested for the nested client root.

Root causes identified: wrong fresh profile; full-option validation deletes other preferences; AA applied/saved fields diverge; fullscreen not saved as startup mode; fixed viewport ignores the existing wheel-controlled zoom endpoint; scripts overwrite that endpoint; exclusive fullscreen/focus-loss exit/paint-driven canvas recreation are unstable on Windows; canvas caps and projection bars constrain wide displays.

Implementation checkpoint: MID/default renderer, isolated preference mutation, local-only display/zoom trailer, independent wheel distance implemented. Display lifecycle and regression verification next.

## 2026-09-09 continuation

Verified the prior uncommitted implementation compiles and its full automated regression ladder is
green: `./gradlew :runescape:check` — cameraZoomRegression 16, audioSettingsRegression 9,
debugOverlayRegression 8, oldStyleHitsplatRegression 23, r13Regression 120,
graphicsDisplayRegression 943, all passed, 0 failures. `desktopDisplayRegression` (not wired into
`check` because it opens a real OS window and would fail headless in CI, matching this repo's own
`.github/workflows/ci.yml` running on `ubuntu-22.04` with no display — left as a local-only manual
regression, same as before) also passed: 5 fullscreen enter/exit cycles, native monitor bounds,
unchanged desktop display mode, no leaked windows.

**Real bug found and fixed: Custom cursor defaulted to On, not Off.** `RspsGraphicsProfile.apply()`
(the MID/default preset every fresh client and every "Restore Defaults" click goes through) called
`options.customCursors.setSafeValue(1)`. The deobfuscated rev-667 client's own
`CustomCursors.getDefaultValue()` also returns `1`, so `1` was carried over as "the original
default" without checking the owner's explicit requirement that this server's default differ from
stock: **Off**. Fixed to `setSafeValue(0)`. Updated the two regression suites that pin the exact MID
value set so a future change cannot silently flip it back: `GraphicsDisplayRegressionTest` (`mid[14]`
1->0, and `values[14]` changed from 0 to 1 so that sub-test still exercises a real value change
rather than a no-op) and `R13RegressionTest` (the "deliberate custom override before a reset" now
sets customCursors to 1 so the following reset-to-MID assertion, corrected to expect 0, actually
proves the preset overrides a non-default user choice rather than comparing 0 to 0).

**Skybox investigated, no code defect found — flagged for a toolkit-specific live check instead.**
`SkyboxesOption` (`@OriginalClass("client!dc")`, unmodified deobfuscated original) gates both
`canMod()` and `canSet()` on `ToolkitType.isHardware(toolkit)` — under the Java software renderer
the option cannot be changed and skybox geometry is only ever built by `GlToolkit`
(`SkyBoxSphere.method5587` and friends have no Java-toolkit equivalent), which is authentic rev-667
behaviour, not a bug introduced by this project. The MID/default preset's `toolkitDefault` is `1`
(OpenGL/hardware), so skyboxes should render normally whenever the OpenGL toolkit actually
initialises. The one path that could produce "no visible effect" is the GL renderer silently
falling back to software at runtime — this repo's own automated regression run logs exactly that
fallback (`Graphics renderer 1 failed (UnsatisfiedLinkError); using Java software rendering.`)
inside `r13Regression`, though that is a headless/no-display test JVM and may not reflect the
owner's real desktop launch. **Not fixed blind**: this needs a live check of which toolkit is
actually active (Graphics Options -> the renderer shown, or the debug console's `skydetail`
command) before any further change — per this project's own rule, an OpenGL init failure is a
launcher/native-library question, and inventing a change without seeing which branch actually runs
on the owner's machine risks masking the real cause.

Fullscreen/resizable/zoom-persistence implementation (native-monitor borderless fullscreen via
`DesktopDisplay`/`AwtFullscreenAdapter`, single-option `validate()` instead of the old full-profile
reset, the `encodeLocal()`/`decodeLocal()` local preference trailer for window mode/size/zoom) is
unchanged this pass and remains **IMPLEMENTED, automated-verified, PENDING_HUMAN_RETEST** — no
human has yet confirmed real fullscreen/resizable/zoom behaviour in the actual launched client.
Custom cursor default is likewise **IMPLEMENTED, automated-verified, PENDING_HUMAN_RETEST**.
Skybox is **INVESTIGATED, PENDING_HUMAN_RETEST of which renderer is active** — no code changed.
