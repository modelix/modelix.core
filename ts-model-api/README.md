<div align="center" style="text-align: center;">
  <h1 style="border-bottom: none;">ts-model-api</h1>

  <p>Simple Node.js module to output greeting message, written in [TypeScript][typescript-url]</p>
</div>

<hr />

[![Follow me][follow-me-badge]][follow-me-url]

[![Version][version-badge]][version-url]
[![Node version][node-version-badge]][node-version-url]
[![MIT License][mit-license-badge]][mit-license-url]

[![Downloads][downloads-badge]][downloads-url]
[![Total downloads][total-downloads-badge]][downloads-url]
[![Packagephobia][packagephobia-badge]][packagephobia-url]
[![Bundlephobia][bundlephobia-badge]][bundlephobia-url]

[![Dependency Status][daviddm-badge]][daviddm-url]
[![codecov][codecov-badge]][codecov-url]
[![Coverage Status][coveralls-badge]][coveralls-url]

[![codebeat badge][codebeat-badge]][codebeat-url]
[![Codacy Badge][codacy-badge]][codacy-url]
[![Code of Conduct][coc-badge]][coc-url]

> Better greeting message

## Table of contents <!-- omit in toc -->

- [Pre-requisites](#pre-requisites)
- [Setup](#setup)
  - [Install](#install)
  - [Usage](#usage)
    - [Node.js](#nodejs)
    - [Native ES modules or TypeScript](#native-es-modules-or-typescript)
- [API Reference](#api-reference)
  - [greeting([name])](#greetingname)
  - [greetingSync([name])](#greetingsyncname)
- [License](#license)

## Pre-requisites

- [Node.js][nodejs-url] >= 8.16.0
- [NPM][npm-url] >= 6.4.1 ([NPM][npm-url] comes with [Node.js][nodejs-url] so there is no need to install separately.)

## Setup

### Install

```sh
# Install via NPM
$ npm install --save ts-model-api
```

### Usage

#### Node.js

```js
const greeting = require("ts-model-api");
```

#### Native ES modules or TypeScript

```ts
import greeting from "ts-model-api";
```

## API Reference

### greeting([name])

- `name` <[string][string-mdn-url]> Name of the person to greet at.
- returns: <[Promise][promise-mdn-url]<[string][string-mdn-url]>> Promise which resolves with a greeting message.

### greetingSync([name])

This methods works the same as `greeting(name)` except that this is the synchronous version.

## License

[MIT License](https://.mit-license.org/) © Sascha Lisson

<!-- References -->

[typescript-url]: https://github.com/Microsoft/TypeScript
[nodejs-url]: https://nodejs.org
[npm-url]: https://www.npmjs.com
[node-releases-url]: https://nodejs.org/en/download/releases

<!-- MDN -->

[array-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array
[boolean-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Boolean
[function-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function
[map-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
[number-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number
[object-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object
[promise-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Promise
[regexp-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
[set-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set
[string-mdn-url]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String

<!-- Badges -->

[follow-me-badge]: https://flat.badgen.net/twitter/follow/?icon=twitter
[version-badge]: https://flat.badgen.net/npm/v/ts-model-api?icon=npm
[node-version-badge]: https://flat.badgen.net/npm/node/ts-model-api
[mit-license-badge]: https://flat.badgen.net/npm/license/ts-model-api
[downloads-badge]: https://flat.badgen.net/npm/dm/ts-model-api
[total-downloads-badge]: https://flat.badgen.net/npm/dt/ts-model-api?label=total%20downloads
[packagephobia-badge]: https://flat.badgen.net/packagephobia/install/ts-model-api
[bundlephobia-badge]: https://flat.badgen.net/bundlephobia/minzip/ts-model-api
[daviddm-badge]: https://flat.badgen.net/david/dep//ts-model-api
[codecov-badge]: https://flat.badgen.net/codecov/c/github//ts-model-api?label=codecov&icon=codecov
[coveralls-badge]: https://flat.badgen.net/coveralls/c/github//ts-model-api?label=coveralls
[codebeat-badge]: https://codebeat.co/badges/123
[codacy-badge]: https://api.codacy.com/project/badge/Grade/123
[coc-badge]: https://flat.badgen.net/badge/code%20of/conduct/pink

<!-- Links -->

[follow-me-url]: https://twitter.com/?utm_source=github.com&utm_medium=referral&utm_content=/ts-model-api
[version-url]: https://www.npmjs.com/package/ts-model-api
[node-version-url]: https://nodejs.org/en/download
[mit-license-url]: https://github.com//ts-model-api/blob/master/LICENSE
[downloads-url]: https://www.npmtrends.com/ts-model-api
[packagephobia-url]: https://packagephobia.now.sh/result?p=ts-model-api
[bundlephobia-url]: https://bundlephobia.com/result?p=ts-model-api
[daviddm-url]: https://david-dm.org//ts-model-api
[codecov-url]: https://codecov.io/gh//ts-model-api
[coveralls-url]: https://coveralls.io/github//ts-model-api?branch=master
[codebeat-url]: https://codebeat.co/projects/github-com--ts-model-api-master
[codacy-url]: https://www.codacy.com/app//ts-model-api?utm_source=github.com&utm_medium=referral&utm_content=/ts-model-api&utm_campaign=Badge_Grade
[coc-url]: https://github.com//ts-model-api/blob/master/CODE_OF_CONDUCT.md
