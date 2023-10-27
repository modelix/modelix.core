import { toReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { Cache } from "./internal/Cache";
import { handleChange } from "./internal/handleChange";
import { INodeJS, org } from "@modelix/model-client";

const { loadModelsFromJson } = org.modelix.model.client2;

type ChangeJS = org.modelix.model.client2.ChangeJS;

/**
 * Loads mutiple JSON strings that represent a node into one reactive root node by combining theier children in one root node.
 *
 * The returned root node uses Vues reactivity and can be used in Vue like an reactive object.
 *
 * @experimental This feature is expected to be finalized with https://issues.modelix.org/issue/MODELIX-500.
 *
 * @param modelDataJsonStrings - Array of string, each representing a root node.
 * @returns A new root node the combines all children from the loaded root nodes.
 */
export function useModelsFromJson(modelDataJsonStrings: string[]): INodeJS {
  const cache = new Cache<INodeJS>();
  const unreactiveRootNode = loadModelsFromJson(
    modelDataJsonStrings,
    (change: ChangeJS) => {
      handleChange(change, cache);
    },
  );
  const reactiveRootNode = toReactiveINodeJS(unreactiveRootNode, cache);
  return reactiveRootNode;
}
