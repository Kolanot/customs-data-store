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

import com.typesafe.config.ConfigFactory

object FeatureSwitch {

  private val configuration = ConfigFactory.load()

  def forName(name: String): FeatureName = {
    name match {
      case ActualMdg.name => ActualMdg
      case DataStore.name => DataStore
    }
  }

  sealed trait FeatureName {

    val name: String

    val confPropertyName: String = s"features.$name"

    val systemPropertyName: String = s"features.$name"

    def isEnabled(): Boolean = {
      val sysPropValue = sys.props.get(systemPropertyName)
      sysPropValue match {
        case Some(x) =>
          x.toBoolean
        case None =>
          if (configuration.hasPath(confPropertyName)) ConfigFactory.load().getBoolean(confPropertyName) else false
      }
    }

    def enable() {
      setProp(true)
    }

    def disable() {
      setProp(false)
    }

    def setProp(value: Boolean) {
      sys.props.+=((systemPropertyName, value.toString))
    }
  }

  case object ActualMdg extends {val name = "actual-mdg"} with FeatureName  // This is to switch between HODS stub and real services on QA
  case object DataStore extends {val name = "data-store"} with FeatureName  // This is to make backend call for performance test

}
