# 第一阶段：安装依赖并编译
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# 第二阶段：Nginx 部署
FROM nginx:alpine
# 将 Vite 编译出的 dist 目录放入 Nginx 的默认静态文件目录
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]