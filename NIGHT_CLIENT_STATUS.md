# RSPS client night status

Baseline: `4452a032` (`Default fresh clients to fixed low graphics`).

## Completed batches

### Audio output rebuild

- Stereo/mono changes now close the old PCM players, discard streams built for the old channel layout, and create fresh output players.
- Re-selecting the active stereo mode no longer needlessly restarts both PCM players.
- Sound-effect and speech-volume changes stop only their own active/queued one-shot category.
- Background-volume changes detach positioned area/NPC/player streams while preserving their registrations and unrelated one-shots; the normal renderer tick recreates them at the new volume.
- Regression task: `:runescape:audioSettingsRegression` — 9 checks passed.
- Existing graphics regression: `:runescape:r13Regression` — 120 checks passed.
- Full `gradlew build --no-daemon` passed on JDK 11.

Owner live acceptance:

1. While an effect repeats, switch Stereo→Mono→Stereo; the old effect must stop and later effects must use the selected layout.
2. Change sound-effects volume during an effect; it must stop immediately without stopping speech or music.
3. Change voice-over volume during speech; speech must stop without stopping normal effects or music.
4. Change ambient volume beside a looping object/NPC sound; it must restart once at the new level without overlap.
5. Confirm music continues across stereo changes and responds to music volume normally.

## Next batch

Mouse-wheel zoom limited to the 3D world viewport, with interface/chat/bank scroll isolation and safe zoom bounds.
