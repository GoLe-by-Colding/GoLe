import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { loadEnvFile } from "node:process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const localEnvPath = resolve(repositoryRoot, ".env");

if (existsSync(localEnvPath)) {
  loadEnvFile(localEnvPath);
}

const requestedArgs = process.argv.slice(2);
const useLocalProfile = requestedArgs.includes("--local");
const gradleArgs = requestedArgs.filter((argument) => argument !== "--local");

if (gradleArgs.length === 0) {
  throw new Error("실행할 Gradle 태스크를 지정해야 합니다.");
}

const apiDirectory = resolve(repositoryRoot, "apps", "api");
const wrapper = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
const environment = {
  ...process.env,
  ...(useLocalProfile && process.env.SPRING_PROFILES_ACTIVE === undefined
    ? { SPRING_PROFILES_ACTIVE: "local" }
    : {}),
};

const child = spawn(wrapper, gradleArgs, {
  cwd: apiDirectory,
  env: environment,
  // Windows의 .bat Wrapper는 cmd.exe를 통해 실행해야 한다.
  shell: process.platform === "win32",
  stdio: "inherit",
});

child.on("error", (error) => {
  throw error;
});

child.on("exit", (code) => {
  process.exitCode = code ?? 1;
});
