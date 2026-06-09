from fastapi import FastAPI

app = FastAPI(title="Agri Monitor CV Inference Service")


@app.get("/health")
async def health():
    return {"status": "ok"}
