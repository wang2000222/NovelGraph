package com.aiwriter;

import static com.aiwriter.persistence.Store.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aiwriter.common.Problem;
import com.aiwriter.persistence.Store;
import com.aiwriter.saas.security.SaasContext;
import com.aiwriter.saas.service.*;
import java.util.*;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 独立内存库验证真实 MyBatis、事务和 HTTP 权限链，不触碰部署数据。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:saas;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "saas.verify-email=false",
      "saas.admin-email=",
      "saas.admin-password=",
      "saas.mail-from=",
      "logging.file.path=target/test-logs"
    })
@AutoConfigureMockMvc
class SaasIntegrationTest {
  @Autowired Store db;
  @Autowired AuthService auth;
  @Autowired WorldService world;
  @Autowired WorkspaceService spaces;
  @Autowired ExportService export;
  @Autowired GenreService genres;
  @Autowired ExtractionService extraction;
  @Autowired MockMvc http;
  Map<String, Object> user;
  long tenant, project;
  Object originalProLimits;

  @BeforeEach
  void account() {
    originalProLimits =
        db.query("SELECT limits FROM saas_plan WHERE code='PRO'").get(0).get("limits");
    String email = "test" + UUID.randomUUID() + "@example.com";
    auth.register(map("email", email, "name", "测试作者", "password", "Test-password-123"));
    user = db.query("SELECT * FROM saas_user WHERE email=?", email).get(0);
    SaasContext.set(user);
    tenant =
        num(db.query("SELECT * FROM saas_tenant WHERE owner_id=?", user.get("id")).get(0), "id", 0);
    project = num((Map<String, Object>) world.create(tenant, map("title", "测试世界")), "id", 0);
  }

  @AfterEach
  void clear() {
    db.sql.update("UPDATE saas_plan SET limits=? WHERE code='PRO'", originalProLimits);
    SaasContext.clear();
  }

  Map<String, Object> save(String kind, Object... values) {
    Map<String, Object> b = map("name", "设定", "visibility", "PUBLIC");
    b.putAll(map(values));
    return (Map<String, Object>) world.save(project, kind, 0, b);
  }

  Problem fails(Runnable r, String code) {
    Problem e = assertThrows(Problem.class, r::run);
    assertEquals(code, e.code);
    return e;
  }

  Map<String, Object> extractionPack() {
    Map<String, Object> pack = map("版本", "1.0", "批次编号", UUID.randomUUID().toString());
    for (String k :
        Arrays.asList(
            "人物", "地点", "势力", "事件", "人物关系", "事件参与", "事件因果", "关系变化", "势力成员", "世界规则", "秘密", "剧情任务"))
      pack.put(k, new ArrayList<>());
    return pack;
  }

  Map<String, Object> extracted(String id, String name, Object... extra) {
    Map<String, Object> row =
        map(
            "编号",
            id,
            "名称",
            name,
            "说明",
            "来自原文",
            "可见性",
            "仅作者",
            "属性",
            map("原文依据", "张三到旧井寻找李四", "来源章节", 2));
    row.putAll(map(extra));
    return row;
  }

