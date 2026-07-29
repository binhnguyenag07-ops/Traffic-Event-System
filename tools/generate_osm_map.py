#!/usr/bin/env python3
"""Generate the bounded District 1 demo network from OpenStreetMap.

Emits roads with lane data, and the buildings beside them with the access
points that let a vehicle actually start or finish a trip at one.
"""

import argparse
import hashlib
import json
import math
import tempfile
import urllib.parse
import urllib.request
from collections import Counter, defaultdict, deque
from datetime import datetime, timedelta, timezone
from pathlib import Path

BOUNDS = (10.7685, 106.6905, 10.7825, 106.7085)
ROAD_CLASSES = "primary|secondary|tertiary|unclassified|residential|living_street"
OVERPASS_URLS = (
    "https://lz4.overpass-api.de/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
)
# A mirror or local cache older than this can silently preserve obsolete
# oneway/lane tags. The bundled map is reproducible because it is committed;
# regeneration should use a recent OSM snapshot unless offline mode is explicit.
MAX_OSM_SNAPSHOT_LAG = timedelta(hours=24)
SPEEDS = {"primary": 50, "secondary": 45, "tertiary": 40, "unclassified": 35,
          "residential": 30, "living_street": 20}
CAPACITIES = {"primary": 100, "secondary": 80, "tertiary": 65, "unclassified": 50,
              "residential": 40, "living_street": 25}
# Lane counts and widths used when OSM does not tag them, by road class.
DEFAULT_LANES = {"primary": 3, "secondary": 2, "tertiary": 2, "unclassified": 1,
                 "residential": 1, "living_street": 1}
DEFAULT_LANE_WIDTH = {"primary": 3.5, "secondary": 3.25, "tertiary": 3.25,
                      "unclassified": 3.0, "residential": 3.0, "living_street": 3.0}
# OSM building/amenity tags to the domain BuildingType enum.
BUILDING_TYPES = {
    "house": "RESIDENTIAL", "apartments": "RESIDENTIAL", "residential": "RESIDENTIAL",
    "detached": "RESIDENTIAL", "terrace": "RESIDENTIAL", "dormitory": "RESIDENTIAL",
    "office": "OFFICE", "commercial": "COMMERCIAL", "retail": "COMMERCIAL",
    "shop": "COMMERCIAL", "supermarket": "COMMERCIAL", "hotel": "COMMERCIAL",
    "school": "SCHOOL", "university": "SCHOOL", "college": "SCHOOL",
    "kindergarten": "SCHOOL", "hospital": "HOSPITAL", "clinic": "HOSPITAL",
    "parking": "PARKING", "garage": "PARKING", "garages": "PARKING",
    "industrial": "INDUSTRIAL", "warehouse": "INDUSTRIAL", "factory": "INDUSTRIAL",
}
# OSM landuse/leisure/natural tags to the domain LandCoverType. Anything the
# map does not classify stays unmapped and is drawn as ordinary urban fabric --
# OSM has only about a fifth of District 1's buildings, so most ground genuinely
# has nothing recorded on it and must not be dressed up as something.
LAND_COVER_TYPES = {
    "park": "PARK", "garden": "PARK", "water": "WATER", "reservoir": "WATER",
    "grass": "GRASS", "meadow": "GRASS", "forest": "FOREST", "wood": "FOREST",
    "commercial": "COMMERCIAL", "retail": "COMMERCIAL",
    "residential": "RESIDENTIAL", "industrial": "INDUSTRIAL",
    "construction": "CONSTRUCTION", "brownfield": "CONSTRUCTION",
    "religious": "RELIGIOUS", "cemetery": "RELIGIOUS",
    "recreation_ground": "RECREATION", "pitch": "RECREATION",
}

# A building further than this from any junction has no plausible access.
MAX_DRIVEWAY_METRES = 160.0


def distance_km(first, second):
    lat1, lon1 = map(math.radians, first)
    lat2, lon2 = map(math.radians, second)
    delta_lat = lat2 - lat1
    delta_lon = lon2 - lon1
    value = (math.sin(delta_lat / 2) ** 2
             + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lon / 2) ** 2)
    return 6371.0 * 2 * math.asin(math.sqrt(value))


def geometry_distance(points):
    return sum(distance_km(points[index - 1], points[index]) for index in range(1, len(points)))


