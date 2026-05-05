@echo off
echo ==========================================
echo    Kimi API 配置向导
echo ==========================================

echo.
echo 请访问 https://platform.moonshot.cn/ 注册账号并获取API密钥
echo.

set /p API_KEY="请输入您的 Moonshot API Key: "

echo.
echo 正在配置环境变量...
setx MOONSHOT_API_KEY "%API_KEY%"

echo.
echo 配置完成！
echo.
echo 您现在可以使用以下命令启动项目：
echo.
echo 1. 启动后端服务：
echo    cd backend && mvn spring-boot:run
echo.
echo 2. 启动Python执行器：
echo    cd python-executor && python app/main.py
echo.
echo 3. 启动前端应用：
echo    cd frontend-next && npm run dev
echo.
echo 或者直接运行 start.bat 启动所有服务
echo.

pause
