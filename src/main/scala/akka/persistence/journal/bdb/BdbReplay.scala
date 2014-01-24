package akka.persistence.journal.bdb

import akka.persistence.journal.SyncWriteJournal
import akka.persistence.PersistentRepr
import scala.concurrent.Future
import com.sleepycat.je._
import scala.annotation.tailrec
import java.nio.ByteBuffer


trait BdbReplay {
  this: BdbJournal with SyncWriteJournal =>

  implicit lazy val replayDispatcher = context.system.dispatchers.lookup(config.getString("replay-dispatcher"))

  private[this] def bytesToPersistentRepr(bytes: Array[Byte]): PersistentRepr = {
    serialization.deserialize(bytes, classOf[PersistentRepr]).get
  }

  @tailrec
  private[this] def scanFlags(tx: Transaction, cursor: Cursor, p: PersistentRepr): PersistentRepr = {
    val dbKey = new DatabaseEntry
    val dbVal = new DatabaseEntry

    if (cursor.getNextDup(dbKey, dbVal, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
      val value = dbVal.getData
      value.apply(0) match {
        case DATA_MAGIC =>
          throw new IllegalStateException("Shouldnt be getting a data value.")
        case CONFIRM_MAGIC =>
          val cvalue = new String(value, 1, value.length - 1, "UTF-8")
          scanFlags(tx, cursor, p.update(confirms = p.confirms :+ cvalue))
        case DELETE_MAGIC =>
          scanFlags(tx, cursor, p.update(deleted = true))
      }
    } else p

  }


  @tailrec
  private[this] def replay(tx: Transaction, cursor: Cursor, processorId: Long, from: Long, to: Long)(replayCallback: (PersistentRepr) => Unit): Unit = {
    val dbKey = new DatabaseEntry
    val dbVal = new DatabaseEntry
    cursor.getCurrent(dbKey, dbVal, LockMode.DEFAULT)
    if (keyRangeCheck(dbKey, processorId, from, to)) {
      val value = dbVal.getData
      val data = ByteBuffer.allocate(value.length - 1)
      ByteBuffer.wrap(value, 1, value.length - 1).get(data.array())
      val persist = bytesToPersistentRepr(data.array)

      replayCallback(scanFlags(tx, cursor, persist))

      if (cursor.getNextNoDup(dbKey, dbVal, LockMode.DEFAULT) == OperationStatus.SUCCESS)
        replay(tx, cursor, processorId, from, to)(replayCallback)
    }

  }

  def asyncReplayMessages(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {

    val pid = getProcessorId(processorId)

    Future {
      withTransactionalCursor(db)(
        (cursor, tx) =>
          if (cursor.getSearchKeyRange(getKey(pid, fromSequenceNr), new DatabaseEntry(), LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            replay(tx, cursor, pid, fromSequenceNr, toSequenceNr)(replayCallback)
          }
      )
    }

  }

  def asyncReadHighestSequenceNr(processorId: String, fromSequenceNr: Long): Future[Long] = {
    Future {
      withTransaction {
        tx =>
          val pid = getProcessorId(processorId)
          val dbVal = new DatabaseEntry()
          val result = if (db.get(tx, getMaxSeqnoKey(pid), dbVal, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            ByteBuffer.wrap(dbVal.getData).getLong
          } else 0L
          result
      }
    }
  }


}
