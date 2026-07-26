# Artwork for the physical view

Every slot the lane view can take real artwork in, what each expects, and where
to get it.

**Nothing here is required.** The renderer draws its own shape for any slot that
is empty, so the view works with no assets at all and improves one file at a
time. Add a file, reload, and it appears — there is no build step and no code
change.

## Where files go

Drop files under `frontend/public/assets/`. Vite serves that directory as-is.

```text
frontend/public/assets/
├── vehicles/
│   ├── car.png
│   ├── motorcycle.png
│   ├── bus.png
│   └── emergency.png
├── surfaces/
│   ├── asphalt.png
│   └── junction.png
├── land/
│   ├── normal.png          ordinary urban fabric — the base everything sits on
│   ├── park.png
│   ├── water.png
│   ├── grass.png
│   └── … one per LandCoverType
└── buildings/
    ├── residential.png
    ├── commercial.png
    ├── office.png
    ├── school.png
    ├── hospital.png
    ├── industrial.png
    ├── parking.png
    └── other.png
```

The paths are declared in `frontend/src/physical/assetManifest.js`. To add a
slot, add it there; the loader picks it up automatically.

## Vehicles — 4 files

Top-down sprites, transparent background, cropped tight to the bodywork.

| File | Vehicle | Real size | Suggested pixels |
|---|---|---|---|
| `car.png` | Car | 4.5 × 1.8 m | 576 × 230 |
| `motorcycle.png` | Motorcycle with rider | 2.1 × 0.75 m | 269 × 96 |
| `bus.png` | City bus | 12 × 2.5 m | 1536 × 320 |
| `emergency.png` | Ambulance or police car | 5.0 × 2.0 m | 640 × 256 |

**The nose must point UP.** The renderer rotates a sprite by the vehicle's
compass heading and adds no offset, so artwork drawn facing right puts every
vehicle sideways. This is the single most common way to get this wrong.

Aspect ratio need not match exactly — the sprite is stretched to the vehicle's
real metres — but a wildly different ratio will look squashed.

Where to look, all CC0 or free-to-use:

- **Kenney — Top-down Racing / Pixel Vehicle Pack** — <https://kenney.nl/assets>
  CC0, no attribution required, consistent style across all four vehicles.
  The closest fit to this list and the easiest single source.
- **OpenGameArt**, filter by CC0 — <https://opengameart.org/> — search
  "top down car", "top down motorcycle".
- **Game-icons.net** — <https://game-icons.net/> — CC BY 3.0, silhouettes
  rather than illustrations; good if a flat-icon look is wanted.

A motorcycle from above is the hard one to find; a scooter sprite reads fine at
this scale.

## Road and ground surfaces — 3 files

**Seamlessly tiling** textures. A tile whose edges do not meet its opposite side
shows as a visible grid across the whole map. 512 × 512 px is plenty.

| File | Used for |
|---|---|
| `asphalt.png` | The carriageway |
| `junction.png` | Paved area inside a junction |

## Land cover — 12 files

The ground between the roads, one texture per `LandCoverType`. `normal.png` is
the base the whole scene is drawn on; the rest fill mapped polygons on top.

| File | What it covers | Polygons in the current map |
|---|---|---|
| `normal.png` | Ordinary urban fabric — everywhere nothing else is mapped | the background |
| `grass.png` | Verges and yards | 136 |
| `commercial.png` | Commercial blocks | 25 |
| `water.png` | Saigon river, Ben Nghe canal | 21 |
| `park.png` | Tao Dan and the rest | 18 |
| `construction.png` | Sites | 17 |
| `other.png` | Mapped, but not a type this model distinguishes | 20 |
| `residential.png`, `industrial.png`, `forest.png`, `religious.png`, `recreation.png` | as named | few or none |

`normal.png` is the one that matters most: it is the majority of the screen.
Something quiet and slightly textured — dry concrete, packed earth, a pale
render — reads as city without pretending to be buildings.

Where to look:

