package akka.persistence.journal.bdb

import java.nio.ByteBuffer

import akka.persistence.PersistentRepr
import akka.persistence.journal.SyncWriteJournal
import com.sleepycat.je._

import scala.annotation.tailrec
import scala.concurrent.Future


trait BdbReplay {
  this: BdbJournal with SyncWriteJournal =>

  private[this] lazy val replayDispatcher = context.system.dispatchers.lookup(config.getString("replay-dispatcher"))

  private[this] def bytesToPersistentRepr(bytes: Array[Byte]): PersistentRepr = {
    serialization.deserialize(bytes, classOf[PersistentRepr]).get
  }

  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {

    val pid = getPersistenceId(persistenceId)

    @tailrec
    def replay(tx: Transaction, cursor: Cursor, persistenceId: Long, count: Long)(replayCallback: (PersistentRepr) => Unit): Unit = {

      @tailrec
      def scanFlags(p: PersistentRepr): PersistentRepr = {
        val dbKey = new DatabaseEntry
        val dbVal = new DatabaseEntry

        if (cursor.getNextDup(dbKey, dbVal, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
          val value = dbVal.getData
          value.apply(0) match {
            case DATA_MAGIC =>
              throw new IllegalStateException("Possible corrupt db, data value after first dup key.")
            case CONFIRM_MAGIC =>
              val cvalue = new String(value, 1, value.length - 1, "UTF-8")
              scanFlags(p.update(confirms = p.confirms :+ cvalue))
            case DELETE_MAGIC =>
              scanFlags(p.update(deleted = true))
          }
        } else p
      }

      val dbKey = new DatabaseEntry
      val dbVal = new DatabaseEntry
      cursor.getCurrent(dbKey, dbVal, LockMode.DEFAULT)
      if (keyRangeCheck(dbKey, persistenceId, fromSequenceNr, toSequenceNr) && count < max) {
        val value = dbVal.getData
        if (value(0) == DATA_MAGIC) {
          val data = ByteBuffer.allocate(value.length - 1)
          ByteBuffer.wrap(value, 1, value.length - 1).get(data.array())
          val persist = bytesToPersistentRepr(data.array)

          replayCallback(scanFlags(persist))
        }
        if (cursor.getNextNoDup(dbKey, dbVal, LockMode.DEFAULT) == OperationStatus.SUCCESS)
          replay(tx, cursor, persistenceId, count + 1)(replayCallback)
      }

    }

    Future(
      withTransactionalCursor(db)(
        (cursor, tx) =>
          if (cursor.getSearchKeyRange(getKey(pid, fromSequenceNr), new DatabaseEntry(), LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            replay(tx, cursor, pid, 0L)(replayCallback)
          }
      )
    )(replayDispatcher)

  }

  def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    Future(
      withTransaction {
        tx =>
          val pid = getPersistenceId(persistenceId)
          val dbVal = new DatabaseEntry()
          val result = if (db.get(tx, getMaxSeqnoKey(pid), dbVal, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            ByteBuffer.wrap(dbVal.getData).getLong
          } else 0L
          result
      }
    )(replayDispatcher)
  }


}
