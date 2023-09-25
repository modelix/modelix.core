import type { ITypedNode, TypedNode } from "./TypedNode.js";
import type { INodeJS } from "./INodeJS.js";
import { LanguageRegistry } from "./LanguageRegistry.js";
import type { IConceptJS } from "./IConceptJS.js";

export abstract class ChildrenAccessor<ChildT extends ITypedNode>
  implements Iterable<ChildT>
{
  constructor(
    public parentNode: INodeJS,
    public role: string | undefined,
  ) {}

  [Symbol.iterator](): Iterator<ChildT> {
    return this.parentNode
      .getChildren(this.role)
      .map((n) => this.wrapChild(n))
      [Symbol.iterator]();
  }

  public asArray(): Array<ChildT> {
    return this.parentNode.getChildren(this.role).map((n) => this.wrapChild(n));
  }

  protected wrapChild(child: INodeJS): ChildT {
    return LanguageRegistry.INSTANCE.wrapNode(child) as ChildT;
  }
}

export class ChildListAccessor<
  ChildT extends ITypedNode,
> extends ChildrenAccessor<ChildT> {
  constructor(parentNode: INodeJS, role: string | undefined) {
    super(parentNode, role);
  }

  public insertNew(index: number, subconcept: IConceptJS | undefined): ChildT {
    return LanguageRegistry.INSTANCE.wrapNode(
      this.parentNode.addNewChild(this.role, index, subconcept),
    ) as ChildT;
  }

  public addNew(subconcept: IConceptJS | undefined): ChildT {
    return this.insertNew(-1, subconcept);
  }
}

export class SingleChildAccessor<
  ChildT extends ITypedNode,
> extends ChildrenAccessor<ChildT> {
  constructor(parentNode: INodeJS, role: string | undefined) {
    super(parentNode, role);
  }

  public get(): ChildT | undefined {
    let children = this.asArray();
    return children.length === 0 ? undefined : children[0];
  }

  public setNew(): ChildT {
    let existing = this.get();
    if (existing !== undefined) {
      this.parentNode.removeChild(existing.unwrap());
    }
    return this.wrapChild(this.parentNode.addNewChild(this.role, 0, undefined));
  }
}
