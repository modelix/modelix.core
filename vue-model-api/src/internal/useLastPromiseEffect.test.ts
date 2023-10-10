import { useLastPromiseEffect } from "./useLastPromiseEffect";
import { toRef } from "vue";

test("older promise result does not override newer promise result", (done) => {
  // The first promise is slower than the second promise.
  const createFirstSlowPromise = () =>
    new Promise((resolve) => {
      setTimeout(() => resolve("firstPromiseResult"), 500);
    });
  const createSecondFastPromise = () =>
    new Promise((resolve) => {
      setTimeout(() => resolve("secondPromiseResult"), 100);
    });
  const ref = toRef(createFirstSlowPromise());
  let firstResultReceived = false;
  let secondResultReceived = false;

  useLastPromiseEffect(
    () => ref.value,
    (value, isResultOfLastStartedPromise) => {
      if (value === "firstPromiseResult") {
        firstResultReceived = true;
        // The result of the second promise should have arrived before
        // the result of the first promise.
        expect(secondResultReceived).toBeTruthy();
        // For the first promise we mark with isResultOfLastStartedPromise,
        // that the result is not from the most current invoked promise.
        expect(isResultOfLastStartedPromise).toBeFalsy();
      } else {
        secondResultReceived = true;
        expect(isResultOfLastStartedPromise).toBeTruthy();
      }
      if (firstResultReceived && secondResultReceived) {
        done();
      }
    },
    () => {},
  );

  ref.value = createSecondFastPromise();
});
