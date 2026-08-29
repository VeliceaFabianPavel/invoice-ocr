# Third-Party Notices

Invoice OCR — Copyright (c) 2026 Fabian Pavel Velicea. All rights reserved.

Invoice OCR itself is **proprietary**; see [LICENSE](LICENSE). It is not open
source, and at present developers are not permitted to use its code.

It does, however, build on third-party software. This file lists those
components and the licences they are distributed under.

> **These licences apply only to the components named below.**
> They grant no rights of any kind in the Invoice OCR source code, build
> scripts, documentation or installers. The presence of permissively licensed
> dependencies does not make this project permissively licensed. If you want to
> use Invoice OCR itself, you need written permission from the Author — see
> section 6 of [LICENSE](LICENSE).

---

## Declared dependencies

Declared in [pom.xml](pom.xml).

| Component | Version | Licence |
|---|---|---|
| [Tess4J](https://github.com/nguyenq/tess4j) — `net.sourceforge.tess4j:tess4j` | 5.11.0 | Apache License 2.0 |
| [JUnit 5 (Jupiter)](https://junit.org/junit5/) — `org.junit.jupiter:junit-jupiter` | 5.10.2 | Eclipse Public License 2.0 — **test scope only, not distributed** |

## Bundled in the shaded jar

`mvn package` produces `target/invoice-ocr.jar` with the Maven Shade plugin,
which embeds the runtime dependency tree. Every component below is therefore
redistributed inside that jar and inside the Windows installer.

| Component | Version | Licence |
|---|---|---|
| [Tess4J](https://github.com/nguyenq/tess4j) | 5.11.0 | Apache License 2.0 |
| [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) native library (`libtesseract534.dll`, win32-x86 and win32-x86-64) | 5.3.4 | Apache License 2.0 |
| [Leptonica](http://www.leptonica.org/) — image library linked into the Tesseract native build | (as shipped with Tesseract 5.3.4) | BSD 2-Clause |
| [lept4j](https://github.com/nguyenq/lept4j) — `net.sourceforge.lept4j:lept4j` | 1.19.1 | Apache License 2.0 |
| [JNA](https://github.com/java-native-access/jna) — `net.java.dev.jna:jna`, including the `jnidispatch` native libraries | 5.14.0 | Dual: Apache License 2.0 **or** LGPL 2.1 or later, at your option |
| [Apache PDFBox](https://pdfbox.apache.org/) — `pdfbox`, `pdfbox-io`, `pdfbox-tools`, `pdfbox-debugger` | 3.0.1 | Apache License 2.0 |
| [Apache FontBox](https://pdfbox.apache.org/) — `fontbox` | 3.0.1 | Apache License 2.0 (includes Adobe AFM font metrics under Adobe's own permissive terms) |
| [Apache PDFBox JBIG2 ImageIO plugin](https://pdfbox.apache.org/) — `jbig2-imageio` | 3.0.4 | Apache License 2.0 |
| [JAI ImageIO Core](https://github.com/jai-imageio/jai-imageio-core) — `com.github.jai-imageio:jai-imageio-core` | 1.4.0 | BSD 3-Clause (with nuclear-use disclaimer) |
| [Apache Commons IO](https://commons.apache.org/proper/commons-io/) | 2.15.1 | Apache License 2.0 |
| [Apache Commons Logging](https://commons.apache.org/proper/commons-logging/) | 1.2 | Apache License 2.0 |
| [SLF4J API](https://www.slf4j.org/) — `org.slf4j:slf4j-api` | 2.0.12 | MIT License |
| [JBoss Logging](https://github.com/jboss-logging/jboss-logging) | 3.1.4.GA | Apache License 2.0 |
| [JBoss VFS](https://github.com/jbossas/jboss-vfs) | 3.2.17.Final | Apache License 2.0 |
| ImageDeskew (`com.recognition.software.jdeskew`) — redistributed inside Tess4J | as shipped with Tess4J 5.11.0 | Apache License 2.0 |
| JNAerator runtime (`com.ochafik.lang.jnaerator`) — redistributed inside lept4j | as shipped with lept4j 1.19.1 | Distributed by lept4j under Apache License 2.0; upstream JNAerator carries its own terms |

The unmodified upstream licence and notice files travel with these components
inside the jar, under `META-INF/` (`LICENSE`, `LICENSE.txt`, `NOTICE`,
`NOTICE.txt`, `AL2.0`, `LGPL2.1`, `DEPENDENCIES`).

## Bundled in the Windows installer

See [installer/](installer/).

| Component | Licence |
|---|---|
| [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) Windows setup (UB Mannheim build), silently installed by the setup as payload | Apache License 2.0 |
| Tesseract language data `eng.traineddata` and `ron.traineddata` ([tesseract-ocr/tessdata](https://github.com/tesseract-ocr/tessdata)) | Apache License 2.0 |
| [NSIS](https://nsis.sourceforge.io/) — used to build the installer, its runtime stub is embedded in the produced `.exe` | zlib/libpng licence |

## Runtime platform

Not bundled; required to be present on the user's machine.

| Component | Licence |
|---|---|
| A Java 17 or later runtime (Swing, `javax.imageio` and the rest of the Java SE class library are used) | Depends on the JDK build the user installs — e.g. GPLv2 with Classpath Exception for OpenJDK builds |

---

## Licence texts

Full texts are not reproduced here. Canonical copies:

- Apache License 2.0 — <https://www.apache.org/licenses/LICENSE-2.0>
- BSD 2-Clause — <https://opensource.org/license/bsd-2-clause>
- BSD 3-Clause — <https://opensource.org/license/bsd-3-clause>
- MIT — <https://opensource.org/license/mit>
- LGPL 2.1 — <https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html>
- EPL 2.0 — <https://www.eclipse.org/legal/epl-2.0/>
- zlib/libpng — <https://opensource.org/license/zlib>

For the components bundled in the shaded jar, the authoritative texts are the
ones already inside `target/invoice-ocr.jar` under `META-INF/`.

---

## Maintenance

This list was compiled by reading the coordinates recorded under
`META-INF/maven/` in the built `target/invoice-ocr.jar` for version 1.1.1, plus
the declared dependencies in [pom.xml](pom.xml). Regenerate it after any
dependency change:

```bash
mvn dependency:list -DincludeScope=runtime
mvn license:aggregate-add-third-party   # if the license-maven-plugin is added
```

Two entries above are reported as their redistributor declares them rather than
from a verified upstream licence file — the JNAerator runtime inside lept4j, and
the exact Leptonica version linked into the Tesseract native library. Confirm
both against upstream before distributing the application outside your own
control.
