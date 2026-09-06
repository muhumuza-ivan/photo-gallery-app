# photo-gallery-app

Spring Boot 3.5 / Java 21 photo gallery. Server-rendered Thymeleaf UI, images in a
private S3 bucket served through CloudFront, descriptions in RDS PostgreSQL.

Runs on ECS Fargate. The infrastructure lives in a **separate repository**,
[`photo-gallery-infra`](../photo-gallery-infra), and this repository contains no
infrastructure definitions at all — not even the task definition, which is read
from the live ECS family at deploy time and only has its image swapped.

## Layout

| Path | Purpose |
|---|---|
| `src/main/java/` | Application: controller, entity, repository, S3 store |
| `src/main/resources/application.yml` | Configuration, entirely from environment variables |
| `src/main/resources/db/migration/` | Flyway migrations — the schema's source of truth |
| `src/main/resources/templates/` | Thymeleaf views |
| `Dockerfile` | Multi-stage build, non-root runtime |
| `appspec.yaml` | CodeDeploy hook. Must match the infra template — see below |
| `.github/workflows/build-and-deploy.yml` | Build, push to ECR, stage the deploy bundle |

## Runtime configuration

Every value comes from the environment, set by the ECS task definition in
`photo-gallery-infra/template.yaml` (§5 Compute). Nothing is baked into the image.

| Variable | Source |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | The RDS instance, referenced directly in the task definition |
| `DB_USERNAME`, `DB_PASSWORD` | Injected by ECS from the RDS-managed Secrets Manager secret |
| `S3_BUCKET` | Private image bucket |
| `CLOUDFRONT_DOMAIN` | Distribution domain used to build image URLs |
| `AWS_REGION` | Read by `S3Config`, not by `application.yml` |
| `MAX_UPLOAD_MB`, `SERVER_PORT`, `SPRING_PROFILES_ACTIVE` | Task definition |

AWS credentials are never configured: the S3 client uses the default provider
chain, which resolves the ECS task role. That role can `s3:PutObject` under
`photos/*` and nothing else — it cannot read objects back, list the bucket, or
reach any other bucket. Reads are CloudFront's job.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Gallery. Newest first, numbered like frames on a roll |
| POST | `/photos` | Multipart upload: `file` + `description` |
| GET | `/actuator/health` | ALB and CodeDeploy health check |

## Run locally

```bash
docker run -d --name gallery-db -e POSTGRES_DB=gallery \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16

S3_BUCKET=your-dev-bucket CLOUDFRONT_DOMAIN=your-dev-bucket.s3.amazonaws.com \
AWS_REGION=eu-west-1 mvn spring-boot:run
```

Every variable has a local-friendly default in `application.yml`, so only the two
S3 values are needed. Uploads will fail without real credentials; everything else
works.

## Build the image

```bash
docker build -t photo-gallery:local .
```

Multi-stage: Maven 3.9 + Temurin 21 to build, `eclipse-temurin:21-jre-alpine` to
run, non-root UID 10001, heap sized from the task memory limit via
`MaxRAMPercentage=75`. The POM is copied before `src` so dependency resolution
caches independently of source changes.

## Deployment

Pushing to `main` runs `.github/workflows/build-and-deploy.yml`:

```
mvn verify
  -> OIDC: exchange the GitHub token for short-lived AWS credentials
  -> docker push :<sha>                      immutable tag
  -> render taskdef.json from the live ECS family, swapping only the image
  -> upload taskdef.json + appspec.yaml to s3://<artifact bucket>/deploy/
  -> docker push :latest                     THE TRIGGER, pushed last
       -> EventBridge -> CodePipeline -> CodeDeploy -> blue/green on ECS
```

`:latest` is pushed **last** on purpose: it is what the EventBridge rule matches,
so the deploy bundle is always in S3 before the pipeline starts.

Rendering the task definition from the live family rather than storing a copy
here is what keeps this repository free of infrastructure detail — roles, secrets,
log configuration and networking stay owned by CloudFormation, and only the image
URI changes.

### Required repository secrets

Both come from the `photo-gallery` stack's outputs, **once it has finished
deploying** — outputs are empty until `CREATE_COMPLETE`, and setting them early
stores the literal string `None`:

| Secret | Set from stack output |
|---|---|
| `AWS_ROLE_ARN` | `GitHubActionsRoleArn` |
| `ARTIFACT_BUCKET` | `ArtifactBucketName` |

```bash
ROLE_ARN=$(aws cloudformation describe-stacks --stack-name photo-gallery \
  --query 'Stacks[0].Outputs[?OutputKey==`GitHubActionsRoleArn`].OutputValue' --output text)
BUCKET=$(aws cloudformation describe-stacks --stack-name photo-gallery \
  --query 'Stacks[0].Outputs[?OutputKey==`ArtifactBucketName`].OutputValue' --output text)

gh secret set AWS_ROLE_ARN    --body "$ROLE_ARN"
gh secret set ARTIFACT_BUCKET --body "$BUCKET"
```

Read them from the stack rather than typing them, and check neither is `None` —
CloudFormation returns that for a stack whose outputs are not yet populated, and
storing it produces a confusing OIDC failure much later.

Neither value is actually a credential — the role ARN is useless without the OIDC
trust policy, which pins this repository and branch. **No long-lived AWS
credentials exist in this repository**; authentication is entirely OIDC. They are
stored as secrets by policy, to keep the account ID out of plaintext, not because
exposure would grant access.

### What can assume the AWS role

The trust policy is pinned to this repository *and* one branch:

```
repo:<owner>/photo-gallery-app:ref:refs/heads/main
```

Consequences worth knowing before you change anything:

- **Only `main` can deploy.** A workflow run on any other branch, or from a fork,
  cannot assume the role. That is deliberate, not a bug to work around.
- **Deleting and recreating this repository breaks it** if the account issues
  immutable subject claims, because the numeric repository ID changes. Renaming is
  fine. Update `AppRepoSubjectPrefix` in the infra repo if the ID changes.
- **`AWS_REGION` in the workflow must match the region the stack was deployed
  into.** It is `eu-west-1` by default.

## The contract with the infrastructure repository

Four values must agree across the two repositories. Change one and you must
change its pair, or the deployment fails after a successful build:

| This repo | Infra repo | Value |
|---|---|---|
| `ECR_REPOSITORY` (workflow env) | `ProjectName` | `photo-gallery` |
| `TASK_FAMILY` (workflow env) | `TaskDefinition.Family` | `photo-gallery` |
| `CONTAINER_NAME` (workflow env) and `appspec.yaml` `ContainerName` | container definition `Name` | `photo-gallery` |
| `ARTIFACT_KEY` (workflow env) | `DeployArtifactKey` parameter | `deploy/photo-gallery-deploy.zip` |

`appspec.yaml`'s `ContainerPort` must also match the infra `ContainerPort`
parameter (`8080`), and `/actuator/health` must stay in the actuator exposure list
because it is the ALB and CodeDeploy health check target.

## Schema

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate is set to
`ddl-auto: validate`, so the entity and the migration must agree exactly — a
mismatch stops the container at startup, *after* a deployment that otherwise
looked healthy.

Add a new `V2__*.sql` rather than editing an applied migration. Editing one that
has already run makes Flyway's checksum validation fail and the container will not
start.
