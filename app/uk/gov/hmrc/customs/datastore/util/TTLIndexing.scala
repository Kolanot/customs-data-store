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
import reactivemongo.bson.{BSONDocument, BSONLong}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

trait TTLIndexing[A, ID] {
  self: ReactiveRepository[A, ID] =>

  val expireAfterSeconds: Long

  def indexes: Seq[Index] = Seq.empty[Index]

  protected val FieldLastUpdated = "lastUpdated"
  private val LastUpdatedIndex = "lastUpdatedIndex"
  private val ExpireAfterSeconds = "expireAfterSeconds"

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Logger.info(s"Creating time to live for entries in ${collection.name} to $expireAfterSeconds seconds")

    for {
      _ <- removeLastUpdatedIndexIfExpireAfterSecondsOptionChanged
      results <- addLastUpdatedIndex(indexes)
    } yield(results)
  }

  private def addLastUpdatedIndex(indexes: Seq[Index] = Seq.empty)(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    val lastUpdatedIndex = Index(
      key = Seq(FieldLastUpdated -> IndexType.Ascending),
      name = Some(LastUpdatedIndex),
      options = BSONDocument(ExpireAfterSeconds -> expireAfterSeconds)
    )
    Future.sequence((lastUpdatedIndex +: indexes).map(collection.indexesManager.ensure))
  }

  private def removeLastUpdatedIndexIfExpireAfterSecondsOptionChanged()(implicit ec: ExecutionContext): Future[Unit] = {
    def getExpireAfterSecondsOptionOf(idx: Index): Long = idx.options.getAs[BSONLong](ExpireAfterSeconds).getOrElse(BSONLong(expireAfterSeconds)).as[Long]

    for {
      indexes <- collection.indexesManager.list()
      index = indexes.find(index => index.eventualName == LastUpdatedIndex && getExpireAfterSecondsOptionOf(index) != expireAfterSeconds) if index.isDefined
      - <- collection.indexesManager.drop(index.get.eventualName)
    } yield ()
  }
}
