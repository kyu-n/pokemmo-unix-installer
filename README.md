# PokeMMO Unix Installer
[![Get it from the Snap Store](https://snapcraft.io/static/images/badges/en/snap-store-black.svg)](https://snapcraft.io/pokemmo)

This program manages:
- Downloads of the PokeMMO game client
- Signature verification for the downloaded files
- Cache management / storage of the program
- Execution of the program

This program is created as a pairing to the PokeMMO Game Client. PokeMMO's Game Client software is provided with the PokeMMO License. To view this license, visit https://pokemmo.eu/tos/ 

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

----

# Build

This program is built with [[Gradle]](https://gradle.org/). We use a local copy of gradle-*-bin.zip due to Launchpad constraints. Download the correct binary version from https://gradle.org/releases/ and place it in the `gradle/wrapper` folder.

Alternatively, automatic downloads can be enabled by setting `distributionUrl=https\://services.gradle.org/distributions/gradle-7.3.1-bin.zip` in your `gradle-wrapper.properties` file.

To build:
- `./gradlew distJar`

To run:
- `java -jar unix-installer.jar`
