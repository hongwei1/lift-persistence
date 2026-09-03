/*
 * Copyright 2011-2014 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package net.liftweb
package db

import org.specs2.mutable.Specification

import common._
import util.DefaultConnectionIdentifier
import util.ControlHelpers._

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql._

/** Transaction tests use tiny JDBC proxies so this suite works on Scala 2 and Scala 3.
  * The old Specs2 Mockito module is not published for Scala 3.
  */
class DBSpec extends Specification {
  sequential

  private final class Recorder {
    var commits = 0
    var rollbacks = 0
    val callbacks = scala.collection.mutable.ListBuffer.empty[Boolean]

    private def defaultValue(t: Class[_]): AnyRef = {
      if (!t.isPrimitive) null
      else if (t == java.lang.Boolean.TYPE) java.lang.Boolean.FALSE
      else if (t == java.lang.Byte.TYPE) java.lang.Byte.valueOf(0.toByte)
      else if (t == java.lang.Short.TYPE) java.lang.Short.valueOf(0.toShort)
      else if (t == java.lang.Integer.TYPE) java.lang.Integer.valueOf(0)
      else if (t == java.lang.Long.TYPE) java.lang.Long.valueOf(0L)
      else if (t == java.lang.Float.TYPE) java.lang.Float.valueOf(0.0f)
      else if (t == java.lang.Double.TYPE) java.lang.Double.valueOf(0.0d)
      else if (t == java.lang.Character.TYPE) java.lang.Character.valueOf('\u0000')
      else null
    }

    private def proxy[T](interfaceClass: Class[T], f: (Method, scala.Array[Object]) => AnyRef): T =
      Proxy.newProxyInstance(
        interfaceClass.getClassLoader,
        scala.Array(interfaceClass),
        new InvocationHandler {
          def invoke(target: Object, method: Method, args: scala.Array[Object]): AnyRef =
            f(method, if (args == null) scala.Array.empty[Object] else args)
        }
      ).asInstanceOf[T]

    private val resultSet: ResultSet = proxy(classOf[ResultSet], (method, _) => defaultValue(method.getReturnType))

    private val statement: PreparedStatement = proxy(classOf[PreparedStatement], (method, _) => {
      if (method.getName == "executeQuery") resultSet
      else defaultValue(method.getReturnType)
    })

    val connection: Connection = proxy(classOf[Connection], (method, _) => method.getName match {
      case "createStatement" => statement
      case "commit" => commits += 1; null
      case "rollback" => rollbacks += 1; null
      case _ => defaultValue(method.getReturnType)
    })
  }

  private def dBVendor(connection: Connection): ProtoDBVendor = new ProtoDBVendor {
    def createOne: Box[Connection] = Full(connection)
  }

  "eager buildLoanWrapper" should {
    "call postTransaction functions with true if transaction is committed" in {
      val recorder = new Recorder
      DB.defineConnectionManager(DefaultConnectionIdentifier, dBVendor(recorder.connection))
      DB.buildLoanWrapper(true) {
        DB.appendPostTransaction(DefaultConnectionIdentifier, b => recorder.callbacks += b)
        DB.currentConnection.map(c => DB.exec(c, "stuff")(_ => ()))
      }
      recorder.commits must_== 1
      recorder.rollbacks must_== 0
      recorder.callbacks.toList must_== List(true)
    }

    "call postTransaction functions with false if transaction is rolled back" in {
      val recorder = new Recorder
      DB.defineConnectionManager(DefaultConnectionIdentifier, dBVendor(recorder.connection))
      val lw = DB.buildLoanWrapper(true)
      tryo(lw.apply {
        DB.appendPostTransaction(DefaultConnectionIdentifier, b => recorder.callbacks += b)
        DB.currentConnection.map(c => DB.exec(c, "stuff")(_ => ()))
        throw new RuntimeException("oh no")
        42
      })
      recorder.commits must_== 0
      recorder.rollbacks must_== 1
      recorder.callbacks.toList must_== List(false)
    }
  }

  "lazy buildLoanWrapper" should {
    "call postTransaction functions with true if transaction is committed" in {
      val recorder = new Recorder
      DB.defineConnectionManager(DefaultConnectionIdentifier, dBVendor(recorder.connection))
      DB.buildLoanWrapper(false) {
        DB.use(DefaultConnectionIdentifier) { c =>
          DB.appendPostTransaction(DefaultConnectionIdentifier, b => recorder.callbacks += b)
          DB.exec(c, "stuff")(_ => ())
        }
        DB.use(DefaultConnectionIdentifier) { c => DB.exec(c, "more stuff")(_ => ()) }
      }
      recorder.commits must_== 1
      recorder.rollbacks must_== 0
      recorder.callbacks.toList must_== List(true)
    }

    "call postTransaction functions with false if transaction is rolled back" in {
      val recorder = new Recorder
      DB.defineConnectionManager(DefaultConnectionIdentifier, dBVendor(recorder.connection))
      val lw = DB.buildLoanWrapper(false)
      tryo(lw.apply {
        DB.use(DefaultConnectionIdentifier) { c => DB.exec(c, "more stuff")(_ => ()) }
        DB.use(DefaultConnectionIdentifier) { c =>
          DB.appendPostTransaction(DefaultConnectionIdentifier, b => recorder.callbacks += b)
          DB.exec(c, "stuff")(_ => throw new RuntimeException("oh no"))
        }
        42
      })
      recorder.commits must_== 0
      recorder.rollbacks must_== 1
      recorder.callbacks.toList must_== List(false)
    }
  }

  "DB.use" should {
    "call postTransaction functions with true if transaction is committed" in {
      val recorder = new Recorder
      DB.defineConnectionManager(DefaultConnectionIdentifier, dBVendor(recorder.connection))
      DB.use(DefaultConnectionIdentifier) { c =>
        DB.appendPostTransaction(DefaultConnectionIdentifier, b => recorder.callbacks += b)
        DB.exec(c, "stuff")(_ => ())
      }
      recorder.commits must_== 1
      recorder.rollbacks must_== 0
      recorder.callbacks.toList must_== List(true)
    }

    "call postTransaction functions with false if transaction is rolled back" in {
      val recorder = new Recorder
      DB.defineConnectionManager(DefaultConnectionIdentifier, dBVendor(recorder.connection))
      tryo(DB.use(DefaultConnectionIdentifier) { c =>
        DB.appendPostTransaction(DefaultConnectionIdentifier, b => recorder.callbacks += b)
        DB.exec(c, "stuff")(_ => throw new RuntimeException("Oh no"))
        42
      })
      recorder.commits must_== 0
      recorder.rollbacks must_== 1
      recorder.callbacks.toList must_== List(false)
    }
  }

  "appendPostTransaction" should {
    "throw if called outside tx context" in {
      DB.appendPostTransaction(_ => ()) must throwA[IllegalStateException]
    }
  }

  "DB.rollback" should {
    "call postTransaction functions with false" in {
      val recorder = new Recorder
      DB.defineConnectionManager(DefaultConnectionIdentifier, dBVendor(recorder.connection))
      tryo(DB.use(DefaultConnectionIdentifier) { c =>
        DB.appendPostTransaction(DefaultConnectionIdentifier, b => recorder.callbacks += b)
        DB.rollback(DefaultConnectionIdentifier)
        42
      })
      recorder.commits must_== 0
      recorder.rollbacks must_== 1
      recorder.callbacks.toList must_== List(false)
    }
  }
}
