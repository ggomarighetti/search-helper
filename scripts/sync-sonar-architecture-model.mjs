import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const projectKey = process.env.SONAR_PROJECT_KEY ?? "ggomarighetti_jpa-rsql-search";
const organizationId =
  process.env.SONAR_ORGANIZATION_ID ?? "df8bd268-70a4-4338-a093-2cfebc6ad4ff";
const token = process.env.SONAR_TOKEN;
const checkOnly = process.argv.includes("--check");
const modelPath = resolve(".sonar", "architecture-model.json");
const expectedModel = expectedArchitectureModel();

const model = JSON.parse(await readFile(modelPath, "utf8"));
validateModel(model);

if (checkOnly) {
  console.log("Sonar intended architecture declaration is valid.");
  process.exit(0);
}

if (!token) {
  throw new Error("SONAR_TOKEN is required to synchronize the architecture model.");
}

const authorization = `Basic ${Buffer.from(`${token}:`).toString("base64")}`;
const commonHeaders = {
  accept: "application/json",
  authorization,
  "content-type": "application/json",
};

try {
  await syncArchitectureModel();
} catch (error) {
  console.warn(
    `Sonar intended architecture sync was skipped: ${error instanceof Error ? error.message : String(error)}`,
  );
  console.warn(
    "The local declaration remains validated; Sonar Architecture analysis still runs through sonar.architecture.enable=true.",
  );
}

async function syncArchitectureModel() {
  const projectId = await findMainBranchId();
  const models = await sonarRequest(
    `https://api.sonarcloud.io/architecture/models?projectId=${encodeURIComponent(projectId)}`,
    { headers: commonHeaders },
  );

  if (models.length === 0) {
    await sonarRequest("https://api.sonarcloud.io/architecture/models", {
      method: "POST",
      headers: commonHeaders,
      body: JSON.stringify({
        projectId,
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
      "The architecture declaration must describe the v2 Maven module DAG and its Sonar constraints.",
    );
  }
}

function expectedArchitectureModel() {
  return {
    perspectives: [
      {
        label: "V2 Maven modules",
        description: "Direct v2 module DAG for the publishable jpa-rsql-search reactor.",
        groups: [
          group(
            "API",
            "jpa-rsql-search-api/src/main/java/**",
          ),
          group(
            "RSQL SPI",
            "jpa-rsql-search-rsql-spi/src/main/java/**",
          ),
          group(
            "Core",
            "jpa-rsql-search-core/src/main/java/**",
          ),
          group(
            "JPA validation",
            "jpa-rsql-search-jpa-validation/src/main/java/**",
          ),
          group(
            "Perplexhub",
            "jpa-rsql-search-perplexhub/src/main/java/**",
          ),
          group(
            "Spring Boot starter",
            "jpa-rsql-search-spring-boot-starter/src/main/java/**",
          ),
        ],
        constraints: [
          exclusiveAllow(
            ["RSQL SPI", "Core", "JPA validation", "Perplexhub", "Spring Boot starter"],
            ["API"],
          ),
          exclusiveAllow(
            ["Core", "Perplexhub", "Spring Boot starter"],
            ["RSQL SPI"],
          ),
          exclusiveAllow(
            ["JPA validation", "Perplexhub", "Spring Boot starter"],
            ["Core"],
          ),
          exclusiveAllow(
            ["Spring Boot starter"],
            ["JPA validation"],
          ),
          exclusiveAllow(
            ["Spring Boot starter"],
            ["Perplexhub"],
          ),
        ],
      },
    ],
  };
}

function group(label, pattern) {
  return {
    label,
    patterns: [pattern],
  };
}

function exclusiveAllow(from, to) {
  return {
    relation: "exclusive-allow",
    from,
    to,
  };
}
