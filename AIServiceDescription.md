# 📖 AI Microservice Description

The **AI Microservice** is the intelligence layer of the Interview Preparation & Knowledge System. It orchestrates the **Content Production Pipeline**, leveraging Generative AI to assist in creating, validating, and refining high-quality interview questions and quizzes.

---

## 🎯 Business Perspective & Responsibilities

The microservice is responsible for the **Automated Content Generation and Validation** workflow. It implements a multi-stage pipeline that combines AI capabilities with human-in-the-loop oversight to ensure content quality and consistency.

### Key Responsibilities:
- **Topic Intake (Step 0)**: Validating and processing topic definitions to be used as context for question generation.
- **AI-Assisted Generation (Step 1)**: Using Large Language Models (LLMs) to generate initial drafts of interview questions, quiz answers, and explanations based on specific topics and difficulty levels.
- **Content Validation**: Automatically checking generated content for format, relevance, and basic correctness before presenting it to human reviewers.
- **Pipeline Orchestration**: Managing the state and artifacts of the multi-stage production process, ensuring that each step is verifiable and auditable.
- **Service Integration**: Fetching existing question prompts and context from the Question microservice to avoid duplication and maintain consistency.

---

## 🛠 Technology Stack

The microservice is built using a modern Kotlin-based stack with specialized AI integration:

- **Language**: [Kotlin](https://kotlinlang.org/) (JVM 25)
- **Framework**: [Spring Boot 3.5.11](https://spring.io/projects/spring-boot)
- **AI Integration**: [Spring AI 1.1.2](https://spring.io/projects/spring-ai)
- **AI Model**: [Google Gemini (GenAI)](https://deepmind.google/technologies/gemini/) via Spring AI starter.
- **API**: Spring Web (MVC) and Spring RestClient for inter-service communication.
- **Build Tool**: Maven

---

## 🏗 Architecture

The microservice follows a **domain-driven, layered architecture** designed for complex workflow management.

### Package Structure:
- `com.kdob.piq.ai.domain`: Core domain models (`Pipeline`, `GeneratedQuestion`, `TopicDefinition`) and repository interfaces. Defines the business logic of the production pipeline.
- `com.kdob.piq.ai.application.service`: Implementation of pipeline steps and business services (e.g., `Step1QuestionGenerationService`, `GeminiQuestionGenerator`).
- `com.kdob.piq.ai.infrastructure`: Technical adapters and external integrations:
    - `client`: HTTP clients for communicating with other microservices (e.g., `QuestionCatalogClient`).
    - `persistence`: Implementation of pipeline repositories.
    - `storage`: Management of intermediate artifacts and pipeline results.
    - `web`: REST controllers for managing pipeline steps and reviewing results.
    - `config`: Configuration for AI models and RestClients.

### Key Design Patterns:
- **Pipeline Pattern**: Organizing complex, multi-stage processes into discrete, manageable steps.
- **Adapter Pattern (Hexagonal)**: Isolating the core logic from external dependencies like AI models and other microservices.
- **Artifact Storage**: Decoupling the storage of large generated content artifacts from the main application state.
- **Validation Strategy**: Using dedicated validator components for both input (topics) and output (generated questions).