  @Test
  void extractionPreviewRollsBackAndCommitMapsReferencesIdempotently() {
    Map<String, Object> pack = extractionPack();
    pack.put(
        "人物",
        Arrays.asList(
            extracted("人物1", "张三", "属性", map("身份", "记者", "原文依据", "张三是一名记者")),
            extracted("人物2", "李四")));
    pack.put("事件", Arrays.asList(extracted("事件1", "旧井事件", "故事序号", 1, "时间精度", "未知")));
    pack.put(
        "人物关系", Arrays.asList(extracted("关系1", "朋友", "起点人物", "人物1", "终点人物", "人物2", "关系方向", "双向")));
    pack.put(
        "事件参与", Arrays.asList(extracted("参与1", "张三目击", "人物", "人物1", "事件", "事件1", "参与角色", "目击者")));
    pack.put(
        "剧情任务",
        Arrays.asList(
            extracted(
                "任务1",
                "寻找李四",
                "执行人物",
                "人物1",
                "关联事件",
                "事件1",
                "属性",
                map("任务目标", "寻找李四", "任务状态", "进行中", "原文依据", "张三到旧井寻找李四"))));
    String raw = "```json\n" + db.json(pack) + "\n```";
    Map<String, Object> preview = extraction.preview(project, map("response", raw));
    assertEquals(6, num(preview, "total", 0));
    assertEquals(
        0,
        num(
            world.graph(project).get("project") instanceof Map
                ? db.obj(world.graph(project).get("project"))
                : map(),
            "revision",
            -1));
    assertTrue(db.list(world.graph(project).get("characters")).isEmpty());
    assertTrue(db.list(world.graph(project).get("tasks")).isEmpty());
    Map<String, Object> committed =
        extraction.commit(project, map("response", raw, "baseRevision", preview.get("revision")));
    assertEquals(6, num(committed, "total", 0));
    Map<String, Object> graph = world.graph(project),
        task = db.list(graph.get("tasks")).get(0),
        character = db.list(graph.get("characters")).get(0);
    assertEquals(num(character, "id", 0), num(task, "character_id", 0));
    assertEquals("寻找李四", db.obj(task.get("data")).get("objective"));
    assertEquals("记者", db.obj(character.get("data")).get("identity"));
    assertEquals(
        true,
        extraction
            .commit(project, map("response", db.json(pack), "baseRevision", 0))
            .get("duplicate"));
    assertEquals(2, db.list(world.graph(project).get("characters")).size());
    pack.put("待确认", Arrays.asList("新疑点"));
    fails(
        () -> extraction.commit(project, map("response", db.json(pack), "baseRevision", 0)),
        "EXTRACTION_REUSED");
    world.delete(project);
    assertTrue(
        db.query("SELECT id FROM ng_extraction_batch WHERE project_id=?", project).isEmpty());
    assertTrue(db.query("SELECT id FROM ng_task WHERE project_id=?", project).isEmpty());
  }

  @Test
  void extractionRejectsInvalidReferencesAndCyclicAnchorsWithoutPartialWrites() {
    Map<String, Object> pack = extractionPack();
    pack.put("人物", Arrays.asList(extracted("人物1", "张三")));
    pack.put("事件参与", Arrays.asList(extracted("参与1", "目击", "人物", "人物1", "事件", "事件不存在")));
    fails(() -> extraction.preview(project, map("response", db.json(pack))), "EXTRACTION_REF");
    assertTrue(db.list(world.graph(project).get("characters")).isEmpty());
    pack.put("事件参与", Collections.emptyList());
    pack.put(
        "事件",
        Arrays.asList(
            extracted("事件1", "前事", "锚定事件", "事件2"), extracted("事件2", "后事", "锚定事件", "事件1")));
    fails(
        () -> extraction.commit(project, map("response", db.json(pack), "baseRevision", 0)),
        "EXTRACTION_CYCLE");
    assertTrue(db.list(world.graph(project).get("characters")).isEmpty());
  }

  @Test
  void extractionExistingReferencesAreScopedAndRevisionIsChecked() {
    Map<String, Object> c = save("characters", "name", "张三");
    Map<String, Object> pack = extractionPack();
    pack.put("剧情任务", Arrays.asList(extracted("任务1", "寻找旧井", "执行人物", "已有_人物_" + c.get("id"))));
    Map<String, Object> preview = extraction.preview(project, map("response", db.json(pack)));
    save("rules", "name", "新规则");
    fails(
        () ->
            extraction.commit(
                project, map("response", db.json(pack), "baseRevision", preview.get("revision"))),
        "EXTRACTION_CONFLICT");
    Map<String, Object> refreshedPreview =
        extraction.preview(project, map("response", db.json(pack)));
    extraction.commit(
        project, map("response", db.json(pack), "baseRevision", refreshedPreview.get("revision")));
    assertEquals(
        num(c, "id", 0), num(db.list(world.graph(project).get("tasks")).get(0), "character_id", 0));
    pack.put("批次编号", UUID.randomUUID().toString());
    pack.put("剧情任务", Arrays.asList(extracted("任务2", "其他任务", "执行人物", "已有_人物_99999999")));
    fails(() -> extraction.preview(project, map("response", db.json(pack))), "EXTRACTION_REF");
  }

