from fastapi import FastAPI

app = FastAPI(title="GameSense Prediction Service")


# Health check endpoint used to verify the service is running.
@app.get("/health")
def health_check():
    return {"status": "ok"}
