import type { INodeJS } from "@modelix/ts-model-api";
import { ReactiveModel } from "./internal/ReactiveModel";
import { org } from "@modelix/model-client";

const { loadModelsFromJsonAsBranch } = org.modelix.model.client2;

type ChangeJS = org.modelix.model.client2.ChangeJS;

/**
 * Loads mutiple JSON strings that represent a node into one reactive root node by combining theier children in one root node.
 *
 * The returned root node uses Vues reactivity and can be used in Vue like an reactive object.
 *
 * @param modelDataJsonStrings - Array of string, each representing a root node.
 * @returns A new root node the combines all children from the loaded root nodes.
 */
export function useModelsFromJson(modelDataJsonStrings: string[]): INodeJS {
  const reactiveModel = new ReactiveModel();
  const branch = loadModelsFromJsonAsBranch(modelDataJsonStrings);
  branch.addListener((change: ChangeJS) => {
    reactiveModel.handleChange(change);
  });
  const reactiveRootNode = reactiveModel.wrap(branch.rootNode);
  return reactiveRootNode;
}