  @Test
  void extractionBatchQuotaFailureRollsBackAndClearsBatchContext() {
    Map<String, Object> pack = extractionPack();
    List<Map<String, Object>> chars = new ArrayList<>();
    for (int i = 0; i < 101; i++) chars.add(extracted("人物" + i, "批次人物" + i));
    pack.put("人物", chars);
    fails(
        () -> extraction.commit(project, map("response", db.json(pack), "baseRevision", 0)),
        "QUOTA_EXCEEDED");
    assertTrue(db.list(world.graph(project).get("characters")).isEmpty());
    // 失败后同线程普通保存仍受正常的版本和权限链约束。
    Map<String, Object> c = save("characters", "name", "正常保存");
    fails(
        () ->
            world.save(
                project,
                "characters",
                num(c, "id", 0),
                map("name", "冲突", "revision", 99, "visibility", "PUBLIC")),
        "ENTITY_CONFLICT");
  }

  @Test
  void extractionUncertainOnlyPackIsPreviewableButCannotBeCommitted() {
    Map<String, Object> pack = extractionPack();
    pack.put("待确认", Arrays.asList("人物动机原文不明"));
    assertEquals(0, num(extraction.preview(project, map("response", db.json(pack))), "total", -1));
    fails(
        () -> extraction.commit(project, map("response", db.json(pack), "baseRevision", 0)),
        "EXTRACTION_EMPTY");
  }

  @Test
  void extractionPromptUsesChineseProtocolAndStrictParser() {
    Map<String, Object> generated =
        extraction.prompt(project, map("text", "张三到旧井寻找李四", "chapter", 2));
    assertTrue(generated.get("prompt").toString().contains("批次编号"));
    assertTrue(generated.get("prompt").toString().contains("张三到旧井寻找李四"));
    fails(
        () -> extraction.preview(project, map("response", "{\"版本\":\"1.0\",\"版本\":\"1.0\"}")),
        "EXTRACTION_JSON");
    fails(() -> extraction.preview(project, map("response", "{} {}")), "EXTRACTION_JSON");
    Map<String, Object> pack = extractionPack();
    Map<String, Object> row = extracted("人物1", "张三");
    row.put("属性", map("身份", "记者"));
    pack.put("人物", Arrays.asList(row));
    fails(() -> extraction.preview(project, map("response", db.json(pack))), "EXTRACTION_EVIDENCE");
    SaasContext.set(map("id", 999999L, "platform_role", "USER"));
    fails(() -> extraction.prompt(project, map("text", "文本")), "PROJECT_NOT_FOUND");
  }

  @Test
  void promptUsesChineseFieldsAndNamesWhileJsonRemainsImportable() {
    Map<String, Object> a =
        save(
            "characters",
            "name",
            "张三",
            "data",
            map(
                "identity",
                "落魄少爷",
                "personality",
                "沉稳",
                "goals",
                "成为兵王",
                "background",
                "废物",
                "tags",
                "无"));
    Map<String, Object> b = save("characters", "name", "李四");
    save(
        "relations",
        "name",
        "好友",
        "sourceId",
        a.get("id"),
        "targetId",
        b.get("id"),
        "direction",
        "BIDIRECTIONAL");
    for (String format : Arrays.asList("MARKDOWN", "COMPACT")) {
      String text =
          export
              .preview(
                  project,
                  map("format", format, "perspective", "READER", "scope", map("chapterTo", 2)))
              .get("content")
              .toString();
      assertTrue(text.contains("身份：落魄少爷"));
      assertTrue(text.contains("性格：沉稳"));
      assertTrue(text.contains("目标与动机：成为兵王"));
      assertTrue(text.contains("公开背景：废物"));
      assertTrue(text.contains("起点：张三"));
      assertTrue(text.contains("终点：李四"));
      assertTrue(text.contains("关系方向：双向"));
      assertTrue(text.contains("模式：读者模式"));
      assertFalse(text.contains("identity"));
      assertFalse(text.contains("source_id"));
      assertFalse(text.contains("[characters:"));
    }
    String json =
        export
            .preview(project, map("format", "JSON", "perspective", "AUTHOR"))
            .get("content")
            .toString();
    assertTrue(json.contains("identity"));
    assertTrue(json.contains("source_id"));
  }

