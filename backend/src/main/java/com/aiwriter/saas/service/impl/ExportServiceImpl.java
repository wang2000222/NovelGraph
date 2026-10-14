package com.aiwriter.saas.service.impl;

import static com.aiwriter.persistence.Store.*;

import com.aiwriter.common.Problem;
import com.aiwriter.persistence.Store;
import com.aiwriter.saas.service.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 确定性模板导出：权限过滤先于遍历，不调用任何模型。 */
@Service
public class ExportServiceImpl implements ExportService {
  private final Store db;
  private final WorldService world;
  private final WorkspaceService spaces;

  public ExportServiceImpl(Store d, WorldService w, WorkspaceService s) {
    db = d;
    world = w;
    spaces = s;
  }

  @Transactional
  public Map<String, Object> preview(long project, Map<String, Object> b) {
    Map<String, Object> p = spaces.project(project, "EXPORT");
    db.query("SELECT id FROM saas_tenant WHERE id=? FOR UPDATE", p.get("tenant_id"));
    db.query(
        "SELECT id FROM ng_project WHERE tenant_id=? AND id=? FOR UPDATE",
        p.get("tenant_id"),
        project);
    p = spaces.project(project, "EXPORT");
    if (b.containsKey("baseContentRevision")
        && num(b, "baseContentRevision", -1) != num(p, "revision", 0))
      throw new Problem(409, "EXPORT_VERSION", "内容已变化，请重新预览");
    String perspective = str(b, "perspective", "READER"), format = str(b, "format", "MARKDOWN");
    if (!Arrays.asList("AUTHOR", "READER").contains(perspective)
        || !Arrays.asList("MARKDOWN", "JSON", "COMPACT", "BACKUP").contains(format))
      throw new Problem(400, "EXPORT_FORMAT", "导出选项无效");
    boolean author = perspective.equals("AUTHOR");
    Map<String, Object> scope = db.obj(b.get("scope"));
    long chapter = num(scope, "chapterTo", 0), from = num(scope, "chapterFrom", 1);
    if (!author && chapter < 1) throw new Problem(400, "CHAPTER_REQUIRED", "读者模式需指定截至章节");
    if (from < 1 || (!author && from > chapter)) throw new Problem(400, "CHAPTER_RANGE", "章节范围无效");
    boolean includeSecrets = Boolean.TRUE.equals(b.get("includeSecrets"));
    if (format.equals("BACKUP")) {
      spaces.project(project, "SECRET");
      author = true;
      includeSecrets = true;
    }
    Map<String, Object> g = world.graph(project);
    Map<String, Object> report = world.validate(project);
    if (!format.equals("BACKUP") && !Boolean.TRUE.equals(report.get("passed")))
      throw new Problem(422, "EXPORT_CONFLICT", "设定存在冲突，请先检查并修正");
    if (format.equals("BACKUP")) {
      for (String k : WorldServiceImpl.TABLES.keySet())
        g.put(
            k,
            db.query(
                "SELECT * FROM "
                    + WorldServiceImpl.TABLES.get(k)
                    + " WHERE tenant_id=? AND project_id=? ORDER BY id",
                p.get("tenant_id"),
                project));
    } else {
      for (String k : WorldServiceImpl.TABLES.keySet()) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> x : db.list(g.get(k))) {
          if (num(x, "deleted", 0) != 0) continue;
          if (k.equals("events")
              && !str(scope, "branch", "").isEmpty()
              && !str(scope, "branch", "").equals(db.obj(x.get("data")).get("branch"))) continue;
          if (k.equals("secrets") && !includeSecrets) continue;
          boolean visible =
              author
                  || ("PUBLIC".equals(x.get("visibility")) && !k.equals("secrets"))
                  || (num(x, "reveal_chapter", Long.MAX_VALUE) <= chapter
                      && !"AUTHOR_ONLY".equals(x.get("visibility")));
          if (!visible) continue;
          if (k.equals("events") && num(x, "reveal_chapter", from) < from) continue;
          if (k.equals("events")
              && scope.get("storyFrom") != null
              && (x.get("story_seq") == null
                  || num(x, "story_seq", 0) < num(scope, "storyFrom", 0))) continue;
          if (k.equals("events")
              && scope.get("storyTo") != null
              && (x.get("story_seq") == null || num(x, "story_seq", 0) > num(scope, "storyTo", 0)))
            continue;
          out.add(x);
        }
        g.put(k, out);
      }
      world.prune(g);
      Set<Long> selected = idSet(scope.get("characterIds"));
      long depth = num(scope, "relationDepth", 1);
      if (depth < 0 || depth > 2) throw new Problem(400, "DEPTH_INVALID", "关系层数0至2");
      if (!selected.isEmpty()) {
        selected.retainAll(ids(g, "characters"));
        for (int i = 0; i < depth; i++) {
          Set<Long> next = new HashSet<>(selected);
          for (Map<String, Object> r : db.list(g.get("relations"))) {
            long a = num(r, "source_id", 0), z = num(r, "target_id", 0);
            if (selected.contains(a)) next.add(z);
            if (selected.contains(z)) next.add(a);
          }
          selected = next;
        }
        final Set<Long> chars = selected;
        g.put(
            "characters",
            db.list(g.get("characters")).stream()
                .filter(x -> chars.contains(num(x, "id", 0)))
                .collect(Collectors.toList()));
        Set<Long> participating =
            db.list(g.get("participants")).stream()
                .filter(x -> chars.contains(num(x, "character_id", 0)))
                .map(x -> num(x, "event_id", 0))
                .collect(Collectors.toSet());
        g.put(
            "events",
            db.list(g.get("events")).stream()
                .filter(x -> participating.contains(num(x, "id", 0)))
                .collect(Collectors.toList()));
      }
      Set<Long> eventIds = idSet(scope.get("eventIds"));
      if (!eventIds.isEmpty())
        g.put(
            "events",
            db.list(g.get("events")).stream()
                .filter(x -> eventIds.contains(num(x, "id", 0)))
                .collect(Collectors.toList()));
      world.prune(g);
      if (scope.get("storyTo") != null) {
        long seq = num(scope, "storyTo", 0);
        g.put(
            "relations",
            db.list(g.get("relations")).stream()
                .filter(
                    x ->
                        (x.get("start_seq") == null || num(x, "start_seq", 0) <= seq)
                            && (x.get("end_seq") == null || num(x, "end_seq", 0) > seq))
                .collect(Collectors.toList()));
        List<Map<String, Object>> changes = db.list(g.get("changes"));
        changes.sort(Comparator.comparingLong(x -> num(x, "effective_seq", 0)));
        for (Map<String, Object> change : changes)
          if (num(change, "effective_seq", 0) <= seq)
            for (Map<String, Object> r : db.list(g.get("relations")))
              if (num(r, "id", 0) == num(change, "relation_id", 0)) {
                Map<String, Object> state = db.obj(db.obj(change.get("data")).get("afterState"));
                if (state.containsKey("name")) r.put("name", state.get("name"));
                if (state.containsKey("description"))
                  r.put("description", state.get("description"));
              }
        world.prune(g);
      }
    }
    Map<String, Object> publicProject =
        map(
            "id",
            project,
            "title",
            p.get("title"),
            "overview",
            p.get("overview"),
            "style",
            p.get("style"),
            "genre",
            p.get("genre"),
            "constraints",
            p.get("constraints"));
    g.put("project", publicProject);
    g.put("packageType", format.equals("BACKUP") ? "AUTHOR_BACKUP" : "PARTIAL");
    g.put(
        "scope",
        map(
            "perspective",
            author ? "AUTHOR" : "READER",
            "chapterTo",
            chapter,
            "revision",
            p.get("revision")));
    if (!format.equals("BACKUP")) g.remove("views");
    for (String k : WorldServiceImpl.TABLES.keySet())
      for (Map<String, Object> x : db.list(g.get(k))) {
        x.remove("tenant_id");
        x.remove("project_id");
        x.remove("created_by");
        x.remove("created_at");
        x.remove("updated_at");
      }
    String text =
        format.equals("JSON") || format.equals("BACKUP")
            ? db.json(g)
            : markdown(g, format.equals("COMPACT"), str(b, "writingTask", ""));
    spaces.audit(num(p, "tenant_id", 0), "EXPORT_" + format, project);
    return map(
        "content",
        text,
        "characters",
        text.codePointCount(0, text.length()),
        "revision",
        p.get("revision"),
        "format",
        format,
        "containsUnrevealed",
        author,
        "pack",
        g);
  }

  private Set<Long> idSet(Object raw) {
    Set<Long> out = new HashSet<>();
    if (raw instanceof List) for (Object x : (List<?>) raw) out.add(Long.parseLong(x.toString()));
    return out;
  }

  private Set<Long> ids(Map<String, Object> g, String k) {
    return db.list(g.get(k)).stream().map(x -> num(x, "id", 0)).collect(Collectors.toSet());
  }

  private String plain(Object value) {
    return value == null ? "" : value.toString();
  }

  /** 提示词只显示可见资料的名称，禁止通过关联引用泄露被过滤的设定。 */
  private String referenceName(Map<String, Object> graph, String kind, Object id) {
    for (Map<String, Object> row : db.list(graph.get(kind)))
      if (Objects.equals(plain(row.get("id")), plain(id))) return plain(row.get("name"));
    return "未纳入本次导出范围";
  }

  private String referenceKind(String section, String key) {
    switch (key) {
      case "source_id":
      case "target_id":
        return section.equals("dependencies") ? "events" : "characters";
      case "event_id":
      case "anchor_id":
        return "events";
      case "character_id":
        return "characters";
      case "relation_id":
        return "relations";
      case "location_id":
      case "parent_id":
        return "locations";
      case "faction_id":
        return "factions";
      default:
        return null;
    }
  }

  private String fieldLabel(String key) {
    Map<String, Object> labels =
        map(
            "status",
            "任务状态",
            "objective",
            "任务目标",
            "outcome",
            "任务结果",
            "evidence",
            "原文依据",
            "sourceChapter",
            "来源章节",
            "identity",
            "身份",
            "personality",
            "性格",
            "goals",
            "目标与动机",
            "background",
            "公开背景",
            "tags",
            "标签 / 支线",
            "knowledge",
            "知情备注",
            "branch",
            "剧情支线",
            "relativeOrder",
            "相对先后说明",
            "beforeState",
            "变化前状态",
            "afterState",
            "变化后状态",
            "name",
            "名称",
            "description",
            "说明",
            "source_id",
            "起点",
            "target_id",
            "终点",
            "event_id",
            "事件",
            "character_id",
            "人物",
            "role",
            "角色",
            "time_kind",
            "时间精度",
            "time_text",
            "时间表达",
            "starts_at",
            "开始时间",
            "ends_at",
            "结束时间",
            "story_seq",
            "故事序号",
            "type",
            "因果类型",
            "direction",
            "关系方向",
            "start_seq",
            "生效故事序号",
            "end_seq",
            "结束故事序号",
            "relation_id",
            "关系",
            "effective_seq",
            "生效故事序号",
            "location_id",
            "地点",
            "anchor_id",
            "锚定事件",
            "parent_id",
            "上级地点",
            "faction_id",
            "势力");
    // 自定义属性名来自作者，原样保留；系统内置字段统一中文化。
    return str(labels, key, key);
  }

  private String displayValue(Object value) {
    if (value == null) return "";
    if (value instanceof Map) {
      StringJoiner parts = new StringJoiner("；");
      for (Map.Entry<String, Object> field : db.obj(value).entrySet()) {
        String text = displayValue(field.getValue());
        if (!text.isEmpty()) parts.add(fieldLabel(field.getKey()) + "：" + text);
      }
      return parts.toString();
    }
    if (value instanceof List) {
      StringJoiner parts = new StringJoiner("、");
      for (Object item : (List<?>) value) {
        String text = displayValue(item);
        if (!text.isEmpty()) parts.add(text);
      }
      return parts.toString();
    }
    if (value instanceof Boolean) return Boolean.TRUE.equals(value) ? "是" : "否";
    return plain(value);
  }

  private String enumValue(Object value) {
    return str(
        map(
            "DIRECTED",
            "单向",
            "BIDIRECTIONAL",
            "双向",
            "BEFORE",
            "先于",
            "CAUSES",
            "导致",
            "ENABLES",
            "促成",
            "EXACT",
            "精确时间",
            "RANGE",
            "时间范围",
            "FUZZY",
            "模糊时间",
            "RELATIVE",
            "相对时间",
            "UNKNOWN",
            "未确定"),
        plain(value),
        plain(value));
  }

  private String markdown(Map<String, Object> g, boolean compact, String task) {
    StringBuilder s = new StringBuilder();
    Map<String, Object> p = db.obj(g.get("project"));
    s.append("# 小说设定：").append(plain(p.get("title"))).append("\n\n");
    s.append("你是一名小说创作助手，请依据作者确认的设定完成写作任务。\n\n");
    s.append("模式：")
        .append("AUTHOR".equals(db.obj(g.get("scope")).get("perspective")) ? "作者模式" : "读者模式")
        .append("；截至章节：")
        .append(db.obj(g.get("scope")).get("chapterTo"))
        .append("\n");
    s.append("世界概述：")
        .append(plain(p.get("overview")))
        .append("\n风格：")
        .append(plain(p.get("style")))
        .append("\n");
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put("rules", "世界规则");
    labels.put("tasks", "剧情任务与目标");
    labels.put("characters", "人物档案");
    labels.put("relations", "人物关系");
    labels.put("events", "事件时间线");
    labels.put("participants", "事件参与者及知情备注");
    labels.put("dependencies", "事件先后与因果");
    labels.put("changes", "关系变化历史");
    labels.put("locations", "地点");
    labels.put("factions", "势力");
    labels.put("factionMembers", "势力成员");
    labels.put("secrets", "本次允许导出的秘密");
    for (Map.Entry<String, String> section : labels.entrySet()) {
      List<Map<String, Object>> rows = db.list(g.get(section.getKey()));
      if (rows.isEmpty()) continue;
      if (section.getKey().equals("events"))
        rows.sort(Comparator.comparingLong(x -> num(x, "story_seq", Long.MAX_VALUE)));
      s.append("\n## ").append(section.getValue()).append("\n");
      for (Map<String, Object> x : rows) {
        s.append("- ").append(x.get("name"));
        if (!plain(x.get("description")).trim().isEmpty())
          s.append("：").append(x.get("description"));
        for (String key :
            Arrays.asList(
                "source_id",
                "target_id",
                "event_id",
                "character_id",
                "role",
                "time_kind",
                "time_text",
                "starts_at",
                "ends_at",
                "story_seq",
                "type",
                "direction",
                "start_seq",
                "end_seq",
                "relation_id",
                "effective_seq",
                "location_id",
                "anchor_id",
                "parent_id",
                "faction_id")) {
          Object value = x.get(key);
          if (value == null || plain(value).trim().isEmpty()) continue;
          String target = referenceKind(section.getKey(), key);
          String readable =
              target == null
                  ? (Arrays.asList("type", "direction", "time_kind").contains(key)
                      ? enumValue(value)
                      : displayValue(value))
                  : referenceName(g, target, value);
          s.append(compact ? "；" : "\n  - ").append(fieldLabel(key)).append("：").append(readable);
        }
        for (Map.Entry<String, Object> field : db.obj(x.get("data")).entrySet()) {
          String readable = displayValue(field.getValue());
          if (!readable.trim().isEmpty())
            s.append(compact ? "；" : "\n  - ")
                .append(fieldLabel(field.getKey()))
                .append("：")
                .append(readable);
        }
        s.append("\n");
      }
    }
    s.append(
            "\n## 创作约束\n1. 不得擅自改变已确认的身份、关系、事件与时间顺序。\n2. 不得让人物提前知道尚未获知的事实；参与不等于完全知情。\n3. 缺失设定不能当成已确认事实，修改设定应先提出建议。\n4. 发现矛盾应先请求作者确认。\n")
        .append(plain(p.get("constraints")))
        .append("\n\n## 本次写作任务\n")
        .append(task);
    return s.toString();
  }
}
