#!/bin/bash

# AI Roundtable 启动脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认配置
REDIS_PORT=6379
SPRING_PORT=8080
FRONTEND_PORT=3000

# 函数定义
print_header() {
    echo -e "${BLUE}"
    echo "======================================================"
    echo "           AI Roundtable - 智能认知圆桌"
    echo "======================================================"
    echo -e "${NC}"
}

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_redis() {
    if command -v redis-cli &> /dev/null; then
        if redis-cli -p $REDIS_PORT ping &> /dev/null; then
            print_status "Redis 已在端口 $REDIS_PORT 上运行"
            return 0
        else
            print_warning "Redis 未运行"
            return 1
        fi
    else
        print_warning "未检测到 Redis"
        return 1
    fi
}

start_redis() {
    if ! check_redis; then
        print_status "尝试启动 Redis..."
        if command -v redis-server &> /dev/null; then
            redis-server --daemonize yes --port $REDIS_PORT
            sleep 2
            if check_redis; then
                print_status "Redis 启动成功"
            else
                print_error "Redis 启动失败"
                exit 1
            fi
        else
            print_error "未找到 redis-server 命令"
            print_error "请先安装 Redis: https://redis.io/download"
            exit 1
        fi
    fi
}

check_java() {
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
        MAJOR_VERSION=$(echo $JAVA_VERSION | cut -d'.' -f1)
        
        if [ "$MAJOR_VERSION" -ge "17" ]; then
            print_status "检测到 Java $JAVA_VERSION"
            return 0
        else
            print_warning "需要 Java 17+，当前版本: $JAVA_VERSION"
            return 1
        fi
    else
        print_warning "未检测到 Java"
        return 1
    fi
}

check_maven() {
    if command -v mvn &> /dev/null; then
        MVN_VERSION=$(mvn -version 2>&1 | head -n1 | cut -d' ' -f3)
        print_status "检测到 Maven $MVN_VERSION"
        return 0
    else
        print_warning "未检测到 Maven"
        return 1
    fi
}

build_project() {
    print_status "编译项目..."
    mvn clean compile -q
    if [ $? -eq 0 ]; then
        print_status "项目编译成功"
    else
        print_error "项目编译失败"
        exit 1
    fi
}

start_backend() {
    print_status "启动后端服务..."
    
    # 检查环境变量
    if [ -z "$OPENAI_API_KEY" ] && [ ! -f ".env" ]; then
        print_warning "未设置 OPENAI_API_KEY 环境变量"
        print_warning "将使用模拟响应进行演示"
    fi
    
    mvn spring-boot:run &
    BACKEND_PID=$!
    
    print_status "后端服务启动中 (PID: $BACKEND_PID)..."
    sleep 10
    
    # 检查端口是否被占用
    if lsof -Pi :$SPRING_PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_status "后端服务已在端口 $SPRING_PORT 上运行"
    else
        print_error "后端服务启动失败"
        exit 1
    fi
}

start_frontend() {
    print_status "启动前端服务..."
    
    if [ -d "frontend" ]; then
        cd frontend
        
        # 尝试使用 Python 启动
        if command -v python3 &> /dev/null; then
            print_status "使用 Python 启动前端服务..."
            python3 -m http.server $FRONTEND_PORT &
            FRONTEND_PID=$!
        elif command -v python &> /dev/null; then
            print_status "使用 Python 启动前端服务..."
            python -m SimpleHTTPServer $FRONTEND_PORT &
            FRONTEND_PID=$!
        # 尝试使用 Node.js
        elif command -v npx &> /dev/null; then
            print_status "使用 Node.js 启动前端服务..."
            npx serve -p $FRONTEND_PORT &
            FRONTEND_PID=$!
        else
            print_warning "未找到合适的前端服务器"
            print_warning "请手动在 frontend 目录下运行 HTTP 服务器"
            return 1
        fi
        
        cd ..
        sleep 2
        
        if lsof -Pi :$FRONTEND_PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
            print_status "前端服务已在端口 $FRONTEND_PORT 上运行"
        else
            print_warning "前端服务启动失败"
            return 1
        fi
    else
        print_error "frontend 目录不存在"
        return 1
    fi
}

show_usage() {
    print_status "应用已启动！"
    echo
    echo -e "${BLUE}访问地址：${NC}"
    echo "  前端界面: http://localhost:$FRONTEND_PORT"
    echo "  API 文档: http://localhost:$SPRING_PORT/airt/swagger-ui.html (如果启用)"
    echo
    echo -e "${BLUE}API 端点：${NC}"
    echo "  健康检查: http://localhost:$SPRING_PORT/airt/api/roundtable/health"
    echo "  创建会话: POST /airt/api/roundtable/sessions"
    echo
    echo -e "${YELLOW}按 Ctrl+C 停止所有服务${NC}"
    echo
}

cleanup() {
    print_status "正在停止服务..."
    
    # 停止后端
    if [ ! -z "$BACKEND_PID" ]; then
        kill $BACKEND_PID 2>/dev/null || true
    fi
    
    # 停止前端
    if [ ! -z "$FRONTEND_PID" ]; then
        kill $FRONTEND_PID 2>/dev/null || true
    fi
    
    # 停止 Redis（如果是脚本启动的）
    if [ "$STOP_REDIS" = "true" ]; then
        redis-cli -p $REDIS_PORT shutdown 2>/dev/null || true
    fi
    
    print_status "服务已停止"
    exit 0
}

show_help() {
    echo "用法: ./run.sh [选项]"
    echo
    echo "选项:"
    echo "  -h, --help      显示帮助信息"
    echo "  --no-redis      不启动 Redis（假设已运行）"
    echo "  --redis-port    Redis 端口 (默认: 6379)"
    echo "  --spring-port   Spring Boot 端口 (默认: 8080)"
    echo "  --frontend-port 前端端口 (默认: 3000)"
    echo
    echo "环境变量:"
    echo "  OPENAI_API_KEY    OpenAI API 密钥"
    echo "  ANTHROPIC_API_KEY Anthropic API 密钥"
    echo
    echo "示例:"
    echo "  ./run.sh"
    echo "  ./run.sh --no-redis"
    echo "  ./run.sh --redis-port 6380 --spring-port 8081"
}

# 主逻辑
main() {
    print_header
    
    # 检查前置条件
    if ! check_java; then
        print_error "需要 Java 17+ 才能运行此应用"
        exit 1
    fi
    
    if ! check_maven; then
        print_error "需要 Maven 3.6+ 才能运行此应用"
        exit 1
    fi
    
    # 解析命令行参数
    STOP_REDIS=false
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            --no-redis)
                STOP_REDIS=false
                shift
                ;;
            --redis-port)
                REDIS_PORT="$2"
                shift 2
                ;;
            --spring-port)
                SPRING_PORT="$2"
                shift 2
                ;;
            --frontend-port)
                FRONTEND_PORT="$2"
                shift 2
                ;;
            *)
                print_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 启动 Redis（如果需要）
    if [ "$STOP_REDIS" = "true" ]; then
        start_redis
    else
        check_redis || print_warning "Redis 未运行，某些功能可能受限"
    fi
    
    # 编译项目
    build_project
    
    # 启动服务
    start_backend
    start_frontend
    
    # 显示使用说明
    show_usage
    
    # 设置信号处理
    trap cleanup SIGINT SIGTERM
    
    # 等待用户输入
    echo "按 Enter 键查看日志，或 Ctrl+C 停止服务"
    read
    
    # 保持运行
    while true; do
        sleep 1
    done
}

# 运行主函数
main "$@"