  @Test
  void storyGenrePermissionsAndDisabledSelection() {
    fails(() -> genres.save(null, map("name", "非法题材")), "PLATFORM_ADMIN_REQUIRED");
    Map<String, Object> admin = new HashMap<>(user);
    admin.put("platform_role", "ADMIN");
    SaasContext.set(admin);
    com.aiwriter.entity.StoryGenre g =
        genres.save(null, map("name", "测试题材" + UUID.randomUUID(), "sortOrder", 5));
    long pid =
        num(
            (Map<String, Object>) world.create(tenant, map("title", "字典测试", "genreId", g.getId())),
            "id",
            0);
    assertEquals(g.getName(), spaces.project(pid, "READ").get("genre"));
    world.update(pid, map("title", "字典更新", "genreId", g.getId(), "baseRevision", 0));
    assertEquals(g.getName(), spaces.project(pid, "READ").get("genre"));
    genres.save(g.getId(), map("name", g.getName(), "enabled", 0));
    assertTrue(genres.list(false).stream().noneMatch(x -> x.getId().equals(g.getId())));
    fails(
        () -> world.create(tenant, map("title", "停用测试", "genreId", g.getId())),
        "GENRE_UNAVAILABLE");
    assertEquals(g.getName(), spaces.project(pid, "READ").get("genre"));
    fails(() -> genres.save(null, map("name", g.getName())), "GENRE_EXISTS");
    SaasContext.set(user);
    fails(() -> genres.list(true), "PLATFORM_ADMIN_REQUIRED");
  }

  @Test
  void tenantAndProjectIsolation() {
    Map<String, Object> other =
        map("id", 999999L, "email", "other@example.com", "platform_role", "USER");
    SaasContext.set(other);
    fails(() -> world.graph(project), "PROJECT_NOT_FOUND");
    fails(() -> world.projects(tenant), "TENANT_NOT_FOUND");
    fails(() -> spaces.admin("users"), "PLATFORM_ADMIN_REQUIRED");
  }

  @Test
  void optimisticVersionPreventsLostUpdates() {
    Map<String, Object> c = save("characters", "name", "庄周");
    Map<String, Object> b = map("name", "新名字", "visibility", "PUBLIC", "revision", 0);
    world.save(project, "characters", num(c, "id", 0), b);
    fails(() -> world.save(project, "characters", num(c, "id", 0), b), "ENTITY_CONFLICT");
    assertEquals("新名字", db.get("ng_character", num(c, "id", 0)).get("name"));
  }

  @Test
  void crossProjectReferenceRollback() {
    long another = num((Map<String, Object>) world.create(tenant, map("title", "另一个世界")), "id", 0);
    Map<String, Object> alien =
        (Map<String, Object>)
            world.save(another, "characters", 0, map("name", "异世界人物", "visibility", "PUBLIC"));
    Map<String, Object> c = save("characters", "name", "庄周");
    fails(
        () -> save("relations", "sourceId", c.get("id"), "targetId", alien.get("id")),
        "ENTITY_NOT_FOUND");
    assertEquals(
        0L,
        db.sql.queryForObject(
            "SELECT COUNT(*) FROM ng_relation WHERE project_id=?", Long.class, project));
  }

  @Test
  void causalCycleRollsBack() {
    Map<String, Object> a = save("events", "name", "事件甲"), b = save("events", "name", "事件乙");
    save("dependencies", "sourceId", a.get("id"), "targetId", b.get("id"));
    fails(
        () -> save("dependencies", "sourceId", b.get("id"), "targetId", a.get("id")),
        "SETTING_CONFLICT");
    assertEquals(
        1L,
        db.sql.queryForObject(
            "SELECT COUNT(*) FROM ng_dependency WHERE project_id=?", Long.class, project));
  }

  @Test
  void readerExportHonorsRevealChapter() {
    save("characters", "name", "庄周");
    save("secrets", "name", "井下真相", "visibility", "REVEALED", "revealChapter", 30);
    Map<String, Object> b =
        map(
            "format",
            "JSON",
            "perspective",
            "READER",
            "includeSecrets",
            true,
            "scope",
            map("chapterTo", 20));
    assertFalse(export.preview(project, b).get("content").toString().contains("井下真相"));
    b.put("scope", map("chapterTo", 30));
    assertTrue(export.preview(project, b).get("content").toString().contains("井下真相"));
  }

