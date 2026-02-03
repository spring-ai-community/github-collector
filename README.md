# GitHub Collector

A Java library and CLI tool for collecting GitHub issues, pull requests, and collaborators with advanced filtering, batch processing, and resume support.

## Features

- **Issue Collection** - Collect issues with state and label filtering, including label events
- **PR Collection** - Collect pull requests with state filtering and soft approval detection
- **Collaborator Collection** - Collect repository collaborators with permissions for maintainer identification
- **Batch Processing** - Configurable batch sizes with automatic file splitting
- **Resume Support** - Resume interrupted collections from the last successful batch
- **Rate Limit Management** - Automatic handling of GitHub API rate limits
- **JSON Output** - Structured JSON output with collection metadata
- **Zip Archives** - Optional zip compression of collected data

## Modules

| Module | Description |
|--------|-------------|
| `github-collector-core` | Reusable library with no Spring dependencies |
| `github-collector-cli` | Standalone CLI application (uber-jar) |

## Requirements

- Java 17+
- GitHub personal access token

## Quick Start

### 1. Set your GitHub token

```bash
export GITHUB_TOKEN=your_github_token_here
```

### 2. Build the project

```bash
mvn clean package -DskipTests
```

### 3. Run the CLI

```bash
java -jar github-collector-cli/target/github-collector-cli-1.0.0-SNAPSHOT.jar --repo owner/repo
```

## CLI Usage

```
Usage: java -jar github-collector-cli.jar [OPTIONS]

OPTIONS:
    -h, --help              Show help message
    -r, --repo REPO         Repository in format owner/repo
    -b, --batch-size SIZE   Items per batch file (default: 100)
    -d, --dry-run           Show what would be collected without doing it
    -i, --incremental       Skip already collected items
    -z, --zip               Create zip archive of collected data
    -v, --verbose           Enable verbose logging
    --clean                 Clean previous data before starting (default)
    --no-clean, --append    Keep previous data and append new data
    --resume                Resume from last successful batch

FILTERING OPTIONS:
    -s, --state <state>     Issue state: open, closed, all (default: open)
    -l, --labels <labels>   Comma-separated list of labels to filter by
    --label-mode <mode>     Label matching: any, all (default: any)

COLLECTION TYPE OPTIONS:
    -t, --type <type>       Collection type: issues, prs, collaborators (default: issues)
    -n, --number <num>      Specific PR number to collect (when type=prs)
    --pr-state <state>      PR state: open, closed, merged, all (default: open)

LIMITING AND SORTING:
    --max-issues <count>    Limit total items collected
    --sort-by <field>       Sort by: updated, created, comments, reactions
    --sort-order <order>    Sort order: desc, asc (default: desc)
```

## Examples

### Collect open issues

```bash
java -jar github-collector-cli.jar --repo spring-projects/spring-ai
```

### Collect issues with specific labels

```bash
java -jar github-collector-cli.jar --repo spring-projects/spring-ai \
    --labels bug,enhancement --state open
```

### Collect the 20 most recently updated issues

```bash
java -jar github-collector-cli.jar --repo spring-projects/spring-ai \
    --max-issues 20 --sort-by updated --sort-order desc
```

### Collect merged pull requests

```bash
java -jar github-collector-cli.jar --repo spring-projects/spring-ai \
    --type prs --pr-state merged
```

### Collect a specific PR with details

```bash
java -jar github-collector-cli.jar --repo spring-projects/spring-ai \
    --type prs --number 1234
```

### Collect repository collaborators

```bash
java -jar github-collector-cli.jar --repo spring-projects/spring-ai \
    --type collaborators
```

### Dry run to preview collection

```bash
java -jar github-collector-cli.jar --repo spring-projects/spring-ai \
    --dry-run --verbose
```

### Resume an interrupted collection

```bash
java -jar github-collector-cli.jar --repo spring-projects/spring-ai --resume
```

## Library Usage

Add the dependency to your project:

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>github-collector-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Use the builder API:

```java
import org.springaicommunity.github.collector.*;

// Create collector with builder
GitHubCollectorBuilder builder = GitHubCollectorBuilder.create()
    .token(System.getenv("GITHUB_TOKEN"));

// Collect issues
IssueCollectionService issueCollector = builder.buildIssueCollector();
CollectionRequest request = new CollectionRequest.Builder()
    .repository("spring-projects/spring-ai")
    .state("open")
    .batchSize(50)
    .build();

CollectionResult result = issueCollector.collectItems(request);

// Collect PRs
PRCollectionService prCollector = builder.buildPRCollector();
CollectionResult prResult = prCollector.collectItems(request);

// Collect collaborators
CollaboratorsCollectionService collaboratorsCollector = builder.buildCollaboratorsCollector();
CollectionResult collaboratorsResult = collaboratorsCollector.collectItems(request);
```

## Output Format

Collections are saved as JSON files in the `github-issues/`, `github-prs/`, or `github-collaborators/` directory:

```
github-issues/
├── issues_batch_001.json
├── issues_batch_002.json
├── metadata.json
└── resume_state.json
```

Each batch file contains:

```json
{
  "metadata": {
    "repository": "spring-projects/spring-ai",
    "collectedAt": "2024-01-15T10:30:00Z",
    "batchNumber": 1,
    "totalInBatch": 100
  },
  "issues": [
    {
      "number": 1234,
      "title": "Issue title",
      "state": "open",
      "author": { "login": "username" },
      "labels": [{ "name": "bug" }],
      "body": "Issue description...",
      "comments": [...],
      "createdAt": "2024-01-10T08:00:00Z",
      "updatedAt": "2024-01-14T15:30:00Z"
    }
  ]
}
```

## License

Apache License 2.0
