#!/usr/bin/env python3
"""Focused regressions for the OSM generator's cache and bus-stop snapping."""

import unittest
from datetime import datetime, timedelta, timezone

try:
    from .generate_osm_map import (
        build_bus_stops, cached_elements, osm_snapshot_is_current)
except ImportError:
    from generate_osm_map import (
        build_bus_stops, cached_elements, osm_snapshot_is_current)


class OsmCacheFreshnessTest(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 7, 28, 12, 0, tzinfo=timezone.utc)

    def payload(self, age):
        timestamp = (self.now - age).isoformat().replace("+00:00", "Z")
        return {
            "osm3s": {"timestamp_osm_base": timestamp},
            "elements": [{"type": "way", "id": 1}],
        }

    def test_recent_snapshot_is_accepted(self):
        payload = self.payload(timedelta(minutes=10))

        self.assertTrue(osm_snapshot_is_current(payload, self.now))
        self.assertEqual(
            [{"type": "way", "id": 1}],
            cached_elements(payload, now=self.now))

    def test_stale_snapshot_is_rejected_by_default(self):
        payload = self.payload(timedelta(days=2))

        self.assertFalse(osm_snapshot_is_current(payload, self.now))
        self.assertIsNone(cached_elements(payload, now=self.now))

    def test_legacy_cache_requires_explicit_offline_mode(self):
        legacy = [{"type": "way", "id": 1}]

        self.assertIsNone(cached_elements(legacy, now=self.now))
        self.assertEqual(
            legacy,
            cached_elements(
                legacy, allow_stale_cache=True, now=self.now))


class BusStopSnappingTest(unittest.TestCase):
    """
    A north-south street inside BOUNDS, split into both directions.

    Forward runs south to north, so a stop east of the centreline is on the
    forward driver's right and belongs to the forward road.
    """

    SOUTH, NORTH = 10.7700, 10.7710
    LON = 106.7000

    def roads(self):
        forward = [[self.SOUTH, self.LON], [self.NORTH, self.LON]]
        return [
            {"id": "W1-0F", "physicalRoadId": "W1-0", "geometry": forward},
            {"id": "W1-0R", "physicalRoadId": "W1-0", "geometry": list(reversed(forward))},
        ]

    def node(self, latitude, longitude, node_id=1, name=None):
        tags = {"highway": "bus_stop"}
        if name:
            tags["name"] = name
        return {"id": node_id, "lat": latitude, "lon": longitude, "tags": tags}

    def test_stop_east_of_a_northbound_street_serves_the_northbound_road(self):
        # 0.00012 degrees of longitude is about 13 m east at this latitude.
        stops, dropped = build_bus_stops(
            [self.node(10.7705, self.LON + 0.00012)], self.roads())

        self.assertEqual(0, dropped)
        self.assertEqual("W1-0F", stops[0]["roadId"])

    def test_stop_west_of_the_same_street_serves_the_opposite_direction(self):
        stops, dropped = build_bus_stops(
            [self.node(10.7705, self.LON - 0.00012)], self.roads())

        self.assertEqual(0, dropped)
        self.assertEqual("W1-0R", stops[0]["roadId"])

    def test_position_along_road_measures_from_the_start_intersection(self):
        stops, _ = build_bus_stops(
            [self.node(10.7705, self.LON + 0.00012)], self.roads())

        self.assertAlmostEqual(0.5, stops[0]["positionAlongRoad"], places=2)

    def test_a_stop_far_from_every_road_is_dropped_not_invented(self):
        # About 300 m east of the only street on the map.
        stops, dropped = build_bus_stops(
            [self.node(10.7705, self.LON + 0.0028)], self.roads())

        self.assertEqual([], stops)
        self.assertEqual(1, dropped)

    def test_an_unnamed_stop_gets_a_label_rather_than_an_empty_name(self):
        stops, _ = build_bus_stops(
            [self.node(10.7705, self.LON + 0.00012, node_id=42)], self.roads())

        self.assertEqual("S42", stops[0]["id"])
        self.assertEqual("Stop 42", stops[0]["name"])

    def test_a_named_stop_keeps_its_name(self):
        stops, _ = build_bus_stops(
            [self.node(10.7705, self.LON + 0.00012, name="Ben Thanh")], self.roads())

        self.assertEqual("Ben Thanh", stops[0]["name"])

    def test_a_stop_outside_the_bounds_is_ignored(self):
        stops, dropped = build_bus_stops([self.node(20.0, 105.0)], self.roads())

        self.assertEqual([], stops)
        self.assertEqual(0, dropped)


if __name__ == "__main__":
    unittest.main()
