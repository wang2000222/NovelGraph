package com.aiwriter.saas.service.impl;

import static com.aiwriter.persistence.Store.*;

import com.aiwriter.common.Problem;
import com.aiwriter.persistence.Store;
import com.aiwriter.saas.security.SaasContext;
import com.aiwriter.saas.service.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 事实独立存储；所有读取和写入显式限定租户、项目和版本。 */
@Service
public class WorldServiceImpl implements WorldService {
  public static final Map<String, String> TABLES = new LinkedHashMap<>();

  static {
    TABLES.put("tasks", "ng_task");
    TABLES.put("characters", "ng_character");
    TABLES.put("events", "ng_event");
    TABLES.put("relations", "ng_relation");
    TABLES.put("participants", "ng_participant");
    TABLES.put("dependencies", "ng_dependency");
    TABLES.put("changes", "ng_relation_change");
    TABLES.put("locations", "ng_location");
    TABLES.put("factions", "ng_faction");
    TABLES.put("rules", "ng_world_rule");
    TABLES.put("secrets", "ng_secret");
    TABLES.put("factionMembers", "ng_faction_member");
  }

  private final Store db;
  private final WorkspaceService spaces;
  private final GenreService genres;
  private final ThreadLocal<Map<String, Object>> batchProject = new ThreadLocal<>();
  private final ThreadLocal<Map<String, Map<Long, Map<String, Object>>>> batchRefs =
      new ThreadLocal<>();

  public WorldServiceImpl(Store db, WorkspaceService spaces, GenreService genres) {
    this.db = db;
    this.spaces = spaces;
    this.genres = genres;
  }

  public Object projects(long tenant) {
    Map<String, Object> t = spaces.access(tenant);
    if (WorkspaceServiceImpl.manager(t))
      return db.query("SELECT * FROM ng_project WHERE tenant_id=? ORDER BY id DESC", tenant);
    return db.query(
        "SELECT p.* FROM ng_project p JOIN saas_project_member m ON m.project_id=p.id AND m.tenant_id=p.tenant_id WHERE p.tenant_id=? AND m.user_id=? ORDER BY p.id DESC",
        tenant,
        SaasContext.id());
  }

  @Transactional
  public Object create(long tenant, Map<String, Object> b) {
    Map<String, Object> t = spaces.access(tenant);
    if ("BILLING".equals(t.get("role"))
        || ("MEMBER".equals(t.get("role")) && num(t, "member_can_create", 0) != 1))
      throw new Problem(403, "CREATE_DENIED", "财务角色不能创建项目");
    spaces.quota(tenant, "projects", 1);
    long id =
        db.insert(
            "ng_project",
            map(
                "tenant_id",
                tenant,
                "title",
                required(b, "title"),
                "genre",
                b.containsKey("genreId")
                    ? genres.selectedName(num(b, "genreId", 0))
                    : str(b, "genre", ""),
                "overview",
                str(b, "overview", ""),
                "style",
                str(b, "style", ""),
                "constraints",
                str(b, "constraints", ""),
                "created_by",
                SaasContext.id()));
    db.insert(
        "saas_project_member",
        map(
            "tenant_id",
            tenant,
            "project_id",
            id,
            "user_id",
            SaasContext.id(),
            "role",
            "ADMIN",
            "can_export",
            1,
            "can_secrets",
            1));
    spaces.audit(tenant, "PROJECT_CREATE", id);
    return spaces.project(id, "READ");
  }

  private Map<String, Object> lock(long id, String operation) {
    Map<String, Object> p = spaces.project(id, operation);
    db.query("SELECT id FROM saas_tenant WHERE id=? FOR UPDATE", p.get("tenant_id"));
    db.query(
        "SELECT id FROM ng_project WHERE id=? AND tenant_id=? FOR UPDATE", id, p.get("tenant_id"));
    return spaces.project(id, operation);
  }

  private void revision(Map<String, Object> p, Map<String, Object> b) {
    if (num(b, "baseRevision", -1) != num(p, "revision", 0))
      throw new Problem(409, "VERSION_CONFLICT", "资料已被更新，请刷新后重试");
  }

  private void bump(Map<String, Object> p, String action, long id) {
    db.sql.update(
        "UPDATE ng_project SET revision=revision+1 WHERE id=? AND tenant_id=?",
        p.get("id"),
        p.get("tenant_id"));
    spaces.audit(num(p, "tenant_id", 0), action, id);
  }

  @Transactional
  public Object update(long id, Map<String, Object> b) {
    Map<String, Object> p = lock(id, "EDIT");
    revision(p, b);
    db.sql.update(
        "UPDATE ng_project SET title=?,genre=?,overview=?,style=?,constraints=?,revision=revision+1 WHERE id=? AND tenant_id=?",
        required(b, "title"),
        b.containsKey("genreId") ? genres.selectedName(num(b, "genreId", 0)) : str(b, "genre", ""),
        str(b, "overview", ""),
        str(b, "style", ""),
        str(b, "constraints", ""),
        id,
        p.get("tenant_id"));
    spaces.audit(num(p, "tenant_id", 0), "PROJECT_UPDATE", id);
    return spaces.project(id, "READ");
  }

  @Transactional
  public void delete(long id) {
    Map<String, Object> p = lock(id, "ADMIN");
    db.sql.update("DELETE FROM ng_project WHERE id=? AND tenant_id=?", id, p.get("tenant_id"));
    spaces.audit(num(p, "tenant_id", 0), "PROJECT_DELETE", id);
  }

  public String table(String kind) {
    if (!TABLES.containsKey(kind)) throw new Problem(404, "KIND_UNKNOWN", "资料类型不存在");
    return TABLES.get(kind);
  }

