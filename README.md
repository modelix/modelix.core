# The Modelix Project

The modelix project develops an open source platform for (meta-)models on the web. We are native to the web and the cloud.

For general information on modelix, please refer to the [official modelix homepage](https://modelix.org) as well as the [platform documentation](https://docs.modelix.org).

A list of individual components and links to component-specific documentation can be found [in our documentation](https://docs.modelix.org/modelix/main/reference/components-table.html).

# modelix.core

This repository contains the core components of the modelix platform.
All components in this repository have no dependencies to JetBrains MPS.
If you are looking for MPS-related modelix components,
see https://github.com/modelix/modelix.mps and https://github.com/modelix/modelix.mps-plugins.

## Development

### Commit convention

This project uses [conventional commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) as the convention for Git commits.

### pre-commit

This project uses [pre-commit](https://pre-commit.com/) to validate that new commits follow intended conventions.
To enable pre-commit hooks, you have to run the following command initially after cloning the repository:

```console
$ pre-commit install
pre-commit installed at .git/hooks/pre-commit
```

Some checks use by pre-commit are implemented by JavaScript components.
Therefore, it's necessary to have the required packages installed via:

```console
$ npm install
added 72 packages, removed 98 packages, changed 203 packages, and audited 654 packages in 3s
...
```

### detekt

We use [detekt](https://detekt.dev/) as a Kotlin linter.
detekt is integrated in the Gradle build process.
Manually, it can be triggered with:

```console
$ ./gradlew detektMain detektTest detektJsMain detektJsTest detektJvmMain detektJvmTest
...
```

The project contains a configuration for the [IntelliJ detekt plugin](https://plugins.jetbrains.com/plugin/10761-detekt).
If you install this plugin, you should get detekt annotations inline in IntelliJ.
Unfortunately, the plugin [does not support detekt rules requiring type resolution](https://github.com/detekt/detekt-intellij-plugin/issues/499).
Therefore, some annotations can only be obtained by running detekt through Gradle.

detekt results are also reported on the GitHub project using GitHub's code scanning feature.
In PRs, detekt finding will be provided as annotations on the PR.

# Authors

Development of modelix is supported by [itemis](https://itemis.com)

# Copyright and License

Copyright © 2021-present by the modelix open source project and the individual contributors. All Rights Reserved.

Use of this software is granted under the terms of the Apache License Version 2.0.
See the [LICENSE](LICENSE) to find the full license text.
