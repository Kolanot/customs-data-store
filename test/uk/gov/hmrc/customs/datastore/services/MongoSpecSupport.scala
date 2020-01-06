/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.customs.datastore.services

import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.LastError
import reactivemongo.api.{DefaultDB, FailoverStrategy}
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.mongo.MongoConnector

trait MongoSpecSupport {

  protected def mongoUri: String = "mongodb://127.0.0.1:27017/test-customs-data-store"

  implicit val mongoConnectorForTest: MongoConnector = MongoConnector(mongoUri)

  implicit val mongo: () => DefaultDB = mongoConnectorForTest.db

  def bsonCollection(name: String)(
    failoverStrategy: FailoverStrategy = mongoConnectorForTest.helper.db.failoverStrategy): BSONCollection =
    mongoConnectorForTest.helper.db(name, failoverStrategy)

  def lastError(successful: Boolean, updated: Boolean = false, originalDoc: Option[BSONDocument] = None) =
    LastError(
      ok                = successful,
      errmsg            = None,
      code              = None,
      lastOp            = None,
      n                 = if (updated) 1 else 0,
      singleShard       = None,
      updatedExisting   = updated,
      upserted          = None,
      wnote             = None,
      wtimeout          = false,
      waited            = None,
      wtime             = None,
      writeErrors       = Nil,
      writeConcernError = None
    )

}