  public Map<String, Object> graph(long id) {
    Map<String, Object> p = spaces.project(id, "READ");
    boolean secrets = num(db.obj(p.get("rights")), "can_secrets", 0) == 1;
    Map<String, Object> g = rawGraph(p);
    if (!secrets) {
      for (String k : TABLES.keySet())
        g.put(
            k,
            db.list(g.get(k)).stream()
                .filter(r -> !k.equals("secrets") && !"AUTHOR_ONLY".equals(r.get("visibility")))
                .collect(Collectors.toList()));
      prune(g);
      for (Map<String, Object> view : db.list(g.get("views"))) {
        Map<String, Object> layout = db.obj(view.get("layout"));
        layout.put(
            "nodes",
            db.list(layout.get("nodes")).stream()
                .filter(
                    n ->
                        TABLES.containsKey(str(n, "kind", ""))
                            && ids(g, str(n, "kind", "")).contains(num(n, "id", 0)))
                .collect(Collectors.toList()));
        view.put("layout", db.json(layout));
      }
    }
    return g;
  }

  private Map<String, Object> rawGraph(Map<String, Object> p) {
    Map<String, Object> g = map("project", p, "schemaVersion", "1.0");
    for (String k : TABLES.keySet())
      g.put(
          k,
          db.query(
              "SELECT * FROM " + table(k) + " WHERE tenant_id=? AND project_id=? ORDER BY id",
              p.get("tenant_id"),
              p.get("id")));
    g.put(
        "views",
        db.query(
            "SELECT * FROM ng_view WHERE tenant_id=? AND project_id=? ORDER BY id",
            p.get("tenant_id"),
            p.get("id")));
    return g;
  }
  /** 过滤后再保留边，隐藏资料的ID与关系不会留在可见图中。 */
  public void prune(Map<String, Object> g) {
    Set<Long> c = ids(g, "characters"),
        e = ids(g, "events"),
        r = ids(g, "relations"),
        f = ids(g, "factions"),
        l = ids(g, "locations");
    filter(
        g,
        "relations",
        x -> c.contains(num(x, "source_id", 0)) && c.contains(num(x, "target_id", 0)));
    r = ids(g, "relations");
    final Set<Long> relations = r;
    filter(
        g,
        "participants",
        x -> e.contains(num(x, "event_id", 0)) && c.contains(num(x, "character_id", 0)));
    filter(
        g,
        "dependencies",
        x -> e.contains(num(x, "source_id", 0)) && e.contains(num(x, "target_id", 0)));
    filter(
        g,
        "changes",
        x -> relations.contains(num(x, "relation_id", 0)) && e.contains(num(x, "event_id", 0)));
    filter(
        g,
        "factionMembers",
        x -> f.contains(num(x, "faction_id", 0)) && c.contains(num(x, "character_id", 0)));
    filter(
        g,
        "tasks",
        x ->
            (x.get("character_id") == null || c.contains(num(x, "character_id", 0)))
                && (x.get("event_id") == null || e.contains(num(x, "event_id", 0))));
    for (Map<String, Object> location : db.list(g.get("locations")))
      if (!l.contains(num(location, "parent_id", 0))) location.remove("parent_id");
    for (Map<String, Object> event : db.list(g.get("events"))) {
      if (!l.contains(num(event, "location_id", 0))) event.remove("location_id");
      if (!e.contains(num(event, "anchor_id", 0))) event.remove("anchor_id");
    }
  }

  private Set<Long> ids(Map<String, Object> g, String k) {
    return db.list(g.get(k)).stream()
        .filter(x -> num(x, "deleted", 0) == 0)
        .map(x -> num(x, "id", 0))
        .collect(Collectors.toSet());
  }

  private void filter(
      Map<String, Object> g, String k, java.util.function.Predicate<Map<String, Object>> test) {
    g.put(k, db.list(g.get(k)).stream().filter(test).collect(Collectors.toList()));
  }

  private Map<String, Object> entity(Map<String, Object> p, String kind, long id) {
    List<Map<String, Object>> r =
        db.query(
            "SELECT * FROM " + table(kind) + " WHERE id=? AND tenant_id=? AND project_id=?",
            id,
            p.get("tenant_id"),
            p.get("id"));
    if (r.isEmpty()) throw new Problem(404, "ENTITY_NOT_FOUND", "资料不存在");
    return r.get(0);
  }

  private void ref(Map<String, Object> p, String kind, Object id) {
    if (id == null) return;
    Map<String, Object> r =
        batchRefs.get() == null
            ? entity(p, kind, Long.parseLong(id.toString()))
            : batchRefs.get().get(kind).get(Long.parseLong(id.toString()));
    if (r == null) throw new Problem(422, "REF_MISSING", "引用的资料不存在");
    if (num(r, "deleted", 0) == 1) throw new Problem(422, "REF_DELETED", "引用的资料已删除");
    if (batchProject.get() == null
        && (kind.equals("secrets") || "AUTHOR_ONLY".equals(r.get("visibility"))))
      spaces.project(num(p, "id", 0), "SECRET");
  }

