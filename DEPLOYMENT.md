# GitHub CI/CD 部署说明

本项目使用 GitHub Actions 完成以下流程：

- `CI`：推送或创建 Pull Request 时，运行前后端测试、前端构建，并验证三个 Docker 镜像可以构建。
- `CD`：代码合并到 `master` 后，重新测试，发布 `backend`、`frontend`、`mysql` 镜像到 GHCR，再部署到 Kubernetes。

## 1. 准备 Kubernetes

目标集群需要满足：

- 服务器已经注册带 `secondhand` 标签的 GitHub self-hosted Runner，由它在本机访问 Kubernetes API。
- 集群有默认 StorageClass，能够为 MySQL 和上传目录创建 PVC。
- 节点可访问 `ghcr.io`、Docker Hub 和 Maven/npm 软件源。

部署后前端通过 Kubernetes 节点的 `30080` 端口访问。正式公网环境建议再配置 Ingress、域名和 HTTPS。

## 2. 配置 GitHub Environment Secrets

打开仓库的 **Settings → Environments → New environment**，创建 `production`，然后添加：

| Secret | 用途 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `MYSQL_PASSWORD` | 应用专用数据库用户 `secondhand` 的密码 |
| `JWT_SECRET` | JWT 签名密钥，建议使用至少 32 字节随机值 |
| `GHCR_USERNAME` | 有权读取本仓库 GHCR 镜像的 GitHub 用户名 |
| `GHCR_TOKEN` | classic PAT，至少包含 `read:packages` 权限 |

建议为 `production` Environment 配置 required reviewers，避免未经确认直接上线。

## 3. 推送并启用流水线

当前开发分支可先推到 GitHub：

```bash
git add .github k8s/production.yaml DEPLOYMENT.md backend/.dockerignore frontend/.dockerignore db/.dockerignore
git commit -m "ci: add GitHub Actions CI/CD"
git push -u origin ci/cd
```

在 GitHub 创建 Pull Request，等待 `CI` 通过后合并到 `master`。合并会自动触发 `CD`；也可在 **Actions → CD → Run workflow** 手动执行。

推荐在 **Settings → Branches → Branch protection rules** 中保护 `master`，要求 Pull Request 和 CI 检查通过后才能合并。

## 4. 查看部署状态

```bash
kubectl -n secondhand get pods,svc,pvc
kubectl -n secondhand get service frontend
kubectl -n secondhand logs deployment/backend --tail=100
```

访问地址为 `http://<任一 Kubernetes 节点 IP>:30080`。

## 5. 安全注意事项

- 不要把数据库密码、JWT 密钥或 kubeconfig 提交到 Git。
- 仓库原有的 `k8s/deployment.yaml` 含明文数据库密码；在推到公开仓库前，应删除该明文并立即轮换已经暴露的密码。
- GHCR 镜像默认可能是私有的，因此生产清单使用 `ghcr-pull` Secret 拉取镜像。
- 数据库初始化 SQL 只会在 MySQL 数据目录首次创建时执行；后续结构变更应使用 Flyway 或 Liquibase 迁移。
