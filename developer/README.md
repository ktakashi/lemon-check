# LemonCheck Developer Guide

This guide provides comprehensive documentation for developers who want to understand, extend, or contribute to LemonCheck.

## Table of Contents

1. [Architecture](architecture.md)
2. [Module Structure](modules.md)
3. [Scenario File Syntax](scenario-syntax.md)
4. [Class Hierarchy](class-hierarchy.md)
5. [Auto-Test Extensibility](auto-test-extensibility.md)
6. [Development Tools](tools.md)
7. [Contributing](contributing.md)

## Overview

LemonCheck is an OpenAPI-driven BDD-style API testing library for Kotlin and Java. It enables developers to write human-readable test scenarios that are automatically validated against OpenAPI specifications.

## Key Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.3.20 | Primary language |
| Java | 21 | Target JVM version |
| JUnit Platform | 6.0.3 | Test engine integration |
| Spring Boot | 4.0.5 | Spring context integration |
| Swagger Parser | 2.1.39 | OpenAPI parsing |
| Jackson | 3.1.1 | JSON processing |
| JSONPath | 3.0.0 | Response data extraction |
| json-schema-validator | 3.0.1 | Schema validation |

## Quick Links

- [Architecture Overview](architecture.md) - Understand the high-level design
- [Scenario Syntax Reference](scenario-syntax.md) - Learn the scenario file format
- [Auto-Test Extensibility](auto-test-extensibility.md) - Create custom test providers
- [Class Hierarchy](class-hierarchy.md) - Navigate the codebase
- [Development Tools](tools.md) - Set up your development environment
