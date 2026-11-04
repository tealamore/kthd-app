# KTHD Developer Guidelines & Testing Policy

This document outlines the testing policy, architecture guidelines, and test execution procedures for the KTHD project.

---

## Mandate for All New Code

> **IMPORTANT MANDATE FOR AGENTS & DEVELOPERS**:  
> Whenever new features, components, services, database models, or API endpoints are added or modified in this repository, **you MUST provide both Unit Tests and Integration Tests** whenever possible to maintain high test coverage and prevent regressions.

---

## Testing Policy & Standards

### 1. Unit Tests (`app/src/test/java/com/tealamore/kthd/`)
* **Coverage Scope**: Individual ViewModels, Fragments, DAO helpers, utility classes, and business logic.
* **Target Execution Time**: Under 5 seconds for the entire local suite.
* **Frameworks**: JUnit 4, Robolectric (`@Config(sdk = [34])`), Kotlin Coroutines Test (`runTest`, `UnconfinedTestDispatcher`).

### 2. Integration Tests (`app/src/test/java/com/tealamore/kthd/integration/`)
* **Coverage Scope**: Multi-component data flows across Room DB -> ViewModel -> Reactive Flows -> Services -> ApiClient.
* **Scenarios Covered**:
  - `TaskLifecycleIntegrationTest`: Full task lifecycle (`insert` -> `getActiveTaskFlow` -> `update` to COMPLETED -> `getCompletedTasks`).
  - `ContextSyncIntegrationTest`: Usage context payload logging in Room -> pending sync state -> updating sync status.
  - `ConsentNavigationIntegrationTest`: Consent state persistence -> Device ID retrieval -> Navigation guard behavior.

### 3. Continuous Integration (`.github/workflows/android_tests.yml`)
* **Automated Workflow**: Executes `./gradlew app:testDebugUnitTest` on every push and pull request.
* **Java Toolchain**: Enforces JDK 17 (`JavaLanguageVersion.of(17)`).

---

## Running Tests

To run the full unit and integration test suite locally via Gradle:

```bash
./gradlew app:testDebugUnitTest
```
