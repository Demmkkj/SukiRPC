# Suki Theme Format 1

Use a UTF-8 JSON object named with the extension `.sukitheme`. With artwork, use an ordinary ZIP (no ZIP64 or archive comment) containing exactly `theme.json` and `background.jpg` at the root. Include `"background":"background.jpg"` only in the ZIP manifest. Importers must validate before installing. No scripts, paths, shader source, URLs or commands are interpreted.

```json
{
  "formatVersion":1,"minSukiRPC":"1.2.0","version":"1","name":"Northern Sky","base":"AURORA","author":"Community","description":"Emerald on ocean blue",
  "palette":{"primary":"#74EAC1","secondary":"#609AC8","accent":"#D3ADFA","background":"#071922","surface":"#123341","text":"#EFFFFA","muted":"#ABCAC4","border":"#49777D","gradient":"#4C427A","onPrimary":"#032E23"},
  "visual":{"opacity":94,"blur":0,"shadow":0,"stroke":1,"radius":22,"motion":25,"speed":50,"glow":45,"imageOpacity":80,"dim":30,"imageBlur":0,"scale":100},
  "styles":{"font":"Sans","button":"Filled","switch":"Switch","fit":"Crop","position":"Center","gradientStyle":"Radial"}
}
```

All keys shown are required. Colors use six hexadecimal RGB digits. The base is one of ECLIPSE, OBSIDIAN, RELIC, PHANTOM, LIQUID, AERO, PRISM, CRIMSON, FROST, AURORA or NEBULA. Base controls layout. The palette overrides visual roles. Name and author allow 64 characters, description 256, version 32. Name must contain a visible character.

Visual values are integers. Opacity, motion, glow, imageOpacity and dim accept 0–100. Speed accepts 1–100. Scale accepts 100–200 percent. Blur accepts 0–32 dp, imageBlur 0–24 dp, shadow 0–16 dp, stroke 0–6 dp and radius 0–48 dp. Live effects require Ultra and device support.

Fonts: Sans, Serif, Mono. Buttons: Filled, Outlined. Toggles: Switch, Checkbox. Fit: Crop, Fit, Fill. Position: Center, Top, Bottom. Gradient: Radial, Diagonal, Solid.

Maximum manifest size is 16 KiB. The optional JPEG is limited to 4 MiB uncompressed, 8192 pixels per side and 24 megapixels. The input package limit is 4 MiB + 128 KiB. Images are sampled and re-encoded locally. Unknown versions and undeclared resources are rejected. A future reader should dispatch by formatVersion and preserve its version 1 decoder.
