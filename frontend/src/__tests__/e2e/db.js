import mysql from 'mysql2/promise'

/**
 * 直连 browser-e2e profile 对应的 MySQL 库（secondhand_e2e），用于落库断言。
 * 连接参数与 backend/src/main/resources/application-browser-e2e.yml 保持一致，
 * 均可通过 E2E_DB_* 环境变量覆盖。
 */
const config = {
  host: process.env.E2E_DB_HOST || 'localhost',
  port: Number(process.env.E2E_DB_PORT || 3306),
  user: process.env.E2E_DB_USER || 'root',
  password: process.env.E2E_DB_PASSWORD || '123000',
  database: process.env.E2E_DB_NAME || 'secondhand_e2e',
}

let pool

function getPool() {
  if (!pool) {
    pool = mysql.createPool({ ...config, waitForConnections: true, connectionLimit: 5 })
  }
  return pool
}

/** 执行 SQL，返回行数组 */
export async function query(sql, params = []) {
  const [rows] = await getPool().execute(sql, params)
  return rows
}

/** 查询单行，无结果返回 null */
export async function findOne(sql, params = []) {
  const rows = await query(sql, params)
  return rows[0] ?? null
}

/** 查询单个标量值 */
export async function scalar(sql, params = []) {
  const row = await findOne(sql, params)
  if (!row) return null
  return row[Object.keys(row)[0]]
}
