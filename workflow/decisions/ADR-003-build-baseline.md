# ADR-003: Part 1 build baseline

- Date: 14 October 2026, Asia/Singapore.
- Status: Implemented under the approved TECHNICAL-SPEC version-selection rule;
  teammate review pending. No specification behavior changed.
- Context: Requester implementation now authorized in small parts. No tracked
  application source/build existed; Java 25 and cached Gradle 9.1.0 are available.
- Decision: Pin Java toolchain 25, Spring Boot 4.1.1, Gradle wrapper 9.1.0 and
  JUnit 5.13.4 (platform 1.13.4). Use Gradle's native BOM support instead of an
  additional dependency-management plugin. Pin the Gradle distribution SHA-256
  from the official distribution service.
- Evidence: [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
  lists 4.1.1 as stable, supports Java 25 and Gradle 9.x. Context7 verified native
  Gradle BOM and application entry-point setup. See the Part 1 session log.
- Options: Older Boot releases would violate the latest stable scaffold rule;
  Boot's default JUnit 6 would violate the stated JUnit 5 choice. A new Gradle
  installation was unnecessary because the compatible distribution is cached.
- Security/consequences: This part runs without HTTP, database or provider
  integration; do not deploy it as the product. Add each starter only when its
  corresponding module is implemented. No secrets in application configuration.
- Approval: Implementation authorized by user, 14 October 2026; required
  teammate merge review remains pending. No merge/deployment performed.
- Supersedes: None.
