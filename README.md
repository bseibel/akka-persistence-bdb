# BDB Journal for Akka Persistence
A journal plugin for akka-persistence using the pure java bdb library.

## Requirements
At least Akka 2.3.0-RC1

## Why BDB over LevelDB?
If your application requires a very high message rate its very possible you will run into the situation where
your LevelDB database can grow unbounded due to old keys not being considered for compaction.

For more details see [this thread](https://groups.google.com/forum/#!msg/leveldb/yL6h1mAOc20/vLU64RylIdMJ).


## Installation
Compile and publish locally `sbt publishLocal`, then include as a dependency:

    libraryDependencies += "com.github.bseibel" %% "akka-persistence-bdb" % "0.1-SNAPSHOT"

## Configuration

Add to your application.conf

    akka.persistence.journal.plugin = "bdb-journal"

### Additional Settings

```
bdb-journal {
    dir = "journal" ; The directory to store the database environment.
    cache-size-percent = 15 ; size of teh heap to use for the bdb cache
}
```


## TODO

- More tunables for the bdb environment
- Optimization pass
- Expose other bdb features (replication, etc)


