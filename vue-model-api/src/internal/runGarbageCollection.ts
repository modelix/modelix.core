/**
 * Force garbage collection to run.
 * See https://github.com/orgs/nodejs/discussions/36467
 **/
export async function runGarbageCollection() {
  // Enqueue a micro task is needed to run before garbage collection
  // See https://github.com/orgs/nodejs/discussions/36467#discussioncomment-3367722
  await new Promise((resolve) => setTimeout(resolve, 0));
  if (global.gc) {
    global.gc();
  } else {
    fail(
      "Garbage collection unavailable. Pass --expose-gc when launching node to enable forced garbage collection.",
    );
  }
}