  @Transactional
  public Object save(long project, String kind, long id, Map<String, Object> b) {
    boolean bulk = batchProject.get() != null;
    if (bulk && (id != 0 || num(batchProject.get(), "id", 0) != project))
      throw new IllegalStateException("批次仅允许当前项目新增资料");
    Map<String, Object> p = bulk ? batchProject.get() : lock(project, "EDIT");
    boolean newEntity = id == 0;
    long previousSize = 0;
    if (!bulk && (kind.equals("secrets") || "AUTHOR_ONLY".equals(b.get("visibility"))))
      spaces.project(project, "SECRET");
    if (id > 0) {
      Map<String, Object> old = entity(p, kind, id);
      previousSize = contentBytes(old);
      if (num(old, "deleted", 0) == 1) throw new Problem(409, "ENTITY_DELETED", "资料已删除，请先恢复");
      if (num(old, "revision", 0) != num(b, "revision", -1))
        throw new Problem(409, "ENTITY_CONFLICT", "资料已有新版本");
      if (kind.equals("secrets") || "AUTHOR_ONLY".equals(old.get("visibility")))
        spaces.project(project, "SECRET");
    } else if (!bulk && (kind.equals("characters") || kind.equals("events")))
      spaces.quota(num(p, "tenant_id", 0), "entities", 1);
    String visibility = str(b, "visibility", "AUTHOR_ONLY");
    if (!Arrays.asList("PUBLIC", "REVEALED", "AUTHOR_ONLY").contains(visibility))
      throw new Problem(400, "VISIBILITY", "可见性无效");
    Long reveal = b.get("revealChapter") == null ? null : num(b, "revealChapter", 0);
    if (reveal != null && reveal < 1) throw new Problem(400, "REVEAL_INVALID", "揭示章节至少1");
    Map<String, Object> v =
        map(
            "tenant_id",
            p.get("tenant_id"),
            "project_id",
            project,
            "name",
            required(b, "name"),
            "description",
            str(b, "description", ""),
            "visibility",
            visibility,
            "reveal_chapter",
            reveal,
            "data",
            db.json(db.obj(b.get("data"))),
            "created_by",
            SaasContext.id());
    if (required(b, "name").length() > 200) throw new Problem(400, "NAME_LONG", "资料名称最多200字符");
    if (str(b, "description", "").length() > 100000 || db.json(b).length() > 200000)
      throw new Problem(413, "ENTITY_TOO_LARGE", "资料内容过长，请拆分");
    switch (kind) {
      case "events":
        String time = str(b, "timeKind", "UNKNOWN");
        if (!Arrays.asList("EXACT", "RANGE", "FUZZY", "RELATIVE", "UNKNOWN").contains(time))
          throw new Problem(400, "TIME_KIND", "时间类型无效");
        put(v, b, "time_kind", "timeKind", time);
        put(v, b, "time_text", "timeText", "");
        put(v, b, "starts_at", "startsAt", null);
        put(v, b, "ends_at", "endsAt", null);
        put(v, b, "story_seq", "storySeq", null);
        put(v, b, "anchor_id", "anchorId", null);
        put(v, b, "location_id", "locationId", null);
        ref(p, "events", v.get("anchor_id"));
        ref(p, "locations", v.get("location_id"));
        if (id > 0 && num(v, "anchor_id", 0) == id)
          throw new Problem(422, "EVENT_CYCLE", "事件不能锚定自身");
        break;
      case "relations":
        put(v, b, "source_id", "sourceId", null);
        put(v, b, "target_id", "targetId", null);
        if (v.get("source_id") == null || v.get("target_id") == null)
          throw new Problem(400, "RELATION_ENDPOINT", "请选择两个人物");
        ref(p, "characters", v.get("source_id"));
        ref(p, "characters", v.get("target_id"));
        if (num(v, "source_id", 0) == num(v, "target_id", 0))
          throw new Problem(422, "REL_SELF", "人物不能关联自身");
        put(v, b, "direction", "direction", "DIRECTED");
        if (!Arrays.asList("DIRECTED", "BIDIRECTIONAL").contains(v.get("direction")))
          throw new Problem(400, "DIRECTION", "方向无效");
        put(v, b, "start_seq", "startSeq", null);
        put(v, b, "end_seq", "endSeq", null);
        if (v.get("start_seq") != null
            && v.get("end_seq") != null
            && num(v, "start_seq", 0) >= num(v, "end_seq", 0))
          throw new Problem(422, "TIME_REVERSED", "关系结束必须晚于开始");
        break;
      case "tasks":
        put(v, b, "character_id", "characterId", null);
        put(v, b, "event_id", "eventId", null);
        ref(p, "characters", v.get("character_id"));
        ref(p, "events", v.get("event_id"));
        break;
      case "participants":
        required(b, "eventId");
        required(b, "characterId");
        put(v, b, "event_id", "eventId", null);
        put(v, b, "character_id", "characterId", null);
        put(v, b, "role", "role", "参与");
        ref(p, "events", v.get("event_id"));
        ref(p, "characters", v.get("character_id"));
        if (!db.query(
                "SELECT id FROM ng_participant WHERE tenant_id=? AND project_id=? AND event_id=? AND character_id=? AND role=? AND deleted=0 AND id<>?",
                p.get("tenant_id"),
                project,
                v.get("event_id"),
                v.get("character_id"),
                v.get("role"),
                id)
            .isEmpty()) throw new Problem(409, "PARTICIPANT_DUPLICATE", "已有同角色参与记录");
        break;
      case "dependencies":
        required(b, "sourceId");
        required(b, "targetId");
        put(v, b, "source_id", "sourceId", null);
        put(v, b, "target_id", "targetId", null);
        put(v, b, "type", "type", "BEFORE");
        ref(p, "events", v.get("source_id"));
        ref(p, "events", v.get("target_id"));
        if (!Arrays.asList("BEFORE", "CAUSES", "ENABLES").contains(v.get("type")))
          throw new Problem(400, "DEPENDENCY_TYPE", "依赖类型无效");
        if (num(v, "source_id", 0) == num(v, "target_id", 0))
          throw new Problem(422, "EVENT_CYCLE", "事件不能关联自身");
        break;
      case "changes":
        required(b, "relationId");
        required(b, "eventId");
        put(v, b, "relation_id", "relationId", null);
        put(v, b, "event_id", "eventId", null);
        put(v, b, "effective_seq", "effectiveSeq", null);
        ref(p, "relations", v.get("relation_id"));
        ref(p, "events", v.get("event_id"));
        if (v.get("effective_seq") == null) throw new Problem(400, "CHANGE_TIME", "请填写生效序号");
        break;
      case "locations":
        put(v, b, "parent_id", "parentId", null);
        ref(p, "locations", v.get("parent_id"));
        break;
      case "factionMembers":
        required(b, "factionId");
        required(b, "characterId");
        put(v, b, "faction_id", "factionId", null);
        put(v, b, "character_id", "characterId", null);
        put(v, b, "role", "role", "");
        ref(p, "factions", v.get("faction_id"));
        ref(p, "characters", v.get("character_id"));
        break;
      default:
        break;
    }
    if (id == 0) id = db.insert(table(kind), v);
    else {
      List<Object> args = new ArrayList<>();
      StringJoiner fields = new StringJoiner(",");
      for (Map.Entry<String, Object> x : v.entrySet()) {
        if (Arrays.asList("tenant_id", "project_id", "created_by").contains(x.getKey())) continue;
        fields.add(x.getKey() + "=?");
        args.add(x.getValue());
      }
      args.add(id);
      args.add(p.get("tenant_id"));
      args.add(project);
      db.sql.update(
          "UPDATE "
              + table(kind)
              + " SET "
              + fields
              + ",revision=revision+1 WHERE id=? AND tenant_id=? AND project_id=?",
          args.toArray());
    }
    if (bulk) {
      Map<String, Object> saved = new LinkedHashMap<>(v);
      saved.put("id", id);
      saved.put("revision", 0);
      saved.put("deleted", 0);
      batchRefs.get().get(kind).put(id, saved);
      return saved;
    }
    if (newEntity && !Arrays.asList("characters", "events").contains(kind))
      spaces.quota(num(p, "tenant_id", 0), "associations", 0);
    if (contentBytes(v) > previousSize) spaces.quota(num(p, "tenant_id", 0), "storageBytes", 0);
    Map<String, Object> report = validateInternal(project);
    if (!Boolean.TRUE.equals(report.get("passed")))
      throw new Problem(422, "SETTING_CONFLICT", "资料存在时间、引用或因果冲突，请检查设定");
    bump(p, "ENTITY_SAVE", id);
    return entity(p, kind, id);
  }

