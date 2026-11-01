// `npx depcruise src` exits non-zero on any rule at severity "error".
module.exports = {
  forbidden: [
    {
      name: "no-circular",
      comment: "A dependency cycle means the modules cannot be understood or tested apart.",
      severity: "error",
      from: {},
      to: { circular: true },
    },
    {
      name: "layering-parse-decide-no-http",
      comment: "Modules that parse and decide stay pure. Only the HTTP module talks to the network.",
      severity: "error",
      from: { path: "^src/(errors|json|wire|template|choices|refusal)\\.ts$" },
      to: { path: "^src/client\\.ts$", reachable: true },
    },
  ],
  options: {
    doNotFollow: { path: "node_modules" },
    tsPreCompilationDeps: true,
    tsConfig: { fileName: "tsconfig.json" },
  },
};
