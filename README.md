# The Modelix Project
The modelix project develops an open source platform for (meta-)models on the web. We are native to the web and the cloud.

For general information on modelix, please refer to the [official modelix homepage](https://modelix.org) as well as the [platform documentation](https://docs.modelix.org).

For individual component specific documentation, see https://docs.modelix.org/modelix/latest/reference/components.html

# modelix.core

This repository contains the core components of the modelix platform.
All components in this repository have no dependencies to JetBrains MPS.
If you are looking for MPS related modelix components, see https://github.com/modelix/modelix.


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


# Authors

Development of modelix is supported by [itemis](https://itemis.com)


# Copyright and License

Copyright © 2021-present by the modelix open source project and the individual contributors. All Rights Reserved.

Use of this software is granted under the terms of the Apache License Version 2.0.
See the [LICENSE](LICENSE) to find the full license text.
