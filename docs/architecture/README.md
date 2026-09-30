# Architecture Documentation

This directory contains system architecture diagrams, data flow models, API specifications, and high-level technical designs for the GameSense platform.

## High-Level Component Structure

GameSense/
│
├── frontend/          # React (TypeScript/JavaScript) User Interface
├── backend/           # Java Core API & Data Handling Services
└── engine/            # Python Data Processing & Prediction Models

## Core Layers
1. **Frontend (JavaScript/React):** Manages user interaction, displays game rankings, win/loss percentages, and visual simulations.
2. **Backend (Java):** Handles routing, data ingestion pipelines, structural backend logic, and communication between the UI and prediction engine.
3. **Prediction Engine (Python):** Ingests historical and current season college football data to run statistical models (Elo, Glicko-2, machine learning, etc.) and compute predictions.