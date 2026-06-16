/*
 * Copyright 2011-2018 WorldWide Conferencing, LLC
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

import sbt._
import Keys._

object Dependencies {

  type ModuleMap = String => ModuleID

  lazy val slf4jVersion = "1.7.25"

  // Compile scope:
  // Scope available in all classpath, transitive by default.
  lazy val commons_codec          = "commons-codec"              % "commons-codec"      % "1.11"
  lazy val jbcrypt                = "org.mindrot"                % "jbcrypt"            % "0.4"
  lazy val joda_time              = "joda-time"                  % "joda-time"          % "2.10"
  lazy val joda_convert           = "org.joda"                   % "joda-convert"       % "2.1"
  lazy val scala_reflect: ModuleMap  = "org.scala-lang"          % "scala-reflect"      % _
  lazy val slf4j_api              = "org.slf4j"                  % "slf4j-api"          % slf4jVersion
  lazy val scala_xml              = "org.scala-lang.modules"     %% "scala-xml"         % "1.3.0"
  lazy val xerces                 = "xerces" % "xercesImpl" % "2.12.2"

  // Provided scope:
  // Scope provided by container, available only in compile and test classpath, non-transitive by default.
  lazy val logback         = "ch.qos.logback"    % "logback-classic"       % "1.2.3"        % Provided

  // Test scope:
  // Scope available only in test classpath, non-transitive by default.
  lazy val derby      = "org.apache.derby"         % "derby"                    % "10.7.1.1" % Test
  lazy val h2database = "com.h2database"           % "h2"                       % "1.2.147"  % Test

  lazy val specs2      = "org.specs2"        %% "specs2-core"          % "4.9.4"         % Test
  lazy val scalacheck  = "org.specs2"        %% "specs2-scalacheck"    % specs2.revision % Test
  lazy val specs2Matchers = "org.specs2"     %% "specs2-matcher-extra" % specs2.revision % Test
  lazy val specs2Mock  = "org.specs2"        %% "specs2-mock"          % specs2.revision % Test

  lazy val scalactic       = "org.scalactic"     %% "scalactic"  % "3.1.2"   % Test
  lazy val scalatest       = "org.scalatest"     %% "scalatest"  % "3.1.2"   % Test

  // Aliases
  lazy val h2 = h2database

}
