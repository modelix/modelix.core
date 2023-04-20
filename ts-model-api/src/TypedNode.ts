import type {INodeJS} from "./INodeJS";

export class TypedNode implements ITypedNode {
  constructor(public node: INodeJS) {
  }

  unwrap(): INodeJS {
    return this.node;
  }

}

export interface ITypedNode {
  unwrap(): INodeJS
}

export class UnknownTypedNode extends TypedNode {
  constructor(node: INodeJS) {
    super(node);
  }
}
