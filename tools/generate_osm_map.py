#!/usr/bin/env python3
"""Generate the bounded District 1 demo network from OpenStreetMap ways."""

import argparse
import hashlib
import json
import math
import urllib.parse
import urllib.request
from collections import Counter, defaultdict, deque
from pathlib import Path

BOUNDS = (10.7685, 106.6905, 10.7825, 106.7085)
ROAD_CLASSES = "primary|secondary|tertiary|unclassified|residential|living_street"
OVERPASS_URLS = (
    "https://lz4.overpass-api.de/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
)
SPEEDS = {"primary": 50, "secondary": 45, "tertiary": 40, "unclassified": 35,
          "residential": 30, "living_street": 20}
CAPACITIES = {"primary": 100, "secondary": 80, "tertiary": 65, "unclassified": 50,
              "residential": 40, "living_street": 25}


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


def fetch_ways():
    south, west, north, east = BOUNDS
    query = f'''[out:json][timeout:90];
way["highway"~"^({ROAD_CLASSES})$"]({south},{west},{north},{east});
out body geom;'''
    cache_key = hashlib.sha256(query.encode()).hexdigest()[:12]
    cache = Path(f"/tmp/traffic-event-system-osm-ways-{cache_key}.json")
    if cache.exists():
        return json.loads(cache.read_text(encoding="utf-8"))
    body = urllib.parse.urlencode({"data": query}).encode()
    last_error = None
    for url in OVERPASS_URLS:
        try:
            request = urllib.request.Request(url, data=body, headers={"User-Agent": "Traffic-Event-System/1.0"})
            with urllib.request.urlopen(request, timeout=120) as response:
                elements = json.load(response)["elements"]
                cache.write_text(json.dumps(elements), encoding="utf-8")
                return elements
        except Exception as exception:  # Try the next public Overpass mirror.
            last_error = exception
    raise RuntimeError(f"Unable to fetch OpenStreetMap data: {last_error}")


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


def build_map(ways):
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
    for segment in segments:
        way = segment["way"]
        tags = way.get("tags", {})
        road_class = tags["highway"]
        capacity = CAPACITIES[road_class]
        target_density = 0.08 + ((way["id"] + segment["number"] * 17) % 44) / 100
        count = min(capacity - 1, round(capacity * target_density))
        density = round(count / capacity, 3)
        congestion = round(max(0.04, density - 0.12), 2)
        status = "CONGESTED" if density >= 0.46 else "OPEN"
        geometry = [[round(lat, 7), round(lon, 7)] for lat, lon in segment["geometry"]]
        common = {
            "distance": round(max(0.01, geometry_distance(segment["geometry"])), 4),
            "speedLimit": parse_speed(tags, road_class),
            "capacity": capacity,
            "currentVehicleCount": count,
            "congestionLevel": congestion,
            "vehicleDensity": density,
            "roadStatus": status,
            "roadClass": road_class.upper(),
        }
        directions = [(segment["start"], segment["end"], geometry, "F")]
        oneway = tags.get("oneway", "").lower()
        if oneway == "-1":
            directions = [(segment["end"], segment["start"], list(reversed(geometry)), "R")]
        elif oneway not in {"yes", "1", "true"} and tags.get("junction") != "roundabout":
            directions.append((segment["end"], segment["start"], list(reversed(geometry)), "R"))
        for start, end, road_geometry, suffix in directions:
            roads.append({
                "id": f"W{way['id']}-{segment['number']}{suffix}",
                "startIntersectionId": f"N{start}",
                "endIntersectionId": f"N{end}",
                **common,
                "geometry": road_geometry,
            })

    if len(intersections) > 600 or len(roads) > 1_200:
        raise RuntimeError(
            f"Generated network exceeds import limits: {len(intersections)} intersections, {len(roads)} roads")
    return {"intersections": intersections, "roads": roads}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output", nargs="?", default="data/district1_network.json")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    network = build_map(fetch_ways())
    print(f"Generated {len(network['intersections'])} intersections and {len(network['roads'])} directed roads")
    if not args.dry_run:
        Path(args.output).write_text(json.dumps(network, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