def osm_snapshot_is_current(payload, now=None):
    """Whether an Overpass response identifies a recent OSM base snapshot."""
    if not isinstance(payload, dict):
        return False
    timestamp = (payload.get("osm3s") or {}).get("timestamp_osm_base")
    if not timestamp:
        return False
    try:
        snapshot_time = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    except (TypeError, ValueError):
        return False
    reference_time = now or datetime.now(timezone.utc)
    return reference_time - snapshot_time <= MAX_OSM_SNAPSHOT_LAG


def cached_elements(payload, allow_stale_cache=False, now=None):
    """Returns usable elements, keeping legacy/stale caches opt-in."""
    if isinstance(payload, dict) and isinstance(payload.get("elements"), list):
        if allow_stale_cache or osm_snapshot_is_current(payload, now):
            return payload["elements"]
        return None
    if allow_stale_cache and isinstance(payload, list):
        # Before freshness metadata was retained, caches contained only this list.
        return payload
    return None


def overpass(query, label, allow_stale_cache=False):
    """Runs one Overpass query and caches a freshness-verifiable response."""
    cache_key = hashlib.sha256(query.encode()).hexdigest()[:12]
    cache_dir = Path(tempfile.gettempdir()) / "traffic-event-system-osm"
    cache_dir.mkdir(parents=True, exist_ok=True)
    cache = cache_dir / f"{label}-{cache_key}.json"
    if cache.exists():
        try:
            elements = cached_elements(
                json.loads(cache.read_text(encoding="utf-8")),
                allow_stale_cache=allow_stale_cache)
            if elements is not None:
                return elements
        except (OSError, json.JSONDecodeError):
            # A truncated cache is recoverable: continue to the mirrors.
            pass
    body = urllib.parse.urlencode({"data": query}).encode()
    last_error = None
    for url in OVERPASS_URLS:
        try:
            request = urllib.request.Request(
                url, data=body, headers={"User-Agent": "Traffic-Event-System/1.0"})
            with urllib.request.urlopen(request, timeout=180) as response:
                payload = json.load(response)
                elements = cached_elements(
                    payload, allow_stale_cache=allow_stale_cache)
                if elements is None:
                    timestamp = (payload.get("osm3s") or {}).get(
                        "timestamp_osm_base", "missing")
                    raise RuntimeError(
                        f"mirror snapshot is stale or unverifiable ({timestamp})")
                temporary_cache = cache.with_suffix(".tmp")
                temporary_cache.write_text(json.dumps(payload), encoding="utf-8")
                temporary_cache.replace(cache)
                return elements
        except Exception as exception:  # Try the next public Overpass mirror.
            last_error = exception
    raise RuntimeError(f"Unable to fetch OpenStreetMap {label}: {last_error}")


def fetch_ways(allow_stale_cache=False):
    south, west, north, east = BOUNDS
    return overpass(f'''[out:json][timeout:180];
way["highway"~"^({ROAD_CLASSES})$"]({south},{west},{north},{east});
out body geom;''', "ways", allow_stale_cache)


def fetch_land_cover(allow_stale_cache=False):
    """Parks, water and yards. Without these the river and Tao Dan read as bare land."""
    south, west, north, east = BOUNDS
    return overpass(f'''[out:json][timeout:180];
(way["leisure"="park"]({south},{west},{north},{east});
 way["landuse"]({south},{west},{north},{east});
 way["natural"="water"]({south},{west},{north},{east});
 way["waterway"="riverbank"]({south},{west},{north},{east}););
out body geom;''', "landcover", allow_stale_cache)


def land_cover_type_of(tags):
    for key in ("leisure", "landuse", "natural", "waterway"):
        value = tags.get(key, "").lower()
        if value in LAND_COVER_TYPES:
            return LAND_COVER_TYPES[value]
        if key == "waterway" and value:
            return "WATER"
    return "OTHER"


