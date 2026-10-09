import type { INodeJS } from "@modelix/ts-model-api";
import type { ReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { toReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { Cache } from "./internal/Cache";
import { addChangeHandler, handleChange } from "./internal/handleChange";
import type { ReactiveModelOptions } from "./ReactiveModelOptions";
import { org } from "@modelix/model-client";

const { loadModelsFromJsonAsBranch } = org.modelix.model.client2;

type ChangeJS = org.modelix.model.client2.ChangeJS;

/**
 * Loads mutiple JSON strings that represent a node into one reactive root node by combining theier children in one root node.
 *
 * The returned root node uses Vues reactivity and can be used in Vue like an reactive object.
 *
 * @param modelDataJsonStrings - Array of string, each representing a root node.
 * @param options - Set `updateReferencesToRemovedNodes` to make `getReferenceTargetNode` read a reference
 *   to a removed node as unset, so that a view showing it updates.
 *   `getReferenceTargetRef` still returns the reference. It costs work for every added or removed node.
 * @returns A new root node the combines all children from the loaded root nodes.
 */
export function useModelsFromJson(
  modelDataJsonStrings: string[],
  options?: ReactiveModelOptions,
): INodeJS {
  const cache = new Cache<ReactiveINodeJS>(
    options?.updateReferencesToRemovedNodes,
  );
  const branch = loadModelsFromJsonAsBranch(modelDataJsonStrings);
  addChangeHandler(branch, cache, (change: ChangeJS) => {
    handleChange(change, cache);
  });
  const reactiveRootNode = toReactiveINodeJS(branch.rootNode, cache);
  return reactiveRootNode;
}
