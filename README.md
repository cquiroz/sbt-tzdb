# sbt-tzdb

Build a custom time zone database for `Scala.js` and `Scala Native` applications.

`sbt-tzdb` is a code generation tool used to build a time zone db compatible with the `Scala.js` and `Scala Native` side of [scala-java-time](https://github.com/cquiroz/scala-java-time).

Its main purpose is to let users build a custom version of tzdb, which has the minimal data your application needs reducing the size of the application.
This is fairly important for `Scala.js` applications.

# What does it do?

The plugin will download a time zone data package from [IANA TimeZone Database](https://www.iana.org/time-zones), then it will parse the database and build suitable code that `scala-java-time` can use to support the timezone base operations.

This should only happen once on your build. If you need to regenerate the db, e.g. if the version has changed or your filter is different, just `clean` your project

# Why?

The full timezone databes is fairly large containing historical records for all time zones. A typical application doesn't need so many timezones, however the full database must be carried due to the properties of the `java.time` API, which search timezones with strings. This doesn't let the `Scala.js` optimizer remove unnecessary zones.

However, it is legal to have a smaller database with only the time zones of your interest. As it is impossible to publish all possible combinations, this plugin lets you generate dynamically the database for your application.

By restricting the available timezones you can reduce your js size up to several megabytes on `fastOptJS`.

## Usage Instructions

add the plugin dependency to `project/plugins.sbt` for sbt 1.1.x

```scala
addSbtPlugin("io.github.cquiroz" % "sbt-tzdb" % "4.2.0") // Or latest version; check release tags
```

The plugin currently supports sbt 1.1.x.

The you need to enable the plugin in your project e.g.:

```scala
  .enablePlugins(TzdbPlugin)
```

Note that the plugin should be only enabled for js projects but it is not enforced. For cross projects you should do:

```scala
lazy val lib = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  ...

lazy val libJVM = lib.jvm
lazy val libJS  = lib.js.enablePlugins(TzdbPlugin)
lazy val libNative  = lib.native.enablePlugins(TzdbPlugin)
```

This only makes sense if you add `scala-java-time` as a dependency

```scala
libraryDependencies ++= Seq(
  "io.github.cquiroz" %%% "scala-java-time" % "2.5.0" // Or latest version; check projects release tags
)
```

If you want TZDB to be in multiple cross projects then include the plugin in a parent one which the other projects depend upon otherwise you may encounter intermittent
[compilation issues](https://github.com/cquiroz/sbt-tzdb/issues/270).

```scala
  // we have created the "modules/tzdb" directory inside the project but this is up to how you want to 
  // organize the modules of your project
  lazy val libTzdb = crossProject(JVMPlatform, JSPlatform)
      .in(file("modules/tzdb"))
      .settings(
        name := "tzdb",
        dbVersion := TzdbPlugin.Version("2024a")
      )
      .jsConfigure(
        _.enablePlugins(TzdbPlugin)
      )

  lazy val libClient = crossProject(JVMPlatform, JSPlatform)
        .dependsOn(libTzdb.js)
```

## Main Tasks

The plugin attaches to the build and adds a custom code generation task which will build a compatible timezone

## Configuration settings

* `dbVersion`: Lets you specify what tzdb version you want to use. It defaults to latest
* `zonesFilter`: A function to filter what timezones are included. By default all zones are included but to reduce the size you should specify only the ones you need, e.g.:

```scala
zonesFilter := {(z: String) => z == "America/Santiago" || z == "Pacific/Honolulu"},
```

## Warning

* This is still an experimental plugin, use with care
