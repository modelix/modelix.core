export type NodeId = string

export interface IModelServerConnection {
  sendUpdate(data: Array<NodeUpdateData>): void

  onModelUpdate(listener: (data: VersionData) => void): void

  onMessage(listener: (message: string) => void): void

  generateIds(quantity: number, callback: (data: IdRangeData) => void): IdGenerator
}

export class ModelService {
  private nodes: Map<NodeId, NodeData> = new Map()
  private versionHash: string | undefined
  private idGenerator: IdGenerator = new IdGenerator(0n, 0n)

  constructor(private server: IModelServerConnection) {
    this.server.onModelUpdate(data => this.versionReceived(data));
    this.server.generateIds(10000, data => {
      this.idGenerator = new IdGenerator(BigInt(data.first), BigInt(data.last))
    })
  }

  public getNodeData(id: NodeId): NodeData | undefined {
    return this.nodes.get(id)
  }

  private versionReceived(data: VersionData) {
    this.versionHash = data.versionHash
    if (data.root !== undefined) this.loadNode(data.root)
    if (data.nodes !== undefined) {
      for (const node of data.nodes) {
        this.loadNode(node)
      }
    }
  }

  private loadNode(nodeData: NodeData): NodeId {
    this.nodes.set(nodeData.nodeId, nodeData)
    for (const childRole of Object.entries(nodeData.children)) {
      const children: Array<NodeId | NodeData> = childRole[1] as Array<NodeId | NodeData>
      for (let i = 0; i < children.length; i++) {
        const child = children[i];
        if (typeof child === "object") {
          children[i] = this.loadNode(child)
        }
      }
    }
    return nodeData.nodeId
  }

  public addNewNode(parent: NodeId, role: string, index: number, concept: string) {
    const body = [<NodeUpdateData>{
      nodeId: this.idGenerator.generate(),
      parent: parent,
      role: role,
      index: index,
      concept: concept,
    }]
    this.server.sendUpdate(body)
  }

  public getChildren(parentId: NodeId, role: string): NodeId[] {
    const parentData = this.nodes.get(parentId)
    if (parentData === undefined) return []
    return parentData.children[role]
  }

  public containsNode(nodeId: NodeId): boolean {
    return this.nodes.has(nodeId)
  }

  public getProperty(nodeId: NodeId, role: string): string | undefined {
    console.log("getProperty(" + nodeId + ", " + role + ")")
    const node = this.nodes.get(nodeId);
    if (node === undefined) return undefined;
    return node.properties[role];
  }

  public setProperty(nodeId: NodeId, role: string, value: string | null | undefined) {
    console.log(`setProperty(${nodeId}, ${role}, ${value})`)
    const node = this.nodes.get(nodeId);
    if (node === undefined) return
    if (node.properties[role] === value) return

    const body = [<NodeUpdateData>{
      nodeId: nodeId,
      properties: {
        [role]: value === undefined ? null : value
      }
    }]

    this.server.sendUpdate(body)
  }
}
/*
export class NodeFromService implements INode {
  constructor(public id: NodeId, public service: ModelService) {
  }

  addNewChild(role: string | null, index: number, concept: IConcept | null): INode {
    return undefined;
  }

  getAllChildren(): Iterable<INode> {
    return undefined;
  }

  getChildren(role: string | null): Iterable<INode> {
    return undefined;
  }

  getConcept(): IConcept | null {
    return undefined;
  }

  getConceptReference(): IConceptReference | null {
    return undefined;
  }

  getParent(): INode | null {
    return undefined;
  }

  getPropertyRoles(): Array<string> {
    return this.service.getNodeData(this.id)?.properties.keys
  }

  getPropertyValue(role: string): string | null {
    let node = this.service.getNodeData(this.id)
    if (node === undefined) return null;
    return node.properties[role];
  }

  getReference(): INodeReference {
    return undefined;
  }

  getReferenceRoles(): Array<string> {
    return undefined;
  }

  getReferenceTargetNode(role: string): INode | null {
    return undefined;
  }

  getReferenceTargetRef(role: string): INodeReference | null {
    return undefined;
  }

  getRoleInParent(): string | null {
    return undefined;
  }

  moveChild(role: string | null, index: number, child: INode): void {
  }

  removeChild(child: INode): void {
  }

  setPropertyValue(role: string, value: string | null): void {
  }

  setReferenceTargetNode(role: string, target: INode | null): void {
  }

  setReferenceTargetRef(role: string, target: INodeReference | null): void {
  }

}
*/
interface VersionData {
  repositoryId: string,
  versionHash: string,
  root: NodeData | undefined,
  nodes: NodeData[] | undefined,
}

interface NodeData {
  nodeId: NodeId,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any,@typescript-eslint/no-unused-vars -- Keep for backward compatibility
  references: any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any,@typescript-eslint/no-unused-vars -- Keep for backward compatibility
  properties: any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any,@typescript-eslint/no-unused-vars -- Keep for backward compatibility
  children: any
}

interface NodeUpdateData {
  nodeId: NodeId,
  parent: NodeId | undefined,
  role: string | undefined,
  index: number | undefined,
  concept: string | undefined,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any,@typescript-eslint/no-unused-vars -- Keep for backward compatibility
  references: any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any,@typescript-eslint/no-unused-vars -- Keep for backward compatibility
  properties: any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any,@typescript-eslint/no-unused-vars -- Keep for backward compatibility
  children: any
}

interface IdRangeData {
  first: NodeId,
  last: NodeId
}

class IdGenerator {

  constructor(private next: bigint, private last: bigint) {
  }

  public generate(): NodeId {
    const id = this.next++;
    if (id > this.last) throw Error("Out of IDs")
    // TODO get new IDs from the server
    return id.toString()
  }
}
