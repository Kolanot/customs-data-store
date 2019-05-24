/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.customs.datastore.util

import java.util.concurrent.TimeUnit

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api._
import play.api.inject.ApplicationLifecycle
import reactivemongo.api.{DB, DefaultDB, FailoverStrategy}
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@ImplementedBy(classOf[ReactiveMongoComponentImpl])
trait ReactiveMongoComponent {
  def getDb(dbName: String): () => DB
}

/**
  * This is copy/paste from [[play.modules.reactivemongo.ReactiveMongoComponentImpl]]
  * It's because original ReactiveMongoComponentImpl is not extensible.
  */

class ReactiveMongoComponentImpl @Inject()(implicit config: Configuration, env: Environment, lifecycle: ApplicationLifecycle)
  extends ReactiveMongoComponent {

  var cache = Map.empty[String, () => DB]

  def getDb(dbName: String): () => DB = {
    def createDB: () => DefaultDB =  {
      mongoConnector(dbName).map { connector =>
        lifecycle.addStopHook { () =>
          Future.successful(connector.close())
        }
        connector.db
      }.getOrElse(throw new Exception(s"ReactiveMongoPlugin error: no MongoConnector available for db: $dbName?"))
    }

    if(!cache.contains(dbName)){
      cache = cache + (dbName -> createDB)
    }
    cache(dbName)
  }

  def mongoConnector(dbName: String)(implicit config: Configuration, env: Environment): Option[MongoConnector]  = {
    Logger.info(s"ReactiveMongoComponent created for $dbName")

    val mongoConfig = config.getConfig(s"${env.mode}.mongodb.$dbName")
      .getOrElse(config.getConfig(s"${Mode.Dev}.mongodb.$dbName")
        .getOrElse(config.getConfig(s"mongodb.$dbName")
          .getOrElse(throw new Exception("The application does not contain required mongodb config"))))

    mongoConfig.getString("uri") match {
      case Some(uri) =>

        mongoConfig.getInt("channels").foreach { _ =>
          Logger.warn("the mongodb.channels config key has been removed and is now ignored. Please use the mongodb URL option described here: https://docs.mongodb.org/manual/reference/connection-string/#connections-connection-options. https://github.com/ReactiveMongo/ReactiveMongo/blob/0.11.3/driver/src/main/scala/api/api.scala#L577")
        }

        val failoverStrategy: Option[FailoverStrategy] = mongoConfig.getConfig("failoverStrategy") match {
          case Some(fs: Configuration) => {

            val initialDelay: FiniteDuration = fs.getLong("initialDelayMsecs").map(delay => new FiniteDuration(delay, TimeUnit.MILLISECONDS)).getOrElse(FailoverStrategy().initialDelay)
            val retries: Int = fs.getInt("retries").getOrElse(FailoverStrategy().retries)

            Some(FailoverStrategy().copy(initialDelay = initialDelay, retries = retries, delayFactor = DelayFactor(fs.getConfig("delay"))))
          }
          case _ => None
        }

        Some(new MongoConnector(uri, failoverStrategy))

      case _ => None
    }
  }

}

private [util] object DelayFactor {

  import scala.math.pow

  def apply(delay : Option[Configuration]) : (Int) => Double = {
    delay match {
      case Some(df: Configuration) => {

        val delayFactor = df.getDouble("factor").getOrElse(1.0)

        df.getString("function") match {
          case Some("linear") => linear(delayFactor)
          case Some("exponential") => exponential(delayFactor)
          case Some("static") => static(delayFactor)
          case Some("fibonacci") => fibonacci(delayFactor)
          case unsupported => throw new PlayException("ReactiveMongoPlugin Error", s"Invalid Mongo configuration for delay function: unknown '$unsupported' function")
        }
      }
      case _ => FailoverStrategy().delayFactor
    }
  }

  private def linear(f: Double): Int => Double = n => n * f

  private def exponential(f: Double): Int => Double = n => pow(n, f)

  private def static(f: Double): Int => Double = n => f

  private def fibonacci(f: Double): Int => Double = n => f * (fib take n).last

  def fib: Stream[Long] = {
    def tail(h: Long, n: Long): Stream[Long] = h #:: tail(n, h + n)
    tail(0, 1)
  }
}