  @Transactional
  public void batch(long project, Map<String, Object> graph, Runnable writes) {
    if (batchProject.get() != null) throw new IllegalStateException("不允许嵌套批次");
    Map<String, Object> p = lock(project, "EDIT");
    spaces.project(project, "SECRET");
    Map<String, Object> supplied = db.obj(graph.get("project"));
    if (num(supplied, "id", 0) != project || num(supplied, "revision", -1) != num(p, "revision", 0))
      throw new Problem(409, "EXTRACTION_CONFLICT", "项目已更新，请重新预览");
    Map<String, Map<Long, Map<String, Object>>> refs = new HashMap<>();
    for (String kind : TABLES.keySet()) {
      Map<Long, Map<String, Object>> rows = new HashMap<>();
      for (Map<String, Object> x : db.list(graph.get(kind)))
        if (num(x, "deleted", 0) == 0) rows.put(num(x, "id", 0), x);
      refs.put(kind, rows);
    }
    batchProject.set(p);
    batchRefs.set(refs);
    try {
      writes.run();
      // 整批新增完成后统一检查，避免每条资料重复扫描远程数据库。
      spaces.quota(num(p, "tenant_id", 0), "entities", 0);
      spaces.quota(num(p, "tenant_id", 0), "associations", 0);
      spaces.quota(num(p, "tenant_id", 0), "storageBytes", 0);
      if (!Boolean.TRUE.equals(validateInternal(project).get("passed")))
        throw new Problem(422, "SETTING_CONFLICT", "资料存在时间、引用或因果冲突，请检查设定");
      bump(p, "ENTITY_BATCH_SAVE", project);
    } finally {
      batchProject.remove();
      batchRefs.remove();
    }
  }

  private long contentBytes(Map<String, Object> row) {
    long bytes = 0;
    for (String key : Arrays.asList("name", "description", "data"))
      if (row.get(key) != null)
        bytes += row.get(key).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    return bytes;
  }

  private void put(
      Map<String, Object> v, Map<String, Object> b, String column, String key, Object fallback) {
    v.put(column, b.getOrDefault(key, fallback));
  }

  @Transactional
  public Object deleteEntity(long project, String kind, long id, Map<String, Object> b) {
    Map<String, Object> p = lock(project, "EDIT"), r = entity(p, kind, id);
    if (num(r, "revision", 0) != num(b, "revision", -1))
      throw new Problem(409, "ENTITY_CONFLICT", "请刷新资料");
    if (kind.equals("secrets") || "AUTHOR_ONLY".equals(r.get("visibility")))
      spaces.project(project, "SECRET");
    if (kind.equals("events")) {
      if (!db.query(
                  "SELECT id FROM ng_relation_change WHERE tenant_id=? AND project_id=? AND event_id=? AND deleted=0",
                  p.get("tenant_id"),
                  project,
                  id)
              .isEmpty()
          || !db.query(
                  "SELECT id FROM ng_event WHERE tenant_id=? AND project_id=? AND anchor_id=? AND deleted=0",
                  p.get("tenant_id"),
                  project,
                  id)
              .isEmpty()) throw new Problem(409, "EVENT_REFERENCED", "请先调整关系变化或相对时间引用");
    }
    if (kind.equals("locations")
        && !db.query(
                "SELECT id FROM ng_event WHERE tenant_id=? AND project_id=? AND location_id=? AND deleted=0",
                p.get("tenant_id"),
                project,
                id)
            .isEmpty()) throw new Problem(409, "LOCATION_USED", "请先调整事件地点");
    if (kind.equals("locations")
        && !db.query(
                "SELECT id FROM ng_location WHERE tenant_id=? AND project_id=? AND parent_id=? AND deleted=0",
                p.get("tenant_id"),
                project,
                id)
            .isEmpty()) throw new Problem(409, "LOCATION_USED", "请先调整子地点");
    if (kind.equals("factions")
        && !db.query(
                "SELECT id FROM ng_faction_member WHERE tenant_id=? AND project_id=? AND faction_id=? AND deleted=0",
                p.get("tenant_id"),
                project,
                id)
            .isEmpty()) throw new Problem(409, "FACTION_USED", "请先移除势力成员引用");
    mark(p, kind, "id=?", id);
    if (kind.equals("characters")) {
      mark(p, "relations", "source_id=? OR target_id=?", id, id);
      mark(p, "participants", "character_id=?", id);
      mark(p, "tasks", "character_id=?", id);
      mark(p, "factionMembers", "character_id=?", id);
      db.sql.update(
          "UPDATE ng_relation_change SET deleted=1,revision=revision+1 WHERE tenant_id=? AND project_id=? AND relation_id IN (SELECT id FROM ng_relation WHERE project_id=? AND deleted=1)",
          p.get("tenant_id"),
          project,
          project);
    }
    if (kind.equals("events")) {
      mark(p, "participants", "event_id=?", id);
      mark(p, "tasks", "event_id=?", id);
      mark(p, "dependencies", "source_id=? OR target_id=?", id, id);
    }
    if (kind.equals("relations")) mark(p, "changes", "relation_id=?", id);
    bump(p, "ENTITY_DELETE", id);
    return map("deleted", true);
  }

