# Vehicle image assets

Place optional vehicle decoration files in this directory. Vite serves these
files from `/assets/vehicles/` without bundling them into JavaScript.

Recommended filenames:

- `car.png` or `car.webp`
- `bus.png` or `bus.webp`
- `emergency.png` or `emergency.webp`

Use a transparent background and consistent top-down orientation. Keep source
images reasonably small; 128 x 128 or 256 x 256 is enough for map markers.

To enable a file, set the corresponding `assetUrl` in
`src/vehicle-assets/vehicleAssetRegistry.js`, for example:

```js
assetUrl: '/assets/vehicles/car.webp'
```

When `assetUrl` is absent, the frontend deliberately falls back to the bundled
Lucide icon. Runtime map and scenario files belong in the repository-level
`data/` directory and should not be mixed with frontend visual assets.
