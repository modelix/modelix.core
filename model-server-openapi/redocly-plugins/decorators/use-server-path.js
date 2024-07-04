module.exports = UseServerPath;

/** @type {import('@redocly/cli').OasDecorator} */

function UseServerPath() {
  let serverPathname
  return {
    Server: {
      enter(server) {
        // example.com is needed as URL doesn't parse relative URLs without a host
        serverPathname = new URL(server.url, "http://example.com").pathname
        if (serverPathname === "/") {
          serverPathname = ""
        }
        server.url = "/"
      },
    },
    Root: {
      leave(root) {
        const newPaths = {}
        for (let path in root.paths) {
          newPaths[serverPathname + path] = root.paths[path]
        }
        root.paths = newPaths
      }
    }
  }
};
