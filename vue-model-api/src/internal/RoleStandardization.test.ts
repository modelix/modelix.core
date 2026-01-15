import { expect, test } from "@jest/globals";
import { useModelsFromJson } from "../useModelsFromJson";
import { toRoleJS } from "@modelix/ts-model-api";

const root = {
  root: {
    children: [
      {
        role: "children1",
        properties: {
          name: "child0",
        },
      },
    ],
  },
};

test("role in parent is standardized", () => {
  const rootNode = useModelsFromJson([JSON.stringify(root)]);
  // Use a combined format role that should be standardized
  const roleJS = toRoleJS(":UID_123:newChildRole");
  const child = rootNode.addNewChild(roleJS, -1, undefined);

  const role = child.getRoleInParent();
  console.log("DEBUG: role =", JSON.stringify(role));

  // We expect the role to be either the UID or the name, but NEVER the combined format starting with ":"
  expect(role).not.toMatch(/^:/);

  // Further, since we use getIdOrName() or getNameOrId(), it should be one of the two parts.
  const possibleValues = ["UID_123", "newChildRole"];
  expect(possibleValues).toContain(role);
});

test("property roles are standardized", () => {
  const rootNode = useModelsFromJson([JSON.stringify(root)]);
  const child = rootNode.getChildren(toRoleJS("children1"))[0];

  const properties = child.getPropertyRoles();

  expect(properties).toContain("name");
  // Ensure no combined formats are present
  properties.forEach((p) => {
    expect(p as unknown as string).not.toMatch(/^:/);
  });
});