  @Test
  void snapshotsRetainDeletedEdgesAndRestore() {
    Map<String, Object> a = save("characters", "name", "甲"), b = save("characters", "name", "乙");
    save("relations", "name", "好友", "sourceId", a.get("id"), "targetId", b.get("id"));
    world.deleteEntity(project, "characters", num(a, "id", 0), map("revision", 0));
    long sid = num((Map<String, Object>) world.snapshot(project, map("name", "删除后快照")), "id", 0);
    Map<String, Object> content = db.obj(db.get("ng_snapshot", sid).get("content"));
    assertEquals(1, db.list(content.get("relations")).size());
    long rev = num(db.get("ng_project", project), "revision", 0);
    world.restore(project, sid, map("baseRevision", rev));
    assertEquals(
        1L,
        db.sql.queryForObject(
            "SELECT COUNT(*) FROM ng_relation WHERE project_id=? AND deleted=1",
            Long.class,
            project));
  }

  @Test
  void hardProjectDeleteCascadesEveryChild() {
    save("characters", "name", "甲");
    world.snapshot(project, map("name", "恢复点"));
    world.layout(
        project, map("baseLayoutRevision", 0, "layout", map("nodes", Collections.emptyList())));
    world.delete(project);
    for (String table :
        Arrays.asList("ng_character", "ng_snapshot", "ng_view", "saas_project_member"))
      assertEquals(
          0L,
          db.sql.queryForObject(
              "SELECT COUNT(*) FROM " + table + " WHERE project_id=?", Long.class, project));
  }

  @Test
  void copyRemapsReferencesAndLayout() {
    Map<String, Object> a = save("characters", "name", "甲"), b = save("characters", "name", "乙");
    save("relations", "sourceId", a.get("id"), "targetId", b.get("id"));
    world.layout(
        project,
        map(
            "baseLayoutRevision",
            0,
            "layout",
            map(
                "nodes",
                Arrays.asList(map("kind", "characters", "id", a.get("id"), "x", 40, "y", 80)))));
    Map<String, Object> g = (Map<String, Object>) world.copy(project, map("title", "副本"));
    long id = num(db.obj(g.get("project")), "id", 0);
    assertNotEquals(project, id);
    Set<Long> ids = new HashSet<>();
    for (Map<String, Object> c : db.list(g.get("characters"))) ids.add(num(c, "id", 0));
    assertFalse(ids.contains(num(a, "id", 0)));
    Map<String, Object> relation = db.list(g.get("relations")).get(0);
    assertTrue(ids.contains(num(relation, "source_id", 0)));
    Map<String, Object> node =
        db.list(db.obj(db.list(g.get("views")).get(0).get("layout")).get("nodes")).get(0);
    assertTrue(ids.contains(num(node, "id", 0)));
  }

  @Test
  void viewerCannotEditExportOrSeeSecretCoordinates() {
    Map<String, Object> hidden = save("characters", "name", "秘密人物", "visibility", "AUTHOR_ONLY");
    world.layout(
        project,
        map(
            "baseLayoutRevision",
            0,
            "layout",
            map(
                "nodes",
                Arrays.asList(map("kind", "characters", "id", hidden.get("id"), "x", 0, "y", 0)))));
    String email = "viewer" + UUID.randomUUID() + "@example.com";
    auth.register(map("email", email, "password", "Test-password-123"));
    Map<String, Object> v = db.query("SELECT * FROM saas_user WHERE email=?", email).get(0);
    db.insert("saas_member", map("tenant_id", tenant, "user_id", v.get("id"), "role", "MEMBER"));
    world.permissions(project, map("userId", v.get("id"), "role", "VIEWER"));
    SaasContext.set(v);
    Map<String, Object> g = world.graph(project);
    assertFalse(db.json(g).contains("秘密人物"));
    assertTrue(
        db.list(db.obj(db.list(g.get("views")).get(0).get("layout")).get("nodes")).isEmpty());
    fails(() -> world.save(project, "characters", 0, map("name", "越权")), "EDIT_DENIED");
    fails(() -> export.preview(project, map()), "EXPORT_DENIED");
  }

