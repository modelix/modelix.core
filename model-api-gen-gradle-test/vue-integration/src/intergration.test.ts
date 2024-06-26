import { useModelsFromJson } from "@modelix/vue-model-api";
import { computed } from "vue";
import { registerLanguages } from "typescript-generation";
import {
  BaseConcept,
  C_Attribute,
  C_INamedConcept,
  isOfConcept_INamedConcept,
} from "typescript-generation/dist/L_jetbrains_mps_lang_core";
import {
  StaticFieldReference,
  Classifier,
} from "typescript-generation/dist/L_jetbrains_mps_baseLanguage";

import { INodeJS, ITypedNode, LanguageRegistry } from "@modelix/ts-model-api";

registerLanguages();

function useRootNode(nodeData: object) {
  return useModelsFromJson([JSON.stringify(nodeData)]);
}

test("change to property is reactivly updated", () => {
  const nodeData = {
    root: {
      children: [
        {
          // concecpt ID of an INamedConcept
          concept: "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468",
          role: "children1",
          properties: {
            name: "aName",
          },
        },
      ],
    },
  };

  const untypedNode = useRootNode(nodeData).getChildren("children1")[0];
  const typedNode = LanguageRegistry.INSTANCE.wrapNode(untypedNode);
  if (!isOfConcept_INamedConcept(typedNode)) {
    fail(`${typedNode} should be a ${C_INamedConcept}`);
  }

  // We use `computed` to test the reactivity with Vue.
  // Accessing the property directly would circumvent Vue
  // and make this test useless.
  const computedProperty = computed(() => typedNode.name);
  expect(computedProperty.value).toBe("aName");

  typedNode.name = "aName2";
  expect(computedProperty.value).toBe("aName2");
});

test("change to children is reactivly updated", () => {
  const nodeData = {
    root: {
      children: [
        {
          // concecpt ID of an BaseConcept
          concept: "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626",
          role: "children1",
          properties: {
            shortDescription: "child0",
          },
          children: [
            {
              // concept ID of an Attribute
              concept:
                "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/5169995583184591161",
              role: "smodelAttribute",
              properties: {
                shortDescription: "attribute1",
              },
            },
          ],
        },
      ],
    },
  };
  const untypedNode = useRootNode(nodeData).getChildren("children1")[0];
  const baseConcept = LanguageRegistry.INSTANCE.wrapNode(
    untypedNode,
  ) as BaseConcept;

  const computedNumberOfAttributes = computed(
    () => baseConcept.smodelAttribute.asArray().length,
  );
  expect(computedNumberOfAttributes.value).toBe(1);

  baseConcept.smodelAttribute.addNew(C_Attribute);
  expect(computedNumberOfAttributes.value).toBe(2);

  const firstAttribute = baseConcept.smodelAttribute.asArray()[0];
  baseConcept.unwrap().removeChild(firstAttribute.unwrap());
  expect(computedNumberOfAttributes.value).toBe(1);
});

test("change to reference is reactivly updated", () => {
  const nodeData = {
    root: {
      children: [
        {
          // concecpt ID of an StaticFieldReference
          concept: "mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1070533707846",
          role: "staticFieldReferences",
          references: {},
        },
        {
          // concept ID of an Classifier
          concept: "mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1107461130800",
          role: "classifiers",
          properties: {
            name: "aName",
          },
        },
      ],
    },
  };
  const rootNode = useRootNode(nodeData);
  const staticFieldReference = LanguageRegistry.INSTANCE.wrapNode(
    rootNode.getChildren("staticFieldReferences")[0],
  ) as StaticFieldReference;
  const classifier = LanguageRegistry.INSTANCE.wrapNode(
    rootNode.getChildren("classifiers")[0],
  ) as Classifier;

  const computedClassifierName = computed(
    () => staticFieldReference.classifier?.name,
  );

  expect(computedClassifierName.value).toBe(undefined);

  staticFieldReference.classifier = classifier;

  expect(computedClassifierName.value).toBe("aName");
});

