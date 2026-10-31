# Feed performance (preliminary)

Emulator only. These numbers say little about a real TV: AVD `GoogleTV_34`, headless, arm64 image on an
Apple Silicon host, GPU emulated (`ro.hardware.egl=emulation`). The verdict waits for a Chromecast.

## How it was measured

`dumpsys gfxinfo io.github.ivanosh.smarttubematerial reset`, then a scripted pass over a signed-out feed
(Gaming tab, 3 rows and more, thumbnails loading from the network), then `dumpsys gfxinfo`. A pass is three times
`22×6 21×6 20`, then the D-pad held for 1.5 s right, left, down and up. Three passes per launch, cold start first.
"Janky" is the `Janky frames` line, not the legacy one.

## Results

Same host, same scenario. The batches differ because the host's load changed between them.

| Build | Card | Host | Pass 1 | Pass 2 | Pass 3 | Slow draw frames, pass 3 | Slow UI frames, pass 3 |
|---|---|---|---|---|---|---|---|
| debug | plain Compose, corners baked | quiet | 55.50% | 23.71% | 12.81% | 20 | 13 |
| debug | one bitmap | quiet | 26.21% | 13.94% | 13.27% | 19 | 11 |
| release | plain Compose, corners baked | quiet | 16.83% | 10.55% | 10.61% | 15 | 3 |
| release | one bitmap | quiet | 14.42% | 10.53% | 10.27% | 17 | 2 |
| release | plain Compose, corners baked | busy | 23.88% | 15.90% | 14.49% | 26 | 14 |
| release | one bitmap | busy | 21.08% | 18.32% | 15.42% | 27 | 8 |

Percentiles for the one-bitmap release, quiet host, pass 3: 50th 25 ms, 90th 32 ms, 99th 53 ms.

Control: the old Leanback screen, release, quiet host: 12.44%, 5.57%, 5.44%, with 18 slow draw frames in each of
the last two passes. It was not a fair feed: signed out, the same keys only walked its sidebar next to an empty Home.
It still lost the same number of frames as the new screen. Those look like the emulator's floor. The new screen
draws fewer frames per pass (about 190 against 340), so the same loss reads as a larger percentage.

## Reading

- The brief's threshold is 10%. Every batch is above it, so cards were simplified to one bitmap as the brief says.
- The bitmap did not measurably reduce jank here. The difference is within the noise, and a busier host moves the
  result more than the card does. It did not hurt either, and it keeps the UI thread almost out of the slow frames.
- What remains is `Slow issue draw commands`, which is the emulated GPU.
- A baked card has costs to weigh on real hardware: the text appears together with the thumbnail, and it is
  slightly soft during the 1.04 focus scale. If a Chromecast shows the plain card is fast enough, it is the simpler one.

## To do on a Chromecast

Repeat the scenario on a release build, for both cards, and replace this file with those numbers.