  @Test
  void orderIdempotencyRenewalAndEntitlementSnapshot() {
    Map<String, Object> b =
        map("planCode", "PRO", "months", 1, "requestKey", UUID.randomUUID().toString());
    Map<String, Object> o = (Map<String, Object>) spaces.order(tenant, b);
    assertEquals(o.get("id"), ((Map<String, Object>) spaces.order(tenant, b)).get("id"));
    user.put("platform_role", "ADMIN");
    spaces.adminUpdate("orders", num(o, "id", 0), map("status", "PAID"));
    Date first = (Date) db.get("saas_tenant", tenant).get("subscription_until");
    spaces.adminUpdate("orders", num(o, "id", 0), map("status", "PAID"));
    assertEquals(first, db.get("saas_tenant", tenant).get("subscription_until"));
    Map<String, Object> next =
        (Map<String, Object>)
            spaces.order(
                tenant,
                map("planCode", "PRO", "months", 1, "requestKey", UUID.randomUUID().toString()));
    spaces.adminUpdate("orders", num(next, "id", 0), map("status", "PAID"));
    assertTrue(((Date) db.get("saas_tenant", tenant).get("subscription_until")).after(first));
    db.sql.update(
        "UPDATE saas_plan SET limits=? WHERE code='PRO'",
        db.json(map("projects", 1, "entities", 1, "snapshots", 1, "seats", 1)));
    assertEquals(
        30L,
        num(db.obj(((Map<String, Object>) spaces.overview(tenant)).get("limits")), "projects", 0));
  }

