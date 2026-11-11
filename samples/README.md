# Sample corpus

Put a few small J2ME applications here to exercise the recompiler:

```
samples/
  Tetris.jar
  2048.jar
  ...
```

`test.cmd` recompiles every `*.jar` in this directory and checks that the generated Java
compiles.

The JARs themselves are **not** part of the repository — they belong to their authors. Only
this file is tracked.
