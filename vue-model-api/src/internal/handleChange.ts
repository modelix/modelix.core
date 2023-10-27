import { INodeJS, org } from "@modelix/model-client";
import { Cache } from "./Cache";

const { PropertyChanged, ReferenceChanged, ChildrenChanged } =
  org.modelix.model.client2;

type ChangeJS = org.modelix.model.client2.ChangeJS;

export function handleChange(change: ChangeJS, cache: Cache<INodeJS>) {}