def build_land_cover(elements):
    """Closed ways with at least three points, clipped to the bounds by centroid."""
    south, west, north, east = BOUNDS
    covers = []
    counts = Counter()
    for way in elements:
        points = [(p["lat"], p["lon"]) for p in (way.get("geometry") or [])]
        if len(points) >= 2 and points[0] == points[-1]:
            points = points[:-1]
        if len(points) < 3:
            continue
        latitude = sum(p[0] for p in points) / len(points)
        longitude = sum(p[1] for p in points) / len(points)
        if not (south <= latitude <= north and west <= longitude <= east):
            continue
        kind = land_cover_type_of(way.get("tags", {}))
        counts[kind] += 1
        covers.append({
            "id": f"L{way['id']}",
            "type": kind,
            "polygon": [[round(lat, 7), round(lon, 7)] for lat, lon in points],
        })
    return covers, counts


def fetch_buildings(allow_stale_cache=False):
    """Building footprints. Without these the map has nowhere for a trip to begin."""
    south, west, north, east = BOUNDS
    return overpass(f'''[out:json][timeout:180];
way["building"]({south},{west},{north},{east});
out body geom;''', "buildings", allow_stale_cache)


def parse_speed(tags, road_class):
    raw = tags.get("maxspeed", "")
    digits = "".join(character for character in raw if character.isdigit())
    return int(digits) if digits else SPEEDS[road_class]


def split_ways(ways):
    node_usage = Counter()
    node_positions = {}
    node_streets = defaultdict(set)
    valid_ways = []
    for way in ways:
        nodes = way.get("nodes", [])
        geometry = way.get("geometry", [])
        if len(nodes) < 2 or len(nodes) != len(geometry):
            continue
        runs = []
        current = []
        south, west, north, east = BOUNDS
        for node_id, point in zip(nodes, geometry):
            coordinate = (point["lat"], point["lon"])
            if south <= coordinate[0] <= north and west <= coordinate[1] <= east:
                current.append((node_id, coordinate))
            else:
                if len(current) > 1:
                    runs.append(current)
                current = []
        if len(current) > 1:
            runs.append(current)

        street = way.get("tags", {}).get("name")
        for run in runs:
            run_nodes = [entry[0] for entry in run]
            points = [entry[1] for entry in run]
            valid_ways.append((way, run_nodes, points))
            node_usage.update(set(run_nodes))
            for node_id, point in run:
                node_positions[node_id] = point
                if street:
                    node_streets[node_id].add(street)

    junctions = {node_id for node_id, count in node_usage.items() if count > 1}
    for _, nodes, points in valid_ways:
        junctions.update((nodes[0], nodes[-1]))
        distance_since_junction = 0.0
        for index in range(1, len(nodes)):
            distance_since_junction += distance_km(points[index - 1], points[index])
            if distance_since_junction >= 0.32 and index < len(nodes) - 1:
                junctions.add(nodes[index])
                distance_since_junction = 0.0

    segments = []
    segment_numbers = defaultdict(int)
    for way, nodes, points in valid_ways:
        start_index = 0
        for index in range(1, len(nodes)):
            if nodes[index] not in junctions:
                continue
            if nodes[start_index] != nodes[index]:
                segments.append({
                    "way": way,
                    "number": segment_numbers[way["id"]],
                    "start": nodes[start_index],
                    "end": nodes[index],
                    "geometry": points[start_index:index + 1],
                })
                segment_numbers[way["id"]] += 1
            start_index = index
    return segments, node_positions, node_streets


def largest_component(segments):
    if not segments:
        raise RuntimeError("OpenStreetMap query returned no usable connected road segments")
    neighbors = defaultdict(set)
    for segment in segments:
        neighbors[segment["start"]].add(segment["end"])
        neighbors[segment["end"]].add(segment["start"])

    components = []
    unseen = set(neighbors)
    while unseen:
        root = next(iter(unseen))
        component = {root}
        queue = deque([root])
        unseen.remove(root)
        while queue:
            for neighbor in neighbors[queue.popleft()]:
                if neighbor in unseen:
                    unseen.remove(neighbor)
                    component.add(neighbor)
                    queue.append(neighbor)
        components.append(component)
    keep = max(components, key=len)
    return [segment for segment in segments if segment["start"] in keep and segment["end"] in keep]



def tag_int(tags, key):
    digits = "".join(character for character in tags.get(key, "") if character.isdigit())
    return int(digits) if digits else None


