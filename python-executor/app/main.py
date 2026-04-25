from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import logging
import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from models.schemas import ExecutorRequest, ExecutorResponse
from executor.python_executor import execute_python

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Python Executor",
    description="安全的Python/SQL执行器",
    version="1.0.0"
)

# CORS配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health_check():
    """健康检查接口"""
    return {"status": "healthy", "service": "python-executor"}


@app.post("/execute", response_model=ExecutorResponse)
async def execute(request: ExecutorRequest):
    """执行SQL或Python代码"""
    logger.info(f"Received request: task_type={request.task_type}")
    
    try:
        if request.task_type == "python":
            if not request.python_code:
                return ExecutorResponse(
                    success=False,
                    error="Python代码不能为空"
                )
            
            if not request.table_name:
                return ExecutorResponse(
                    success=False,
                    error="表名不能为空"
                )
            
            success, data, summary, error = execute_python(
                request.python_code,
                request.duckdb_path,
                request.table_name
            )
            
            if success:
                return ExecutorResponse(
                    success=True,
                    data=data,
                    summary=summary
                )
            else:
                return ExecutorResponse(
                    success=False,
                    error=error
                )
        else:
            return ExecutorResponse(
                success=False,
                error=f"不支持的任务类型: {request.task_type}"
            )
            
    except Exception as e:
        logger.error(f"Execution error: {e}")
        return ExecutorResponse(
            success=False,
            error=str(e)
        )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
