#!/usr/bin/env python3
"""Focused regressions for the OSM generator's direction-data cache."""

import unittest
from datetime import datetime, timedelta, timezone

try:
    from .generate_osm_map import cached_elements, osm_snapshot_is_current
except ImportError:
    from generate_osm_map import cached_elements, osm_snapshot_is_current


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


if __name__ == "__main__":
    unittest.main()
