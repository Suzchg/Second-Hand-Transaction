"""汇总 Surefire/Failsafe 原始 XML；失败、跳过或缺少必测场景时返回非零状态。"""
import argparse
from datetime import datetime, timezone
from pathlib import Path
import xml.etree.ElementTree as ET


REQUIRED_API_SUITES = {
    "AuthFlowIT", "ProductApiIT", "PurchaseApiIT", "ShippingApiIT",
    "OfferApiIT", "AfterSaleApiIT", "ReportApiIT",
}


def cell(value):
    # 转义表格分隔符和换行，避免错误消息破坏 Markdown 表格。
    return str(value).replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def summarize(target):
    # 分别收集单元/E2E 与集成/API 结果，不把缺失的测试当成通过。
    suites, problems, properties = [], [], {}
    for directory in ("surefire-reports", "failsafe-reports"):
        paths = sorted((target / directory).glob("TEST-*.xml"))
        if not paths:
            problems.append(f"缺少 {directory}/TEST-*.xml；未运行不能算通过。")
        for path in paths:
            try:
                root = ET.parse(path).getroot()
            except (ET.ParseError, OSError) as exc:
                problems.append(f"无法读取 {path.name}: {exc}")
                continue
            cases = root.findall("testcase")
            if not cases:  # JUnit 嵌套类的容器节点不重复计入用例总数。
                continue
            properties.update({p.get("name"): p.get("value") for p in root.findall("properties/property")})
            total = len(cases)
            failures = sum(c.find("failure") is not None for c in cases)
            errors = sum(c.find("error") is not None for c in cases)
            skipped = sum(c.find("skipped") is not None for c in cases)
            suites.append((root.get("name", path.stem), total, total - failures - errors - skipped,
                           failures, errors, skipped, cases))
    # 七个业务场景都必须有实际执行的测试结果。
    found = {s[0].rsplit(".", 1)[-1] for s in suites}
    missing = sorted(REQUIRED_API_SUITES - found)
    if missing:
        problems.append("缺少必须执行的集成/API 测试类：" + ", ".join(missing))
    lines = ["# 自动测试执行报告", "", f"生成时间（UTC）：{datetime.now(timezone.utc).isoformat(timespec='seconds')}",
             "", "来源：本次构建的 Surefire/Failsafe XML。请先 clean verify，避免混入旧结果。", "",
             "## 运行环境", "", "| 项目 | 值 |", "|---|---|"]
    for key in ("java.version", "java.vendor", "os.name", "os.version", "os.arch", "file.encoding"):
        lines.append(f"| {key} | {cell(properties.get(key, '未知（未生成测试 XML）'))} |")
    lines += ["| 数据库 | Testcontainers mysql:8.0 临时容器（非 H2） |",
              "| API 测试链路 | MockMvc + Spring Security + Controller + Service + JPA + MySQL |",
              "| E2E 链路 | 随机端口 + JDK HttpClient + 完整后端 + MySQL |",
              "| 外部服务边界 | 物流为项目 MockLogisticsProvider；不连接真实支付/快递服务 |", "",
              "## 结果统计", "", "| 测试类 | 总数 | 通过 | 断言失败 | 执行错误 | 跳过 |", "|---|---:|---:|---:|---:|---:|"]
    totals = [0] * 5
    for name, total, passed, failed, errors, skipped, _ in suites:
        counts = [total, passed, failed, errors, skipped]
        totals = [a + b for a, b in zip(totals, counts)]
        lines.append("| " + cell(name) + " | " + " | ".join(map(str, counts)) + " |")
    lines.append("| **总计** | " + " | ".join(map(str, totals)) + " |")
    lines += ["", "## 失败原因 / 未执行项", ""]
    # 保留断言失败、运行错误和跳过原因，便于定位流水线失败。
    reasons = list(problems)
    for name, *_, cases in suites:
        for case in cases:
            for tag in ("failure", "error", "skipped"):
                node = case.find(tag)
                if node is not None:
                    detail = node.get("message") or node.text or tag
                    reasons.append(f"{name}.{case.get('name')}: {tag}: {detail}")
    lines.extend(["- " + cell(reason) for reason in reasons] or ["无。"])
    lines += ["", "## 集成/API 用例执行明细", "", "| 测试类 | 用例（参数化展开） | 结果 |", "|---|---|---|"]
    for name, *_, cases in suites:
        if name.rsplit(".", 1)[-1] not in REQUIRED_API_SUITES:
            continue
        for case in cases:
            result = "通过" if all(case.find(tag) is None for tag in ("failure", "error", "skipped")) else "未通过"
            lines.append(f"| {cell(name.rsplit('.', 1)[-1])} | {cell(case.get('name'))} | {result} |")
    # 任一失败、错误、跳过或报告缺失都会使最终结果不通过。
    return "\n".join(lines) + "\n", bool(problems or any(totals[2:]))


def main():
    # 输出报告后保留失败退出码，供 CI/CD 门禁使用。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", type=Path, default=Path(__file__).resolve().parents[1] / "backend" / "target")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report, failed = summarize(args.target)
    output = args.output or args.target / "test-report.md"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(report, encoding="utf-8")
    print(f"Report: {output}; status: {'FAILED / INCOMPLETE' if failed else 'PASSED'}")
    return int(failed)


if __name__ == "__main__":
    raise SystemExit(main())
