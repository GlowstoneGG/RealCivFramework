# Contributing

Issues and pull requests are welcome. For substantial gameplay changes, open an issue first so the rules and server behavior can be discussed.

## Development

1. Install JDK 25.
2. Run `./gradlew build` before opening a pull request.
3. Put pure, Bukkit-free rules in `core`, the kernel in `folia/.../framework`, and gameplay in one package per module under `folia/.../modules`. See [docs/extending.md](docs/extending.md).
4. Treat Folia region ownership as part of the design: use entity, region, global, or async schedulers as appropriate. Do not assume a single server thread.
5. Add focused tests for non-obvious pure logic and for bug fixes. Obvious wiring does not need a test.

Describe the behavior you changed and how you checked it in your pull request. Please follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

By contributing, you agree that your contribution is licensed under the repository's GPL-3.0 license.
