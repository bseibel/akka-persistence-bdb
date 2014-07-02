package akka.persistence.journal.bdb

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

import akka.actor.Actor
import com.sleepycat.je._

import scala.annotation.tailrec

private[bdb] trait BdbKeys extends Actor {
  this: BdbJournal =>

  var currentId: AtomicLong = new AtomicLong(10L)

  var mapping: Map[String, Long] = Map.empty

  val mappingDbConfig = new DatabaseConfig()
    .setAllowCreate(true)
    .setTransactional(true)

  val mappingDb = env.openDatabase(null, "processorIdMapping", mappingDbConfig)


  def getKey(processorId: String, sequenceNo: Long): DatabaseEntry = {
    val buffer = ByteBuffer.allocate(16)
    buffer.putLong(getPersistenceId(processorId))
    buffer.putLong(sequenceNo)

    new DatabaseEntry(buffer.array)
  }

  def getKey(processorId: Long, seqNo: Long): DatabaseEntry = {
    new DatabaseEntry(
      ByteBuffer.allocate(16)
        .putLong(processorId)
        .putLong(seqNo)
        .array
    )
  }

  def getMaxSeqnoKey(processorId: Long): DatabaseEntry = {
    new DatabaseEntry(
      ByteBuffer.allocate(16)
        .putLong(0L)
        .putLong(processorId)
        .array
    )
  }

  def getPersistenceId(persistenceId: String): Long = {
    mapping.get(persistenceId) match {
      case Some(id) => id

      case None =>
        val nextId = currentId.addAndGet(1L)
        val dbKey = new DatabaseEntry(persistenceId.getBytes("UTF-8"))
        val dbVal = new DatabaseEntry(ByteBuffer.allocate(8).putLong(nextId).array)
        val tx = env.beginTransaction(null, null)
        try {
          if (mappingDb.put(tx, dbKey, dbVal) == OperationStatus.KEYEXIST) {
            throw new IllegalStateException("Attempted to insert already existing persistenceId mapping.")
          }
          mapping = mapping + (persistenceId -> nextId)
          nextId
        } finally {
          cleanupTx(tx)
        }
    }
  }

  def init() = {

    @tailrec
    def cursorIterate(first: Boolean, cursor: Cursor, mapping: Map[String, Long]): Map[String, Long] = {
      val dbKey = new DatabaseEntry()
      val dbVal = new DatabaseEntry()
      first match {
        case true =>
          if (cursor.getFirst(dbKey, dbVal, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            cursorIterate(first = false, cursor, Map(new String(dbKey.getData, "UTF-8") -> ByteBuffer.wrap(dbVal.getData).getLong))
          } else {
            Map.empty
          }
        case false =>
          if (cursor.getNext(dbKey, dbVal, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            cursorIterate(first = false, cursor, mapping + (new String(dbKey.getData, "UTF-8") -> ByteBuffer.wrap(dbVal.getData).getLong))
          } else {
            mapping
          }
      }
    }

    withTransactionalCursor(mappingDb) {
      (cursor, tx) =>
        mapping = cursorIterate(first = true, cursor, Map.empty)
    }

  }


  override def preStart(): Unit = {
    super.preStart()
    init()
  }


  override def postStop(): Unit = {
    mappingDb.close()
    super.postStop()
  }


}
