function present(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function titleCase(value) {
  return String(value || 'other')
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\p{L}/gu, (letter) => letter.toUpperCase())
}

function coordinateLabel(item) {
  const latitude = Number(item?.latitude ?? item?.y)
  const longitude = Number(item?.longitude ?? item?.x)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return ''
  return `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`
}

export function intersectionLabel(intersection) {
  const name = present(intersection?.name)
  const coordinates = coordinateLabel(intersection)
  if (name && name !== intersection?.id) {
    return coordinates ? `${name} · ${coordinates}` : name
  }
  return coordinates ? `Junction at ${coordinates}` : 'Unnamed junction'
}

export function roadLabel(road, intersectionById = new Map()) {
  const name = present(road?.name) || 'Unnamed road'
  const from = intersectionLabel(intersectionById.get(road?.startIntersectionId))
  const to = intersectionLabel(intersectionById.get(road?.endIntersectionId))
  const lanes = Math.max(1, Number(road?.laneCount) || 1)
  const distanceKm = Number(road?.distance)
  const length = Number.isFinite(distanceKm)
    ? ` · ${Math.max(1, Math.round(distanceKm * 1_000))} m`
    : ''
  return `${name} — ${from} → ${to} · ${lanes} ${lanes === 1 ? 'lane' : 'lanes'}${length}`
}

export function buildingLabel(building, intersectionById = new Map()) {
  const rawName = present(building?.name)
  const generic = !rawName || /^building\s+\d+$/i.test(rawName)
  const type = titleCase(building?.type)
  const name = generic ? `${type} building` : rawName
  const accessId = building?.accessPoints?.[0]?.intersectionId
  const access = intersectionById.get(accessId)
  const location = access ? intersectionLabel(access) : coordinateLabel(building)
  const coordinates = coordinateLabel(building)
  const disambiguator = coordinates && location !== coordinates
    ? ` · ${coordinates}`
    : ''
  return location ? `${name} · near ${location}${disambiguator}` : name
}
