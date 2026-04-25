import duckdb
import pandas as pd
import numpy as np
from typing import List, Dict, Any, Optional, Tuple
import logging
import multiprocessing as mp
from queue import Empty
from security.code_whitelist import validate_python_code

logger = logging.getLogger(__name__)
MAX_ROWS = 50000
EXEC_TIMEOUT_SECONDS = 30.0

# 安全的内置函数白名单
SAFE_BUILTINS = {
    'abs': abs, 'all': all, 'any': any, 'bool': bool,
    'dict': dict, 'enumerate': enumerate, 'filter': filter,
    'float': float, 'int': int, 'len': len, 'list': list,
    'map': map, 'max': max, 'min': min, 'pow': pow,
    'print': print, 'range': range, 'reversed': reversed,
    'round': round, 'set': set, 'sorted': sorted,
    'str': str, 'sum': sum, 'tuple': tuple, 'zip': zip,
    'True': True, 'False': False, 'None': None,
}

def _execute_python_worker(
    code: str,
    duckdb_path: str,
    table_name: str,
    result_queue: "mp.Queue"
) -> None:
    try:
        with duckdb.connect(duckdb_path, read_only=True) as conn:
            total_rows = conn.execute(f"SELECT COUNT(*) FROM {table_name}").fetchone()[0]
            df = conn.execute(f"SELECT * FROM {table_name} LIMIT {MAX_ROWS}").fetchdf()

        if total_rows > MAX_ROWS:
            logger.warning(f"Table {table_name} has {total_rows} rows, truncated to {MAX_ROWS}")

        safe_globals = {
            '__builtins__': SAFE_BUILTINS,
            'pd': pd,
            'np': np,
            'pandas': pd,
            'numpy': np,
            'df': df,
            'conn': None,
        }
        safe_locals = {}
        exec(code, safe_globals, safe_locals)

        result = safe_locals.get('result', df.head(100))

        if isinstance(result, pd.DataFrame):
            data = result.to_dict('records')
        elif isinstance(result, pd.Series):
            data = result.to_frame().to_dict('records')
        elif isinstance(result, (list, dict)):
            data = result if isinstance(result, list) else [result]
        else:
            data = [{'result': str(result)}]

        data = sanitize_data(data)
        summary = safe_locals.get('summary', generate_summary(result))
        result_queue.put({
            "success": True,
            "data": data,
            "summary": summary,
            "error": None
        })
    except Exception as e:
        logger.error(f"Python execution error in worker: {e}")
        result_queue.put({
            "success": False,
            "data": None,
            "summary": None,
            "error": str(e)
        })


def execute_python(
    code: str, 
    duckdb_path: str, 
    table_name: str
) -> Tuple[bool, Optional[List[Dict[str, Any]]], Optional[str], Optional[str]]:
    """
    执行Python代码
    返回: (success, data, summary, error_message)
    """
    # 验证代码安全性
    is_valid, error_msg = validate_python_code(code)
    if not is_valid:
        return False, None, None, f"代码安全校验失败: {error_msg}"

    result_queue: "mp.Queue" = mp.Queue(maxsize=1)
    worker = mp.Process(
        target=_execute_python_worker,
        args=(code, duckdb_path, table_name, result_queue),
    )

    try:
        worker.start()
        worker.join(timeout=EXEC_TIMEOUT_SECONDS)

        if worker.is_alive():
            worker.terminate()
            worker.join()
            return False, None, None, f"代码执行超时 (超过 {int(EXEC_TIMEOUT_SECONDS)} 秒)"

        try:
            payload = result_queue.get(timeout=1)
        except Empty:
            return False, None, None, "执行器未返回结果，请重试"

        return (
            payload.get("success", False),
            payload.get("data"),
            payload.get("summary"),
            payload.get("error"),
        )
    finally:
        result_queue.close()
        result_queue.join_thread()


def sanitize_data(data: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """清理数据中的特殊类型"""
    for row in data:
        for key, value in row.items():
            if pd.isna(value):
                row[key] = None
            elif isinstance(value, (np.integer, np.floating)):
                row[key] = float(value) if np.isfinite(value) else None
            elif isinstance(value, np.ndarray):
                row[key] = value.tolist()
            elif hasattr(value, 'isoformat'):
                row[key] = value.isoformat()
    return data


def generate_summary(result) -> str:
    """生成结果摘要"""
    if isinstance(result, pd.DataFrame):
        return f"返回 {len(result)} 行 {len(result.columns)} 列的数据"
    elif isinstance(result, pd.Series):
        return f"返回 {len(result)} 个值的序列"
    elif isinstance(result, (list, tuple)):
        return f"返回 {len(result)} 个元素"
    else:
        return f"返回结果: {type(result).__name__}"