// Implementation of the wrapper cache.
function getWraperCache() {
  const cache = new WeakMap<INodeJS, ITypedNode>();
  return function getWrapperFromCache(newWrapper: ITypedNode): ITypedNode {
    const untypedNode = newWrapper.unwrap();
    const oldWrapper = cache.get(untypedNode);
    if (oldWrapper !== undefined) {
      return oldWrapper;
    } else {
      cache.set(untypedNode, newWrapper);
      return newWrapper;
    }
  };
}

test("typed node objects can be cached", () => {
  try {
    const nodeData = {
      root: {
        children: [
          {
            // concecpt ID of an INamedConcept
            concept: "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468",
            role: "children1",
            properties: {
              name: "aName",
            },
          },
        ],
      },
    };
    // Using the wrapper cache.
    LanguageRegistry.INSTANCE.wrapperCache = getWraperCache();
    const rootNode = useRootNode(nodeData);
    const untypedNodeCreatedFirst = rootNode.getChildren("children1")[0];
    const untypedNodeCreatedSecond = rootNode.getChildren("children1")[0];
    const typedNodeCreatedFirst = LanguageRegistry.INSTANCE.wrapNode(
      untypedNodeCreatedFirst,
    );
    const typedNodeCreatedSecond = LanguageRegistry.INSTANCE.wrapNode(
      untypedNodeCreatedSecond,
    );

    // Make comparison inside `expect` function,
    // because differences between properties do not help you to understand why a test fails.
    // > If differences between properties do not help you to understand why a test fails,
    // > especially if the report is large,
    // > then you might move the comparison into the expect function.
    // See https://jestjs.io/docs/29.6/expect#tobevalue
    expect(typedNodeCreatedFirst === typedNodeCreatedSecond).toBe(true);
  } finally {
    LanguageRegistry.INSTANCE.wrapperCache = undefined;
  }
});

test("untyped node objects can be cached", () => {
  const nodeData = {
    root: {
      children: [
        {
          // concecpt ID of an INamedConcept
          concept: "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468",
          role: "children1",
          properties: {
            name: "aName",
          },
        },
      ],
    },
  };
  const rootNode = useRootNode(nodeData);
  const untypedNodeCreatedFirst = rootNode.getChildren("children1")[0];
  const untypedNodeCreatedSecond = rootNode.getChildren("children1")[0];

  // Works without workaround when INodeJS is `ReactiveINodeJS`,
  // because `ReactiveINodeJS` are always create with `toReactiveINodeJS` whicht utilizes a cache.
  // NOTE: `===` would not work, if we used plain `INodeJS` returned by `@modelix/model-client`.
  expect(untypedNodeCreatedFirst === untypedNodeCreatedSecond).toBe(true);
  expect(untypedNodeCreatedFirst.equals(untypedNodeCreatedSecond)).toBe(true);
});

/*
  Background in JVM and Kotlin objects have `.equals`/`.hashCode`.
  (Note, that when you write in `==` in Kotlin it is converted to an `.equals` call in JS.)

  Because in JVM and Kotlin we have `.equals`/`.hashCode` we can create many `INode` objects for one "conceptual" node.

  To always return the same UNTYPED object for the same "conceptual" node but different `INodeJS` objects of it,
  we would need to introduce caching, on the levels of `INodeJS`-create.
  This is actually already done for `ReactiveINodeJS` but actually for reasons specific to Vue.js and not equality.

  To always return the same TYPED object, we need to additionally set a `
  LanguageRegistry.INSTANCE.wrapperCache = getWraperCache()`.
  Check out the custom `getWraperCache` in this test.

  Still my suggestion is:
    * Avoid caches. Do not bother wiht `getWraperCache`. Do not use `===`/`==` in TS. Always compare the references.
    * For ease of use, use a util method `isSameNode` or add it as `.same`/`.equals`/`.isSameNode` method to `ITypedNode`.
*/
