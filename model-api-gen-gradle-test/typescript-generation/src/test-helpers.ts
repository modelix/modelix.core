import { org } from "@modelix/model-client";
import { type ITypedNode, toRoleJS } from "@modelix/ts-model-api";
import { LanguageRegistry } from "@modelix/ts-model-api";
import { registerLanguages } from "../build/typescript_src";

const DEFAULT_NODE_DATA = {
  root: {
    children: [
      {
        // concecpt ID of an PropertyAttribute
        concept: "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/3364660638048049750",
        role: "children1",
        properties: {
          name: "aName",
        },
      },
    ],
  },
};

export function useFakeRootNode(nodeData: object = DEFAULT_NODE_DATA) {
  registerLanguages();

  const { loadModelsFromJson } = org.modelix.model.client2;
  const rootNode = loadModelsFromJson(
    [JSON.stringify(nodeData)],
  );

  function getUntypedNode(role: string = "children1") {
    return rootNode.getChildren(toRoleJS(role))[0];
  }

  function getTypedNode<T extends ITypedNode>(role?: string) {
    return LanguageRegistry.INSTANCE.wrapNode(getUntypedNode(role)) as T;
  }

  return {
    rootNode,
    getUntypedNode,
    getTypedNode,
  };
}

export function useFakeNode<T extends ITypedNode>(
  role?: string,
  nodeData?: object
) {
  const { getUntypedNode, getTypedNode, rootNode } = useFakeRootNode(nodeData);
  return {
    rootNode,
    untypedNode: getUntypedNode(role),
    typedNode: getTypedNode<T>(role),
  };
}
