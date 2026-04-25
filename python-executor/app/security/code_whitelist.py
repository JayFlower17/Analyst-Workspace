import re
from typing import Set

FORBIDDEN_IMPORTS: Set[str] = {
    'os', 'subprocess', 'sys', 'shutil', 'socket', 'pickle',
    'multiprocessing', 'threading', 'ctypes', 'importlib',
    'builtins', 'code', 'codeop', 'commands', 'compileall',
    'pty', 'popen', 'select', 'signal', 'tempfile'
}

ALLOWED_IMPORTS: Set[str] = {
    'pandas', 'numpy', 'scipy', 'sklearn', 'math', 'statistics',
    'datetime', 'json', 'collections', 'itertools', 'functools',
    're', 'decimal', 'fractions'
}

IMPORT_PATTERN = re.compile(
    r'(?:^|\n)\s*(?:import|from)\s+([\w.]+)',
    re.MULTILINE
)

FUNCTION_PATTERN = re.compile(
    r'\b(eval|exec|compile|open|__import__|getattr|setattr|delattr|input|breakpoint)\s*\('
)

DUNDER_PATTERN = re.compile(r'__\w+__')


def validate_python_code(code: str) -> tuple[bool, str]:
    """
    验证Python代码是否安全
    返回: (is_valid, error_message)
    """
    if not code or not code.strip():
        return False, "代码不能为空"

    # 检查禁止的导入
    for match in IMPORT_PATTERN.finditer(code):
        module = match.group(1).split('.')[0]
        if module in FORBIDDEN_IMPORTS:
            return False, f"禁止导入模块: {module}"
        if module not in ALLOWED_IMPORTS and not module.startswith('pandas') and not module.startswith('numpy'):
            return False, f"不允许导入模块: {module}"

    # 检查禁止的函数调用
    if FUNCTION_PATTERN.search(code):
        return False, "检测到禁止的函数调用"

    # 检查双下划线访问（允许 __name__ 和 __main__）
    for match in DUNDER_PATTERN.finditer(code):
        dunder = match.group()
        if dunder not in ('__name__', '__main__'):
            return False, f"禁止访问特殊属性: {dunder}"

    # 检查文件操作
    file_patterns = [
        r'\.read\s*\(', r'\.write\s*\(', r'\.readlines\s*\(',
        r'with\s+open', r'pathlib', r'Path\s*\('
    ]
    for pattern in file_patterns:
        if re.search(pattern, code):
            return False, "禁止文件操作"

    # 检查网络操作
    network_patterns = [
        r'requests\.', r'urllib', r'http\.client',
        r'socket\.', r'ftplib', r'smtplib'
    ]
    for pattern in network_patterns:
        if re.search(pattern, code):
            return False, "禁止网络操作"

    return True, ""
