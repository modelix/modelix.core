const UseServerPath = require('./decorators/use-server-path');
const id = 'plugin';

/** @type {import('@redocly/cli').DecoratorsConfig} */
const decorators = {
  oas3: {
    'use-server-path': UseServerPath,
  },
};

module.exports = {
  id,
  decorators,
};
