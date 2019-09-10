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

import play.api.Logger
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONInteger, BSONLong, BSONString}
import reactivemongo.core.commands.{BSONCommandResultMaker, Command, CommandError}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

trait TTLIndexing[A, ID] {
  self: ReactiveRepository[A, ID] =>

  val expireAfterSeconds: Long
  def additionalIndexes: Seq[Index] = Seq.empty[Index]

  protected val LastUpdated = "lastUpdated"
  private val LastUpdatedIndex = "lastUpdatedIndex"
  private val ExpireAfterSeconds = "expireAfterSeconds"

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    import reactivemongo.bson.DefaultBSONHandlers._
    def findExpireSeconds(idx:Index):Long = idx.options.getAs[BSONLong](ExpireAfterSeconds).getOrElse(BSONLong(expireAfterSeconds)).as[Long]
    collection.indexesManager.list()
      .flatMap {
          _.find(index => index.eventualName == LastUpdatedIndex && findExpireSeconds(index) != expireAfterSeconds)
          .fold(ensureCustomIndexes(additionalIndexes)) { index =>
          collection.indexesManager.drop(index.eventualName).flatMap(_ => ensureCustomIndexes(additionalIndexes))
        }
      }
    Logger.info(s"Creating time to live for entries in ${collection.name} to $expireAfterSeconds seconds")  //TODO refactor
    ensureCustomIndexes(additionalIndexes)  //TODO Why run this 3 times?
  }

  private def ensureCustomIndexes(otherIndexes:Seq[Index] = Seq.empty)(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    val lastUpdatedIndex =       Index(
      key = Seq(LastUpdated -> IndexType.Ascending),
      name = Some(LastUpdatedIndex),
      options = BSONDocument(ExpireAfterSeconds -> expireAfterSeconds)
    )
    Future.sequence((lastUpdatedIndex +: otherIndexes).map(collection.indexesManager.ensure))
  }
}
