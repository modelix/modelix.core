import { watchEffect } from "vue";

/**
 * This function takes a `promiseEffect` callback and executes it every time its used reactive values are changed.
 *
 * If a promise is fullfilled, the `onfulfilled` call back is called with the value and aboolean,
 * which indicates if the value is the result of the last create promise.
 *
 * If a promise is rejected, the `onrejected` call back is called with the reason and a boolean,
 * which indicates if the value is the result of the last create promise.
 *
 * This function is usefull, if you need to trigger a new promise every time some reavtive value changes and
 * then need to distinguashe if the value/error of the settled promise comes from the last started promise.
 *
 * @param promiseEffect A function that may create a new promise.
 *
 * The function is executed in {@link watchEffect} and can therfore use reacitve values.
 * This function is executed everytime on of used reactive values changed.
 *
 * @param onfulfilled The callback to execute when the Promise is resolved.
 * @param onrejected The callback to execute when the Promise is rejected.
 */
export function useLastPromiseEffect<ValueT>(
  promiseEffect: () => Promise<ValueT> | undefined,
  onfulfilled: (value: ValueT, isResultOfLastStartedPromise: boolean) => void,
  onrejected: (reason: unknown, isResultOfLastStartedPromise: boolean) => void,
) {
  let lastStartedPromise: Promise<ValueT> | undefined;
  watchEffect(() => {
    const thisPromise = promiseEffect();
    lastStartedPromise = thisPromise;
    const isResultOfLastStartedPromise = () =>
      // `lastStartedPromise` might be diffrent to `thisPromise`,
      // because this function gets called asynchronously, when `thisPromise` is settled.
      // In the meantime `promiseEffect()` might have been called again and assigned a new value to `lastStartedPromise`.
      lastStartedPromise == thisPromise;
    if (thisPromise !== undefined) {
      thisPromise.then(
        (value) => onfulfilled(value, isResultOfLastStartedPromise()),
        (value) => onrejected(value, isResultOfLastStartedPromise()),
      );
    }
  });
}
