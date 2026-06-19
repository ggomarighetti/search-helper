import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const projectKey = process.env.SONAR_PROJECT_KEY ?? "ggomarighetti_jpa-rsql-search";
const organizationId =
  process.env.SONAR_ORGANIZATION_ID ?? "df8bd268-70a4-4338-a093-2cfebc6ad4ff";
const token = process.env.SONAR_ADMIN_TOKEN;
const checkOnly = process.argv.includes("--check");
const modelPath = resolve(".sonar", "architecture-model.json");
const expectedPatterns = new Set([
  "jpa-rsql-search-api:**",
  "jpa-rsql-search-rsql-spi:**",
  "jpa-rsql-search-core:**",
  "jpa-rsql-search-jpa-validation:**",
  "jpa-rsql-search-perplexhub:**",
  "jpa-rsql-search-spring-boot-starter:**",
]);

const model = JSON.parse(await readFile(modelPath, "utf8"));
validateModel(model);

if (checkOnly) {
  console.log("Sonar intended architecture declaration is valid.");
  process.exit(0);
}

if (!token) {
  throw new Error(
    "SONAR_ADMIN_TOKEN is required to synchronize the architecture model.",
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
  const patterns = new Set(
    perspective.groups?.flatMap((group) => group.patterns ?? []) ?? [],
  );
  if (
    patterns.size !== expectedPatterns.size ||
    [...expectedPatterns].some((pattern) => !patterns.has(pattern))
  ) {
    throw new Error("The architecture declaration must map every public Maven module once.");
  }
  const groupLabels = new Set(perspective.groups.map((group) => group.label));
  for (const constraint of perspective.constraints ?? []) {
    if (!groupLabels.has(constraint.from) || !groupLabels.has(constraint.to)) {
      throw new Error(
        `Unknown architecture constraint: ${constraint.from} -> ${constraint.to}`,
      );
    }
  }
}
