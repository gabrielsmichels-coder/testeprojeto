# Cinematic artwork

The backgrounds in `CinematicArt.kt` are original abstract landscapes created for this launcher: curved ridgelines, a distant light, stars and color gradients. The visual designs are dedicated to the public domain under CC0 1.0 (https://creativecommons.org/publicdomain/zero/1.0/). This dedication applies to these original background designs, not to third-party app icons, service names or other project code.

No movie/series stills, streaming catalog images, screenshots or service logos are included. Package names select a color family; the image does not depict or advertise a particular title. Netflix, Prime Video, Max/HBO, Disney+, Globoplay, YouTube, Apple TV and Paramount package mappings have original color-themed fallback scenes. Unrecognized packages use the neutral landscape. Service names and installed app icons belong to their respective owners.

Art is rendered once on a worker thread into an opaque RGB_565 bitmap at the actual view size, capped at 1920×1080 while preserving aspect ratio. There are no live blur passes, RenderEffect, image downloads, animation loops or image libraries. Current and incoming frames are the only retained wallpaper images. A dark/light readability gradient is separate from the artwork; High Contrast uses a solid background instead.

This version does not import user-supplied images. To add photographic art in a future version, obtain suitable rights and preserve attribution/license metadata; do not scrape streaming services.
