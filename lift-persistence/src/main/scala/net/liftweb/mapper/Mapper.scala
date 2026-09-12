/*
 * Copyright 2006-2011 WorldWide Conferencing, LLC
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

import java.util.Date

import scala.xml.{Elem, NodeSeq}
// OBP fork: webkit removed — net.liftweb.http.{S} and net.liftweb.http.js usages
// (asJs / asSafeJs / suplementalJs / form-submission toForm) were deleted.
import util._
import common.{Box, Empty, Full, ParamFailure}

import collection.mutable.StringBuilder

trait BaseMapper extends FieldContainer {
  type MapperType <: Mapper[MapperType]

  def dbName: String
  def save: Boolean
}

trait Mapper[A<:Mapper[A]] extends BaseMapper with Serializable with SourceInfo {
  self: A =>
  type MapperType = A

  private var was_deleted_? = false
  private var dbConnectionIdentifier: Box[ConnectionIdentifier] = Empty
  private[mapper] var addedPostCommit = false
  @volatile private[mapper] var persisted_? = false

  def getSingleton : MetaMapper[A];
  final def safe_? : Boolean = {
    util.Safe.safe_?(System.identityHashCode(this))
  }

  def dbName:String = getSingleton.dbName

  implicit def thisToMappee(in: Mapper[A]): A = this.asInstanceOf[A]

  def runSafe[T](f : => T) : T = {
    util.Safe.runSafe(System.identityHashCode(this))(f)
  }

  def connectionIdentifier(id: ConnectionIdentifier): A = {
    if (id != getSingleton.dbDefaultConnectionIdentifier || dbConnectionIdentifier.isDefined) dbConnectionIdentifier = Full(id)
    thisToMappee(this)
  }

  def connectionIdentifier = dbConnectionIdentifier openOr calcDbId

  def dbCalculateConnectionIdentifier: PartialFunction[A, ConnectionIdentifier] = Map.empty

  private def calcDbId = if (dbCalculateConnectionIdentifier.isDefinedAt(this)) dbCalculateConnectionIdentifier(this)
  else getSingleton.dbDefaultConnectionIdentifier

  /**
   * Append a function to perform after the commit happens
   * @param func - the function to perform after the commit happens
   */
  def doPostCommit(func: () => Unit): A = {
    DB.appendPostTransaction(connectionIdentifier, dontUse => func())
    this
  }

  /**
   * Save the instance and return the instance
   */
  def saveMe(): A = {
    this.save
    this
  }

  def save: Boolean = {
    runSafe {
      getSingleton.save(this)
    }
  }

  def htmlLine : NodeSeq = {
    getSingleton.doHtmlLine(this)
  }

  def asHtml : NodeSeq = {
    getSingleton.asHtml(this)
  }

  def validate : List[FieldError] = {
    runSafe {
      getSingleton.validate(this)
    }
  }

  /**
   * Returns the instance in a Full Box if the instance is valid, otherwise
   * returns a Failure with the validation errors
   */
  def asValid: Box[A] = validate match {
    case Nil => Full(this)
    case xs => ParamFailure(xs.map(_.msg.text).mkString(", "), Empty, Empty, xs)
  }

  /**
   * Given a name, look up the field
   * @param name the name of the field
   * @return the metadata
   */
  def findSourceField(name: String): Box[SourceFieldInfo] =
  for {
    mf <- getSingleton.fieldNamesAsMap.get(name.toLowerCase)
    f <- fieldByName[mf.ST](name)
  } yield SourceFieldInfoRep[mf.ST](f.get.asInstanceOf[mf.ST], mf).asInstanceOf[SourceFieldInfo]

  /**
   * Get a list of all the fields
   * @return a list of all the fields
   */
  def allFieldNames(): Seq[(String, SourceFieldMetadata)] = getSingleton.doAllFieldNames

  /**
   * Delete the model from the RDBMS
   */
  def delete_! : Boolean = {
    if (!db_can_delete_?) false else
    runSafe {
      was_deleted_? = getSingleton.delete_!(this)
      was_deleted_?
    }
  }

  /**
   * Get the fields (in order) for displaying a form
   */
  def formFields: List[MappedField[_, A]] =
  getSingleton.formFields(this)

  def allFields: Seq[BaseField] = formFields

  /**
   * map the fields titles and forms to generate a list
   * @param func called with displayHtml, fieldId, form
   */
  def mapFieldTitleForm[T](func: (NodeSeq, Box[NodeSeq], NodeSeq) => T): List[T] =
  getSingleton.mapFieldTitleForm(this, func)


  /**
   * flat map the fields titles and forms to generate a list
   * @param func called with displayHtml, fieldId, form
   */
  def flatMapFieldTitleForm[T]
  (func: (NodeSeq, Box[NodeSeq], NodeSeq) => Seq[T]): List[T] =
  getSingleton.flatMapFieldTitleForm(this, func)

    /**
   * flat map the fields titles and forms to generate a list
   * @param func called with displayHtml, fieldId, form
   */
  def flatMapFieldTitleForm2[T]
  (func: (NodeSeq, MappedField[_, A], NodeSeq) => Seq[T]): List[T] =
  getSingleton.flatMapFieldTitleForm2(this, func)

  /**
   * Present the model as a HTML using the same formatting as toForm
   *
   * @return the html view of the model
   */
  def toHtml: NodeSeq = getSingleton.toHtml(this)

  // OBP fork: the form-submission `toForm` overloads were removed during the
  // webkit decoupling — they wired S.fmapFunc / S.redirectTo / S.error /
  // S.currentSnippet / S.mapSnippet into Lift's stateful request scope. The
  // mapper layer no longer renders or processes forms.

  def saved_? : Boolean = getSingleton.saved_?(this)

  /**
   * Can this model object be deleted?
   */
  def db_can_delete_? : Boolean =  getSingleton.saved_?(this) && !was_deleted_?

  def dirty_? : Boolean = getSingleton.dirty_?(this)

  override def toString: String =
    new StringBuilder(this.getClass.getName)
      .append("={")
      .append(getSingleton.appendFieldToStrings(this))
      .append("}")
      .toString

  def toXml: Elem = {
    getSingleton.toXml(this)
  }

  def checkNames(): Unit = {
    runSafe {
      getSingleton match {
        case null =>
        case s => s.checkFieldNames(this)
      }
    }
  }

  def comparePrimaryKeys(other: A) = false

  /**
   * Find the field by name
   * @param fieldName -- the name of the field to find
   *
   * @return Box[MappedField]
   */
  def fieldByName[T](fieldName: String): Box[MappedField[T, A]] = getSingleton.fieldByName[T](fieldName, this)

  type FieldPF = PartialFunction[String, NodeSeq => NodeSeq]


  /**
   * If there's a field in this record that defines the locale, return it
   */
  def localeField: Box[MappedLocale[A]] = Empty

  def timeZoneField: Box[MappedTimeZone[A]] = Empty

  def countryField: Box[MappedCountry[A]] = Empty
}

