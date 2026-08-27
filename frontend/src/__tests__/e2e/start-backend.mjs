// 由 Playwright webServer 调用的后端启动脚本。
// 通过 cmd.exe 的 `call <绝对路径>\mvnw.cmd` 启动，规避 Windows 下相对路径批处理
// 与含空格路径的引号处理问题（已验证 `call D:\...\backend\mvnw.cmd` 可用）。
import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const backendDir = path.resolve(__dirname, '..', '..', 'backend')
const mvnwPath = path.join(backendDir, 'mvnw.cmd')
const javaHome = process.env.JAVA_HOME || 'D:\\JAVA\\IDK24'

const child = spawn(
  'cmd.exe',
  ['/d', '/c', 'call', mvnwPath, '-DskipTests', 'spring-boot:run', '-Dspring-boot.run.profiles=browser-e2e'],
  {
    cwd: backendDir,
    env: { ...process.env, JAVA_HOME: javaHome },
    stdio: 'inherit',
  },
)

child.on('error', (err) => {
  console.error('[start-backend] 启动失败:', err)
  process.exit(1)
})

child.on('exit', (code) => process.exit(code ?? 0))
