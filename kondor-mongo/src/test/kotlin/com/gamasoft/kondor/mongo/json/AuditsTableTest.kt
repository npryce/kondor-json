package com.gamasoft.kondor.mongo.json

import com.gamasoft.kondor.mongo.core.MongoConnectionTest.Companion.dbName
import com.gamasoft.kondor.mongo.core.MongoConnectionTest.Companion.mongoConnection
import com.ubertob.kondor.mongo.core.*
import com.ubertob.kondortools.expectSuccess
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.time.Duration
import kotlin.reflect.KClass

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditsTableTest {
    companion object{
        val dbName = "MongoKondorTest"

        @Container
        private val mongoContainer = MongoDBContainer("mongo:4.4.4")
            .apply {
                start()
            }

        val mongoConnection = MongoConnection(
            connString = mongoContainer.getReplicaSetUrl(dbName),
            timeout = Duration.ofMillis(50)
        )

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            mongoContainer.stop()
        }
    }

    private object AuditsTable : TypedTable<AuditMessage>(JAuditMessage) {
        override val collectionName: String = "Audits"
        //retention... policy.. index
    }

    inline fun <reified T: Any> getRandomSealedSubclass(): KClass<out T> {
        val subclasses = T::class.sealedSubclasses
        val randomSubclass = subclasses.random()
        return randomSubclass
    }

    fun randomString(lengthRange: IntRange = (1..10)): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz0123456789"
        val length = (lengthRange.first..lengthRange.last).random()
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    fun <T> randomList(generator: () -> T, sizeRange: IntRange = (1..10)): List<T> {
        val size = sizeRange.random()
        return List(size) { generator() }
    }

    fun randomText() = randomList(::randomString)

    private fun randomAudit(): AuditMessage =
      when(  AuditMessage::class.sealedSubclasses.random() ){
          StringAudit::class -> StringAudit(randomString())
          TextAudit::class -> TextAudit(randomText())
          ErrorCodeAudit::class -> ErrorCodeAudit(randomInt(), randomString())
          MultiAudit::class -> MultiAudit(
              randomList( { StringAudit(randomString()) }, (1..5)))
          else -> error("Unknown class $this")
      }

    private fun randomInt(range: IntRange = 1..1000): Int = range.random()

    private val audit = randomAudit()

    val oneDocReader = mongoOperation {
        AuditsTable.drop()
        AuditsTable.addDocument(audit)
        val docs = AuditsTable.all()
        expectThat(1).isEqualTo(docs.count())
        docs.first()
    }

    val audits = (1..100).map { randomAudit() }
    val docWriteAndReadAll = mongoOperation {
        audits.forEach {
            AuditsTable.addDocument(it)
        }
        AuditsTable.all()
    }

    val cleanUp = mongoOperation {
        AuditsTable.drop()
    }

    val executor = MongoExecutor(mongoConnection, dbName)

    @Test
    fun `add and query doc safely`() {

        val myDoc = executor(oneDocReader).expectSuccess()
        expectThat(audit).isEqualTo(myDoc)

    }


    @Test
    fun `parsing query safely`() {

        val myDocs = executor(cleanUp + docWriteAndReadAll).expectSuccess().toList()

        expectThat(myDocs).hasSize(100)
        expectThat(myDocs).isEqualTo (audits)

//        println(myDocs)
    }
}