trait LongKeyedMapper[OwnerType <: LongKeyedMapper[OwnerType]] extends KeyedMapper[Long, OwnerType] with BaseLongKeyedMapper {
  self: OwnerType =>
}

trait BaseKeyedMapper { self: BaseMapper =>
  type TheKeyType
  type KeyedMapperType <: MapperType

  def primaryKeyField: MappedField[TheKeyType, MapperType] with IndexedField[TheKeyType]
  /**
   * Save the model to the RDBMS.
   *
   * Declared here, not just inherited from the self type, because ManyToMany
   * and any other stackable trait extending BaseKeyedMapper reaches the rest of
   * the chain through `super.save`, and that only resolves against a member of
   * this trait. Restoring `extends BaseMapper` would supply it as well, but
   * Scala 3 rejects that with a cyclic reference at LongKeyedMapper (as it does
   * the original `KeyedMapperType <: KeyedMapper[TheKeyType, KeyedMapperType]`
   * bound); both were verified separately against 3.3.8.
   */
  def save: Boolean
  /**
   * Delete the model from the RDBMS
   */
  def delete_! : Boolean
}

trait BaseLongKeyedMapper extends BaseKeyedMapper { self: BaseMapper =>
  override type TheKeyType = Long
}

trait IdPK /* extends BaseLongKeyedMapper */ {
  self: BaseLongKeyedMapper with BaseMapper =>
  def primaryKeyField: MappedLongIndex[MapperType] = id
  object id extends MappedLongIndex[MapperType](this.asInstanceOf[MapperType])
}

