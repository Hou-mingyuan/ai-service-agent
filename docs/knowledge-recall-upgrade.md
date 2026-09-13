# 知识库召回升级评估（词法 → 向量）

> 2026-09-13。当前 `KnowledgeService` 是本地词法召回（citations 标注 `local-lexical`），
> 语义泛化能力有限。本文档评估升级路径，供后续实现立项。

## 现状与问题

- 词法匹配依赖关键词命中：同义表达（"退款" vs "退钱" / "取消订单"）召回不到。
- `knowledgeMinScore` 阈值对词法分数与对余弦分数的语义完全不同，阈值不可复用。
- 数据量小（< 数千条）时词法足够，不必提前升级。

## 推荐路径：OpenAI 兼容 Embeddings + 进程内余弦

知识库条目量级在数千以内，**无需引入独立向量库**（Qdrant/Milvus 的运维成本不划算）：

1. 新增 `EmbeddingClient`（可参照 chatbi-copilot `EmbeddingClient.java` 的实现与
   `LLM_EMBEDDINGS_*` 配置约定：base-url/api-key 复用 `LLM_*`，模型独立配置）。
2. `KnowledgeService` 初始化时批量向量化条目，缓存于进程内；条目增删时增量重建。
3. 查询时对用户问题做一次 embedding，余弦 Top-K 后再过 `knowledgeMinScore`。
4. 失败即回退词法（embedding 是增强不是依赖）。
5. 多副本部署时 embedding 缓存随副本各存一份（条目少，可接受）；向量持久化可选
   （表结构加 `embedding` 列或落本地文件，均为增量改动）。

## 不推荐

- 独立向量数据库集群：当前条目量级与单实例部署形态下收益为负。
- 把向量检索塞进 MySQL FULLTEXT：语义能力有限，且引入分词器依赖。

## 验收标准

- 固定评测集（复用 `acceptance` 流程）上：同义改写问题的召回命中率显著高于词法基线。
- `LLM_EMBEDDINGS_ENABLED=false` 时行为与现状完全一致（回退路径有测试覆盖）。
