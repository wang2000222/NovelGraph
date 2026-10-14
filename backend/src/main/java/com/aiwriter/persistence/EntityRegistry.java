package com.aiwriter.persistence;

import com.aiwriter.entity.*;
import com.aiwriter.mapper.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.*;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** 只允许代码中注册的表，避免将HTTP参数作为SQL标识符。 */
@Component
public class EntityRegistry {
  private final ApplicationContext context;
  private final Map<String, Class<?>> entities = new HashMap<>(), mappers = new HashMap<>();

  public EntityRegistry(ApplicationContext context) {
    this.context = context;
    register("saas_user", SaasUser.class, SaasUserMapper.class);
    register("saas_session", SaasSession.class, SaasSessionMapper.class);
    register("saas_tenant", SaasTenant.class, SaasTenantMapper.class);
    register("saas_member", SaasMember.class, SaasMemberMapper.class);
    register("saas_plan", SaasPlan.class, SaasPlanMapper.class);
    register("saas_invitation", SaasInvitation.class, SaasInvitationMapper.class);
    register("saas_email", SaasEmail.class, SaasEmailMapper.class);
    register("saas_verification", SaasVerification.class, SaasVerificationMapper.class);
    register("saas_order", SaasOrder.class, SaasOrderMapper.class);
    register("saas_notification", SaasNotification.class, SaasNotificationMapper.class);
    register("saas_audit", SaasAudit.class, SaasAuditMapper.class);
    register("saas_setting", SaasSetting.class, SaasSettingMapper.class);
    register("ng_project", NgProject.class, NgProjectMapper.class);
    register("saas_project_member", SaasProjectMember.class, SaasProjectMemberMapper.class);
    register("ng_extraction_batch", NgExtractionBatch.class, NgExtractionBatchMapper.class);
    register("ng_task", NgTask.class, NgTaskMapper.class);
    register("ng_character", NgCharacter.class, NgCharacterMapper.class);
    register("ng_location", NgLocation.class, NgLocationMapper.class);
    register("ng_faction", NgFaction.class, NgFactionMapper.class);
    register("ng_world_rule", NgWorldRule.class, NgWorldRuleMapper.class);
    register("ng_secret", NgSecret.class, NgSecretMapper.class);
    register("ng_event", NgEvent.class, NgEventMapper.class);
    register("ng_relation", NgRelation.class, NgRelationMapper.class);
    register("ng_participant", NgParticipant.class, NgParticipantMapper.class);
    register("ng_dependency", NgDependency.class, NgDependencyMapper.class);
    register("ng_relation_change", NgRelationChange.class, NgRelationChangeMapper.class);
    register("ng_faction_member", NgFactionMember.class, NgFactionMemberMapper.class);
    register("ng_view", NgView.class, NgViewMapper.class);
    register("ng_snapshot", NgSnapshot.class, NgSnapshotMapper.class);
  }

  private void register(String table, Class<?> entity, Class<?> mapper) {
    entities.put(table, entity);
    mappers.put(table, mapper);
  }

  public Class<?> entity(String table) {
    if (!entities.containsKey(table)) throw new IllegalArgumentException("未注册数据表");
    return entities.get(table);
  }

  public BaseMapper mapper(String table) {
    entity(table);
    return (BaseMapper) context.getBean(mappers.get(table));
  }
}
