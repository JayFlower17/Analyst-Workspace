from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from enum import Enum




class ExecutorRequest(BaseModel):
    task_type: str  # "sql" or "python"
    sql: Optional[str] = None
    python_code: Optional[str] = None
    duckdb_path: str
    table_name: Optional[str] = None


class ExecutorResponse(BaseModel):
    success: bool
    error: Optional[str] = None
    data: Optional[List[Dict[str, Any]]] = None
    summary: Optional[str] = None
