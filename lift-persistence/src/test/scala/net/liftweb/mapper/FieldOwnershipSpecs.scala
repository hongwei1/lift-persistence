/*
 * Copyright 2009-2011 WorldWide Conferencing, LLC
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

package net.liftweb
package mapper

import org.specs2.mutable.Specification

/**
 * Field discovery must only pick up fields the entity actually owns. An accessor
 * that hands back another entity's field instance is not a column of this table:
 * MetaMapper renames whatever it discovers via setName_!, so adopting a foreign
 * field renames a live column on the other entity and binds this entity's SQL
 * through a field whose owner is a different instance.
 */
class FieldOwnershipSpecs extends Specification {
  "Field ownership Specification".title
  sequential

  "Field discovery" should {
    "not adopt an object field belonging to another entity" in {
      Borrower.mappedFields.map(_.name).toList.sorted must_== List("id", "own")
    }

    "leave the lending entity's field name intact" in {
      Borrower.mappedFields.map(_.name) // force discovery on both entities
      Lender.mappedFields.map(_.name).toList.sorted must_== List("id", "lent")
    }

    "still discover fields the entity does own" in {
      Lender.mappedFields.map(_.name).toList must contain("lent")
    }

    // Pins the shapes field discovery has to handle, so the three per-version
    // FieldFinder implementations cannot drift apart silently. User declares its
    // fields in a trait as vals (Scala 3 backs those with `name$lzy<n>` erased to
    // Object); the others use the `object` form.
    "register a bridged field once, not twice" in {
      // Without the bridge filter the synthetic `MappedString thing()` is accepted
      // alongside the real accessor and the column is registered twice.
      Bridged.mappedFields.map(_.name).toList.sorted must_== List("id", "thing")
    }

    "find the same fields on every Scala version" in {
      Dog.mappedFields.map(_.name).toList.sorted must_==
        List("id", "name", "owner", "price", "weight")

      User.mappedFields.map(_.name).toList.sorted must_==
        List("email", "firstName", "id", "lastName", "locale",
             "password", "superUser", "timezone", "uniqueId", "validated")

      SampleModel.mappedFields.map(_.name).toList.sorted must_==
        List("firstName", "id", "moose", "notNull", "status")
    }
  }
}

class Lender extends LongKeyedMapper[Lender] with IdPK {
  def getSingleton = Lender
  object lent extends MappedString(this, 32)
}
object Lender extends Lender with LongKeyedMetaMapper[Lender]

class Borrower extends LongKeyedMapper[Borrower] with IdPK {
  def getSingleton = Borrower
  object own extends MappedString(this, 32)

  // Same accessor name as Lender's field, returning Lender's instance. The
  // return type is Lender$lent$, so a check that only asks "does the return type
  // look like an object field called `lent`" accepts it.
  def lent: Lender.lent.type = Lender.lent
}
object Borrower extends Borrower with LongKeyedMetaMapper[Borrower]

// A trait declaring the field with its general type, implemented by an `object`
// whose accessor returns the narrower synthetic class. Scala 3 emits a bridge
// `MappedString thing()` next to the real `Bridged$thing$ thing()` so the trait's
// signature is satisfied. Both are public and zero-arg, and Method.equals compares
// return types, so .distinct keeps both unless bridges are filtered out.
trait DeclaresThing[T <: Mapper[T]] { self: T =>
  def thing: MappedString[T]
}

class Bridged extends LongKeyedMapper[Bridged] with IdPK with DeclaresThing[Bridged] {
  def getSingleton = Bridged
  object thing extends MappedString(this, 32)
}
object Bridged extends Bridged with LongKeyedMetaMapper[Bridged]
