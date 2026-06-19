import { readdir, readFile } from "node:fs/promises";
import { relative, resolve } from "node:path";

const projectKey = process.env.SONAR_PROJECT_KEY ?? "ggomarighetti_jpa-rsql-search";
const organizationId =
  process.env.SONAR_ORGANIZATION_ID ?? "df8bd268-70a4-4338-a093-2cfebc6ad4ff";
const token = process.env.SONAR_TOKEN;
const checkOnly = process.argv.includes("--check");
const modelPath = resolve(".sonar", "architecture-model.json");
const productModules = [
  "jpa-rsql-search-api",
  "jpa-rsql-search-rsql-spi",
  "jpa-rsql-search-core",
  "jpa-rsql-search-jpa-validation",
  "jpa-rsql-search-perplexhub",
  "jpa-rsql-search-spring-boot-starter",
];
const expectedPatterns = await collectExpectedPatterns();

const model = JSON.parse(await readFile(modelPath, "utf8"));
validateModel(model);

if (checkOnly) {
  console.log("Sonar intended architecture declaration is valid.");
  process.exit(0);
}

if (!token) {
  throw new Error(
    "SONAR_TOKEN is required to synchronize the architecture model.",
  );
}

const mainBranchId = await findMainBranchId();
const authorization = `Basic ${Buffer.from(`${token}:`).toString("base64")}`;
const commonHeaders = {
  accept: "application/json",
  authorization,
  "content-type": "application/json",
};
const models = await sonarRequest(
  `https://api.sonarcloud.io/architecture/models?projectId=${encodeURIComponent(mainBranchId)}`,
  { headers: commonHeaders },
);

if (models.length === 0) {
  await sonarRequest("https://api.sonarcloud.io/architecture/models", {
    method: "POST",
    headers: commonHeaders,
    body: JSON.stringify({
      projectId: mainBranchId,
      organizationId,
      model,
    }),
  });
  console.log(`Created the Sonar intended architecture for ${projectKey}.`);
} else {
  await sonarRequest(
    `https://api.sonarcloud.io/architecture/models/${encodeURIComponent(models[0].id)}`,
    {
      method: "PATCH",
      headers: {
        ...commonHeaders,
        "content-type": "merge-patch+json",
      },
      body: JSON.stringify({ model }),
    },
  );
  console.log(`Updated the Sonar intended architecture for ${projectKey}.`);
}

async function findMainBranchId() {
  const response = await fetch(
    `https://sonarcloud.io/api/project_branches/list?project=${encodeURIComponent(projectKey)}`,
    { headers: { accept: "application/json" } },
  );
  if (!response.ok) {
    throw new Error(`Unable to list Sonar branches: HTTP ${response.status}`);
  }
  const payload = await response.json();
  const mainBranch = payload.branches?.find((branch) => branch.isMain);
  if (!mainBranch?.branchId) {
    throw new Error(`Unable to resolve the main Sonar branch for ${projectKey}.`);
  }
  return mainBranch.branchId;
}

async function sonarRequest(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Sonar architecture request failed: HTTP ${response.status}: ${body}`);
  }
  if (response.status === 204) {
    return undefined;
  }
  const body = await response.text();
  return body ? JSON.parse(body) : undefined;
}

function validateModel(candidate) {
  const perspectives = candidate?.perspectives;
  if (!Array.isArray(perspectives) || perspectives.length !== 1) {
    throw new Error("The architecture declaration must contain one Java perspective.");
  }
  const [perspective] = perspectives;
  if (perspective.language !== "java" || perspective.qualifiers !== "namespace") {
    throw new Error("The architecture perspective must target Java namespaces.");
  }
  const groups = perspective.groups ?? [];
  const labels = new Set();
  const patterns = new Set();
  for (const group of groups) {
    const groupPatterns = group.patterns ?? [];
    if ((group.groups ?? []).length > 0) {
      throw new Error(
        "The architecture declaration must keep production Java types as flat Sonar groups.",
      );
    }
    if (groupPatterns.length !== 1 || groupPatterns[0] !== group.label) {
      throw new Error(
        "Each Sonar architecture group must map exactly one production Java type and use the same label.",
      );
    }
    labels.add(group.label);
    patterns.add(groupPatterns[0]);
  }
  if (
    patterns.size !== expectedPatterns.size ||
    labels.size !== expectedPatterns.size ||
    [...expectedPatterns].some((pattern) => !patterns.has(pattern) || !labels.has(pattern))
  ) {
    throw new Error("The architecture declaration must map every production Java type once.");
  }
  if ((perspective.constraints ?? []).length > 0) {
    throw new Error(
      "Sonar intended architecture constraints are intentionally kept out of the synchronized model; Maven and ArchUnit enforce the DAG.",
    );
  }
}

async function collectExpectedPatterns() {
  const patterns = [];
  for (const moduleName of productModules) {
    const sourceRoot = resolve(moduleName, "src", "main", "java");
    const files = await collectJavaFiles(sourceRoot);
    patterns.push(
      ...files.map((file) => {
        const className = relative(sourceRoot, file)
          .replace(/[\\/]/g, ".")
          .replace(/\.java$/, "");
        return `${moduleName}:${className}`;
      }),
    );
  }
  return new Set(patterns);
}

async function collectJavaFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(
    entries.map(async (entry) => {
      const path = resolve(directory, entry.name);
      if (entry.isDirectory()) {
        return collectJavaFiles(path);
      }
      return entry.isFile() && entry.name.endsWith(".java") ? [path] : [];
    }),
  );
  return files.flat().sort();
}
