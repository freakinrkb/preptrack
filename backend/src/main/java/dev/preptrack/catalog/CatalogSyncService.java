package dev.preptrack.catalog;

import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

@Service
public class CatalogSyncService {
    private final JdbcTemplate jdbc;
    private final DsaSourceAdapter adapter;
    private final ObjectMapper json;
    private static final String SOURCE = "leetcode-company-wise-problems";

    public CatalogSyncService(JdbcTemplate jdbc,DsaSourceAdapter adapter,ObjectMapper json){this.jdbc=jdbc;this.adapter=adapter;this.json=json;}

    public Preview preview() throws Exception {
        var snapshot=adapter.fetch();
        long additions=0, updates=0, metadataUpdates=0, associations=0;
        List<Change> changes=new ArrayList<>();
        Set<String> incomingKeys=snapshot.items().stream().map(DsaSourceAdapter.Item::externalKey).collect(java.util.stream.Collectors.toSet());
        for(var item:snapshot.items()){
            Integer present=jdbc.queryForObject("SELECT count(*) FROM content_item WHERE canonical_url=?",Integer.class,item.url());
            Integer sourcePresent=jdbc.queryForObject("SELECT count(*) FROM source_item si JOIN source s ON s.id=si.source_id WHERE s.slug=? AND si.external_key=?",Integer.class,SOURCE,item.externalKey());
            if(present==null||present==0){additions++;if(changes.size()<60)changes.add(new Change("NEW_QUESTION",item.title(),item.company(),item.url()));}
            else {
                String old=jdbc.queryForObject("SELECT canonical_title FROM content_item WHERE canonical_url=?",String.class,item.url());
                if(!item.title().equals(old)){updates++;if(changes.size()<60)changes.add(new Change("TITLE_CHANGED",item.title(),item.company(),old+" → "+item.title()));}
                if(sourcePresent==null||sourcePresent==0){associations++;if(changes.size()<60)changes.add(new Change("COMPANY_ADDED",item.title(),item.company(),"Question now appears in this company’s list"));}
                else {
                    String existing=jdbc.queryForObject("SELECT si.raw_metadata_json::text FROM source_item si JOIN source s ON s.id=si.source_id WHERE s.slug=? AND si.external_key=?",String.class,SOURCE,item.externalKey());
                    var oldMeta=json.readTree(existing);var changed=new ArrayList<String>();
                    compare(changed,"difficulty",oldMeta.path("difficulty").asText(""),item.metadata().getOrDefault("difficulty",""));
                    compare(changed,"frequency",oldMeta.path("frequency").asText(""),item.metadata().getOrDefault("frequency",""));
                    compare(changed,"acceptance rate",oldMeta.path("acceptanceRate").asText(""),item.metadata().getOrDefault("acceptance rate",""));
                    var oldTopics=new TreeSet<String>();oldMeta.path("topics").forEach(t->oldTopics.add(t.asText()));
                    if(!oldTopics.equals(new TreeSet<>(item.topics())))changed.add("topics");
                    if(!changed.isEmpty()){metadataUpdates++;if(changes.size()<60)changes.add(new Change("SOURCE_METADATA_CHANGED",item.title(),item.company(),String.join(", ",changed)));}
                }
            }
        }
        var activeKeys=jdbc.queryForList("SELECT si.external_key FROM source_item si JOIN source s ON s.id=si.source_id WHERE s.slug=? AND si.active=true",String.class,SOURCE);
        long removals=0;
        for(String key:activeKeys)if(!incomingKeys.contains(key)){removals++;if(changes.size()<60){String[] parts=key.split("/",3);changes.add(new Change("SOURCE_REMOVED",parts.length>2?parts[2]:key,parts.length>0?parts[0]:"",key));}}
        return new Preview(snapshot.fingerprint(),snapshot.items().size(),snapshot.items().stream().map(DsaSourceAdapter.Item::company).distinct().count(),additions,updates,metadataUpdates,associations,removals,changes);
    }

