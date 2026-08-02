# Artwork for the physical view

Every slot the lane view can take real artwork in, what each expects, and where
to get it.

**Nothing here is required.** The renderer draws its own shape for any slot that
is empty, so the view works with no assets at all and improves one file at a
time. No code change is needed. A Vite development server sees a new public
asset on reload; the packaged demo must rebuild the frontend/JAR so the new file
is copied into the embedded distribution.

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
│   ├── asphalt.jpg
│   └── junction.jpg
├── land/
│   ├── normal.jpg          ordinary urban fabric — the base everything sits on
│   ├── park.jpg
│   ├── water.jpg
│   ├── grass.jpg
│   └── … one per LandCoverType
└── buildings/
    ├── residential.jpg
    ├── commercial.jpg
    ├── office.jpg
    ├── school.jpg
    ├── hospital.jpg
    ├── industrial.jpg
    ├── parking.jpg
    └── other.jpg
```

The paths are declared in `frontend/src/physical/assetManifest.js`. To add a
slot, add it there; the loader picks it up automatically.

Vehicle sprites use PNG because they need transparency. Opaque textures use
JPEG to keep the shipped artwork small. Generated roof/surface textures are
512 px and colour-normalised; several retained land textures are 1024 px, and
cropped vehicle sprites keep their useful native dimensions. Full-resolution
source archives do not belong in `public/`.

## Vehicles — 4 files

Top-down sprites, transparent background, cropped tight to the bodywork.

| File | Vehicle | Real size | Suggested pixels |
|---|---|---|---|
| `car.png` | Car | 4.5 × 1.8 m | 230 × 576 |
| `motorcycle.png` | Motorcycle with rider | 2.1 × 0.75 m | 96 × 269 |
| `bus.png` | City bus | 12 × 2.5 m | 320 × 1536 |
| `emergency.png` | Ambulance or police car | 5.0 × 2.0 m | 256 × 640 |

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

## Road surfaces — 2 files

**Seamlessly tiling** textures. A tile whose edges do not meet its opposite side
shows as a visible grid across the whole map. 512 × 512 px is plenty.

| File | Used for |
|---|---|
| `asphalt.jpg` | The carriageway |
| `junction.jpg` | Paved area inside a junction |

## Land cover — 12 files

The ground between the roads, one texture per `LandCoverType`. `normal.jpg` is
the base the whole scene is drawn on; the rest fill mapped polygons on top.

| File | What it covers | Polygons in the current map |
|---|---|---|
| `normal.jpg` | Ordinary urban fabric — everywhere nothing else is mapped | the background |
| `grass.jpg` | Verges and yards | 136 |
| `commercial.jpg` | Commercial blocks | 25 |
| `water.jpg` | Saigon river, Ben Nghe canal | 21 |
| `park.jpg` | Tao Dan and the rest | 18 |
| `construction.jpg` | Sites | 17 |
| `other.jpg` | Mapped, but not a type this model distinguishes | 20 |
| `residential.jpg`, `industrial.jpg`, `forest.jpg`, `religious.jpg`, `recreation.jpg` | as named | few or none |

`normal.jpg` is the one that matters most: it is the majority of the screen.
Use a quiet, cool-grey pavement or concrete texture. Brown render, packed earth
and warm gravel make this dense District 1 map read as countryside.

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
| `other.jpg` | Untagged | 885 |
| `residential.jpg` | Houses, apartments | 147 |
| `office.jpg` | Offices | 57 |
| `commercial.jpg` | Shops, hotels | 36 |
| `school.jpg` | Schools, universities | 18 |
| `hospital.jpg` | Hospitals, clinics | 17 |
| `parking.jpg` | Car parks | 2 |
| `industrial.jpg` | Warehouses | 0 |

These are not complete building sprites. The renderer must fill arbitrary OSM
footprints, so each file is a top-down roof material. The shipped set adds
semantic cues while keeping one restrained palette: terracotta for residential,
blue panels for commercial/office, green tiles for schools, red crosses for
hospitals, corrugated metal for industrial, parking bays, and a neutral plant
roof for `OTHER`.

`tools/generate_urban_assets.py` reproducibly colour-normalises the CC0 source
materials and adds those cues. The type tint is drawn first and the material is
then overlaid at partial opacity, so an asset cannot erase the semantic colour.

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
2. **`asphalt.jpg` and `land/normal.jpg`.** Two files, and they cover most of
   the screen area.
3. **`buildings/other.jpg` and `buildings/residential.jpg`.** 1032 of the 1162
   buildings.

Everything else is polish.

## Licensing

Prefer **CC0** so nothing has to be attributed in the report or the demo. If you
use a CC BY asset, record the author and licence in this file next to the slot —
a university submission is a publication.

## Checking it worked

The physical view's status bar counts the slots that loaded:

```text
Live · frame 412 · 126 vehicles · 68 buildings · 26 assets · drag to move, scroll to zoom
```

Anything below `26 assets` means at least one file was not found or decoded.
Check the filenames and that the files sit under `frontend/public/assets/`, not
`frontend/src/`.

## Filled so far

| Slot | Source | Licence |
|---|---|---|
| `vehicles/car.png` | [Devil's Work.shop 2D Car Pack](https://opengameart.org/content/2d-car-pack-0), `car01/top_0000.png` | CC0 |
| `vehicles/motorcycle.png` | [Kenney Racing Pack](https://kenney.nl/assets/racing-pack), `motorcycle_blue.png` | CC0 |
| `vehicles/bus.png` | [Devil's Work.shop 2D Car Pack](https://opengameart.org/content/2d-car-pack-0), `bus/top_0000.png` | CC0 |
| `vehicles/emergency.png` | [Devil's Work.shop 2D Car Pack](https://opengameart.org/content/2d-car-pack-0), `policeCar/top_0000.png` | CC0 |
| `surfaces/asphalt.jpg` | [Poly Haven Clean Asphalt](https://polyhaven.com/a/clean_asphalt), 1K diffuse, colour-normalised | CC0 |
| `surfaces/junction.jpg` | [Poly Haven Clean Asphalt](https://polyhaven.com/a/clean_asphalt), 1K diffuse, slightly lightened | CC0 |
| `buildings/residential.jpg` | [Poly Haven Ceramic Roof 01](https://polyhaven.com/a/ceramic_roof_01), colour-normalised | CC0 |
| `buildings/commercial.jpg` | [Poly Haven Grey Roof Tiles 02](https://polyhaven.com/a/grey_roof_tiles_02), blue panel overlay | CC0 |
| `buildings/office.jpg` | [Poly Haven Grey Roof 01](https://polyhaven.com/a/grey_roof_01), slate panel overlay | CC0 |
| `buildings/school.jpg` | [Poly Haven Roof Tiles 14](https://polyhaven.com/a/roof_tiles_14), green panel overlay | CC0 |
| `buildings/hospital.jpg` | [Poly Haven White Plaster 02](https://polyhaven.com/a/white_plaster_02), red-cross overlay | CC0 |
| `buildings/industrial.jpg` | [Poly Haven Corrugated Iron](https://polyhaven.com/a/corrugated_iron), seam overlay | CC0 |
| `buildings/parking.jpg` | [Poly Haven Aerial Asphalt 01](https://polyhaven.com/a/aerial_asphalt_01), parking-bay overlay | CC0 |
| `buildings/other.jpg` | [Poly Haven Concrete Floor 02](https://polyhaven.com/a/concrete_floor_02), rooftop-plant overlay | CC0 |
| `land/normal.jpg` | [Poly Haven Concrete Pavement 03](https://polyhaven.com/a/concrete_pavement_03), 1K diffuse, colour-normalised | CC0 |
| `land/park.jpg` | [Poly Haven Aerial Grass Rock](https://polyhaven.com/a/aerial_grass_rock), 1K diffuse | CC0 |
| `land/water.jpg` | [OpenGameArt Seamless Water Tiles](https://opengameart.org/content/seamless-water-tiles), `dark_water.jpg` by Hazmat Harry | CC0 |
| `land/grass.jpg` | [Poly Haven Leafy Grass](https://polyhaven.com/a/leafy_grass), 1K diffuse | CC0 |
| `land/forest.jpg` | [Poly Haven Forest Ground 01](https://polyhaven.com/a/forrest_ground_01), 1K diffuse | CC0 |
| `land/commercial.jpg` | [Poly Haven Pavement 02](https://polyhaven.com/a/pavement_02), 1K diffuse | CC0 |
| `land/residential.jpg` | [Poly Haven Large Square Pattern 01](https://polyhaven.com/a/large_square_pattern_01), 1K diffuse | CC0 |
| `land/industrial.jpg` | [Poly Haven Gravel Concrete 03](https://polyhaven.com/a/gravel_concrete_03), 1K diffuse | CC0 |
| `land/construction.jpg` | [Poly Haven Dry Ground Rocks](https://polyhaven.com/a/dry_ground_rocks), 1K diffuse | CC0 |
| `land/religious.jpg` | [Poly Haven Patterned Brick Floor](https://polyhaven.com/a/patterned_brick_floor), 1K diffuse | CC0 |
| `land/recreation.jpg` | [Poly Haven Rubber Tiles](https://polyhaven.com/a/rubber_tiles), 1K diffuse | CC0 |
| `land/other.jpg` | [Poly Haven Gravel Embedded Concrete](https://polyhaven.com/a/gravel_embedded_concrete), 1K diffuse | CC0 |

All 26 slots are filled. The fallback renderer remains in place so a missing or
unreadable file still cannot prevent the scene from rendering.

Texture scale follows what the material represents: surface tiles cover 12 m,
building roofs 24 m, and land cover 42 m per repeat. Land, roof and road photos
are low-opacity overlays over the renderer's colour palette rather than full
strength replacements. This keeps lane markings and building categories
readable.
