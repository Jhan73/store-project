// Only what the suite reads from Node, so the project needs no @types/node dependency.
declare const process: { env: Record<string, string | undefined> };
