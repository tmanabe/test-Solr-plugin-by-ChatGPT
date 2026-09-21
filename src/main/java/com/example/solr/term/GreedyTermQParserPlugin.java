
package com.example.solr.term;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.request.SolrQueryRequest;

public class GreedyTermQParserPlugin extends QParserPlugin {

    @Override
    public QParser createParser(
            String qstr,
            SolrParams localParams,
            SolrParams params,
            SolrQueryRequest req) {

        return new QParser(qstr, localParams, params, req) {
            @Override
            public Query parse() {
                String fieldName = localParams.get("field");
                String[] terms = localParams.getParams("terms");
                int min = localParams.getInt("min");

                if (fieldName == null || terms == null || terms.length == 0) {
                    throw new SolrException(
                            SolrException.ErrorCode.BAD_REQUEST,
                            "field and terms are required");
                }

                if (min < 0) {
                    throw new SolrException(
                            SolrException.ErrorCode.BAD_REQUEST,
                            "min must be >= 0");
                }

                List<String> candidates = Arrays.stream(terms)
                        .flatMap(s -> StrUtils.splitSmart(s, ',', true).stream())
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

                if (candidates.isEmpty()) {
                    throw new SolrException(
                            SolrException.ErrorCode.BAD_REQUEST,
                            "terms must not be empty");
                }

                IndexSchema schema = req.getSchema();
                SchemaField schemaField = schema.getFieldOrNull(fieldName);

                if (schemaField == null) {
                    throw new SolrException(
                            SolrException.ErrorCode.BAD_REQUEST,
                            "Unknown field: " + fieldName);
                }

                SolrIndexSearcher searcher = req.getSearcher();

                String selected = candidates.get(candidates.size() - 1);

                for (String candidate : candidates) {
                    String indexed = schemaField.getType()
                            .readableToIndexed(candidate);

                    int df;
                    try {
                        df = searcher.docFreq(new Term(fieldName, indexed));
                    } catch (IOException e) {
                        throw new SolrException(
                                SolrException.ErrorCode.SERVER_ERROR,
                                "Failed to read docFreq", e);
                    }

                    if (df >= min) {
                        selected = candidate;
                        break;
                    }
                }

                String indexed = schemaField.getType()
                        .readableToIndexed(selected);

                return new TermQuery(new Term(fieldName, indexed));
            }
        };
    }
}
