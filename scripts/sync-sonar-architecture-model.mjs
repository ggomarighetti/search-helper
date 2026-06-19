import { readdir, readFile } from "node:fs/promises";
import { relative, resolve } from "node:path";

const projectKey = process.env.SONAR_PROJECT_KEY ?? "ggomarighetti_jpa-rsql-search";
const organizationId =
  process.env.SONAR_ORGANIZATION_ID ?? "df8bd268-70a4-4338-a093-2cfebc6ad4ff";
const token = process.env.SONAR_TOKEN;
const checkOnly = process.argv.includes("--check");
const deleteOnly = process.argv.includes("--delete");
const modelPath = resolve(".sonar", "architecture-model.json");
const perspectiveLabel = "V2 Maven package leaves";
const perspectiveDescription =
  "Direct modular boundaries for jpa-rsql-search v2 at production package granularity.";
const basePackage = "io.github.ggomarighetti.jparsqlsearch";
const allowAllPerspectiveConstraint = {
  from: ["**"],
  to: ["**"],
  relation: "exclusive-allow",
};
const productModules = [
  "jpa-rsql-search-api",
  "jpa-rsql-search-rsql-spi",
  "jpa-rsql-search-core",
  "jpa-rsql-search-jpa-validation",
  "jpa-rsql-search-perplexhub",
  "jpa-rsql-search-spring-boot-starter",
];
const expectedModel = await buildExpectedModel();

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

if (deleteOnly) {
  if (models.length === 0) {
    console.log(`No Sonar intended architecture model exists for ${projectKey}.`);
    process.exit(0);
  }
  for (const existingModel of models) {
    await sonarRequest(
      `https://api.sonarcloud.io/architecture/models/${encodeURIComponent(existingModel.id)}`,
      {
        method: "DELETE",
        headers: commonHeaders,
      },
    );
  }
  console.log(`Deleted ${models.length} Sonar intended architecture model(s) for ${projectKey}.`);
  process.exit(0);
}

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
  if (JSON.stringify(candidate) !== JSON.stringify(expectedModel)) {
    throw new Error(
      "The architecture declaration must map every production Java package with file-path patterns.",
    );
  }
}

async function buildExpectedModel() {
  const perspective = {
    label: perspectiveLabel,
    description: perspectiveDescription,
    groups: [],
    constraints: [allowAllPerspectiveConstraint],
  };
  for (const moduleName of productModules) {
    const sourceRoot = resolve(moduleName, "src", "main", "java");
    const files = await collectJavaFiles(sourceRoot);
    const packageNames = new Set();
    for (const file of files) {
      const className = relative(sourceRoot, file)
        .replace(/[\\/]/g, ".")
        .replace(/\.java$/, "");
      packageNames.add(className.split(".").slice(0, -1).join("."));
    }
    for (const packageName of [...packageNames].sort()) {
      perspective.groups.push({
        label: architectureLabel(moduleName, packageName),
        patterns: [
          `${moduleName}/src/main/java/${packageName.replaceAll(".", "/")}/*.java`,
        ],
      });
    }
  }
  return { perspectives: [perspective] };
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

function architectureLabel(moduleName, packageName) {
  const relativePackage = packageName.startsWith(`${basePackage}.`)
    ? packageName.slice(basePackage.length + 1)
    : packageName;
  return `${moduleName}-${relativePackage.replaceAll(".", "-")}`;
}