  private void mark(Map<String, Object> p, String k, String where, Object... params) {
    List<Object> a = new ArrayList<>(Arrays.asList(p.get("tenant_id"), p.get("id")));
    a.addAll(Arrays.asList(params));
    db.sql.update(
        "UPDATE "
            + table(k)
            + " SET deleted=1,revision=revision+1 WHERE tenant_id=? AND project_id=? AND ("
            + where
            + ")",
        a.toArray());
  }

  @Transactional
  public Object restoreEntity(long project, String kind, long id, Map<String, Object> b) {
    Map<String, Object> p = lock(project, "EDIT");
    Map<String, Object> r = entity(p, kind, id);
    if (num(r, "revision", 0) != num(b, "revision", -1))
      throw new Problem(409, "ENTITY_CONFLICT", "请刷新资料");
    if (kind.equals("secrets") || "AUTHOR_ONLY".equals(r.get("visibility")))
      spaces.project(project, "SECRET");
    db.sql.update(
        "UPDATE "
            + table(kind)
            + " SET deleted=0,revision=revision+1 WHERE id=? AND tenant_id=? AND project_id=?",
        id,
        p.get("tenant_id"),
        project);
    if (!Boolean.TRUE.equals(validateInternal(project).get("passed")))
      throw new Problem(422, "RESTORE_REF", "关联资料已删除，请先恢复引用");
    bump(p, "ENTITY_RESTORE", id);
    return entity(p, kind, id);
  }

