import { watchEffect } from "vue";
import { useModelClient } from "./useModelClient";

test("test client connection error is exposed", (done) => {
  const { error } = useModelClient("anURL", () => {
    return Promise.reject("A connection error.");
  });
  watchEffect(() => {
    if (error.value !== null) {
      expect(error.value).toBe("A connection error.");
      done();
    }
  });
});
