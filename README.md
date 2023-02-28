# polygon2ejudge-v2

Command-line tool for importing contests from Polygon to ejudge

## Configuration

Before building you should configure some parameters for the tool. To do it, edit `src/main/resources/app.properties` file.

## Build

Use `mvnw clean compile assembly:single` to build a `.jar` file. After successful build it will be available at `target` directory.

## Usage

Use `java -jar polygon2ejudge-<VERSION>-jar-with-dependencies.jar --help` to show help message.
