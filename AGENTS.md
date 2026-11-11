# SPEC-Driven Development 约束文档
- 参考 Notion Page ID: 37d0ec7a4b9280bd8c84c922f57f6d3e

# 全局研发环境与行为准则
- 参考 Notion Page ID: 37d0ec7a4b928005aee8d863af5b2518

# 开源车联网研发与行为准则
- 参考 Notion Page ID: 37d0ec7a4b92802d8915c7c310e24486

# 当前项目需求文档
- 参考 Notion Page ID: 4150ec7a4b9280e1a67cc72fdbb89932
- 变更记录 Notion Page ID: 4150ec7a4b9280c5ab15cdd8f9522619

# 当前项目设计文档
- 参考 Notion Page ID: 4150ec7a4b9280759e6ded81d69e5783
- 变更记录 Notion Page ID: 4150ec7a4b9280208f5ff8be4d43a88e

# 当前项目数据库文档
- 参考 Notion Page ID: 4150ec7a4b92807c89f5cda4902cba18

# graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep — these traverse the graph's EXTRACTED + INFERRED edges instead of scanning files
- After modifying code files in this session, run `graphify update .` to keep the graph current (AST-only, no API cost)
