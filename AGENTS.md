# Development Guidelines

## Code Quality

- Preserve the design intent and consistency of the existing codebase.
- Prefer simple, clear solutions. Avoid unnecessary abstractions and overengineering.
- Keep changes focused on the assigned task. Avoid unrelated modifications or refactoring.
- Introduce external dependencies only when clearly justified.

## Testing

- Test observable behavior and contracts, not implementation details.
- Write meaningful tests that can detect real regressions.
- Cover relevant success, failure, and edge cases where defects are likely.
- Avoid trivial assertions, redundant tests, unnecessary mocking, and coverage-driven tests.
- Keep tests deterministic, independent, and maintainable.

## Development Workflow

- Never bypass or weaken existing architecture enforcement rules.
- Run relevant tests and verify the results before completing a task.
- Provide a concise summary of changes and verification results.