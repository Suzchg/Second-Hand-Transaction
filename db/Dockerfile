FROM mysql:8.0
# 将初始化脚本放入官方镜像指定的特殊目录，容器启动时会自动执行它
COPY init.sql /docker-entrypoint-initdb.d/