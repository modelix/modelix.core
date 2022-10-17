import type {TypedNode} from "./TypedNode";
import type {INodeJS} from "./INodeJS";
import {LanguageRegistry} from "./LanguageRegistry";

export abstract class ChildrenAccessor<ChildT extends TypedNode> implements Iterable<ChildT> {
  constructor(public parentNode: INodeJS, public role: string | undefined) {
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


export class ChildListAccessor<ChildT extends TypedNode> extends ChildrenAccessor<ChildT> {
  constructor(parentNode: INodeJS, role: string | undefined) {
    super(parentNode, role);
  }
}

export class SingleChildAccessor<ChildT extends TypedNode> extends ChildrenAccessor<ChildT> {
  constructor(parentNode: INodeJS, role: string | undefined) {
    super(parentNode, role);
  }

  public get(): ChildT | undefined {
    let children = this.asArray()
    return children.length === 0 ? undefined : children[0]
  }

  public setNew(): ChildT {
    let existing = this.get();
    if (existing !== undefined) {
      this.parentNode.removeChild(existing.node)
    }
    return this.wrapChild(this.parentNode.addNewChild(this.role, 0, undefined))
  }
}
