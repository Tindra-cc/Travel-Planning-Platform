# 多阶段构建：Node 构建静态产物 → nginx 托管 + 反代 /api。
# server 上无需预装 Node/nginx，也无需本地 build。

# ---- 构建阶段 ----
FROM node:22-alpine AS build
WORKDIR /build
# 先拷 lockfile 安装依赖，源码改动不触发重装
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- 运行阶段 ----
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
