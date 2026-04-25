import duckdb
import pandas as pd
from typing import List, Dict, Any, Optional
import logging

logger = logging.getLogger(__name__)


def execute_sql(sql: str, duckdb_path: str) -> tuple[bool, Optional[List[Dict[str, Any]]], Optional[str]]:
    """
    执行SQL查询
    返回: (success, data, error_message)
    """
    try:
        conn = duckdb.connect(duckdb_path, read_only=True)
        
        # 只允许SELECT语句
        sql_upper = sql.strip().upper()
        if not sql_upper.startswith('SELECT'):
            return False, None, "只允许SELECT查询"
        
        result = conn.execute(sql).fetchdf()
        conn.close()
        
        # 转换为字典列表
        data = result.to_dict('records')
        
        # 处理特殊类型
        for row in data:
            for key, value in row.items():
                if pd.isna(value):
                    row[key] = None
                elif hasattr(value, 'isoformat'):
                    row[key] = value.isoformat()
        
        return True, data, None
        
    except Exception as e:
        logger.error(f"SQL execution error: {e}")
        return False, None, str(e)


