# BDB Journal for Akka Persistence
A journal plugin for akka-persistence using the pure java bdb library.

## Requirements
At least Akka 2.3.4

## Why BDB over LevelDB?
If your application requires a very high message rate its very possible you will run into the situation where
your LevelDB database can grow unbounded due to old keys not being considered for compaction.

For more details see [this thread](https://groups.google.com/forum/#!msg/leveldb/yL6h1mAOc20/vLU64RylIdMJ).


## Installation
Add the following lines to your build.sbt:

    resolvers += "bseibel at bintray" at "http://dl.bintray.com/bseibel/release"

    libraryDependencies += "com.github.bseibel" %% "akka-persistence-bdb" % "1.0"

This version of the plugin is cross compiled with Scala 2.10.4 and 2.11.1

## Configuration

Add to your application.conf

    akka.persistence.journal.plugin = "bdb-journal"

### Additional Settings

```
bdb-journal {
    dir = "journal" ; The directory to store the database environment
    sync = true ; true = Durability.COMMIT_SYNC, false = Durability.COMMIT_WRITE_NO_SYNC
    cache-size-percent = 15 ; size of the heap to use for the bdb cache
    cleaner-threads = 2 ; Number of threads used for journal compaction
    stats-collect = false ; Log usage stats to journal directory
}
```


## TODO

- Optimization pass
- Expose other bdb features (replication, etc)


