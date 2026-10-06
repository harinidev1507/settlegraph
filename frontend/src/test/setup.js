import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Vitest globals are off (every test imports what it uses), so Testing
// Library can't register its own cleanup: unmount rendered components here.
// localStorage is per-test state too (the session lives there).
afterEach(() => {
  cleanup();
  localStorage.clear();
});