def parse_lane_count(tags, road_class, direction, split_both_ways):
    """Lanes for one direction, and whether OSM said so or we inferred it."""
    forward = tag_int(tags, "lanes:forward")
    backward = tag_int(tags, "lanes:backward")
    if direction == "F" and forward:
        return max(1, forward), "OSM"
    if direction == "R" and backward:
        return max(1, backward), "OSM"
    total = tag_int(tags, "lanes")
    if total:
        # A bidirectional way tags the total for both sides, so a directed half
        # gets half of it.
        return max(1, total // 2 if split_both_ways else total), "OSM"
    return DEFAULT_LANES[road_class], "INFERRED"


def parse_lane_width(tags, road_class, lane_count):
    raw = "".join(c for c in tags.get("width", "") if c.isdigit() or c == ".")
    try:
        carriageway = float(raw) if raw else None
    except ValueError:
        carriageway = None
    if carriageway and lane_count:
        return round(max(2.5, min(5.0, carriageway / lane_count)), 2), "OSM"
    return DEFAULT_LANE_WIDTH[road_class], "INFERRED"


def building_type_of(tags):
    for key in ("building", "amenity", "shop", "office"):
        value = tags.get(key, "").lower()
        if value in BUILDING_TYPES:
            return BUILDING_TYPES[value]
        if key == "shop" and value:
            return "COMMERCIAL"
        if key == "office" and value:
            return "OFFICE"
    return "OTHER"


def build_buildings(building_ways, intersections, center_lat, center_lon):
    """
    Buildings snapped to the junction that serves them.

    A building whose nearest junction is further than MAX_DRIVEWAY_METRES is
    dropped rather than given an invented access point: the loader validates
    that every access point names a real intersection, and a fabricated one
    would put trips on roads that do not reach the building.
    """
    anchors = [(entry["id"], entry["latitude"], entry["longitude"]) for entry in intersections]
    buildings = []
    dropped = 0
    for way in building_ways:
        geometry = way.get("geometry") or []
        points = [(point["lat"], point["lon"]) for point in geometry
                  if point.get("lat") is not None]
        if len(points) < 3:
            continue
        if points[0] == points[-1]:
            points = points[:-1]
        latitude = sum(point[0] for point in points) / len(points)
        longitude = sum(point[1] for point in points) / len(points)
        south, west, north, east = BOUNDS
        if not (south <= latitude <= north and west <= longitude <= east):
            continue

        nearest_id, nearest_metres = None, None
        for anchor_id, anchor_lat, anchor_lon in anchors:
            metres = distance_km((latitude, longitude), (anchor_lat, anchor_lon)) * 1000.0
            if nearest_metres is None or metres < nearest_metres:
                nearest_id, nearest_metres = anchor_id, metres
        if nearest_id is None or nearest_metres > MAX_DRIVEWAY_METRES:
            dropped += 1
            continue

        tags = way.get("tags", {})
        building_id = f"B{way['id']}"
        buildings.append({
            "id": building_id,
            "name": tags.get("name") or f"Building {way['id']}",
            "type": building_type_of(tags),
            "x": round((longitude - center_lon) * 111.32 * math.cos(math.radians(center_lat)), 6),
            "y": round((latitude - center_lat) * 110.574, 6),
            "latitude": round(latitude, 7),
            "longitude": round(longitude, 7),
            "accessPoints": [{
                "id": f"{building_id}-A0",
                "intersectionId": nearest_id,
                "drivewayDistanceMetres": round(max(1.0, nearest_metres), 1),
            }],
            "footprint": [[round(lat, 7), round(lon, 7)] for lat, lon in points],
        })
    return buildings, dropped


def build_map(ways, building_ways, land_cover_ways):
    segments, node_positions, node_streets = split_ways(ways)
    segments = largest_component(segments)
    used_nodes = {segment[key] for segment in segments for key in ("start", "end")}
    center_lat = sum(node_positions[node][0] for node in used_nodes) / len(used_nodes)
    center_lon = sum(node_positions[node][1] for node in used_nodes) / len(used_nodes)

    intersections = []
    for node_id in sorted(used_nodes):
        latitude, longitude = node_positions[node_id]
        streets = sorted(node_streets[node_id])
        name = " / ".join(streets[:2]) if streets else f"District 1 junction {node_id}"
        intersections.append({
            "id": f"N{node_id}",
            "name": name,
            "x": round((longitude - center_lon) * 111.32 * math.cos(math.radians(center_lat)), 6),
            "y": round((latitude - center_lat) * 110.574, 6),
            "latitude": latitude,
            "longitude": longitude,
            "locationType": "NORMAL",
        })

    roads = []
    lane_provenance = Counter()
    width_provenance = Counter()
    for segment in segments:
        way = segment["way"]
        tags = way.get("tags", {})
        road_class = tags["highway"]
        capacity = CAPACITIES[road_class]
        geometry = [[round(lat, 7), round(lon, 7)] for lat, lon in segment["geometry"]]
        common = {
            "distance": round(max(0.01, geometry_distance(segment["geometry"])), 4),
            "speedLimit": parse_speed(tags, road_class),
            "capacity": capacity,
            # Runtime traffic is owned by the simulator. Persisting synthetic
            # counts here creates invisible vehicles that consume road capacity.
            "currentVehicleCount": 0,
            "congestionLevel": 0.0,
            "vehicleDensity": 0.0,
            "roadStatus": "OPEN",
            "roadClass": road_class.upper(),
        }
        directions = [(segment["start"], segment["end"], geometry, "F")]
        oneway = tags.get("oneway", "").lower()
        if oneway == "-1":
            directions = [(segment["end"], segment["start"], list(reversed(geometry)), "R")]
        elif oneway not in {"yes", "1", "true"} and tags.get("junction") != "roundabout":
            directions.append((segment["end"], segment["start"], list(reversed(geometry)), "R"))
        split_both_ways = len(directions) > 1
        street = tags.get("name") or f"{road_class.replace('_', ' ').title()} way {way['id']}"
        for start, end, road_geometry, suffix in directions:
            lane_count, lane_source = parse_lane_count(
                tags, road_class, suffix, split_both_ways)
            lane_width, width_source = parse_lane_width(tags, road_class, lane_count)
            lane_provenance[lane_source] += 1
            width_provenance[width_source] += 1
            roads.append({
                "id": f"W{way['id']}-{segment['number']}{suffix}",
                "name": street,
                "physicalRoadId": f"W{way['id']}-{segment['number']}",
                "startIntersectionId": f"N{start}",
                "endIntersectionId": f"N{end}",
                **common,
                "laneCount": lane_count,
                "laneWidthMetres": lane_width,
                "geometry": road_geometry,
            })

    if len(intersections) > 600 or len(roads) > 1_200:
        raise RuntimeError(
            f"Generated network exceeds import limits: {len(intersections)} intersections, {len(roads)} roads")

    buildings, dropped = build_buildings(building_ways, intersections, center_lat, center_lon)
    land_covers, cover_counts = build_land_cover(land_cover_ways)
    # Provenance is reported, not written into the map: the loader rejects
    # fields it does not know, and a guessed lane count must still not be
    # presented as a measured one.
    print(f"  lane count   : {lane_provenance['OSM']} from OSM, {lane_provenance['INFERRED']} inferred")
    print(f"  lane width   : {width_provenance['OSM']} from OSM, {width_provenance['INFERRED']} inferred")
    print(f"  buildings    : {len(buildings)} kept, {dropped} dropped with no junction within "
          f"{MAX_DRIVEWAY_METRES:.0f} m")
    by_type = Counter(building["type"] for building in buildings)
    print(f"  building mix : {dict(by_type.most_common())}")
    print(f"  land cover   : {len(land_covers)} polygons {dict(cover_counts.most_common(6))}")
    return {"intersections": intersections, "roads": roads,
            "buildings": buildings, "landCovers": land_covers}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output", nargs="?", default="data/maps/district1_network.json")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument(
        "--offline-cache",
        action="store_true",
        help="explicitly allow an old/legacy Overpass cache without refreshing it")
    args = parser.parse_args()
    network = build_map(
        fetch_ways(args.offline_cache),
        fetch_buildings(args.offline_cache),
        fetch_land_cover(args.offline_cache))
    print(f"Generated {len(network['intersections'])} intersections, "
          f"{len(network['roads'])} directed roads, {len(network['buildings'])} buildings, "
          f"{len(network['landCovers'])} land-cover polygons")
    if not args.dry_run:
        Path(args.output).write_text(json.dumps(network, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