  @Transactional
  public Object layout(long project, Map<String, Object> b) {
    Map<String, Object> p = lock(project, "EDIT");
    String key = str(b, "viewKey", "main");
    List<Map<String, Object>> views =
        db.query(
            "SELECT * FROM ng_view WHERE tenant_id=? AND project_id=? AND view_key=?",
            p.get("tenant_id"),
            project,
            key);
    Map<String, Object> v = views.isEmpty() ? map("revision", 0) : views.get(0);
    if (num(b, "baseLayoutRevision", -1) != num(v, "revision", 0))
      throw new Problem(409, "LAYOUT_CONFLICT", "布局已有新版本，请刷新");
    Map<String, Object> layout = db.obj(b.get("layout"));
    for (Map<String, Object> n : db.list(layout.get("nodes"))) {
      String type = required(n, "kind");
      if (!Arrays.asList("characters", "events", "locations", "factions").contains(type))
        throw new Problem(400, "NODE_TYPE", "节点类型无效");
      ref(p, type, n.get("id"));
      double x = Double.parseDouble(n.get("x").toString()),
          y = Double.parseDouble(n.get("y").toString());
      if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 1e7 || Math.abs(y) > 1e7)
        throw new Problem(400, "POSITION_INVALID", "坐标无效");
    }
    if (views.isEmpty())
      db.insert(
          "ng_view",
          map(
              "tenant_id",
              p.get("tenant_id"),
              "project_id",
              project,
              "view_key",
              key,
              "revision",
              1,
              "layout",
              db.json(layout)));
    else
      db.sql.update(
          "UPDATE ng_view SET layout=?,revision=revision+1 WHERE id=? AND tenant_id=? AND project_id=?",
          db.json(layout),
          v.get("id"),
          p.get("tenant_id"),
          project);
    return map("revision", num(v, "revision", 0) + 1);
  }

  @Transactional
  public Object permissions(long project, Map<String, Object> b) {
    Map<String, Object> p = lock(project, "ADMIN");
    long user = num(b, "userId", 0);
    if (db.query(
            "SELECT id FROM saas_member WHERE tenant_id=? AND user_id=?", p.get("tenant_id"), user)
        .isEmpty()) throw new Problem(400, "MEMBER_REQUIRED", "请先邀请为团队成员");
    if (Boolean.TRUE.equals(b.get("revoke"))) {
      db.sql.update(
          "DELETE FROM saas_project_member WHERE tenant_id=? AND project_id=? AND user_id=?",
          p.get("tenant_id"),
          project,
          user);
      bump(p, "PROJECT_PERMISSION_REVOKE", user);
      return map("success", true);
    }
    String role = required(b, "role");
    if (!Arrays.asList("ADMIN", "EDITOR", "VIEWER").contains(role))
      throw new Problem(400, "PROJECT_ROLE", "角色无效");
    db.sql.update(
        "DELETE FROM saas_project_member WHERE tenant_id=? AND project_id=? AND user_id=?",
        p.get("tenant_id"),
        project,
        user);
    db.insert(
        "saas_project_member",
        map(
            "tenant_id",
            p.get("tenant_id"),
            "project_id",
            project,
            "user_id",
            user,
            "role",
            role,
            "can_export",
            Boolean.TRUE.equals(b.get("canExport")) ? 1 : 0,
            "can_secrets",
            Boolean.TRUE.equals(b.get("canSecrets")) ? 1 : 0));
    bump(p, "PROJECT_PERMISSION", user);
    return map("success", true);
  }

  public Object grants(long project) {
    Map<String, Object> p = spaces.project(project, "ADMIN");
    return map(
        "members",
        db.query(
            "SELECT m.user_id,m.role,u.name,u.email FROM saas_member m JOIN saas_user u ON u.id=m.user_id WHERE m.tenant_id=? ORDER BY m.id",
            p.get("tenant_id")),
        "grants",
        db.query(
            "SELECT user_id,role,can_export,can_secrets FROM saas_project_member WHERE tenant_id=? AND project_id=?",
            p.get("tenant_id"),
            project));
  }

  @Transactional
  public void deleteSnapshot(long project, long sid) {
    Map<String, Object> p = lock(project, "EDIT");
    spaces.project(project, "SECRET");
    List<Map<String, Object>> r =
        db.query(
            "SELECT created_by FROM ng_snapshot WHERE tenant_id=? AND project_id=? AND id=?",
            p.get("tenant_id"),
            project,
            sid);
    if (r.isEmpty()) throw new Problem(404, "SNAPSHOT_MISSING", "恢复点不存在");
    if (!"ADMIN".equals(db.obj(p.get("rights")).get("role"))
        && num(r.get(0), "created_by", 0) != SaasContext.id())
      throw new Problem(403, "SNAPSHOT_OWNER", "仅可删除自己的恢复点");
    db.sql.update(
        "DELETE FROM ng_snapshot WHERE tenant_id=? AND project_id=? AND id=?",
        p.get("tenant_id"),
        project,
        sid);
    spaces.audit(num(p, "tenant_id", 0), "SNAPSHOT_DELETE", sid);
  }

  public Object snapshots(long project) {
    Map<String, Object> p = spaces.project(project, "READ");
    spaces.project(project, "SECRET");
    return db.query(
        "SELECT id,name,revision,created_by,created_at FROM ng_snapshot WHERE tenant_id=? AND project_id=? ORDER BY id DESC",
        p.get("tenant_id"),
        project);
  }

  @Transactional
  public Object snapshot(long project, Map<String, Object> b) {
    Map<String, Object> p = lock(project, "EDIT");
    spaces.project(project, "SECRET");
    spaces.quota(num(p, "tenant_id", 0), "snapshots", 1);
    long id =
        db.insert(
            "ng_snapshot",
            map(
                "tenant_id",
                p.get("tenant_id"),
                "project_id",
                project,
                "revision",
                p.get("revision"),
                "name",
                str(b, "name", "手动恢复点"),
                "content",
                db.json(rawGraph(p)),
                "created_by",
                SaasContext.id()));
    spaces.quota(num(p, "tenant_id", 0), "storageBytes", 0);
    return map("id", id);
  }

  @Transactional
  public Object restore(long project, long sid, Map<String, Object> b) {
    Map<String, Object> p = lock(project, "ADMIN");
    spaces.project(project, "SECRET");
    revision(p, b);
    List<Map<String, Object>> s =
        db.query(
            "SELECT * FROM ng_snapshot WHERE id=? AND tenant_id=? AND project_id=?",
            sid,
            p.get("tenant_id"),
            project);
    if (s.isEmpty()) throw new Problem(404, "SNAPSHOT_MISSING", "恢复点不存在");
    Map<String, Object> g = db.obj(s.get(0).get("content"));
    snapshot(project, map("name", "恢复前自动快照"));
    for (String k : TABLES.keySet()) {
      db.sql.update(
          "DELETE FROM " + table(k) + " WHERE tenant_id=? AND project_id=?",
          p.get("tenant_id"),
          project);
      for (Map<String, Object> row : db.list(g.get(k))) {
        row.put("tenant_id", p.get("tenant_id"));
        row.put("project_id", project);
        row.put("revision", num(p, "revision", 0) + 1);
        db.insert(table(k), row);
      }
    }
    db.sql.update(
        "DELETE FROM ng_view WHERE tenant_id=? AND project_id=?", p.get("tenant_id"), project);
    for (Map<String, Object> v : db.list(g.get("views"))) {
      v.remove("id");
      v.put("tenant_id", p.get("tenant_id"));
      v.put("project_id", project);
      v.put("revision", num(p, "revision", 0) + 1);
      db.insert("ng_view", v);
    }
    Map<String, Object> old = db.obj(g.get("project"));
    db.sql.update(
        "UPDATE ng_project SET title=?,overview=?,genre=?,style=?,constraints=?,revision=revision+1 WHERE tenant_id=? AND id=?",
        old.get("title"),
        old.get("overview"),
        old.get("genre"),
        old.get("style"),
        old.get("constraints"),
        p.get("tenant_id"),
        project);
    spaces.quota(num(p, "tenant_id", 0), "entities", 0);
    spaces.quota(num(p, "tenant_id", 0), "associations", 0);
    spaces.quota(num(p, "tenant_id", 0), "storageBytes", 0);
    if (!Boolean.TRUE.equals(validateInternal(project).get("passed")))
      throw new Problem(422, "SNAPSHOT_INVALID", "恢复点存在冲突");
    spaces.audit(num(p, "tenant_id", 0), "SNAPSHOT_RESTORE", sid);
    return graph(project);
  }

  @Transactional
  public Object copy(long project, Map<String, Object> b) {
    Map<String, Object> p = spaces.project(project, "EXPORT");
    spaces.project(project, "EDIT");
    spaces.project(project, "SECRET");
    Map<String, Object> g = rawGraph(p);
    g.put(
        "project",
        map(
            "title",
            str(b, "title", p.get("title") + " · 副本"),
            "overview",
            p.get("overview"),
            "genre",
            p.get("genre"),
            "style",
            p.get("style"),
            "constraints",
            p.get("constraints")));
    return importCommit(num(p, "tenant_id", 0), g);
  }

  public Object importPreview(Map<String, Object> b) {
    if (!"1.0".equals(b.get("schemaVersion")))
      throw new Problem(400, "SCHEMA_VERSION", "仅支持1.0设定包");
    int count = 0;
    for (String k : TABLES.keySet()) count += db.list(b.get(k)).size();
    if (count > 10000 || db.json(b).length() > 10000000)
      throw new Problem(413, "IMPORT_LARGE", "导入文件过大");
    required(db.obj(b.get("project")), "title");
    return map(
        "schemaVersion",
        "1.0",
        "title",
        db.obj(b.get("project")).get("title"),
        "entities",
        count,
        "mode",
        "NEW_PROJECT",
        "partial",
        !"AUTHOR_BACKUP".equals(b.get("packageType")));
  }

  @Transactional
  public Object importCommit(long tenant, Map<String, Object> b) {
    importPreview(b);
    Map<String, Object> newProject = (Map<String, Object>) create(tenant, db.obj(b.get("project")));
    long id = num(newProject, "id", 0);
    Map<String, Object> p = lock(id, "EDIT");
    Map<String, Map<Long, Long>> maps = new HashMap<>();
    for (String k : TABLES.keySet()) {
      Map<Long, Long> ids = new HashMap<>();
      maps.put(k, ids);
      for (Map<String, Object> row : db.list(b.get(k))) {
        long old = num(row, "id", 0);
        if (old < 1 || ids.containsKey(old)) throw new Problem(400, "IMPORT_ID", "资料ID重复或无效");
        Map<String, Object> v = new LinkedHashMap<>(row);
        v.put("data", db.json(db.obj(row.get("data"))));
        v.remove("id");
        v.remove("created_at");
        v.remove("updated_at");
        v.put("tenant_id", tenant);
        v.put("project_id", id);
        v.put("created_by", SaasContext.id());
        v.put("revision", 0);
        ids.put(old, db.insert(table(k), v));
      }
    }
    for (String k : TABLES.keySet())
      for (Map<String, Object> row : db.list(b.get(k))) {
        long newId = maps.get(k).get(num(row, "id", 0));
        Map<String, String> refs = references(k);
        for (Map.Entry<String, String> x : refs.entrySet()) {
          Object old = row.get(x.getKey());
          if (old != null) {
            Long nid = maps.get(x.getValue()).get(Long.parseLong(old.toString()));
            if (nid == null) throw new Problem(422, "IMPORT_REF", "文件存在缺失引用");
            db.sql.update(
                "UPDATE "
                    + table(k)
                    + " SET "
                    + x.getKey()
                    + "=? WHERE tenant_id=? AND project_id=? AND id=?",
                nid,
                tenant,
                id,
                newId);
          }
        }
      }
    for (Map<String, Object> view : db.list(b.get("views"))) {
      Map<String, Object> layout = db.obj(view.get("layout"));
      List<Map<String, Object>> nodes = new ArrayList<>();
      for (Map<String, Object> node : db.list(layout.get("nodes"))) {
        String kind = str(node, "kind", "");
        Long mapped = maps.containsKey(kind) ? maps.get(kind).get(num(node, "id", 0)) : null;
        if (mapped != null) {
          Map<String, Object> copy = new LinkedHashMap<>(node);
          copy.put("id", mapped);
          nodes.add(copy);
        }
      }
      layout.put("nodes", nodes);
      layout(
          id,
          map("viewKey", str(view, "view_key", "main"), "baseLayoutRevision", 0, "layout", layout));
    }
    long entities = db.list(b.get("characters")).size() + db.list(b.get("events")).size();
    spaces.quota(tenant, "entities", 0);
    spaces.quota(tenant, "associations", 0);
    spaces.quota(tenant, "storageBytes", 0);
    Map<String, Object> report = validateInternal(id);
    if (!Boolean.TRUE.equals(report.get("passed")))
      throw new Problem(422, "IMPORT_CONFLICT", db.json(report.get("issues")));
    bump(p, "PROJECT_IMPORT", id);
    return graph(id);
  }

  private Map<String, String> references(String k) {
    Map<String, String> r = new LinkedHashMap<>();
    switch (k) {
      case "relations":
        r.put("source_id", "characters");
        r.put("target_id", "characters");
        break;
      case "events":
        r.put("anchor_id", "events");
        r.put("location_id", "locations");
        break;
      case "tasks":
      case "participants":
        r.put("event_id", "events");
        r.put("character_id", "characters");
        break;
      case "dependencies":
        r.put("source_id", "events");
        r.put("target_id", "events");
        break;
      case "changes":
        r.put("relation_id", "relations");
        r.put("event_id", "events");
        break;
      case "locations":
        r.put("parent_id", "locations");
        break;
      case "factionMembers":
        r.put("faction_id", "factions");
        r.put("character_id", "characters");
        break;
      default:
        break;
    }
    return r;
  }

  public Map<String, Object> validate(long project) {
    Map<String, Object> result = validateInternal(project);
    Map<String, Object> p = spaces.project(project, "READ");
    if (num(db.obj(p.get("rights")), "can_secrets", 0) != 1) {
      Map<String, Object> visible = graph(project);
      List<Map<String, Object>> safe = new ArrayList<>();
      for (Map<String, Object> issue : db.list(result.get("issues")))
        if (issue.get("kind") == null
            || ids(visible, issue.get("kind").toString()).contains(num(issue, "entityId", 0)))
          safe.add(issue);
      result.put("issues", safe);
    }
    return result;
  }

  private Map<String, Object> validateInternal(long project) {
    Map<String, Object> p = spaces.project(project, "READ");
    Map<String, List<Map<String, Object>>> all = new HashMap<>();
    for (String k : TABLES.keySet())
      all.put(
          k,
          db.query(
              "SELECT * FROM " + table(k) + " WHERE tenant_id=? AND project_id=? AND deleted=0",
              p.get("tenant_id"),
              project));
    List<Map<String, Object>> issues = new ArrayList<>();
    Map<String, Set<Long>> ids = new HashMap<>();
    for (String k : TABLES.keySet())
      ids.put(k, all.get(k).stream().map(x -> num(x, "id", 0)).collect(Collectors.toSet()));
    Map<Long, List<Long>> edges = new HashMap<>(), parents = new HashMap<>();
    Set<String> changeBoundaries = new HashSet<>();
    for (String k : TABLES.keySet())
      for (Map<String, Object> x : all.get(k)) {
        for (Map.Entry<String, String> ref : references(k).entrySet())
          if (x.get(ref.getKey()) != null
              && !ids.get(ref.getValue()).contains(num(x, ref.getKey(), 0)))
            issue(issues, "REF_MISSING", "ERROR", k, x, "引用资料不存在或已删除");
        if (k.equals("secrets") && x.get("reveal_chapter") == null)
          issue(issues, "REVEAL_MISSING", "WARNING", k, x, "秘密未设定揭示章节");
        if (!Arrays.asList("PUBLIC", "REVEALED", "AUTHOR_ONLY").contains(x.get("visibility")))
          issue(issues, "VISIBILITY_INVALID", "ERROR", k, x, "可见性无效");
        if (k.equals("events")) {
          Long start = timestamp(x.get("starts_at")), end = timestamp(x.get("ends_at"));
          if (Arrays.asList("EXACT", "RANGE").contains(x.get("time_kind"))) {
            if (x.get("starts_at") != null
                && !x.get("starts_at").toString().isEmpty()
                && start == null) issue(issues, "DATE_INVALID", "ERROR", k, x, "精确时间须为ISO日期或日期时间");
            if (x.get("ends_at") != null && !x.get("ends_at").toString().isEmpty() && end == null)
              issue(issues, "DATE_INVALID", "ERROR", k, x, "结束时间格式无效");
            if (start != null && end != null && start > end)
              issue(issues, "DATE_REVERSED", "ERROR", k, x, "结束时间早于开始");
          }
        }
        if (k.equals("relations") && num(x, "source_id", 0) == num(x, "target_id", 0))
          issue(issues, "REL_SELF", "ERROR", k, x, "人物不能关联自身");
        if (k.equals("events") && x.get("story_seq") == null)
          issue(issues, "TIME_UNKNOWN", "WARNING", k, x, "故事顺序未确定");
        if (k.equals("events") && x.get("anchor_id") != null)
          edges
              .computeIfAbsent(num(x, "anchor_id", 0), z -> new ArrayList<>())
              .add(num(x, "id", 0));
        if (k.equals("locations") && x.get("parent_id") != null)
          parents
              .computeIfAbsent(num(x, "parent_id", 0), z -> new ArrayList<>())
              .add(num(x, "id", 0));
        if (k.equals("dependencies")) {
          long a = num(x, "source_id", 0), z = num(x, "target_id", 0);
          edges.computeIfAbsent(a, v -> new ArrayList<>()).add(z);
          Map<String, Object> from = find(all.get("events"), a), to = find(all.get("events"), z);
          Long fromDate = timestamp(from.get("starts_at")), toDate = timestamp(to.get("starts_at"));
          if (fromDate != null && toDate != null && fromDate > toDate)
            issue(issues, "DATE_CONFLICT", "ERROR", k, x, "先后约束与精确日期冲突");
          if (from.get("story_seq") != null
              && to.get("story_seq") != null
              && num(from, "story_seq", 0) >= num(to, "story_seq", 0))
            issue(issues, "TIME_CONFLICT", "ERROR", k, x, "先后或因果与故事序号冲突");
        }
        if (k.equals("changes")) {
          String boundary = x.get("relation_id") + ":" + x.get("effective_seq");
          if (!changeBoundaries.add(boundary))
            issue(issues, "CHANGE_BOUNDARY", "ERROR", k, x, "同一关系在同一故事序号存在重复变化");
          Map<String, Object> event = find(all.get("events"), num(x, "event_id", 0));
          if (event.get("story_seq") != null
              && num(x, "effective_seq", 0) < num(event, "story_seq", 0))
            issue(issues, "CHANGE_EARLY", "ERROR", k, x, "关系变化早于触发事件");
        }
      }
    if (cycle(edges))
      issues.add(map("ruleId", "EVENT_CYCLE", "severity", "ERROR", "message", "事件先后、因果或相对时间存在循环"));
    if (cycle(parents))
      issues.add(map("ruleId", "LOCATION_CYCLE", "severity", "ERROR", "message", "地点层级存在循环"));
    return map(
        "passed",
        issues.stream().noneMatch(x -> "ERROR".equals(x.get("severity"))),
        "issues",
        issues,
        "revision",
        p.get("revision"));
  }

  private Long timestamp(Object raw) {
    if (raw == null || raw.toString().trim().isEmpty()) return null;
    String text = raw.toString().trim().replace(' ', 'T');
    try {
      return java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli();
    } catch (Exception ignored) {
    }
    try {
      return java.time.LocalDateTime.parse(text)
          .atZone(java.time.ZoneId.of("Asia/Shanghai"))
          .toInstant()
          .toEpochMilli();
    } catch (Exception ignored) {
    }
    try {
      return java.time.LocalDate.parse(text)
          .atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))
          .toInstant()
          .toEpochMilli();
    } catch (Exception ignored) {
      return null;
    }
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, long id) {
    return rows.stream().filter(x -> num(x, "id", 0) == id).findFirst().orElse(new HashMap<>());
  }

  private void issue(
      List<Map<String, Object>> out,
      String rule,
      String severity,
      String k,
      Map<String, Object> x,
      String message) {
    out.add(
        map(
            "ruleId",
            rule,
            "severity",
            severity,
            "kind",
            k,
            "entityId",
            x.get("id"),
            "message",
            message));
  }

  private boolean cycle(Map<Long, List<Long>> edges) {
    Set<Long> done = new HashSet<>(), stack = new HashSet<>();
    for (Long id : edges.keySet()) if (visit(id, edges, done, stack)) return true;
    return false;
  }

  private boolean visit(Long id, Map<Long, List<Long>> edges, Set<Long> done, Set<Long> stack) {
    if (stack.contains(id)) return true;
    if (done.contains(id)) return false;
    stack.add(id);
    for (Long next : edges.getOrDefault(id, Collections.emptyList()))
      if (visit(next, edges, done, stack)) return true;
    stack.remove(id);
    done.add(id);
    return false;
  }
}
