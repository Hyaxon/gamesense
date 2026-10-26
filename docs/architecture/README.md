# Architecture Documentation

This directory contains system architecture diagrams, data flow models, API specifications, and high-level technical designs for the GameSense platform.

## High-Level Component Structure

GameSense/
│
├── frontend/          # React (TypeScript/JavaScript) User Interface
├── backend/           # Java Core API & Data Handling Services
└── prediction/        # Python Data Processing & Prediction Models

## Core Layers

1. **Frontend (JavaScript/React):** Manages user interaction, displays game rankings, win/loss percentages, and visual simulations.
2. **Backend (Java):** Handles routing, data ingestion pipelines, structural backend logic, and communication between the UI and prediction engine.
3. **Prediction Engine (Python):** Ingests historical and current season college football data to run statistical models (Elo, Glicko-2, machine learning, etc.) and compute predictions.

## Contents

- [Shared Domain Model](domain-model.md): the domain entities and data schema shared across the frontend, backend, and prediction service.
- [Ingestion Notes](ingestion-notes.md): how Adam's data feed maps to the domain model, for whoever builds ingestion.
- [Core API JSON Schemas](api-schemas.md): proposed payload contracts, examples, validation rules, and deferred additions.
- [Prediction Model Interface](prediction-model-interface.md): common model invocation, capabilities, configuration, results, and errors.
- [Prediction Model Development](prediction-model-development.md): implemented Python framework, model package structure, data access, registration, and testing.
- [Prediction Model Walkthrough](prediction-model-walkthrough.md): step-by-step implementation instructions using the working coin-flip model and an Elo adapter example.

- [Prediction Service Connectivity](prediction-service-connectivity.md): Java client, Python HTTP boundary, configuration, errors, and local testing.
