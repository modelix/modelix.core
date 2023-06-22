import type {INodeJS} from "./INodeJS.js";

export class TypedNode implements ITypedNode {
  constructor(public _node: INodeJS) {
  }

  unwrap(): INodeJS {
    return this._node;
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
