# Tool And Command Discovery Guide

## Goal
Use stable discovery patterns so operations still work when command names, groups, or flags change.

## Tool-To-Task Mapping
- `exec_command` (terminal): local repo checks, builds, deploy commands, runtime inspection.
- `multi_tool_use.parallel`: run independent read/check commands in parallel for faster diagnosis.
- `list_mcp_resources` / `read_mcp_resource`: prefer these first when internal project context is available.
- `web.search_query` + `web.open`: external docs lookup when local help is missing or behavior changed.

## Discovery Workflow (Generic)
1. Start at top-level help for the tool.
2. Narrow to service/group help.
3. Narrow again to resource help.
4. List resources first, then describe one resource in detail.
5. Use machine-readable output (`json`/`yaml`) before making mutations.
6. Run a small-scope mutation first, then verify state, then continue.

## Generic Patterns By CLI

### gcloud
```bash
gcloud --help
gcloud <group> --help
gcloud <group> <resource> --help
gcloud <group> <resource> list --format=json
gcloud <group> <resource> describe <name> --format=yaml
gcloud <group> <resource> update --help
```

### firebase
```bash
firebase --help
firebase <group> --help
firebase <group>:<command> --help
firebase <group>:<command> --json
```

### gradle
```bash
./gradlew tasks
./gradlew <module>:tasks
./gradlew help --task <taskName>
```

### npm
```bash
npm --help
npm help <topic>
npm run
npm run <script> -- --help
```

### git
```bash
git help -a
git <command> -h
git status
git log --oneline --decorate -n <count>
```

## When A Command Is Missing
1. Re-check top-level and group help output.
2. Verify CLI version and active account/project context.
3. Use list/describe endpoints to find renamed resources.
4. If CLI coverage is missing, call the service REST API with auth token.
5. Record the fallback path in docs so future runs do not guess.

## Output Rules For Reliable Ops
- Prefer explicit project and region context.
- Prefer `list` + `describe` over assumptions.
- Prefer structured output formats for parsing.
- Avoid one-off brittle commands when a generic discovery path exists.
