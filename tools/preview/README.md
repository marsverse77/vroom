# Screenshot preview (no phone needed)

These screenshots use the game's real geometry, simulation and UI code. The only difference is the lighting:
three.js does it here instead of Filament, so it's close to the phone but not identical.

```
export PREVIEW_DIR=$PWD/tools/preview/data
./gradlew :app:testDebugUnitTest --tests '*PreviewExport*' --tests '*PreviewUi*'   # meshes, camera poses, UI PNGs, textures
cd tools/preview && npm install && python3 -m http.server 8765 &
node shoot.js race_chase race_start race_jump race_tires showroom showroom:0.35 showroom:0:0.3 showroom_car1:0:0.3
```

`shoot.js` writes `out_<shot>.png` (3D only). Lay `data/ui_*.png` / `data/hud_*.png` on top for the full screen.
If Maven Central rate-limits Robolectric's `android-all` jar, download it into a folder and set `ROBOLECTRIC_DEPS` to it.
