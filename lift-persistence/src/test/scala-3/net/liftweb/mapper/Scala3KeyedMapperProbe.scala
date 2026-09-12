package net.liftweb.mapper

/**
 * Compile-only probe for the public Mapper shape used by OBP entities.
 *
 * This deliberately mirrors the normal `class X` / `object X` pair. It is kept
 * separate from production APIs so the Scala 3 port can be tested without
 * changing downstream Entity source files.
 */
private[mapper] object Scala3KeyedMapperProbe {
  class Parent extends LongKeyedMapper[Parent] with IdPK {
    override def getSingleton: LongKeyedMetaMapper[Parent] = Parent
  }

  object Parent extends Parent with LongKeyedMetaMapper[Parent]

  class Probe extends LongKeyedMapper[Probe] with IdPK {
    override def getSingleton: LongKeyedMetaMapper[Probe] = Probe

    object parentId extends MappedLongForeignKey[Probe, Parent](this, Parent)
    object name extends MappedString(this, 64)
  }

  object Probe extends Probe with LongKeyedMetaMapper[Probe]

  class Child extends LongKeyedMapper[Child] with IdPK {
    override def getSingleton: LongKeyedMetaMapper[Child] = Child

    object parentId extends MappedLongForeignKey[Child, ParentWithChildren](this, ParentWithChildren)
  }

  object Child extends Child with LongKeyedMetaMapper[Child]

  class ParentWithChildren extends LongKeyedMapper[ParentWithChildren]
    with OneToMany[Long, ParentWithChildren] with IdPK {
    override def getSingleton: LongKeyedMetaMapper[ParentWithChildren] = ParentWithChildren

    object children extends MappedOneToMany[Child](Child, Child.parentId)
  }

  object ParentWithChildren extends ParentWithChildren with LongKeyedMetaMapper[ParentWithChildren]
}
