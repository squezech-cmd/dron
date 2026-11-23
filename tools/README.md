# tools

Reproducible generators for the mod's binary assets. Both scripts only need
Python 3 with `pillow` (textures/preview) and `numpy` + `soundfile` (sounds).

| script | output |
|---|---|
| `gen_textures.py` | `assets/dron/textures/entity/combat_drone.png`, `textures/item/drone_remote.png`, `icon.png` |
| `gen_sounds.py` | `assets/dron/sounds/*.ogg` (Vorbis, 22.05 kHz mono) |
| `preview_model.py` | `tools/preview/*.png` — software render of the model + skin |

```bash
pip install pillow numpy soundfile
python3 tools/gen_textures.py
python3 tools/gen_sounds.py
python3 tools/preview_model.py
```

`preview_model.py` projects the same boxes `DroneModel` declares with a painter's
algorithm, which makes it possible to check UV layout, proportions and palettes
without launching the game.
