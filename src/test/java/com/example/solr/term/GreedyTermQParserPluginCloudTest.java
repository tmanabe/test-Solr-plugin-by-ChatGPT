package com.example.solr.term;

import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.cloud.SolrCloudTestCase;
import org.junit.BeforeClass;
import org.junit.Test;

public class GreedyTermQParserPluginCloudTest
        extends SolrCloudTestCase {

    private static final String COLLECTION = "greedy_test";

    @BeforeClass
    public static void setupCluster() throws Exception {
        configureCluster(2)
                .addConfig("conf", configset("greedy-config"))
                .configure();

        // Implicit router allows explicit shard placement.
        CollectionAdminRequest.createCollectionWithImplicitRouter(
                        COLLECTION, "conf", "shard1,shard2", 1)
                .process(cluster.getSolrClient());
    }

    private void addDoc(
            String shard, String id, String category) throws Exception {

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", id);
        doc.addField("category", category);

        UpdateRequest req = new UpdateRequest();
        req.add(doc);
        req.setParam("_route_", shard);
        req.process(cluster.getSolrClient(), COLLECTION);
    }

    private void indexTestDocs() throws Exception {
        // shard1: notepc=3, pc=2
        for (int i = 0; i < 3; i++) {
            addDoc("shard1", "n1_" + i, "notepc");
        }
        for (int i = 0; i < 2; i++) {
            addDoc("shard1", "p1_" + i, "pc");
        }

        // shard2: notepc=1, pc=3
        addDoc("shard2", "n2_0", "notepc");
        for (int i = 0; i < 3; i++) {
            addDoc("shard2", "p2_" + i, "pc");
        }

        cluster.getSolrClient().commit(COLLECTION);
    }

    @Test
    public void testShardLocalFallback() throws Exception {
        indexTestDocs();

        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "*:*");
        params.set(
                "fq",
                "{!greedy field=category min=3 terms='notepc,pc'}");

        QueryResponse rsp =
                cluster.getSolrClient().query(COLLECTION, params);

        // shard1 selects notepc (df=3)
        // shard2 selects pc     (df=1 for notepc)
        assertEquals(6, rsp.getResults().getNumFound());

        // Expected IDs:
        // n1_0, n1_1, n1_2, p2_0, p2_1, p2_2
        assertEquals(6, rsp.getResults().size());
    }
}
