# Third-party notices

Zipit uses and/or redistributes the following third-party components.

## ASM 9.11

- Project: <https://asm.ow2.io/>
- License: BSD 3-Clause
- The ASM jars are vendored under `lib/` and are bundled into the executable
  fat jars.

```text
Copyright (c) 2000-2011 INRIA, France Telecom
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
3. Neither the name of the copyright holders nor the names of its contributors
   may be used to endorse or promote products derived from this software
   without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## FlatLaf 3.5.4

- Project: <https://github.com/JFormDesigner/FlatLaf>
- License: Apache License 2.0
- Bundled into the GUI fat jar. The license text is included in the FlatLaf
  jar under `META-INF/LICENSE`.

## Gradle wrapper

- Project: <https://gradle.org/>
- License: Apache License 2.0
- The checked-in `gradlew`, `gradlew.bat` and `gradle-wrapper.jar` are the
  standard Gradle wrapper files. Gradle 8.14 is downloaded at build time.

## JUnit Jupiter 5.10.1

- Project: <https://junit.org/junit5/>
- License: Eclipse Public License 2.0
- Test dependencies only; not bundled into distributed artifacts.

## Integration test dependencies

`integration/hibernate-legacy` and `integration/hibernate-modern` resolve
Hibernate ORM and H2 from Maven Central at build time. They are not vendored in
this repository; refer to their upstream projects for license terms.
