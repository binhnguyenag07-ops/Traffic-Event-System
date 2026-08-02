# Drop artwork here

Empty is a supported state: the physical view draws its own shapes for any slot
with no file, so the app works with nothing in this folder and improves as files
are added. During `npm run dev`, add a file and reload. A packaged demo served
from `frontend/dist` must be rebuilt with `npm run build` before the new artwork
appears.

See `docs/ASSETS.md` for what each slot expects, the sizes, and where to
download CC0 artwork.

Vehicle sprites must be drawn **nose-up**; surface and roof textures must tile
seamlessly.