- **ambientCG** — <https://ambientcg.com/> — CC0, search "Asphalt", "Concrete",
  "Ground". Download the **Color/Albedo** map only; the normal and roughness
  maps are not used here.
- **Poly Haven textures** — <https://polyhaven.com/textures> — CC0, same idea.
- **CC0 Textures** mirrors of the above.

Keep them low-contrast. A busy asphalt photo fights with the lane markings and
the vehicles, which are the things that actually need to be read.

## Building roofs — 8 files

Tiled over each footprint, chosen by what the building is for. Any missing one
falls back to that type's flat tint, so this group can be filled partially —
start with `residential`, `office` and `other`, which cover most of District 1.

| File | Building type | Count in the current map |
|---|---|---|
| `other.png` | Untagged | 885 |
| `residential.png` | Houses, apartments | 147 |
| `office.png` | Offices | 57 |
| `commercial.png` | Shops, hotels | 36 |
| `school.png` | Schools, universities | 18 |
| `hospital.png` | Hospitals, clinics | 17 |
| `parking.png` | Car parks | 2 |
| `industrial.png` | Warehouses | 0 |

Roof textures, seen from directly above: tiles, corrugated metal, flat concrete
with rooftop plant. Same CC0 sources as the surfaces above; ambientCG has
`RoofingTiles` and `Metal` sets.

## What does not work — learned the hard way

The first round of downloads filled **1 slot out of 5 attempts**. These are the
traps, all of which look right on the download page.

**Side-view sprite packs.** Kenney's *Pixel Vehicle Pack* is drawn from the
side: you can see the wheels below the body and the rider in profile. It is
excellent art and completely unusable here — this view looks straight down. The
give-away is not the aspect ratio, because a top-down car is about 2.5:1 and so
is a side-on one. Open one and look. Kenney's **Racing Pack** and **Top-down
Tanks** are the top-down ones.

**Road textures that already have markings painted on.** ambientCG's `Road007`
is a complete two-lane carriageway: solid white edge lines, dashed centre line,
baked in. The renderer strokes each **lane** separately and draws its own
markings as vectors, so a marked texture would repeat a full set of lines inside
every single lane, under a second set drawn on top.

What is wanted is plain asphalt with no paint: ambientCG `Road012A`, or anything
in their **Asphalt** or **Concrete** sets.

**`RoadLines*` sets.** These are decals for 3D authoring. The colour map is flat
yellow or white; the actual line shape lives in the **Opacity** map, which only
means something to a material system. Nothing usable here.

**Everything except `_Color.png`.** An ambientCG download is ten to twelve files
— normal, roughness, displacement, ambient occlusion, plus `.blend` and `.usdc`
scene files. This renderer is 2D and uses the colour map alone. A 1K set is
around 15 MB of which roughly 1.3 MB is wanted.

## Priority, if you only download a few

1. **The four vehicles.** They are the moving thing on screen and the current
   stand-ins are the weakest part of the view.
2. **`asphalt.png` and `ground.png`.** Two files, and they cover most of the
   screen area.
3. **`other.png` and `residential.png`.** 1032 of the 1162 buildings.

Everything else is polish.

## Licensing

Prefer **CC0** so nothing has to be attributed in the report or the demo. If you
use a CC BY asset, record the author and licence in this file next to the slot —
a university submission is a publication.

## Checking it worked

The physical view's status bar counts the slots that loaded:

```text
Live · frame 412 · 126 vehicles · 68 buildings · 7 assets · drag to move, scroll to zoom
```

`0 assets` means nothing was found — check the filenames and that the files sit
under `frontend/public/assets/`, not `frontend/src/`.

## Filled so far

| Slot | Source | Licence |
|---|---|---|
| `surfaces/asphalt.png` | ambientCG `Road012A` colour map | CC0 |

The other fourteen slots fall back to drawn shapes, which is why the view still
renders.

A surface texture is tiled to cover six metres of ground per repeat. Mapped one
pixel to one metre a 1024-pixel tile would span a kilometre and read as a flat
smear, so the scale is set where the grain stays visible.