  @Test
  void httpRequiresCookieCsrfAndAllowedOrigin() throws Exception {
    SaasContext.clear();
    http.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    Map<String, Object> login =
        auth.login(map("email", user.get("email"), "password", "Test-password-123"));
    Cookie cookie = new Cookie("ng_session", login.get("token").toString());
    http.perform(
            post("/api/v1/tenants")
                .cookie(cookie)
                .contentType("application/json")
                .content("{\"name\":\"测试\"}"))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/v1/tenants")
                .cookie(cookie)
                .header("X-CSRF-Token", login.get("csrf"))
                .header("Origin", "https://evil.example")
                .contentType("application/json")
                .content("{\"name\":\"测试\"}"))
        .andExpect(status().isForbidden());
    http.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isOk());
  }

  @Test
  void exactDatesAndConflictingChangesRollback() {
    Map<String, Object>
        a = save("events", "name", "晚事件", "timeKind", "EXACT", "startsAt", "2026-10-10T12:00:00"),
        b = save("events", "name", "早事件", "timeKind", "EXACT", "startsAt", "2026-10-09T12:00:00");
    fails(
        () -> save("dependencies", "sourceId", a.get("id"), "targetId", b.get("id")),
        "SETTING_CONFLICT");
    fails(
        () -> save("events", "timeKind", "RANGE", "startsAt", "2026-10-10", "endsAt", "2026-10-09"),
        "SETTING_CONFLICT");
  }

  @Test
  void quotaProtectsConcurrentCreates() throws Exception {
    world.create(tenant, map("title", "第二个"));
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
    List<java.util.concurrent.Future<Boolean>> results = new ArrayList<>();
    for (int n = 0; n < 2; n++)
      results.add(
          pool.submit(
              () -> {
                SaasContext.set(user);
                try {
                  gate.await();
                  world.create(tenant, map("title", "并发世界"));
                  return true;
                } catch (Problem e) {
                  assertEquals("QUOTA_EXCEEDED", e.code);
                  return false;
                } finally {
                  SaasContext.clear();
                }
              }));
    gate.countDown();
    int passed = 0;
    try {
      for (java.util.concurrent.Future<Boolean> f : results) if (f.get()) passed++;
    } finally {
      pool.shutdownNow();
    }
    assertEquals(1, passed);
    assertEquals(
        3L,
        db.sql.queryForObject(
            "SELECT COUNT(*) FROM ng_project WHERE tenant_id=?", Long.class, tenant));
  }

  @Test
  void teamTransferNeedsRecipientConfirmationAndDeletionCleansTenant() {
    long team = num((Map<String, Object>) spaces.create(map("name", "测试团队")), "id", 0);
    String email = "member" + UUID.randomUUID() + "@example.com";
    auth.register(map("email", email, "password", "Test-password-123"));
    Map<String, Object> next = db.query("SELECT * FROM saas_user WHERE email=?", email).get(0);
    db.insert("saas_member", map("tenant_id", team, "user_id", next.get("id"), "role", "MEMBER"));
    world.create(team, map("title", "团队作品"));
    spaces.tenantAction(team, "transfer", map("userId", next.get("id")));
    assertEquals(user.get("id"), db.get("saas_tenant", team).get("owner_id"));
    fails(() -> spaces.tenantAction(team, "accept-transfer", map()), "TRANSFER_INVALID");
    SaasContext.set(next);
    spaces.tenantAction(team, "accept-transfer", map());
    assertEquals(next.get("id"), db.get("saas_tenant", team).get("owner_id"));
    assertEquals(
        1L,
        db.sql.queryForObject(
            "SELECT COUNT(*) FROM saas_member WHERE tenant_id=? AND role='OWNER'",
            Long.class,
            team));
    spaces.tenantAction(team, "delete", map("confirmName", "测试团队"));
    assertEquals(
        0L,
        db.sql.queryForObject(
            "SELECT COUNT(*) FROM saas_member WHERE tenant_id=?", Long.class, team));
  }

  @Test
  void invitationRevokeInvalidatesToken() {
    db.sql.update("UPDATE saas_tenant SET plan_code='PRO' WHERE id=?", tenant);
    Map<String, Object> i =
        (Map<String, Object>) spaces.invite(tenant, map("email", "invite@example.com"));
    spaces.invitation(tenant, num(i, "id", 0), false);
    assertEquals("REVOKED", db.get("saas_invitation", num(i, "id", 0)).get("status"));
    assertEquals(
        1L, num(db.obj(((Map<String, Object>) spaces.overview(tenant)).get("usage")), "seats", 0));
  }

  @Test
  void representativeDatasetImportsChecksAndExports() throws Exception {
    db.sql.update("UPDATE saas_tenant SET plan_code='TEAM' WHERE id=?", tenant);
    List<Map<String, Object>> characters = new ArrayList<>(),
        events = new ArrayList<>(),
        participants = new ArrayList<>();
    for (int n = 1; n <= 200; n++)
      characters.add(map("id", n, "name", "人物" + n, "visibility", "PUBLIC", "data", "{}"));
    for (int n = 1; n <= 500; n++)
      events.add(
          map("id", n, "name", "事件" + n, "visibility", "PUBLIC", "story_seq", n, "data", "{}"));
    for (int n = 1; n <= 1000; n++)
      participants.add(
          map(
              "id",
              n,
              "name",
              "参与" + n,
              "visibility",
              "PUBLIC",
              "event_id",
              (n - 1) % 500 + 1,
              "character_id",
              (n - 1) % 200 + 1,
              "role",
              "参与",
              "data",
              "{}"));
    long start = System.nanoTime();
    Map<String, Object> g =
        (Map<String, Object>)
            world.importCommit(
                tenant,
                map(
                    "schemaVersion",
                    "1.0",
                    "project",
                    map("title", "200人物500事件1000关联"),
                    "characters",
                    characters,
                    "events",
                    events,
                    "participants",
                    participants));
    long importMs = (System.nanoTime() - start) / 1000000;
    long id = num(db.obj(g.get("project")), "id", 0);
    assertEquals(200, db.list(g.get("characters")).size());
    assertEquals(500, db.list(g.get("events")).size());
    assertEquals(1000, db.list(g.get("participants")).size());
    assertTrue(Boolean.TRUE.equals(world.validate(id).get("passed")));
    long graphStart = System.nanoTime();
    String graphJson = db.json(world.graph(id));
    long graphMs = (System.nanoTime() - graphStart) / 1000000;
    long exportStart = System.nanoTime();
    Map<String, Object> output = export.preview(id, map("format", "JSON", "perspective", "AUTHOR"));
    long exportMs = (System.nanoTime() - exportStart) / 1000000;
    assertTrue(output.get("content").toString().contains("人物200"));
    assertTrue(graphJson.length() > 10000);
    java.nio.file.Files.write(
        java.nio.file.Paths.get("target/performance.json"),
        db.json(
                map(
                    "dataset",
                    map("characters", 200, "events", 500, "associations", 1000),
                    "importMs",
                    importMs,
                    "graphAndSerializeMs",
                    graphMs,
                    "exportMs",
                    exportMs,
                    "environment",
                    "isolated H2 integration test"))
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
