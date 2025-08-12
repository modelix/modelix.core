import type {ITypedNode} from "./TypedNode.js";
import type {INodeJS, ChildRole} from "./INodeJS.js";
import {LanguageRegistry} from "./LanguageRegistry.js";
import type {IConceptJS} from "./IConceptJS.js";

export abstract class ChildrenAccessor<ChildT extends ITypedNode> implements Iterable<ChildT> {
  constructor(public parentNode: INodeJS, public role: ChildRole | undefined) {
  }

  [Symbol.iterator](): Iterator<ChildT> {
    return this.parentNode.getChildren(this.role).map(n => this.wrapChild(n))[Symbol.iterator]();
  }

  public asArray(): Array<ChildT> {
    return this.parentNode.getChildren(this.role).map(n => this.wrapChild(n))
  }

  protected wrapChild(child: INodeJS): ChildT {
    return LanguageRegistry.INSTANCE.wrapNode(child) as ChildT
  }
}

export class ChildListAccessor<ChildT extends ITypedNode> extends ChildrenAccessor<ChildT> {
  constructor(parentNode: INodeJS, role: ChildRole | undefined) {
    super(parentNode, role);
  }

  public insertNew(index: number, subconcept: IConceptJS | undefined): ChildT {
    return LanguageRegistry.INSTANCE.wrapNode(this.parentNode.addNewChild(this.role, index, subconcept)) as ChildT
  }

  public addNew(subconcept: IConceptJS | undefined): ChildT {
    return this.insertNew(-1, subconcept)
  }
}

export class SingleChildAccessor<ChildT extends ITypedNode> extends ChildrenAccessor<ChildT> {
  constructor(parentNode: INodeJS, role: ChildRole | undefined) {
    super(parentNode, role);
  }

  public get(): ChildT | undefined {
    const children = this.asArray()
    return children.length === 0 ? undefined : children[0]
  }

  public setNew(subconcept?: IConceptJS | undefined): ChildT {
    const existing = this.get();
    if (existing !== undefined) {
      existing.remove();
    }
    return this.wrapChild(this.parentNode.addNewChild(this.role, 0, subconcept))
  }
}
