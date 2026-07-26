// Every place the physical view can take real artwork.
//
// Nothing here is required. Each slot names a file that may or may not exist;
// whatever is missing falls back to the shape the renderer draws itself, so the
// view works with an empty assets folder and improves one file at a time as
// they are dropped in. That means artwork can be sourced gradually without ever
// leaving the app in a broken state.
//
// Files live under `frontend/public/assets/...`, so they are served as-is and
// need no build step. See `docs/ASSETS.md` for what to download and the sizes
// each slot expects.

const BASE = 'assets'

/**
 * Vehicle sprites, drawn from above with the nose pointing UP.
 *
 * Orientation matters: the renderer rotates a sprite by the vehicle's compass
 * heading and adds no offset, so a texture drawn facing right would put every
 * vehicle sideways. Transparent background, tightly cropped to the bodywork.
 */
export const VEHICLE_ASSETS = {
  CAR: `${BASE}/vehicles/car.png`,
  MOTORCYCLE: `${BASE}/vehicles/motorcycle.png`,
  BUS: `${BASE}/vehicles/bus.png`,
  EMERGENCY: `${BASE}/vehicles/emergency.png`,
}

/**
 * Seamlessly tiling surface textures. These are repeated across an area, so an
 * edge that does not meet its opposite side will show as a visible grid.
 */
export const SURFACE_ASSETS = {
  ROAD: `${BASE}/surfaces/asphalt.png`,
  JUNCTION: `${BASE}/surfaces/junction.png`,
}

/**
 * Roof textures, tiled over a building footprint and chosen by what the
 * building is for. A missing entry falls back to the flat tint for that type.
 */
export const BUILDING_ASSETS = {
  RESIDENTIAL: `${BASE}/buildings/residential.png`,
  COMMERCIAL: `${BASE}/buildings/commercial.png`,
  OFFICE: `${BASE}/buildings/office.png`,
  SCHOOL: `${BASE}/buildings/school.png`,
  HOSPITAL: `${BASE}/buildings/hospital.png`,
  INDUSTRIAL: `${BASE}/buildings/industrial.png`,
  PARKING: `${BASE}/buildings/parking.png`,
  OTHER: `${BASE}/buildings/other.png`,
}

/**
 * Ground cover, tiled over the patches between the roads. NORMAL is the
 * background everything is drawn on — ordinary urban fabric with nothing
 * recorded on it — so it takes the place the old `ground` slot held.
 */
export const LAND_ASSETS = {
  NORMAL: `${BASE}/land/normal.png`,
  PARK: `${BASE}/land/park.png`,
  WATER: `${BASE}/land/water.png`,
  GRASS: `${BASE}/land/grass.png`,
  FOREST: `${BASE}/land/forest.png`,
  COMMERCIAL: `${BASE}/land/commercial.png`,
  RESIDENTIAL: `${BASE}/land/residential.png`,
  INDUSTRIAL: `${BASE}/land/industrial.png`,
  CONSTRUCTION: `${BASE}/land/construction.png`,
  RELIGIOUS: `${BASE}/land/religious.png`,
  RECREATION: `${BASE}/land/recreation.png`,
  OTHER: `${BASE}/land/other.png`,
}

/** Every slot, flattened, for the loader to attempt in one pass. */
export function allAssetSlots() {
  return [
    ...Object.entries(VEHICLE_ASSETS).map(([key, url]) => ({ group: 'vehicles', key, url })),
    ...Object.entries(SURFACE_ASSETS).map(([key, url]) => ({ group: 'surfaces', key, url })),
    ...Object.entries(BUILDING_ASSETS).map(([key, url]) => ({ group: 'buildings', key, url })),
    ...Object.entries(LAND_ASSETS).map(([key, url]) => ({ group: 'land', key, url })),
  ]
}
