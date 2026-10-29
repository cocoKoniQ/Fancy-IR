# Third-party notices

Fancy-IR's own code is MIT-licensed (see `LICENSE`). The bundled IR catalog
(`core/data/src/main/assets/catalog/`) contains data from the projects below; every code set in
it records its source, upstream path and licence. `tools/irdb-compiler/generate-catalog.sh`
pins the exact upstream revisions.

## Flipper-IRDB

- Source: https://github.com/Lucaslhm/Flipper-IRDB
- Licence: CC0 1.0 Universal (public-domain dedication). Its README states that commits
  prior to `2319685` are not covered. The bundled catalog therefore only includes files added
  after that commit (`POLICY=cc0-only`, the default). Older files are fetched on the user's
  device on demand and are not redistributed by this project.

## SmartIR

- Source: https://github.com/smartHomeHub/SmartIR
- Licence: MIT. Code files for climate, fan and light devices are bundled (decoded from
  Broadlink base64 / Pronto) and fetched on demand.

```
MIT License

Copyright (c) 2019 Vassilis Panos

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
