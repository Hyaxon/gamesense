"""Application entry point for the GameSense prediction service.

Creates the FastAPI application and exposes basic service endpoints used
to verify that the prediction service is running correctly.
"""
from fastapi import FastAPI

app = FastAPI(title="GameSense Prediction Service")

# Health check endpoint used to verify the service is running.
@app.get("/health")
def health_check():
    return {"status": "ok"}