/**
 * A trait you can mix into a Mapper class that gives you
 * a createdat column
 */
trait CreatedTrait {
  self: BaseMapper =>

  import net.liftweb.util._

  /**
   * Override this method to index the createdAt field
   */
  protected def createdAtIndexed_? = false

  /**
   * The createdAt field.  You can change the behavior of this
   * field:
   * <pre name="code" class="scala">
   * override lazy val createdAt = new MyCreatedAt(this) {
   *   override def dbColumnName = "i_eat_time"
   * }
   * </pre>
   */
  lazy val createdAt: MappedDateTime[MapperType] = new MyCreatedAt(this)

  protected class MyCreatedAt(obj: self.type) extends MappedDateTime[MapperType](obj.asInstanceOf[MapperType]) {
    override def defaultValue = Helpers.now
    override def dbIndexed_? = createdAtIndexed_?
  }

}

/**
 * A trait you can mix into a Mapper class that gives you
 * an updatedat column
 */
trait UpdatedTrait {
  self: BaseMapper =>

  import net.liftweb.util._

  /**
   * Override this method to index the updatedAt field
   */
  protected def updatedAtIndexed_? = false

  /**
   * The updatedAt field.  You can change the behavior of this
   * field:
   * <pre name="code" class="scala">
   * override lazy val updatedAt = new MyUpdatedAt(this) {
   *   override def dbColumnName = "i_eat_time_for_breakfast"
   * }
   * </pre>
   */
  lazy val updatedAt: MyUpdatedAt = new MyUpdatedAt(this)

  protected class MyUpdatedAt(obj: self.type) extends MappedDateTime(obj.asInstanceOf[MapperType]) with LifecycleCallbacks {
    override def beforeSave: Unit = {super.beforeSave; this.set(Helpers.now)}
    override def defaultValue: Date = Helpers.now
    override def dbIndexed_? : Boolean = updatedAtIndexed_?
  }

}

/**
* Mix this trait into your Mapper instance to get createdAt and updatedAt fields.
*/
trait CreatedUpdated extends CreatedTrait with UpdatedTrait {
  self: BaseMapper =>

}

trait KeyedMapper[KeyType, OwnerType<:KeyedMapper[KeyType, OwnerType]] extends Mapper[OwnerType] with BaseKeyedMapper {
  self: OwnerType =>

  type TheKeyType = KeyType
  type KeyedMapperType = OwnerType

  def primaryKeyField: MappedField[KeyType, OwnerType] with IndexedField[KeyType]
  def getSingleton: KeyedMetaMapper[KeyType, OwnerType];

  override def comparePrimaryKeys(other: OwnerType): Boolean = primaryKeyField.get == other.primaryKeyField.get

  def reload: OwnerType = getSingleton.find(By(primaryKeyField, primaryKeyField.get)) openOr this

  override def hashCode(): Int = primaryKeyField.get.hashCode

  override def equals(other: Any): Boolean = {
    other match {
      case null => false
      case km: KeyedMapper[_, _] if this.getClass.isAssignableFrom(km.getClass) ||
                                    km.getClass.isAssignableFrom(this.getClass) =>
        this.primaryKeyField == km.primaryKeyField
      case k => super.equals(k)
    }
  }
}

/**
* If this trait is mixed into a validation function, the validation for a field
* will stop if this validation function returns an error
*/
trait StopValidationOnError[T] extends Function1[T, List[FieldError]]

object StopValidationOnError {
  def apply[T](f: T => List[FieldError]): StopValidationOnError[T] =
  new StopValidationOnError[T] {
    def apply(in: T): List[FieldError] = f(in)
  }

  def apply[T](f: PartialFunction[T, List[FieldError]]): PartialFunction[T, List[FieldError]] with StopValidationOnError[T] =
  new PartialFunction[T, List[FieldError]] with StopValidationOnError[T] {
    def apply(in: T): List[FieldError] = f(in)
    def isDefinedAt(in: T): Boolean = f.isDefinedAt(in)
  }
}
