package akka.persistence.journal.bdb

import akka.persistence.journal.SyncWriteJournal
import akka.persistence.{PersistentRepr, PersistentConfirmation, PersistentId}
import com.sleepycat.je._
import java.io.File
import scala.collection.immutable.Seq
import akka.serialization.SerializationExtension
import java.nio.ByteBuffer
import scala.annotation.tailrec
import akka.actor.Actor


trait BdbEnvironment extends Actor {

  val config = context.system.settings.config.getConfig("bdb-journal")
  val journalDir = new File(config.getString("dir"))
  journalDir.mkdirs()


  val envConfig = new EnvironmentConfig()
    .setAllowCreate(true)
    .setTransactional(true)
    .setLocking(true)

  val env = new Environment(journalDir, envConfig)

  override def postStop(): Unit = {
    env.close()
    super.postStop()
  }


}


class BdbJournal extends SyncWriteJournal with BdbEnvironment with BdbKeys with BdbReplay {

  private[bdb] final val DATA_MAGIC = 0x0.toByte
  private[bdb] final val CONFIRM_MAGIC = 0x1.toByte
  private[bdb] final val DELETE_MAGIC = 0x2.toByte

  val serialization = SerializationExtension(context.system)

  private[bdb] val dbConfig = new DatabaseConfig()
    .setAllowCreate(true)
    .setTransactional(true)
    .setSortedDuplicates(true)

  private[bdb] val db = env.openDatabase(null, "journal", dbConfig)

  override def postStop(): Unit = {
    db.close()
    super.postStop()
  }

  private[bdb] def bdbSerialize(persistent: PersistentRepr): DatabaseEntry = {
    val payload = serialization.serialize(persistent).get
    val buffer = ByteBuffer.allocate(payload.length + 1)
    buffer.put(DATA_MAGIC)
    buffer.put(payload)

    new DatabaseEntry(buffer.array)
  }

  def writeMessages(messages: Seq[PersistentRepr]): Unit = {
    var max: Map[Long, Long] = Map.empty.withDefaultValue(-1L)

    withTransaction {
      tx =>
        messages.foreach {
          m =>
            val pid = getProcessorId(m.processorId)
            if (db.put(tx, getKey(pid, m.sequenceNr), bdbSerialize(m)) != OperationStatus.SUCCESS)
              throw new IllegalStateException("Failed to write message to database")

            if (max(pid) < m.sequenceNr)
              max += (pid -> m.sequenceNr)
        }

        for ((p, m) <- max) {
          val key = getMaxSeqnoKey(p)
          db.delete(tx, key)
          if (db.put(tx, key, new DatabaseEntry(ByteBuffer.allocate(8).putLong(m).array)) != OperationStatus.SUCCESS)
            throw new IllegalStateException("Failed to write maxSeqno entry to database.")
        }

    }
  }

  def writeConfirmations(confirmations: Seq[PersistentConfirmation]): Unit = {
    withTransaction {
      tx => confirmations.foreach {
        c =>
          val cid = c.channelId.getBytes("UTF-8")
          if (db.put(
            tx,
            getKey(c.processorId, c.sequenceNr),
            new DatabaseEntry(
              ByteBuffer.allocate(cid.length + 1)
                .put(CONFIRM_MAGIC)
                .put(cid)
                .array()
            )
          ) != OperationStatus.SUCCESS)
            throw new IllegalStateException("Failed to write confirmation to database.")
      }
    }
  }

  def deleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Unit = {
    withTransaction(tx => messageIds.foreach(m => deleteKey(tx, getKey(m.processorId, m.sequenceNr), permanent)))
  }

  private[bdb] def keyRangeCheck(e: DatabaseEntry, processorId: Long, minSeqno: Long, maxSeqno: Long): Boolean = {
    val buf = ByteBuffer.wrap(e.getData)
    val pid = buf.getLong
    val sno = buf.getLong
    processorId == pid && sno >= minSeqno && sno <= maxSeqno
  }


  def deleteMessagesTo(processorId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    @tailrec
    def iterateCursor(cursor: Cursor, processorId: Long): Unit = {
      val dbKey = new DatabaseEntry()
      val dbVal = new DatabaseEntry()
      cursor.getCurrent(dbKey, dbVal, LockMode.DEFAULT)
      if (keyRangeCheck(dbKey, processorId, 1L, toSequenceNr)) {
        deleteKey(cursor, dbKey, permanent)
        if (cursor.getNextNoDup(dbKey, dbVal, LockMode.DEFAULT) == OperationStatus.SUCCESS)
          iterateCursor(cursor, processorId)
      }
    }

    withTransactionalCursor(db) {
      (cursor, tx) =>
        if (cursor.getSearchKeyRange(getKey(processorId, 1L), new DatabaseEntry(), LockMode.DEFAULT) == OperationStatus.SUCCESS)
          iterateCursor(cursor, getProcessorId(processorId))
    }

  }


  private[this] def deleteKey(cursor: Cursor, key: DatabaseEntry, permanent: Boolean) = {
    if (permanent) {
      cursor.delete()
    } else {
      cursor.put(key, new DatabaseEntry(Array(DELETE_MAGIC)))
    }
  }

  private[this] def deleteKey(tx: Transaction, key: DatabaseEntry, permanent: Boolean) = {
    if (permanent) {
      db.delete(tx, key)
    } else {
      db.put(tx, key, new DatabaseEntry(Array(DELETE_MAGIC)))
    }
  }

  private[bdb] def withTransaction[T](p: Transaction => T) = {
    val tx = env.beginTransaction(null, null)
    try {
      p(tx)
    } finally {
      cleanupTx(tx)
    }
  }

  private[bdb] def withTransactionalCursor[T](db: Database)(p: (Cursor, Transaction) => T) = {
    val tx = env.beginTransaction(null, null)
    val cursor = db.openCursor(tx, CursorConfig.DEFAULT)
    try {
      p(cursor, tx)
    } finally {
      cursor.close()
      cleanupTx(tx)
    }
  }

  private[bdb] def cleanupTx(tx: Transaction) = {
    if (tx.isValid) tx.commit() else tx.abort()
  }

}