    @Transactional
    public ApplyResult apply(String expectedFingerprint,String actorEmail) throws Exception {
        var snapshot=adapter.fetch();
        if(!snapshot.fingerprint().equals(expectedFingerprint))throw new IllegalStateException("The upstream catalogue changed after preview. Run preview again.");
        UUID sourceId=jdbc.queryForObject("SELECT id FROM source WHERE slug=?",UUID.class,SOURCE);
        UUID revisionId=jdbc.queryForObject("INSERT INTO source_revision(source_id,revision_ref,status,summary) VALUES (?,?,'APPLIED',?::jsonb) " +
            "ON CONFLICT(source_id,revision_ref) DO UPDATE SET fetched_at=now(),status='APPLIED',summary=excluded.summary RETURNING id",
            UUID.class,sourceId,snapshot.fingerprint(),"{\"items\":"+snapshot.items().size()+"}");
        Set<String> seen=new HashSet<>(); int inserted=0;
        for(var item:snapshot.items()){
            String companySlug=slug(item.company());
            UUID companyId=jdbc.queryForObject("INSERT INTO company(name,slug) VALUES (?,?) ON CONFLICT(name) DO UPDATE SET name=excluded.name RETURNING id",UUID.class,item.company(),companySlug);
            Integer exists=jdbc.queryForObject("SELECT count(*) FROM content_item WHERE canonical_url=?",Integer.class,item.url());
            UUID contentId=jdbc.queryForObject("INSERT INTO content_item(track,kind,canonical_title,canonical_url) VALUES ('DSA','QUESTION',?,?) " +
                "ON CONFLICT(canonical_url) DO UPDATE SET canonical_title=excluded.canonical_title,lifecycle='ACTIVE',updated_at=now() RETURNING id",UUID.class,item.title(),item.url());
            if(exists!=null&&exists==0)inserted++;
            String metadata=json.writeValueAsString(Map.of("difficulty",item.metadata().getOrDefault("difficulty",""),"frequency",item.metadata().getOrDefault("frequency",""),"acceptanceRate",item.metadata().getOrDefault("acceptance rate",""),"company",item.company(),"topics",item.topics(),"window","All"));
            UUID sourceItemId=jdbc.queryForObject("INSERT INTO source_item(source_id,external_key,content_item_id,raw_metadata_json,active,last_seen_revision_id) VALUES (?,?,?,?::jsonb,true,?) " +
                "ON CONFLICT(source_id,external_key) DO UPDATE SET content_item_id=excluded.content_item_id,raw_metadata_json=excluded.raw_metadata_json,active=true,last_seen_revision_id=excluded.last_seen_revision_id RETURNING id",
                UUID.class,sourceId,item.externalKey(),contentId,metadata,revisionId);
            jdbc.update("INSERT INTO content_company(content_item_id,company_id,evidence_source_item_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",contentId,companyId,sourceItemId);
            for(String name:item.topics()){
                String topicSlug=slug(name);
                UUID topicId=jdbc.queryForObject("INSERT INTO topic(name,slug,provenance) VALUES (?,?,'SOURCE') ON CONFLICT(name) DO UPDATE SET name=excluded.name RETURNING id",UUID.class,name,topicSlug);
                jdbc.update("INSERT INTO content_topic(content_item_id,topic_id) VALUES (?,?) ON CONFLICT DO NOTHING",contentId,topicId);
            }
            seen.add(item.externalKey());
        }
        // Full “All” lists allow safe detection of removed associations; keep rows and learning history.
        jdbc.update("UPDATE source_item SET active=false WHERE source_id=? AND active=true AND last_seen_revision_id<>?",sourceId,revisionId);
        jdbc.update("UPDATE content_item ci SET lifecycle='ARCHIVED',updated_at=now() WHERE ci.track='DSA' AND EXISTS " +
            "(SELECT 1 FROM source_item own WHERE own.content_item_id=ci.id AND own.source_id=?) AND NOT EXISTS " +
            "(SELECT 1 FROM source_item si WHERE si.content_item_id=ci.id AND si.active=true)",sourceId);
        jdbc.update("DELETE FROM content_topic ct USING topic t, content_item ci WHERE ct.topic_id=t.id AND ct.content_item_id=ci.id " +
            "AND ci.track='DSA' AND t.provenance='SOURCE' AND NOT EXISTS (SELECT 1 FROM source_item si, " +
            "jsonb_array_elements_text(si.raw_metadata_json->'topics') AS source_topic(value) WHERE si.content_item_id=ci.id AND si.active=true AND source_topic.value=t.name)");
        jdbc.update("INSERT INTO audit_event(actor_user_id,event_type,safe_metadata) SELECT id,'DSA_CATALOG_SYNC',jsonb_build_object('revision',?,'items',?) FROM user_account WHERE email=?",snapshot.fingerprint(),snapshot.items().size(),actorEmail);
        return new ApplyResult(snapshot.fingerprint(),snapshot.items().size(),inserted);
    }

    private static String slug(String value){String slug=Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");return slug.isBlank()?"topic":slug;}
    private static void compare(List<String> changes,String name,String oldValue,String newValue){if(!Objects.equals(oldValue,newValue))changes.add(name);}
    public record Change(String type,String title,String company,String detail){}
    public record Preview(String fingerprint,long rows,long companies,long newQuestions,long titleUpdates,long metadataUpdates,long newCompanyAssociations,long removedAssociations,List<Change> changes){}
    public record ApplyResult(String fingerprint,long rows,long newQuestions){}
}
